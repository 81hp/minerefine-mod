package minerefinehud.shop;

import java.util.Locale;
import java.util.OptionalInt;

/**
 * Upgrade levels are shown as Roman numerals, "[Debris Shovel] [VI]".
 *
 * Six levels per item is what has been observed, but nothing here assumes that. A server that
 * adds a seventh tier should not need a code change.
 */
public final class RomanNumerals {

    private static final int[] VALUES = { 1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1 };
    private static final String[] SYMBOLS =
            { "M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I" };

    private RomanNumerals() {
    }

    public static OptionalInt parse(String raw) {
        if (raw == null) {
            return OptionalInt.empty();
        }
        String s = raw.trim().toUpperCase(Locale.ROOT);
        if (s.isEmpty() || !s.matches("[IVXLCDM]+")) {
            return OptionalInt.empty();
        }

        int total = 0;
        int index = 0;
        for (int i = 0; i < VALUES.length; i++) {
            while (s.startsWith(SYMBOLS[i], index)) {
                total += VALUES[i];
                index += SYMBOLS[i].length();
            }
        }
        // Reject things that merely contain Roman letters but are not valid, such as "IIII".
        if (index != s.length() || !toRoman(total).equals(s)) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(total);
    }

    public static String toRoman(int value) {
        if (value <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int remaining = value;
        for (int i = 0; i < VALUES.length; i++) {
            while (remaining >= VALUES[i]) {
                sb.append(SYMBOLS[i]);
                remaining -= VALUES[i];
            }
        }
        return sb.toString();
    }
}
