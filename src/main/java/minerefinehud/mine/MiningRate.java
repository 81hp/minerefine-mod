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
 * No Minecraft types; times are epoch milliseconds from the caller.
 */
public final class MiningRate {

    /** No new reading for this long and the player has stopped mining. */
    public static final long IDLE_MS = 30_000L;
    /** Longest time one block is taken to have taken. */
    public static final long MAX_GAP_MS = 10_000L;
    /** Blocks averaged at most, whatever is asked. */
    public static final int MAX_BLOCKS = 100;

    private record Block(double gain, long tookMs) {}

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
        } else if (amount > lastAmount + 1e-9) {
            double gain = amount - lastAmount;
            long took = Math.max(0L, Math.min(MAX_GAP_MS, now - lastGainAt));
            blocks.addLast(new Block(gain, took));
            while (blocks.size() > MAX_BLOCKS) {
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
    }

    private static String key(String mine) {
        return mine == null ? "" : mine.trim().toLowerCase(Locale.ROOT);
    }
}
