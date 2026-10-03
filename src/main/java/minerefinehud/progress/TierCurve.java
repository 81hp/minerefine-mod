package minerefinehud.progress;

import java.util.Map;

/**
 * Each tier's share of an item's whole-piece price, so a tier can be priced from the spreadsheet
 * before its shop has been opened.
 *
 * The server prices every ladder on the same curve for a given number of tiers: across 400-odd
 * complete ladders read in game, a six-tier item's tier I was 8.6% of the whole, give or take
 * 0.1%, in every world. Against the spreadsheet's whole-piece figures, 1325 tiers seen in game
 * came out with a median error of 0.9% and 90% within 4.5%; the shop's own price replaces the
 * estimate as soon as it is seen. Measured per tier count; any other count falls back to the six-tier
 * steps (each tier 1.5, 1.29, 1.15, 1.08, 1.05 ... times the one before), which the measured
 * curves follow closely.
 */
public final class TierCurve {

    private static final Map<Integer, double[]> MEASURED = Map.of(
            1, new double[] { 1.0 },
            // Boss gear. Looser than the rest: tier I runs from 23% to 38% of the pair.
            2, new double[] { 0.307, 0.693 },
            3, new double[] { 0.198, 0.327, 0.474 },
            4, new double[] { 0.137, 0.218, 0.306, 0.339 },
            5, new double[] { 0.110, 0.165, 0.214, 0.246, 0.266 },
            6, new double[] { 0.086, 0.129, 0.167, 0.192, 0.208, 0.218 },
            7, new double[] { 0.072, 0.108, 0.138, 0.156, 0.168, 0.176, 0.182 });

    private static final double[] STEPS = { 1.5, 1.29, 1.15, 1.08, 1.05, 1.04, 1.03 };

    private TierCurve() {
    }

    /** Tier {@code level}'s share of the whole, for an item with {@code tiers} tiers; 0 outside it. */
    public static double share(int tiers, int level) {
        if (tiers < 1 || level < 1 || level > tiers) {
            return 0.0;
        }
        double[] measured = MEASURED.get(tiers);
        if (measured != null) {
            return measured[level - 1] / sum(measured);
        }
        double[] weights = new double[tiers];
        weights[0] = 1.0;
        for (int i = 1; i < tiers; i++) {
            weights[i] = weights[i - 1] * STEPS[Math.min(i - 1, STEPS.length - 1)];
        }
        return weights[level - 1] / sum(weights);
    }

    /** What tier {@code level} costs, estimated from the whole-piece price. */
    public static long estimate(long wholePiece, int tiers, int level) {
        return Math.round(wholePiece * share(tiers, level));
    }

    private static double sum(double[] values) {
        double s = 0.0;
        for (double v : values) {
            s += v;
        }
        return s;
    }
}
