package minerefinehud.mine;

import java.util.Objects;
import java.util.function.DoubleSupplier;

/**
 * A rate that is worked out afresh at most every {@link #EVERY_MS}, so the number on the panel does
 * not change every frame. The rate itself is already averaged over its window; this only decides
 * how often the figure shown is replaced.
 *
 * It is worked out at once when there is no figure yet (so the first one is not held back), and
 * when {@code key} changes (another mine, or another setting), so a change is not shown late.
 *
 * No Minecraft types; times are epoch milliseconds from the caller.
 */
public final class HeldRate {

    /** How long a shown figure stays. */
    public static final long EVERY_MS = 5_000L;

    private double value = Double.NaN;
    private long at = Long.MIN_VALUE;
    private Object key;

    /** The figure to show: the held one, or {@code calc} if it is time or the key changed. */
    public double get(long now, Object key, DoubleSupplier calc) {
        boolean stale = Double.isNaN(value) || !Objects.equals(this.key, key) || now < at
                || now - at >= EVERY_MS;
        if (stale) {
            value = calc.getAsDouble();
            at = now;
            this.key = key;
        }
        return value;
    }

    public void reset() {
        value = Double.NaN;
        at = Long.MIN_VALUE;
        key = null;
    }
}
