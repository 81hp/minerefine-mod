package minerefinehud.boss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The "about to respawn" reminder.
 *
 * Only for a boss whose timer can be trusted: it is dead, waiting to respawn, and its interval has
 * been measured at least twice, which is what the panel shows without a "?". A reminder built on a
 * single sample would ring at the wrong moment, and a reminder that cries wolf gets switched off.
 *
 * Two questions, kept apart:
 *
 *   {@link #active}   which bosses the title should show right now. Stateless.
 *   {@link #newlyDue} which just entered the window, so the sound plays once per respawn rather
 *                     than every tick for ten seconds.
 *
 * No Minecraft types.
 */
public final class BossAlerts {

    public static final long DEFAULT_LEAD_MS = 10_000L;

    /** Per boss, the kill the reminder last rang for. A new kill is a new cycle. */
    private final Map<String, Long> rungForKill = new HashMap<>();

    /** Inside the window: trusted timer, still counting down, and at most {@code leadMs} to go. */
    public static boolean due(BossTracker.BossView v, long leadMs) {
        return v.status() == BossTracker.Status.DEAD
                && v.intervalConfident()
                && v.etaMs().isPresent()
                && v.etaMs().getAsLong() > 0L
                && v.etaMs().getAsLong() <= leadMs;
    }

    public static List<BossTracker.BossView> active(List<BossTracker.BossView> views, long leadMs) {
        List<BossTracker.BossView> out = new ArrayList<>();
        for (BossTracker.BossView v : views) {
            if (due(v, leadMs)) {
                out.add(v);
            }
        }
        return out;
    }

    /** Bosses that entered the window since the last call, each at most once per kill. */
    public List<BossTracker.BossView> newlyDue(List<BossTracker.BossView> views, long nowMs, long leadMs) {
        List<BossTracker.BossView> out = new ArrayList<>();
        for (BossTracker.BossView v : views) {
            if (!due(v, leadMs)) {
                continue;
            }
            // A dead boss's sinceEventMs is measured from its kill, so this is the kill time.
            long killedAt = nowMs - v.sinceEventMs();
            Long rung = rungForKill.put(BossMessageParser.key(v.displayName()), killedAt);
            if (rung == null || rung != killedAt) {
                out.add(v);
            }
        }
        return out;
    }
}
