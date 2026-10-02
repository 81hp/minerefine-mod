package minerefinehud.turret;

import java.util.Optional;

/**
 * What two turrets merge into.
 *
 * Ported from the community Turret Calculator by garfieldthelord21
 * (github.com/garfieldthelord21/Turret-Calculator, "turret_calculator V2.py"), so the mod gives
 * the same answer players already know:
 *
 *   - the smaller turret is the "efficiency" turret: only 4/9 + 500/(9 x size) of it is added,
 *     kept between 50% and 100%, and all of it when its size is 0;
 *   - the larger turret is added in full;
 *   - every merge lowers the size cap by 100, starting at 2000: merge #1 caps at 1900;
 *   - the result is the combined size or the cap, whichever is lower.
 *
 * No Minecraft types.
 */
public final class TurretCalculator {

    /** The cap before any merge. */
    public static final double BASE_CAP = 2000.0;
    /** How much each merge lowers the cap. */
    public static final double CAP_STEP = 100.0;

    /**
     * @param efficiency share of the smaller turret that is added, 0.5 to 1.0
     * @param mergeNumber which merge this is: previous merges plus one
     * @param cap        the largest size this merge can produce
     * @param capped     the combined size was over the cap
     */
    public record Result(double size, double efficiency, int mergeNumber, double cap, boolean capped) {}

    private TurretCalculator() {
    }

    // ------------------------------------------------------------ reverse

    /** How much of itself a turret of this size adds when it is the smaller of the two. */
    public static double added(double smaller) {
        return smaller * efficiency(smaller);
    }

    /**
     * The smallest turret that adds this much as the smaller of two. Inverse of {@link #added}:
     * up to 100 a turret adds all of itself, from 100 to 1000 it adds 4x/9 + 500/9 (100 up to
     * 500), and from 1000 on half of itself.
     */
    static double sizeAdding(double amount) {
        if (amount <= 100.0) {
            return Math.max(0.0, amount);
        }
        if (amount <= 500.0) {
            return (9.0 * amount - 500.0) / 4.0;
        }
        return 2.0 * amount;
    }

    public enum Need {
        /** The turret is already at or above the target; merging only costs cap. */
        ALREADY_THERE,
        /** A turret no bigger than yours does it; it becomes the efficiency turret. */
        SMALLER_MERGER,
        /** It takes a turret bigger than yours; yours becomes the efficiency turret. */
        LARGER_MERGER,
        /** The target is above what this merge is allowed to reach. */
        OVER_CAP
    }

    /**
     * @param need   which case applies
     * @param merger the merger size needed, whole and rounded up; 0 when none is needed or
     *               possible
     * @param result what merging with that turret gives, as {@link #combine} works it out
     */
    public record Needed(Need need, long merger, double cap, Optional<Result> result) {}

    /**
     * The merger size that takes a turret to a target size.
     *
     * Every bigger merger adds more, so there is one answer: if the extra needed is no more than
     * this turret would add as the smaller one, the merger is the smaller turret and the curve is
     * read backwards; otherwise the merger must be the larger one, and this turret is the one
     * partly added. Rounded up to a whole size, then checked with {@link #combine} so the answer
     * always reaches the target.
     */
    public static Needed needed(double current, double target, int previousMerges) {
        if (current < 0.0 || target < 0.0 || previousMerges < 0 || Double.isNaN(current) || Double.isNaN(target)) {
            throw new IllegalArgumentException("sizes and merges must be 0 or higher");
        }
        double cap = cap(previousMerges + 1);
        if (target <= current) {
            return new Needed(Need.ALREADY_THERE, 0L, cap, Optional.empty());
        }
        if (target > cap) {
            return new Needed(Need.OVER_CAP, 0L, cap, Optional.empty());
        }
        double extra = target - current;
        Need need;
        double exact;
        if (extra <= added(current)) {
            need = Need.SMALLER_MERGER;
            exact = sizeAdding(extra);
        } else {
            need = Need.LARGER_MERGER;
            exact = target - added(current);
        }
        // Floating point can leave 999.9999 for an exact 1000; the check below catches the rest.
        long merger = (long) Math.ceil(exact - 1e-9);
        while (combine(current, merger, previousMerges).size() < target - 1e-9) {
            merger++;
        }
        return new Needed(need, merger, cap, Optional.of(combine(current, merger, previousMerges)));
    }

    /** The line under the answer. */
    public static String explain(Needed n) {
        return switch (n.need()) {
            case ALREADY_THERE -> "Already that size: no merge needed";
            case OVER_CAP -> String.format(java.util.Locale.ROOT,
                    "Above the cap: this merge can reach at most %,d", Math.round(n.cap()));
            case SMALLER_MERGER -> String.format(java.util.Locale.ROOT,
                    "%.1f%% of it is added  •  Merge #%,d  •  Max %,d",
                    n.result().orElseThrow().efficiency() * 100.0, n.result().orElseThrow().mergeNumber(),
                    Math.round(n.cap()));
            case LARGER_MERGER -> String.format(java.util.Locale.ROOT,
                    "Bigger than yours: yours adds %.1f%% of itself  •  Merge #%,d  •  Max %,d",
                    n.result().orElseThrow().efficiency() * 100.0, n.result().orElseThrow().mergeNumber(),
                    Math.round(n.cap()));
        };
    }

    /** The size as the original shows it: rounded, with thousands separators. "1,100". */
    public static String size(Result r) {
        return String.format(java.util.Locale.ROOT, "%,d", Math.round(r.size()));
    }

    /** The line under the result, worded as in the original. */
    public static String details(Result r) {
        return String.format(java.util.Locale.ROOT, "%.1f%% of smaller turret added  •  Merge #%,d  •  Max %,d%s",
                r.efficiency() * 100.0, r.mergeNumber(), Math.round(r.cap()), r.capped() ? "  (capped)" : "");
    }

    /** Share of the smaller turret that is added: 4/9 + 500/(9x), between 50% and 100%. */
    public static double efficiency(double smaller) {
        if (smaller <= 0.0) {
            return 1.0;
        }
        return Math.max(0.5, Math.min(1.0, 4.0 / 9.0 + 500.0 / (9.0 * smaller)));
    }

    /** The cap for this merge number: 2000 less 100 per merge, never below zero. */
    public static double cap(int mergeNumber) {
        return Math.max(0.0, BASE_CAP - mergeNumber * CAP_STEP);
    }

    /**
     * @param a, b           the two turret sizes, either order
     * @param previousMerges merges already done, 0 if none
     * @throws IllegalArgumentException for a negative input, as the original refuses those
     */
    public static Result combine(double a, double b, int previousMerges) {
        if (a < 0.0 || b < 0.0 || previousMerges < 0 || Double.isNaN(a) || Double.isNaN(b)) {
            throw new IllegalArgumentException("sizes and merges must be 0 or higher");
        }
        double smaller = Math.min(a, b);
        double larger = Math.max(a, b);
        double eff = efficiency(smaller);
        double combined = larger + smaller * eff;
        int merge = previousMerges + 1;
        double cap = cap(merge);
        return new Result(Math.min(combined, cap), eff, merge, cap, combined > cap);
    }
}
