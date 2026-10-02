package minerefinehud.hud;

import java.util.Locale;
import java.util.OptionalInt;

/**
 * The overlay's colours, as opaque ARGB ints.
 *
 * Stored in the config as "#RRGGBB" strings, which is what people type and what every colour
 * picker on the internet hands out. Parsing lives here rather than in the settings screen so a
 * typo is caught by a test instead of by a black-on-black panel.
 */
public record Theme(int header, int label, int value, int good, int warn, int dim,
                    int barFill, int barDone, int barTrack) {

    public static Theme defaults() {
        return new Theme(0xFFFFD24A, 0xFFBFBFBF, 0xFFFFFFFF, 0xFF5CE65C, 0xFFFF6B6B, 0xFF8A8A8A,
                0xFFFFD24A, 0xFF5CE65C, 0xFF3A3A3A);
    }

    public int color(HudModel.Style style) {
        return switch (style) {
            case HEADER -> header;
            case LABEL -> label;
            case VALUE -> value;
            case GOOD -> good;
            case WARN -> warn;
            case DIM -> dim;
            case BAR -> barFill;
        };
    }

    /**
     * "#RRGGBB", "RRGGBB" or the short "#RGB", case-insensitive, always made fully opaque: a
     * see-through text colour is never what anyone meant. Empty for anything else.
     */
    public static OptionalInt parse(String text) {
        if (text == null) {
            return OptionalInt.empty();
        }
        String hex = text.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() == 3) {
            StringBuilder doubled = new StringBuilder();
            for (char c : hex.toCharArray()) {
                doubled.append(c).append(c);
            }
            hex = doubled.toString();
        }
        if (hex.length() != 6 || !hex.matches("[0-9A-Fa-f]{6}")) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(0xFF000000 | Integer.parseInt(hex, 16));
    }

    /** The colour if it parses, otherwise the fallback, so one bad entry costs only itself. */
    public static int parseOr(String text, int fallback) {
        return parse(text).orElse(fallback);
    }

    public static String format(int argb) {
        return String.format(Locale.ROOT, "#%06X", argb & 0xFFFFFF);
    }

    /** A black panel background at this opacity, 0 to 100 percent. */
    public static int background(int opacityPercent) {
        int pct = Math.max(0, Math.min(100, opacityPercent));
        int alpha = Math.round(pct * 255 / 100f);
        return alpha << 24;
    }
}
