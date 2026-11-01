package minerefinehud.mine;

import minerefinehud.halloween.Rates;

import java.util.Locale;

/**
 * Resources mined per hour, from the action bar's balance going up while mining, the same way
 * the Halloween panel measures crops per hour from the sidebar.
 *
 * Time only counts while the action bar keeps showing a mine's balance: walking to the shop or
 * standing at spawn is not mining and does not drag the rate down. A balance only counts against
 * the reading just before it of the same mine: after mining another mine (or a guess that briefly
 * named another one), the first reading is only a baseline, so turret income or a sale made
 * meanwhile is never taken for mining. Spending makes the balance fall, which is ignored.
 *
 * No Minecraft types; times are epoch milliseconds from the caller.
 */
public final class MiningRate {

    /** No new reading for this long and the player has stopped mining. */
    public static final long IDLE_MS = 30_000L;

    private final Rates rates = new Rates();
    private String currency = "";
    /** The mine as the caller named it, for the panel. */
    private String name = "";
    private long lastReadingAt = -1L;

    /** Every tick. */
    public void tick(long now) {
        rates.tick(now, mining(now));
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
        if (!key.equals(currency) || !mining(now)) {
            // Another mine, or back after a break: start again from this reading.
            rates.forgetBaselines();
            currency = key;
        }
        rates.observe(key, amount);
        name = mine.trim();
        lastReadingAt = now;
    }

    /** The mine last read, as the caller named it; empty before any reading. */
    public String mine() {
        return name;
    }

    /** Per hour of mining, over the last {@code windowMs} of it, or NaN while too little is measured. */
    public double perHour(String mine, long windowMs) {
        return rates.perHour(key(mine), windowMs);
    }

    /** Mined from {@code mine} since joining. */
    public double session(String mine) {
        return rates.session().getOrDefault(key(mine), 0.0);
    }

    /** Time spent mining since joining. */
    public long miningMs() {
        return rates.activeMs();
    }

    public void reset() {
        rates.reset();
        currency = "";
        name = "";
        lastReadingAt = -1L;
    }

    private static String key(String mine) {
        return mine == null ? "" : mine.trim().toLowerCase(Locale.ROOT);
    }
}
