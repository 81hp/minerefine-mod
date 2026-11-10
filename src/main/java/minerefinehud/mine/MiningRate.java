package minerefinehud.mine;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Resources mined per hour, worked out block by block from the action bar's balance.
 *
 * Each rise of the balance is one block (or a few) mined: what it brought, over the time since
 * the rise before it, is the rate at that moment. The rate shown is the last few blocks taken
 * together, so it is right after the first block instead of creeping up over minutes, and one
 * lucky Fortune roll does not swing it wildly. A gap longer than {@link #MAX_GAP_MS} between two
 * blocks counts as that long, so walking to the shop does not drag the rate down.
 *
 * Only a reading of the same mine right after the one before counts: after mining another mine
 * (or a guess that briefly named another one), or a break, the first reading is a baseline, so
 * turret income or a sale made meanwhile is never taken for mining. Spending makes the balance
 * fall, which is ignored.
 *
 * The total is everything of that resource in the inventory, so items taken out of a private
 * vault, a chest or a backpack raise it as well. A menu open in between makes the next reading a
 * baseline ({@link #interrupt}), and a single rise far above the blocks before it
 * ({@link #OUTLIER} times their median) is taken for the same and not counted: a user's rate
 * went to 174,318b/h and the session to +15,041b from what they took out of their vault.
 *
 * No Minecraft types; times are epoch milliseconds from the caller.
 */
public final class MiningRate {

    /** No new reading for this long and the player has stopped mining. */
    public static final long IDLE_MS = 30_000L;
    /** Longest time one block is taken to have taken. */
    public static final long MAX_GAP_MS = 10_000L;
    /** Blocks averaged at most, whatever is asked. */
    public static final int MAX_BLOCKS = 100;
    /** A rise this many times the usual block's is not a block: items came in some other way. */
    static final double OUTLIER = 20.0;
    /** Blocks needed before a rise can be judged against them. */
    static final int OUTLIER_AFTER = 3;
    /** The usual block is the median of this many latest ones. */
    private static final int OUTLIER_OVER = 20;

    /** Longest time window a rate can be asked over, in milliseconds. */
    public static final long MAX_WINDOW_MS = 10L * 60_000L;
    /** Most blocks kept, even for the longest window. */
    private static final int KEEP_BLOCKS = 5_000;

    private record Block(double gain, long tookMs, long at) implements WindowBlock {}

    private final Deque<Block> blocks = new ArrayDeque<>();
    private final Map<String, Double> session = new HashMap<>();
    private String currency = "";
    /** The mine as the caller named it, for the panel. */
    private String name = "";
    private double lastAmount = Double.NaN;
    private long lastReadingAt = -1L;
    /** When the balance last rose, or the baseline was taken: a block's time runs from here. */
    private long lastGainAt = -1L;
    private long miningMs;
    private long lastTick = -1L;
    /** Rises in a row too big to be a block. */
    private int outliersInRow;

    /** Every tick: adds up the time spent mining. */
    public void tick(long now) {
        if (lastTick >= 0L && mining(now) && now > lastTick) {
            miningMs += Math.min(now - lastTick, 5_000L);
        }
        lastTick = now;
    }

    /** Whether the player is mining right now, by the action bar. */
    public boolean mining(long now) {
        return lastReadingAt >= 0L && now >= lastReadingAt && now - lastReadingAt <= IDLE_MS;
    }

    /** The action bar's balance of {@code mine}. */
    public void observe(String mine, double amount, long now) {
        if (mine == null || mine.isBlank() || Double.isNaN(amount) || amount < 0) {
            return;
        }
        String key = key(mine);
        if (!key.equals(currency) || !mining(now) || Double.isNaN(lastAmount)) {
            // Another mine, or back after a break: start again from this reading.
            if (!key.equals(currency)) {
                blocks.clear();
            }
            currency = key;
            lastAmount = amount;
            lastGainAt = now;
        } else if (amount > lastAmount + 1e-9 && outlier(amount - lastAmount)) {
            // Taken out of a vault or a chest: the figure moves on, nothing is counted, and the
            // next block's time runs from here. Several in a row are no accident but a far better
            // tool or boost: the old blocks are no measure any more, so it starts again.
            if (++outliersInRow >= OUTLIER_AFTER) {
                blocks.clear();
                outliersInRow = 0;
            }
            lastAmount = amount;
            lastGainAt = now;
        } else if (amount > lastAmount + 1e-9) {
            double gain = amount - lastAmount;
            outliersInRow = 0;
            long took = Math.max(0L, Math.min(MAX_GAP_MS, now - lastGainAt));
            blocks.addLast(new Block(gain, took, now));
            // Kept as long as the longest window needs, and at least the blocks a count asks for.
            while (blocks.size() > KEEP_BLOCKS
                    || (blocks.size() > MAX_BLOCKS && now - blocks.peekFirst().at() > MAX_WINDOW_MS)) {
                blocks.removeFirst();
            }
            session.merge(key, gain, Double::sum);
            lastAmount = amount;
            lastGainAt = now;
        } else {
            // Spent, or the same figure again: nothing mined, but the next block starts from it.
            lastAmount = amount;
        }
        name = mine.trim();
        lastReadingAt = now;
    }

    /**
     * A menu is open (a vault, a chest, the inventory): whatever comes into the inventory meanwhile
     * is not mined, so the next reading starts again from its own figure. The blocks so far stay.
     */
    public void interrupt() {
        lastAmount = Double.NaN;
    }

    /** Far more than the latest blocks brought, judged once there are a few of them. */
    private boolean outlier(double gain) {
        if (blocks.size() < OUTLIER_AFTER) {
            return false;
        }
        double[] recent = new double[Math.min(OUTLIER_OVER, blocks.size())];
        var it = blocks.descendingIterator();
        for (int i = 0; i < recent.length; i++) {
            recent[i] = it.next().gain();
        }
        java.util.Arrays.sort(recent);
        double median = recent.length % 2 == 1 ? recent[recent.length / 2]
                : (recent[recent.length / 2 - 1] + recent[recent.length / 2]) / 2.0;
        return median > 0.0 && gain > median * OUTLIER;
    }

    /** The mine last read, as the caller named it; empty before any reading. */
    public String mine() {
        return name;
    }

    /**
     * Per hour from the last {@code overBlocks} blocks of {@code mine}, or NaN before the first
     * block. 1 is the last block on its own.
     */
    public double perHour(String mine, int overBlocks) {
        if (!key(mine).equals(currency) || blocks.isEmpty()) {
            return Double.NaN;
        }
        int n = Math.max(1, Math.min(MAX_BLOCKS, overBlocks));
        double gain = 0.0;
        long took = 0L;
        var it = blocks.descendingIterator();
        for (int i = 0; i < n && it.hasNext(); i++) {
            Block b = it.next();
            gain += b.gain();
            took += b.tookMs();
        }
        // Blocks in the same instant: too little time to tell a rate from.
        return took <= 0L ? Double.NaN : gain * 3_600_000.0 / took;
    }

    /**
     * Per hour from everything {@code mine} brought in the last {@code windowMs} up to {@code now},
     * over that time: so it falls by itself when mining stops, and a walk to the shop inside the
     * window counts against it. Until the window has filled (a minute after the first block, for a
     * minute-long window) it is over the time since the first block, so the first figure is as
     * right as a block-by-block one. NaN before the first block; 0 once nothing came for a whole window.
     */
    public double perHourOver(String mine, long windowMs, long now) {
        if (!key(mine).equals(currency) || blocks.isEmpty()) {
            return Double.NaN;
        }
        return windowRate(blocks, windowMs, now);
    }

    /** Shared by {@link XpRate}: the wall-clock rate of the blocks in the window ending at {@code now}. */
    static double windowRate(Deque<? extends WindowBlock> blocks, long windowMs, long now) {
        long window = Math.max(1L, Math.min(MAX_WINDOW_MS, windowMs));
        long from = now - window;
        double gain = 0.0;
        var it = blocks.descendingIterator();
        while (it.hasNext()) {
            WindowBlock b = it.next();
            if (b.at() <= from) {
                break;
            }
            gain += b.gain();
        }
        WindowBlock first = blocks.peekFirst();
        // Mining began when the first block's own time started, if that is inside the window.
        long since = Math.max(from, first.at() - first.tookMs());
        long elapsed = now - since;
        return elapsed <= 0L ? Double.NaN : gain * 3_600_000.0 / elapsed;
    }

    /** A block as the window sees it. */
    interface WindowBlock {
        double gain();
        long tookMs();
        long at();
    }

    /** Mined from {@code mine} since joining. */
    public double session(String mine) {
        return session.getOrDefault(key(mine), 0.0);
    }

    /** Time spent mining since joining. */
    public long miningMs() {
        return miningMs;
    }

    public void reset() {
        blocks.clear();
        session.clear();
        currency = "";
        name = "";
        lastAmount = Double.NaN;
        lastReadingAt = -1L;
        lastGainAt = -1L;
        miningMs = 0L;
        lastTick = -1L;
        outliersInRow = 0;
    }

    private static String key(String mine) {
        return mine == null ? "" : mine.trim().toLowerCase(Locale.ROOT);
    }
}
