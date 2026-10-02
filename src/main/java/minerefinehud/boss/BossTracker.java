package minerefinehud.boss;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Tracks every boss the client has seen and works out its respawn interval by itself.
 *
 * The server announces both the spawn and the kill, which means the interval never has to be
 * configured: it is simply the gap between a kill and the next spawn of the same boss. Observed
 * intervals differ per boss (around three minutes for most, around fifteen for some), so each
 * boss learns its own.
 *
 * The cycle is anchored on the kill rather than on a wall clock, because the boss does not
 * despawn. A boss left alive for twenty minutes pushes the whole schedule back by twenty minutes,
 * and a clock-based timer would be wrong from that point on.
 *
 * No Minecraft types here: all times are plain epoch milliseconds supplied by the caller.
 */
public final class BossTracker {

    public enum Status {
        /** Never seen. */
        UNKNOWN,
        /** Spawn seen, no kill since. */
        ALIVE,
        /** Kill seen, waiting for the next spawn. */
        DEAD
    }

    /** Gaps outside this range are treated as a missed message rather than a real interval. */
    public static final long MIN_PLAUSIBLE_INTERVAL_MS = 10_000L;
    public static final long MAX_PLAUSIBLE_INTERVAL_MS = 60L * 60_000L;

    /** Rolling window of learned gaps. Median of these is the estimate. */
    private static final int MAX_SAMPLES = 7;

    /**
     * Explicit "no timestamp recorded" marker. Zero must not be used for this: it is a
     * legitimate instant, and treating it as unset silently discards a learned interval.
     */
    public static final long UNSET = Long.MIN_VALUE;

    private final Map<String, Record> bosses = new HashMap<>();

    // ---------------------------------------------------------------- events

    /** Call when the server broadcasts that a boss has spawned. */
    public void onSpawned(String bossName, long nowMs) {
        Record r = recordFor(bossName);

        // Only learn an interval when we actually watched the whole gap. awaitingSpawn is
        // cleared by any disconnect or world change, so a gap spanning an absence is discarded
        // instead of poisoning the estimate.
        if (r.awaitingSpawn && r.lastSlainAt != UNSET) {
            long gap = nowMs - r.lastSlainAt;
            if (gap >= MIN_PLAUSIBLE_INTERVAL_MS && gap <= MAX_PLAUSIBLE_INTERVAL_MS) {
                r.intervals.addLast(gap);
                while (r.intervals.size() > MAX_SAMPLES) {
                    r.intervals.removeFirst();
                }
            }
        }

        r.status = Status.ALIVE;
        r.lastSpawnAt = nowMs;
        r.awaitingSpawn = false;
    }

    /** Call when the server broadcasts that a boss has been slain. */
    public void onSlain(String bossName, long nowMs) {
        Record r = recordFor(bossName);
        r.status = Status.DEAD;
        r.lastSlainAt = nowMs;
        r.awaitingSpawn = true;
    }

    /**
     * Call on disconnect, server switch or world change. Timers keep running, but no interval
     * will be learned across the break, because the kill-to-spawn gap we would measure may
     * contain events we never saw.
     */
    public void onContinuityBreak() {
        for (Record r : bosses.values()) {
            r.awaitingSpawn = false;
        }
    }

    // ----------------------------------------------------------------- views

    public Optional<BossView> view(String bossName, long nowMs) {
        Record r = bosses.get(BossMessageParser.key(bossName));
        return r == null ? Optional.empty() : Optional.of(r.toView(nowMs));
    }

    /** Every known boss, soonest spawn first, with live bosses pinned to the top. */
    public List<BossView> views(long nowMs) {
        List<BossView> out = new ArrayList<>(bosses.size());
        for (Record r : bosses.values()) {
            out.add(r.toView(nowMs));
        }
        out.sort(Comparator
                .comparingInt((BossView v) -> v.status() == Status.ALIVE ? 0 : 1)
                .thenComparingLong(v -> v.etaMs().orElse(Long.MAX_VALUE))
                .thenComparing(BossView::displayName));
        return out;
    }

