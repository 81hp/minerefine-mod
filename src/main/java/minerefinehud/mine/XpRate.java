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
 * Every block mined puts a fresh line on the bar, but the server also resends a line unchanged,
 * so a line is only counted when its balance or its gain differs from the one before. The first
 * line after a break is counted in the session total but gives no rate, since it is not known
 * how long that block took. After that a block took the time since the one before it, at most
 * {@link #MAX_GAP_MS}, so a walk to the shop does not drag the rate down. The rate shown is the
 * last few blocks together, like {@link MiningRate}.
 *
 * No Minecraft types; times are epoch milliseconds from the caller.
 */
public final class XpRate {

    /** No new line for this long and the player has stopped mining. */
    public static final long IDLE_MS = 30_000L;
    /** Longest time one block is taken to have taken. */
    public static final long MAX_GAP_MS = 10_000L;
    /** Blocks averaged at most, whatever is asked. */
    public static final int MAX_BLOCKS = 100;

    private static final Pattern GAIN = Pattern.compile(
            "(?<![\\p{L}\\p{N}])\\+\\s*(?<num>[0-9][0-9,]*(?:\\.[0-9]+)?)\\s*(?<suffix>[KMBTQkmbtq])?\\s*EXP\\b");
    private static final Pattern FORMATTING = Pattern.compile("[§&][0-9A-Fa-fK-Ok-oRr]");

    /** Longest time window a rate can be asked over, in milliseconds. */
    public static final long MAX_WINDOW_MS = MiningRate.MAX_WINDOW_MS;
    private static final int KEEP_BLOCKS = 5_000;

    private record Block(double gain, long tookMs, long at) implements MiningRate.WindowBlock {}

    private final Deque<Block> blocks = new ArrayDeque<>();
    private String lastKey = "";
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
     * @param balance the balance on the same line, which tells a new block from a resent line
     */
    public void observe(double gain, long balance, long now) {
        if (Double.isNaN(gain) || gain <= 0.0) {
            return;
        }
        boolean wasMining = mining(now);
        lastLineAt = now;
        String key = balance + "|" + gain;
        if (key.equals(lastKey)) {
            return;
        }
        lastKey = key;
        session += gain;
        if (wasMining && lastGainAt >= 0L && now > lastGainAt) {
            blocks.addLast(new Block(gain, Math.min(now - lastGainAt, MAX_GAP_MS), now));
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

    /** EXP per hour over the last {@code count} blocks, NaN until two lines have come. */
    public double perHour(int count) {
        int take = Math.max(1, Math.min(MAX_BLOCKS, count));
        double gained = 0.0;
        long ms = 0L;
        int seen = 0;
        var it = blocks.descendingIterator();
        while (it.hasNext() && seen < take) {
            Block b = it.next();
            gained += b.gain();
            ms += b.tookMs();
            seen++;
        }
        return ms <= 0L ? Double.NaN : gained / ms * 3_600_000.0;
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
        lastKey = "";
        lastLineAt = -1L;
        lastGainAt = -1L;
        session = 0.0;
        miningMs = 0L;
        lastTick = -1L;
    }
}
