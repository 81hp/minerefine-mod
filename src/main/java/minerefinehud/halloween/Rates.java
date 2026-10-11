package minerefinehud.halloween;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Candy and crops per hour, from how the sidebar balances go up.
 *
 * Every rise of a balance is a gain: crops harvested, the 0.1 candy a crop sometimes gives, candy
 * from a pest or the caravan. A fall is spending and is ignored. The rate is the gains of the last
 * few minutes over the time spent in the Halloween dimension in those minutes, so time spent
 * elsewhere does not drag it down, and the first reading after arriving only sets a baseline:
 * whatever changed while away is not a gain made here.
 *
 * No Minecraft types; times are epoch milliseconds from the caller.
 */
public final class Rates {

    /** Gains older than this, in active time, are dropped. The longest window offered. */
    public static final long MAX_WINDOW_MS = 60L * 60_000L;

    /** Below this much active time the rate is too jumpy to show. */
    public static final long MIN_SPAN_MS = 60_000L;

    /** A tick gap longer than this is a pause (a loading screen, the game frozen), not play. */
    private static final long MAX_TICK_GAP_MS = 5_000L;

    private record Gain(long at, String currency, double amount) {}

    private final Map<String, Double> last = new HashMap<>();
    private final Deque<Gain> gains = new ArrayDeque<>();
    private final Map<String, Double> session = new LinkedHashMap<>();
    private long activeMs;
    private long lastTick = -1L;

    /** Call regularly. Time only counts while {@code active}, i.e. in the Halloween dimension. */
    public void tick(long now, boolean active) {
        if (lastTick >= 0L && active) {
            long gap = now - lastTick;
            if (gap > 0L) {
                activeMs += Math.min(gap, MAX_TICK_GAP_MS);
            }
        }
        lastTick = now;
        while (!gains.isEmpty() && gains.peekFirst().at() < activeMs - MAX_WINDOW_MS) {
            gains.removeFirst();
        }
    }

    /** A balance as read now. The first reading of a currency only sets the baseline. */
    public void observe(String currency, double amount) {
        Double prev = last.put(currency, amount);
        if (prev != null && amount > prev + 1e-9) {
            double gain = amount - prev;
            gains.addLast(new Gain(activeMs, currency, gain));
            session.merge(currency, gain, Double::sum);
        }
    }

    /** Forget the baselines, e.g. on leaving the dimension, so changes made elsewhere never count. */
    public void forgetBaselines() {
        last.clear();
    }

    /** Start the rates and session totals over. */
    public void reset() {
        last.clear();
        gains.clear();
        session.clear();
        activeMs = 0L;
    }

    /** Per hour over the last {@code windowMs} of active time, or NaN while there is too little. */
    public double perHour(Set<String> currencies, long windowMs) {
        long span = Math.min(windowMs, activeMs);
        if (span < MIN_SPAN_MS) {
            return Double.NaN;
        }
        long from = activeMs - span;
        double sum = 0.0;
        for (Gain g : gains) {
            if (g.at() >= from && currencies.contains(g.currency())) {
                sum += g.amount();
            }
        }
        return sum * 3_600_000.0 / span;
    }

    public double perHour(String currency, long windowMs) {
        return perHour(Set.of(currency), windowMs);
    }

    /** Gained this session, per currency. */
    public Map<String, Double> session() {
        return Map.copyOf(session);
    }

    public long activeMs() {
        return activeMs;
    }
}