    public int size() {
        return bosses.size();
    }

    // ----------------------------------------------------------- persistence

    /** Flat form for writing to disk, so timers survive a relog. */
    public record Persisted(String displayName, Status status, long lastSpawnAt,
                            long lastSlainAt, List<Long> intervals) {}

    public List<Persisted> export() {
        List<Persisted> out = new ArrayList<>(bosses.size());
        for (Record r : bosses.values()) {
            out.add(new Persisted(r.displayName, r.status, r.lastSpawnAt, r.lastSlainAt,
                    new ArrayList<>(r.intervals)));
        }
        return out;
    }

    public void importAll(List<Persisted> saved) {
        if (saved == null) {
            return;
        }
        for (Persisted p : saved) {
            Record r = recordFor(p.displayName());
            r.status = p.status() == null ? Status.UNKNOWN : p.status();
            r.lastSpawnAt = p.lastSpawnAt();
            r.lastSlainAt = p.lastSlainAt();
            r.intervals.clear();
            if (p.intervals() != null) {
                for (Long v : p.intervals()) {
                    if (v != null) {
                        r.intervals.addLast(v);
                    }
                }
            }
            // A restored session never counts as continuous.
            r.awaitingSpawn = false;
        }
    }

    // --------------------------------------------------------------- internals

    private Record recordFor(String bossName) {
        return bosses.computeIfAbsent(BossMessageParser.key(bossName), k -> new Record(bossName));
    }

    private static final class Record {
        final String displayName;
        Status status = Status.UNKNOWN;
        long lastSpawnAt = UNSET;
        long lastSlainAt = UNSET;
        boolean awaitingSpawn;
        final Deque<Long> intervals = new ArrayDeque<>();

        Record(String displayName) {
            this.displayName = displayName;
        }

        /** Median rather than mean, so one bad sample cannot drag the estimate off. */
        OptionalLong estimatedInterval() {
            if (intervals.isEmpty()) {
                return OptionalLong.empty();
            }
            List<Long> sorted = new ArrayList<>(intervals);
            sorted.sort(Long::compareTo);
            int mid = sorted.size() / 2;
            long median = sorted.size() % 2 == 1
                    ? sorted.get(mid)
                    : (sorted.get(mid - 1) + sorted.get(mid)) / 2;
            return OptionalLong.of(median);
        }

        BossView toView(long nowMs) {
            OptionalLong interval = estimatedInterval();

            OptionalLong eta = OptionalLong.empty();
            if (status == Status.DEAD && interval.isPresent() && lastSlainAt != UNSET) {
                eta = OptionalLong.of(lastSlainAt + interval.getAsLong() - nowMs);
            }

            long since = switch (status) {
                case ALIVE -> lastSpawnAt != UNSET ? nowMs - lastSpawnAt : 0L;
                case DEAD -> lastSlainAt != UNSET ? nowMs - lastSlainAt : 0L;
                case UNKNOWN -> 0L;
            };

            return new BossView(displayName, status, since, eta, intervals.size(), interval);
        }
    }

    /** Immutable snapshot handed to the HUD. */
    public record BossView(String displayName, Status status, long sinceEventMs,
                           OptionalLong etaMs, int sampleCount, OptionalLong intervalMs) {

        /** True once at least one clean kill-to-spawn gap has been measured. */
        public boolean intervalKnown() {
            return intervalMs.isPresent();
        }

        /** The estimate is still settling on a single sample. */
        public boolean intervalConfident() {
            return sampleCount >= 2;
        }

        /** Countdown has run out but no spawn was seen, so a message was probably missed. */
        public boolean overdue() {
            return etaMs.isPresent() && etaMs.getAsLong() < 0L;
        }
    }
}
