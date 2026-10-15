package minerefinehud.hud;

import java.util.Locale;

/**
 * How the panels look, as opposed to what they say. Shared by every panel; the background is the
 * one look that stays per panel, see {@link Background}.
 *
 * Every choice is an enum with a label for the settings screen, stored in the config by name so a
 * hand edit with odd casing still works and a typo falls back to the default for that one choice.
 */
public record HudStyle(Titles titles, Numbers numbers, GearLabels gearLabels, BossStatus bossStatus,
                       BarStyle bar, Percent percent, BarColor barColor, Spacing spacing,
                       boolean shadow) {

    /** Behind one panel. */
    public enum Background {
        NONE("None"), SHADE("Shade"), TOOLTIP("Tooltip"), FRAMED("Framed");

        public final String label;

        Background(String label) {
            this.label = label;
        }
    }

    /** How a panel's title stands out. */
    public enum Titles {
        PLAIN("Plain"), UNDERLINE("Underlined"), BAND("Band");

        public final String label;

        Titles(String label) {
            this.label = label;
        }
    }

    /** "Sword: 20.24b" on one line, or the label left and the numbers in right-aligned columns. */
    public enum Numbers {
        INLINE("After label"), COLUMNS("Lined up");

        public final String label;

        Numbers(String label) {
            this.label = label;
        }
    }

    /** Gear names, item icons in their place, or both. */
    public enum GearLabels {
        WORDS("Words"), ICONS("Icons"), BOTH("Both");

        public final String label;

        GearLabels(String label) {
            this.label = label;
        }
    }

    /** Boss rows: the whole row coloured, a coloured dot, or the status on a coloured chip. */
    public enum BossStatus {
        TEXT("Text"), DOTS("Dots"), CHIPS("Chips");

        public final String label;

        BossStatus(String label) {
            this.label = label;
        }
    }

    public enum BarStyle {
        THIN("Thin"), THICK("Thick"), SEGMENTED("Segments"), NONE("None");

        public final String label;

        BarStyle(String label) {
            this.label = label;
        }
    }

    /** Where a bar's percentage goes. Rounded down, so 100% only once it is affordable. */
    public enum Percent {
        OFF("Off"), TEXT("In text"), AFTER("After bar"), INSIDE("Inside");

        public final String label;

        Percent(String label) {
            this.label = label;
        }
    }

    /** One colour for every bar, or red through to green as it fills. */
    public enum BarColor {
        ACCENT("One colour"), PROGRESS("Red to green");

        public final String label;

        BarColor(String label) {
            this.label = label;
        }
    }

    public enum Spacing {
        COMPACT("Compact", 2, 0), NORMAL("Normal", 3, 1), ROOMY("Roomy", 4, 2);

        public final String label;
        /** Padding inside a panel, before the background adds its own. */
        public final int padding;
        /** Extra pixels between rows. */
        public final int gap;

        Spacing(String label, int padding, int gap) {
            this.label = label;
            this.padding = padding;
            this.gap = gap;
        }
    }

    /** What a fresh install looks like: the panels as they always were, with percentages. */
    public static HudStyle defaults() {
        return new HudStyle(Titles.PLAIN, Numbers.INLINE, GearLabels.WORDS, BossStatus.TEXT,
                BarStyle.THIN, Percent.AFTER, BarColor.ACCENT, Spacing.COMPACT, true);
    }

    /**
     * Where the percentage really goes with this bar: inside needs the thick bar, and with no bar
     * at all the number moves into the text.
     */
    public Percent effectivePercent() {
        if (percent == Percent.OFF) {
            return Percent.OFF;
        }
        if (bar == BarStyle.NONE) {
            return Percent.TEXT;
        }
        if (percent == Percent.INSIDE && bar != BarStyle.THICK) {
            return Percent.AFTER;
        }
        return percent;
    }

    /** Whether this percentage choice can be shown with this bar, for greying out the button. */
    public static boolean percentFits(Percent p, BarStyle bar) {
        return switch (p) {
            case INSIDE -> bar == BarStyle.THICK;
            case AFTER -> bar != BarStyle.NONE;
            default -> true;
        };
    }

    /** Percent text for a fraction, rounded down so a bar never claims 100% early. */
    public static String percentText(double fraction) {
        double f = Double.isNaN(fraction) ? 0 : Math.max(0.0, Math.min(1.0, fraction));
        return (int) Math.floor(f * 100 + 1e-9) + "%";
    }

    public HudStyle withTitles(Titles v) {
        return new HudStyle(v, numbers, gearLabels, bossStatus, bar, percent, barColor, spacing, shadow);
    }

    public HudStyle withNumbers(Numbers v) {
        return new HudStyle(titles, v, gearLabels, bossStatus, bar, percent, barColor, spacing, shadow);
    }

    public HudStyle withGearLabels(GearLabels v) {
        return new HudStyle(titles, numbers, v, bossStatus, bar, percent, barColor, spacing, shadow);
    }

    public HudStyle withBossStatus(BossStatus v) {
        return new HudStyle(titles, numbers, gearLabels, v, bar, percent, barColor, spacing, shadow);
    }

    public HudStyle withBar(BarStyle v) {
        return new HudStyle(titles, numbers, gearLabels, bossStatus, v, percent, barColor, spacing, shadow);
    }

    public HudStyle withPercent(Percent v) {
        return new HudStyle(titles, numbers, gearLabels, bossStatus, bar, v, barColor, spacing, shadow);
    }

    public HudStyle withBarColor(BarColor v) {
        return new HudStyle(titles, numbers, gearLabels, bossStatus, bar, percent, v, spacing, shadow);
    }

    public HudStyle withSpacing(Spacing v) {
        return new HudStyle(titles, numbers, gearLabels, bossStatus, bar, percent, barColor, v, shadow);
    }

    public HudStyle withShadow(boolean v) {
        return new HudStyle(titles, numbers, gearLabels, bossStatus, bar, percent, barColor, spacing, v);
    }

    /** Tolerant lookup for the config: the enum constant by name, or the fallback. */
    public static <E extends Enum<E>> E parse(Class<E> type, String raw, E fallback) {
        if (raw == null) {
            return fallback;
        }
        String key = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (E e : type.getEnumConstants()) {
            if (e.name().equals(key)) {
                return e;
            }
        }
        return fallback;
    }

    // ---------------------------------------------------------------- presets

    /**
     * The five looks from the design page. Picking one sets every look setting at once, plus
     * the credits column and the "still needed" line, which those designs include.
     */
    public enum Preset {
        FORK("Fork", "The classic layout with percentages added. Plain text on a dark shade.",
                Background.SHADE, 55,
                new HudStyle(Titles.PLAIN, Numbers.INLINE, GearLabels.WORDS, BossStatus.TEXT, BarStyle.THIN,
                        Percent.AFTER, BarColor.ACCENT, Spacing.COMPACT, true),
                true, false, 0xFFFFD24A, 0xFFFFD24A),
        TOOLTIP("Tooltip", "Like Minecraft's item tooltips: near-black panel, fading coloured edge.",
                Background.TOOLTIP, 94,
                new HudStyle(Titles.UNDERLINE, Numbers.COLUMNS, GearLabels.WORDS, BossStatus.DOTS, BarStyle.THICK,
                        Percent.INSIDE, BarColor.ACCENT, Spacing.NORMAL, true),
                true, false, 0xFFFFD24A, 0xFF7A3CFF),
        LEDGER("Ledger", "Tinted title band, numbers lined up, boss chips, red-to-green segmented bars.",
                Background.SHADE, 70,
                new HudStyle(Titles.BAND, Numbers.COLUMNS, GearLabels.WORDS, BossStatus.CHIPS, BarStyle.SEGMENTED,
                        Percent.AFTER, BarColor.PROGRESS, Spacing.NORMAL, true),
                true, true, 0xFFFFD24A, 0xFFFFD24A),
        MINIMAL("Minimal", "No panel at all, only shadowed text and thin bars. Takes the least room.",
                Background.NONE, 0,
                new HudStyle(Titles.PLAIN, Numbers.COLUMNS, GearLabels.WORDS, BossStatus.DOTS, BarStyle.THIN,
                        Percent.TEXT, BarColor.ACCENT, Spacing.COMPACT, true),
                false, false, 0xFF7FE3FF, 0xFF7FE3FF),
        ICONS("Icons", "Item icons instead of gear names, framed panels, thick bars with the % inside.",
                Background.FRAMED, 60,
                new HudStyle(Titles.BAND, Numbers.COLUMNS, GearLabels.ICONS, BossStatus.CHIPS, BarStyle.THICK,
                        Percent.INSIDE, BarColor.PROGRESS, Spacing.ROOMY, true),
                true, false, 0xFFFFD24A, 0xFF4FC3D9);

        public final String label;
        public final String description;
        public final Background background;
        public final int opacity;
        public final HudStyle style;
        public final boolean credits;
        public final boolean needed;
        public final int accent;
        public final int frame;

        Preset(String label, String description, Background background, int opacity, HudStyle style,
               boolean credits, boolean needed, int accent, int frame) {
            this.label = label;
            this.description = description;
            this.background = background;
            this.opacity = opacity;
            this.style = style;
            this.credits = credits;
            this.needed = needed;
            this.accent = accent;
            this.frame = frame;
        }
    }
}
