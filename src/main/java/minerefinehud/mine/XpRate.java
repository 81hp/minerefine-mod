package minerefinehud.mine;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * EXP per hour, from the "+140,732 EXP" on the mining action bar. By Arreba.
 *
 * The number on the bar is not the EXP of one block. In a player's log it climbed by the same step
 * from line to line while coins and tokens beside it climbed in proportion, and adding every line
 * up gave a session of 1.29 billion for an account whose whole lifetime total is 654 million (and
 * another player's rate came out about 27 times too high). So a line is read as a point on a rising
 * run: while the number goes up, only the rise since the line before is new EXP; when it falls (or
 * comes after a break) a new run starts and its first line counts in full. Added up, every run counts
 * its highest value once, whether the bar is counting up through one block or adding up a streak.
 *
 * A line with the same number as the one before counts for nothing, even when the balance beside it
 * moved: every block sends the new balance and the new EXP on two separate lines ("304934 EXP bal
 * 84.43B", then "304934 EXP bal 84.48B", then "353082 EXP bal 84.48B"), and counting the balance-only
 * line in full put a player at 100m for a 12m session. The price: a new run that happens to start on
 * exactly the number the last one showed looks like a balance-only line and that block counts 0, a
 * rare undercount of one block instead of an overcount on every block.
 *
 * The first line after a break is counted in the session but gives no rate, since it is not known
 * how long it took. The rate is what came in over a window of time ({@link #perHourOver}), like
 * {@link MiningRate}, not line by line, so a burst of lines cannot inflate it.
 *
 * No Minecraft types; times are epoch milliseconds from the caller.
 */
public final class XpRate {

    /** No new line for this long and the player has stopped mining. */
    public static final long IDLE_MS = 30_000L;
    /** Longest time one block is taken to have taken. */
    public static final long MAX_GAP_MS = 10_000L;
    /** Blocks kept for sure, whatever the window. */
    public static final int MAX_BLOCKS = 100;

    private static final Pattern GAIN = Pattern.compile(
            "(?<![\\p{L}\\p{N}])\\+\\s*(?<num>[0-9][0-9,]*(?:\\.[0-9]+)?)\\s*(?<suffix>[KMBTQkmbtq])?\\s*EXP\\b");
    private static final Pattern FORMATTING = Pattern.compile("[§&][0-9A-Fa-fK-Ok-oRr]");

    /** Longest time window a rate can be asked over, in milliseconds. */
    public static final long MAX_WINDOW_MS = MiningRate.MAX_WINDOW_MS;
    private static final int KEEP_BLOCKS = 5_000;

    private record Block(double gain, long tookMs, long at) implements MiningRate.WindowBlock {}

    /** One line seen, for /mrhud debug: when, what it showed, and whether it was counted. */
    private record Seen(long at, double gain, long balance, boolean counted) {}

    /** How many lines /mrhud debug can show. */
    public static final int LOG = 15;
    private final Deque<Seen> seen = new ArrayDeque<>();
    private long linesSeen;
    private long linesCounted;

    private final Deque<Block> blocks = new ArrayDeque<>();
    /** The number on the latest line that counted, to tell a rise from a new run. */
    private double lastGain;
    private long lastLineAt = -1L;
    private long lastGainAt = -1L;
    private double session;
    private long miningMs;
    private long lastTick = -1L;

    /** The EXP a bar line shows, "+140,732 EXP" as 140732, or empty when it shows none. */
    public static OptionalDouble parse(String line) {
        if (line == null || line.isBlank()) {
            return OptionalDouble.empty();
        }
        Matcher m = GAIN.matcher(FORMATTING.matcher(line).replaceAll(""));
        if (!m.find()) {
            return OptionalDouble.empty();
        }
        try {
            double value = Double.parseDouble(m.group("num").replace(",", ""));
            String suffix = m.group("suffix");
            if (suffix != null) {
                value *= switch (suffix.toUpperCase(Locale.ROOT)) {
                    case "K" -> 1e3;
                    case "M" -> 1e6;
                    case "B" -> 1e9;
                    case "T" -> 1e12;
                    default -> 1e15;
                };
            }
            return value > 0 ? OptionalDouble.of(value) : OptionalDouble.empty();
        } catch (NumberFormatException e) {
            return OptionalDouble.empty();
        }
    }

    /** Every tick: adds up the time spent mining. */
    public void tick(long now) {
        if (lastTick >= 0L && mining(now) && now > lastTick) {
            miningMs += Math.min(now - lastTick, 5_000L);
        }
        lastTick = now;
    }

    /** Whether the player is mining right now, by the action bar. */
    public boolean mining(long now) {
        return lastLineAt >= 0L && now >= lastLineAt && now - lastLineAt <= IDLE_MS;
    }

    /**
     * One mining line from the action bar.
     *
     * @param gain    the EXP it shows
     * @param balance the balance on the same line, only kept for /mrhud debug
     */
    public void observe(double gain, long balance, long now) {
        if (Double.isNaN(gain) || gain <= 0.0) {
            return;
        }
        boolean wasMining = mining(now);
        lastLineAt = now;
        linesSeen++;
        // The same number again is no new EXP, whatever the balance did: the bar sends the new
        // balance and the new EXP of a block on two separate lines. After a break it is a new
        // starting line like any other, so it is not swallowed and the rate gets its baseline.
        if (wasMining && gain == lastGain) {
            note(now, gain, balance, false);
            return;
        }
        // Rising on the run before: only the rise is new. Anything else starts a run, counted in full.
        double credit = wasMining && gain > lastGain ? gain - lastGain : gain;
        lastGain = gain;
        linesCounted++;
        note(now, gain, balance, true);
        session += credit;
        if (wasMining && lastGainAt >= 0L && now > lastGainAt) {
            blocks.addLast(new Block(credit, Math.min(now - lastGainAt, MAX_GAP_MS), now));
            while (blocks.size() > KEEP_BLOCKS
                    || (blocks.size() > MAX_BLOCKS && now - blocks.peekFirst().at() > MAX_WINDOW_MS)) {
                blocks.removeFirst();
            }
        } else if (!wasMining) {
            // After a break the first line is a baseline for the time, not a block with a time.
            blocks.clear();
        }
        lastGainAt = now;
    }

    private void note(long now, double gain, long balance, boolean counted) {
        seen.addLast(new Seen(now, gain, balance, counted));
        while (seen.size() > LOG) {
            seen.removeFirst();
        }
    }

    /**
     * The latest EXP lines for /mrhud debug, oldest first: seconds since the line before, the EXP it
     * showed, the balance beside it, and whether it counted. Shows how the number moves from block
     * to block, which is what decides how it must be read.
     */
    public java.util.List<String> recent() {
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add("exp lines seen " + linesSeen + ", counted " + linesCounted + ", latest " + seen.size() + ":");
        long prev = -1L;
        for (Seen s : seen) {
            String gap = prev < 0 ? "first" : String.format(Locale.ROOT, "+%.1fs", (s.at() - prev) / 1000.0);
            out.add("  " + gap + "  " + Math.round(s.gain()) + " EXP  bal " + s.balance()
                    + (s.counted() ? "" : "  (same EXP, not counted)"));
            prev = s.at();
        }
        return out;
    }

    /**
     * EXP per hour from what came in the last {@code windowMs} up to {@code now}, over that time:
     * stops where it was {@link MiningRate#PAUSE_MS} after the last block. Same rule as
     * {@link MiningRate#perHourOver}; NaN until two lines have come.
     */
    public double perHourOver(long windowMs, long now) {
        if (blocks.isEmpty()) {
            return Double.NaN;
        }
        return MiningRate.windowRate(blocks, windowMs, now);
    }

    /** EXP counted since joining or the last reset. */
    public double session() {
        return session;
    }

    /** Time spent mining since joining or the last reset. */
    public long miningMs() {
        return miningMs;
    }

    /** Whether any EXP has been seen yet. */
    public boolean any() {
        return session > 0.0;
    }

    public void reset() {
        blocks.clear();
        lastGain = 0.0;
        lastLineAt = -1L;
        lastGainAt = -1L;
        session = 0.0;
        miningMs = 0L;
        lastTick = -1L;
        seen.clear();
        linesSeen = 0L;
        linesCounted = 0L;
    }
}
