package minerefinehud.hud;

import java.util.Locale;
import java.util.OptionalInt;

/**
 * The overlay's colours, as opaque ARGB ints.
 *
 * Stored in the config as "#RRGGBB" strings, which is what people type and what every colour
 * picker on the internet hands out. Parsing lives here rather than in the settings screen so a
 * typo is caught by a test instead of by a black-on-black panel.
 *
 * @param frame       panel borders, title bands and underlines
 * @param barFillEnd  the bar's colour at its right end, fading from {@code barFill}; the same
 *                    colour for a plain bar
 * @param frameEnd    the frame's colour at the bottom, fading from {@code frame}; the same
 *                    colour for a plain frame
 */
public record Theme(int header, int label, int value, int good, int warn, int dim,
                    int barFill, int barDone, int barTrack, int frame, int barFillEnd, int frameEnd) {

    /** One colour for the whole bar and the whole frame, as every theme but the Halloween one. */
    public Theme(int header, int label, int value, int good, int warn, int dim,
                 int barFill, int barDone, int barTrack, int frame) {
        this(header, label, value, good, warn, dim, barFill, barDone, barTrack, frame, barFill, frame);
    }

    public static Theme defaults() {
        return new Theme(0xFFFFD24A, 0xFFBFBFBF, 0xFFFFFFFF, 0xFF5CE65C, 0xFFFF6B6B, 0xFF8A8A8A,
                0xFFFFD24A, 0xFF5CE65C, 0xFF3A3A3A, 0xFFFFD24A);
    }

    /**
     * The Halloween panels' own look: pumpkin orange fading into witch purple, on the game's
     * near-black tooltip, with slime green for what is ready.
     */
    public static Theme spooky() {
        return new Theme(0xFFFF9F2E, 0xFFCDB8EC, 0xFFF4EEFF, 0xFF8CF26B, 0xFFFF6464, 0xFF8E7BAE,
                0xFFFF8A1F, 0xFF8CF26B, 0xFF2B1B3D, 0xFFFF8A1F, 0xFFA04BFF, 0xFF8A3FFC);
    }

    /** The bar fades from one colour to another along its length. */
    public boolean barFades() {
        return barFillEnd != barFill;
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
        return alpha(0xFF000000, opacityPercent);
    }

    /** The colour at this opacity, 0 to 100 percent, whatever its own alpha was. */
    public static int alpha(int argb, int opacityPercent) {
        int pct = Math.max(0, Math.min(100, opacityPercent));
        int a = Math.round(pct * 255 / 100f);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    // ---------------------------------------------------------- colour maths

    /**
     * Hue 0 to 360, saturation and value 0 to 1, as an opaque colour. What the colour picker's
     * square and hue strip are drawn from.
     */
    public static int hsv(double hue, double saturation, double value) {
        double h = ((hue % 360.0) + 360.0) % 360.0 / 60.0;
        double s = clamp01(saturation);
        double v = clamp01(value);
        double c = v * s;
        double x = c * (1 - Math.abs(h % 2 - 1));
        double m = v - c;
        double r;
        double g;
        double b;
        switch ((int) h) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        return rgb((int) Math.round((r + m) * 255), (int) Math.round((g + m) * 255), (int) Math.round((b + m) * 255));
    }

    /** {hue 0-360, saturation 0-1, value 0-1}. A grey keeps hue 0. */
    public static double[] toHsv(int argb) {
        double r = ((argb >> 16) & 0xFF) / 255.0;
        double g = ((argb >> 8) & 0xFF) / 255.0;
        double b = (argb & 0xFF) / 255.0;
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double d = max - min;
        double h;
        if (d == 0) {
            h = 0;
        } else if (max == r) {
            h = 60 * (((g - b) / d) % 6);
        } else if (max == g) {
            h = 60 * ((b - r) / d + 2);
        } else {
            h = 60 * ((r - g) / d + 4);
        }
        if (h < 0) {
            h += 360;
        }
        return new double[] { h, max == 0 ? 0 : d / max, max };
    }

    /** Hue 0 to 360, saturation and lightness 0 to 1, as an opaque colour. */
    public static int hsl(double hue, double saturation, double lightness) {
        double s = clamp01(saturation);
        double l = clamp01(lightness);
        double a = s * Math.min(l, 1 - l);
        int[] out = new int[3];
        int[] ns = { 0, 8, 4 };
        for (int i = 0; i < 3; i++) {
            double k = (ns[i] + hue / 30.0) % 12;
            double c = l - a * Math.max(-1, Math.min(Math.min(k - 3, 9 - k), 1));
            out[i] = (int) Math.round(c * 255);
        }
        return rgb(out[0], out[1], out[2]);
    }

    /**
     * The "red to green" bar colour: red when empty, through orange and yellow, green when nearly
     * there. A finished bar uses its own colour instead, see {@link #barDone()}.
     */
    public static int progressColor(double fraction) {
        double f = Math.max(0.0, Math.min(1.0, fraction));
        return hsl(Math.round(f * 115), 0.80, 0.55);
    }

    /** Part way from {@code from} to {@code to}, 0 being {@code from}; opaque. */
    public static int mix(int from, int to, double t) {
        double f = clamp01(t);
        int r = (int) Math.round(((from >> 16) & 0xFF) * (1 - f) + ((to >> 16) & 0xFF) * f);
        int g = (int) Math.round(((from >> 8) & 0xFF) * (1 - f) + ((to >> 8) & 0xFF) * f);
        int b = (int) Math.round((from & 0xFF) * (1 - f) + (to & 0xFF) * f);
        return rgb(r, g, b);
    }

    private static int rgb(int r, int g, int b) {
        return 0xFF000000 | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
    }

    private static int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static double clamp01(double v) {
        return Double.isNaN(v) ? 0 : Math.max(0, Math.min(1, v));
    }
}
