package minerefinehud.shop;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * Parses the quantity formats the server prints in shop tooltips.
 *
 * Seen in the wild: "1.96B", "5B", "7,725,607,214", "1.04M", "0".
 *
 * BigDecimal rather than double on purpose. 1.96 has no exact binary representation, so
 * 1.96 * 1e9 in floating point lands on 1959999999.9999998, and a cost that renders as one block
 * short of the real price is exactly the kind of bug nobody notices until it matters.
 */
public final class Amounts {

    private Amounts() {
    }

    public static OptionalLong parse(String raw) {
        if (raw == null) {
            return OptionalLong.empty();
        }

        String s = raw.trim().replace(",", "").replace(" ", "");
        if (s.isEmpty()) {
            return OptionalLong.empty();
        }

        int multiplierDigits = 0;
        char last = Character.toUpperCase(s.charAt(s.length() - 1));
        switch (last) {
            case 'K' -> multiplierDigits = 3;
            case 'M' -> multiplierDigits = 6;
            case 'B' -> multiplierDigits = 9;
            case 'T' -> multiplierDigits = 12;
            default -> { }
        }
        if (multiplierDigits > 0) {
            s = s.substring(0, s.length() - 1).trim();
        }

        try {
            BigDecimal value = new BigDecimal(s);
            if (multiplierDigits > 0) {
                value = value.scaleByPowerOfTen(multiplierDigits);
            }
            if (value.signum() < 0) {
                return OptionalLong.empty();
            }
            return OptionalLong.of(value.setScale(0, RoundingMode.HALF_UP).longValueExact());
        } catch (NumberFormatException | ArithmeticException e) {
            return OptionalLong.empty();
        }
    }

    /** Renders back in the server's own style, for the HUD. */
    public static String format(long value) {
        if (value >= 1_000_000_000L) {
            return trim(value / 1_000_000_000.0) + "B";
        }
        if (value >= 1_000_000L) {
            return trim(value / 1_000_000.0) + "M";
        }
        if (value >= 1_000L) {
            return trim(value / 1_000.0) + "K";
        }
        return Long.toString(value);
    }

    private static String trim(double v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return s;
    }
}
