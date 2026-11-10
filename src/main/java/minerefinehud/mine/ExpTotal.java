package minerefinehud.mine;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * EXP per hour from the player's own total, read from the head in /profile ("TOTAL EXP: 653,952,884").
 * By Arreba.
 *
 * The action bar's "+EXP" says what one block gave, but the exact meaning of its number is not
 * certain (it climbed by the same step every block in a player's log). The total in /profile is
 * exact, so the rate is simply how much it rose between two readings, over the time between them,
 * and nothing can be counted twice.
 *
 * Readings come every {@link #EVERY_MS} while the player mines. After a gap longer than
 * {@link #MAX_GAP_MS} (the player stopped, or the check was held back) the next reading is only a
 * new starting point: whatever came in meanwhile (hunting, a quest) is not mined and is not counted.
 * A total that falls (a rebirth or prestige) starts again the same way.
 *
 * No Minecraft types; times are epoch milliseconds from the caller.
 */
public final class ExpTotal {

    /** How often the profile is read. */
    public static final long EVERY_MS = 30_000L;
    /** Further apart than this and the readings are not one stretch of mining. */
    public static final long MAX_GAP_MS = 3L * EVERY_MS;
    /** Longest time window a rate can be asked over. */
    public static final long MAX_WINDOW_MS = MiningRate.MAX_WINDOW_MS;

    private static final Pattern TOTAL = Pattern.compile("(?i)TOTAL\\s+EXP[^0-9]*([0-9][0-9,]*)");
    private static final Pattern FORMATTING = Pattern.compile("[§&][0-9A-Fa-fK-Ok-oRr]");

    private record Sample(long at, long total) {}

    private final Deque<Sample> samples = new ArrayDeque<>();
    private double session;
    private boolean any;

    /** The total EXP a menu's lore lines show, or empty when none of them does. */
    public static OptionalLong parse(List<String> lore) {
        if (lore == null) {
            return OptionalLong.empty();
        }
        for (String line : lore) {
            if (line == null) {
                continue;
            }
            Matcher m = TOTAL.matcher(FORMATTING.matcher(line).replaceAll(""));
            if (m.find()) {
                try {
                    long total = Long.parseLong(m.group(1).replace(",", ""));
                    return total > 0 ? OptionalLong.of(total) : OptionalLong.empty();
                } catch (NumberFormatException e) {
                    return OptionalLong.empty();
                }
            }
        }
        return OptionalLong.empty();
    }

    /** A reading of the total at {@code now}. */
    public void observe(long total, long now) {
        if (total <= 0L) {
            return;
        }
        any = true;
        Sample last = samples.peekLast();
        if (last != null && now >= last.at() && now - last.at() <= MAX_GAP_MS && total >= last.total()) {
            session += total - last.total();
        } else if (last != null && now >= last.at() && total >= last.total() && now - last.at() > MAX_GAP_MS) {
            // After a break: a new starting point, nothing counted for what came in meanwhile.
            samples.clear();
        } else if (last != null) {
            // The total fell, or the clock went back: start again.
            samples.clear();
        }
        samples.addLast(new Sample(now, total));
        while (samples.size() > 2 && now - samples.peekFirst().at() > 2L * MAX_WINDOW_MS) {
            samples.removeFirst();
        }
    }

    /**
     * EXP per hour over the last {@code windowMs} up to the latest reading: how far the total rose
     * from the reading just before the window (or the first one, while the window has not filled)
     * over their time apart. NaN with fewer than two readings in one stretch.
     */
    public double perHourOver(long windowMs) {
        Sample latest = samples.peekLast();
        if (latest == null || samples.size() < 2) {
            return Double.NaN;
        }
        long from = latest.at() - Math.max(1L, Math.min(MAX_WINDOW_MS, windowMs));
        Sample base = samples.peekFirst();
        var it = samples.descendingIterator();
        it.next();
        while (it.hasNext()) {
            Sample s = it.next();
            if (s.at() <= from) {
                base = s;
                break;
            }
        }
        long ms = latest.at() - base.at();
        return ms <= 0L ? Double.NaN : (latest.total() - base.total()) * 3_600_000.0 / ms;
    }

    /** Whether the latest reading is too old to say anything about now. */
    public boolean stale(long now) {
        Sample latest = samples.peekLast();
        return latest == null || now < latest.at() || now - latest.at() > MAX_GAP_MS;
    }

    /** What the total rose by since joining or the last reset, over the stretches that were measured. */
    public double session() {
        return session;
    }

    /** The latest total, or empty before any reading. */
    public OptionalLong total() {
        Sample latest = samples.peekLast();
        return latest == null ? OptionalLong.empty() : OptionalLong.of(latest.total());
    }

    public boolean any() {
        return any;
    }

    public void reset() {
        samples.clear();
        session = 0.0;
        any = false;
    }
}
