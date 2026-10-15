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
        r.respawnAt = UNSET;
        r.upFromIndex = false;
    }

    /** Call when the server broadcasts that a boss has been slain. */
    public void onSlain(String bossName, long nowMs) {
        Record r = recordFor(bossName);
        r.status = Status.DEAD;
        r.lastSlainAt = nowMs;
        r.awaitingSpawn = true;
        r.respawnAt = UNSET;
        r.upFromIndex = false;
    }

    // ------------------------------------------------------------ boss index

    /** Two readings of the same respawn timer this close together are the same respawn. */
    private static final long SAME_RESPAWN_MS = 5_000L;

    /**
     * How long a boss the /boss index showed up stays "UP" without its kill being heard. The
     * kill is only broadcast in the boss's own area, so for a boss elsewhere it never comes; after
     * this the boss shows as unknown rather than up for ever.
     */
    public static final long INDEX_UP_MS = 5L * 60_000L;

    /**
     * The /boss index menu shows this boss alive ("SPAWNED!"). A spawn that was missed is taken
     * as having happened now; nothing is learned from it, since its real time is unknown.
     *
     * A spawn heard, or an index reading, older than {@link #INDEX_UP_MS} is stale, so the menu's
     * reading renews it. Anything fresher is left alone: the menu is read twice a second.
     *
     * @return true if anything changed, so the caller knows to save
     */
    public boolean onIndexAlive(String bossName, long nowMs) {
        Record r = recordFor(bossName);
        if (r.status == Status.ALIVE && r.lastSpawnAt != UNSET && r.lastSpawnAt <= nowMs
                && nowMs - r.lastSpawnAt < INDEX_UP_MS) {
            return false;
        }
        r.status = Status.ALIVE;
        r.lastSpawnAt = nowMs;
        r.awaitingSpawn = false;
        r.respawnAt = UNSET;
        r.upFromIndex = true;
        return true;
    }

    /**
     * The /boss index menu shows this boss dead, back in {@code msLeft}. That is the server's own
     * timer, so the countdown becomes exact. If the kill was watched, kill to respawn is also a
     * real interval, learned once per kill.
     *
     * @return true if anything changed, so the caller knows to save
     */
    public boolean onIndexRespawnIn(String bossName, long msLeft, long nowMs) {
        Record r = recordFor(bossName);
        long respawnAt = nowMs + Math.max(0L, msLeft);
        if (r.status == Status.DEAD && r.respawnAt != UNSET
                && Math.abs(r.respawnAt - respawnAt) <= SAME_RESPAWN_MS) {
            return false;   // the menu is read twice a second; the same timer again
        }
        // The kill on record is stale when the respawn it led to is already over: the boss came
        // back and died again unseen, and this timer is for that newer kill.
        OptionalLong known = r.estimatedInterval();
        long previousRespawn = r.respawnAt != UNSET ? r.respawnAt
                : known.isPresent() && r.lastSlainAt != UNSET ? r.lastSlainAt + known.getAsLong() : UNSET;
        // A kill watched without a break would have had its respawn broadcast, so it is never stale.
        boolean staleKill = !r.awaitingSpawn && previousRespawn != UNSET
                && previousRespawn < nowMs - SAME_RESPAWN_MS;
        if (r.status != Status.DEAD || r.lastSlainAt == UNSET || r.lastSlainAt > nowMs || staleKill) {
            // The kill was missed. Put it where the learned interval says it was, so the reminder
            // has a fixed kill to ring once for; without an interval, now will do.
            OptionalLong interval = r.estimatedInterval();
            r.lastSlainAt = interval.isPresent() ? respawnAt - interval.getAsLong() : nowMs;
            r.awaitingSpawn = false;
        } else if (r.awaitingSpawn) {
            long gap = respawnAt - r.lastSlainAt;
            if (gap >= MIN_PLAUSIBLE_INTERVAL_MS && gap <= MAX_PLAUSIBLE_INTERVAL_MS) {
                r.intervals.addLast(gap);
                while (r.intervals.size() > MAX_SAMPLES) {
                    r.intervals.removeFirst();
                }
            }
            // Learned here; the spawn broadcast must not count the same gap a second time.
            r.awaitingSpawn = false;
        }
        r.status = Status.DEAD;
        r.respawnAt = respawnAt;
        r.upFromIndex = false;
        return true;
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
                            long lastSlainAt, List<Long> intervals, long respawnAt, boolean upFromIndex) {}

    public List<Persisted> export() {
        List<Persisted> out = new ArrayList<>(bosses.size());
        for (Record r : bosses.values()) {
            out.add(new Persisted(r.displayName, r.status, r.lastSpawnAt, r.lastSlainAt,
                    new ArrayList<>(r.intervals), r.respawnAt, r.upFromIndex));
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
            // Files from before the boss index have no respawnAt, which Gson reads as 0.
            r.respawnAt = p.respawnAt() > 0L ? p.respawnAt() : UNSET;
            r.upFromIndex = p.status() == Status.ALIVE && p.upFromIndex();
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
        /** When the /boss index said this boss is back, or UNSET. Beats the learned interval. */
        long respawnAt = UNSET;
        /** Up because the /boss index said so, not because the spawn was heard. */
        boolean upFromIndex;
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

            // Up per the /boss index, and its kill never heard: by now it is anyone's guess.
            if (status == Status.ALIVE && upFromIndex && nowMs - lastSpawnAt > INDEX_UP_MS) {
                return new BossView(displayName, Status.UNKNOWN, 0L, OptionalLong.empty(),
                        intervals.size(), interval, false, false);
            }

            OptionalLong eta = OptionalLong.empty();
            boolean fromIndex = status == Status.DEAD && respawnAt != UNSET;
            if (fromIndex) {
                eta = OptionalLong.of(respawnAt - nowMs);
            } else if (status == Status.DEAD && interval.isPresent() && lastSlainAt != UNSET) {
                eta = OptionalLong.of(lastSlainAt + interval.getAsLong() - nowMs);
            }

            long since = switch (status) {
                case ALIVE -> lastSpawnAt != UNSET ? nowMs - lastSpawnAt : 0L;
                case DEAD -> lastSlainAt != UNSET ? nowMs - lastSlainAt : 0L;
                case UNKNOWN -> 0L;
            };

            return new BossView(displayName, status, since, eta, intervals.size(), interval, fromIndex,
                    status == Status.ALIVE && upFromIndex);
        }
    }

    /** Immutable snapshot handed to the HUD. */
    /**
     * @param fromIndex   the countdown is the /boss index's own timer
     * @param upFromIndex up because the /boss index showed it spawned, its spawn not heard
     */
    public record BossView(String displayName, Status status, long sinceEventMs,
                           OptionalLong etaMs, int sampleCount, OptionalLong intervalMs,
                           boolean fromIndex, boolean upFromIndex) {

        public BossView(String displayName, Status status, long sinceEventMs,
                        OptionalLong etaMs, int sampleCount, OptionalLong intervalMs,
                        boolean fromIndex) {
            this(displayName, status, sinceEventMs, etaMs, sampleCount, intervalMs, fromIndex, false);
        }

        /**
         * True once the countdown can be shown: a clean kill-to-spawn gap has been measured, or
         * the /boss index gave the server's own timer.
         */
        public boolean intervalKnown() {
            return intervalMs.isPresent() || fromIndex;
        }

        /** The estimate is past its first sample, or is the server's own timer. */
        public boolean intervalConfident() {
            return sampleCount >= 2 || fromIndex;
        }

        /** Countdown has run out but no spawn was seen, so a message was probably missed. */
        public boolean overdue() {
            return etaMs.isPresent() && etaMs.getAsLong() < 0L;
        }
    }
}
