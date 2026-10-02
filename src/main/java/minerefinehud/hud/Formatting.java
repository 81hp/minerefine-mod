package minerefinehud.hud;

import java.util.Locale;

/** Number and duration formatting, matching the job calculator's own conventions. */
public final class Formatting {

    private static final long MILLION = 1_000_000L;
    private static final long BILLION = 1_000_000_000L;

    private Formatting() {
    }

    /** Block counts the way the site writes them: 3.25b, 1.04m, 12345. */
    public static String blocks(long value) {
        if (value >= BILLION) {
            return trim(value / (double) BILLION) + "b";
        }
        if (value >= MILLION) {
            return trim(value / (double) MILLION) + "m";
        }
        return String.format(Locale.ROOT, "%,d", value);
    }

    /**
     * Blocks converted to credits.
     *
     * @param blocksPerCreditMillions the site's "block rate" field, in millions of blocks per
     *                                credit. Defaults to 200 there.
     */
    public static String credits(long blocks, double blocksPerCreditMillions) {
        if (blocksPerCreditMillions <= 0.0) {
            return "?";
        }
        double credits = blocks / (blocksPerCreditMillions * MILLION);
        return trim(credits) + "c";
    }

    /** mm:ss, or h:mm:ss once past an hour. Negative inputs are clamped to zero. */
    public static String duration(long millis) {
        long totalSeconds = Math.max(0L, millis) / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        if (hours > 0L) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }

    /** Up to two decimals, with trailing zeroes removed. */
    private static String trim(double value) {
        String s = String.format(Locale.ROOT, "%.2f", value);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "");
            s = s.replaceAll("\\.$", "");
        }
        return s;
    }
}
