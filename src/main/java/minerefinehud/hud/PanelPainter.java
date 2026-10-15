package minerefinehud.hud;

import minerefinehud.hud.HudModel.Cell;
import minerefinehud.hud.HudModel.Kind;
import minerefinehud.hud.HudModel.Line;
import minerefinehud.hud.HudStyle.Background;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Lays a panel out and turns it into a list of simple drawing steps: rectangles, gradients, text
 * and item icons, in panel pixels before scaling.
 *
 * Every look decision (columns, chips, bars, title bands, the tooltip frame) is made here, in
 * plain Java, so it is unit tested and the same for every Minecraft version. The renderer only
 * replays the steps. Text width comes in as a function, the game's font in game and a fixed
 * width in the tests.
 */
public final class PanelPainter {

    public sealed interface Op permits Fill, Gradient, Text, Icon {}

    /** A filled rectangle from (x1, y1) up to but not including (x2, y2). */
    public record Fill(int x1, int y1, int x2, int y2, int color) implements Op {}

    /** A rectangle fading from {@code top} to {@code bottom}. */
    public record Gradient(int x1, int y1, int x2, int y2, int top, int bottom) implements Op {}

    public record Text(String text, int x, int y, int color, boolean shadow) implements Op {}

    /** The item for a gear word ("sword", "total"), drawn {@link #ICON} pixels square. */
    public record Icon(String key, int x, int y) implements Op {}

    public record Painted(int width, int height, List<Op> ops) {

        public static final Painted EMPTY = new Painted(0, 0, List.of());

        public boolean isEmpty() {
            return width == 0 && height == 0;
        }
    }

    /** Height of one text row. Minecraft's font is 9 tall including the shadow. */
    static final int TEXT_H = 9;
    public static final int ICON = 8;
    private static final int ICON_GAP = 3;
    private static final int COLUMN_GAP = 8;
    private static final int DOT = 4;
    /** A bar stretches to the panel's text width, but never so short it reads as a dash. */
    public static final int BAR_MIN_WIDTH = 100;
    private static final int GROUP_GAP = 3;
    private static final int RULE_GAP = 3;
    private static final int CHIP_PAD = 2;
    private static final int CHIP_INK = 0xFF101010;
    private static final String WIDEST_PERCENT = "100%";

    private PanelPainter() {
    }

    public static Painted paint(List<Line> all, HudStyle style, Background background, int opacity,
                                Theme theme, ToIntFunction<String> width) {
        List<Line> lines = new ArrayList<>();
        for (Line l : all) {
            if (l.kind() == Kind.BAR && style.bar() == HudStyle.BarStyle.NONE) {
                continue;
            }
            lines.add(l);
        }
        if (lines.isEmpty()) {
            return Painted.EMPTY;
        }
        return new Painter(lines, style, background, opacity, theme, width).paint();
    }

    /** Padding inside a panel: the spacing's, plus room for the frame the background draws. */
    public static int padding(HudStyle style, Background background) {
        return switch (background) {
            case NONE -> 1;
            case SHADE -> style.spacing().padding;
            case FRAMED -> style.spacing().padding + 1;
            case TOOLTIP -> style.spacing().padding + 2;
        };
    }

    /** How far the frame reaches in, which a title band stays inside of. */
    private static int frameInset(Background background) {
        return switch (background) {
            case TOOLTIP -> 2;
            case FRAMED -> 1;
            default -> 0;
        };
    }

    public static int barHeight(HudStyle.BarStyle bar) {
        return switch (bar) {
            case THIN -> 3;
            case THICK -> 10;
            case SEGMENTED -> 4;
            case NONE -> 0;
        };
    }

    /** One panel being painted. Holds the measurements the passes share. */
    private static final class Painter {
        private final List<Line> lines;
        private final HudStyle style;
        private final Background background;
        private final int opacity;
        private final Theme theme;
        private final ToIntFunction<String> font;
        private final boolean columns;
        private final HudStyle.Percent percent;
        private final int pad;

        /** Widths of the label column and each figure column, when lined up. */
        private int labelWidth;
        private final List<Integer> cellWidths = new ArrayList<>();

        private final List<Op> ops = new ArrayList<>();

        Painter(List<Line> lines, HudStyle style, Background background, int opacity, Theme theme,
                ToIntFunction<String> font) {
            this.lines = lines;
            this.style = style;
            this.background = background;
            this.opacity = opacity;
            this.theme = theme;
            this.font = font;
            this.columns = style.numbers() == HudStyle.Numbers.COLUMNS;
            this.percent = style.effectivePercent();
            this.pad = padding(style, background);
        }

        private int w(String s) {
            return s == null || s.isEmpty() ? 0 : font.applyAsInt(s);
        }

        // ------------------------------------------------------------ measure

        private boolean tabular(Line l) {
            return columns && switch (l.kind()) {
                case ROW, TOTAL, STATUS, PROGRESS -> true;
                default -> false;
            };
        }

        private boolean iconShown(Line l) {
            return style.gearLabels() != HudStyle.GearLabels.WORDS && !l.icon().isEmpty()
                    && (l.kind() == Kind.ROW || l.kind() == Kind.TOTAL || l.kind() == Kind.PROGRESS);
        }

        /** The icon takes the gear name's place, in the cost rows only; a bar keeps its name. */
        private boolean labelReplaced(Line l) {
            return style.gearLabels() == HudStyle.GearLabels.ICONS && iconShown(l) && l.kind() != Kind.PROGRESS;
        }

        private boolean chip(Cell c) {
            return style.bossStatus() == HudStyle.BossStatus.CHIPS && !c.chip().isEmpty();
        }

        private String percentSuffix(Line l) {
            return l.kind() == Kind.PROGRESS && percent == HudStyle.Percent.TEXT
                    ? "  " + HudStyle.percentText(l.progress()) : "";
        }

        private int cellWidth(Line l, int i) {
            Cell c = l.cells().get(i);
            return switch (l.kind()) {
                case STATUS -> chip(c) ? w(c.chip()) + CHIP_PAD * 2 : w(c.text());
                case PROGRESS -> w(c.text()) + w(percentSuffix(l));
                default -> w(c.text());
            };
        }

        /** The left part of a lined-up row: icon, dot, label. */
        private int leftWidth(Line l) {
            int x = 0;
            if (iconShown(l)) {
                x += ICON;
                if (!labelReplaced(l)) {
                    x += ICON_GAP + w(l.label());
                }
                return x;
            }
            if (l.kind() == Kind.STATUS && style.bossStatus() == HudStyle.BossStatus.DOTS) {
                x += DOT + ICON_GAP;
            }
            return x + w(l.label());
        }

        /** Width of a row written out on one line. */
        private int inlineWidth(Line l) {
            return switch (l.kind()) {
                case ROW, TOTAL -> (iconShown(l) ? ICON + ICON_GAP : 0) + w(labelReplaced(l) ? l.tail() : l.text());
                case STATUS -> {
                    if (style.bossStatus() == HudStyle.BossStatus.TEXT) {
                        yield w(l.text());
                    }
                    int dot = style.bossStatus() == HudStyle.BossStatus.DOTS ? DOT + ICON_GAP : 0;
                    yield dot + w(l.label() + ":") + w(" ") + cellWidth(l, 0);
                }
                case PROGRESS -> (iconShown(l) ? ICON + ICON_GAP : 0) + w(l.text()) + w(percentSuffix(l));
                case BAR -> barRowWidth();
                default -> w(l.text());
            };
        }

        private int barRowWidth() {
            return BAR_MIN_WIDTH + (percent == HudStyle.Percent.AFTER ? 4 + w(WIDEST_PERCENT) : 0);
        }

        private int contentWidth() {
            int width = 0;
            for (Line l : lines) {
                if (tabular(l)) {
                    labelWidth = Math.max(labelWidth, leftWidth(l));
                    for (int i = 0; i < l.cells().size(); i++) {
                        while (cellWidths.size() <= i) {
                            cellWidths.add(0);
                        }
                        cellWidths.set(i, Math.max(cellWidths.get(i), cellWidth(l, i)));
                    }
                } else {
                    width = Math.max(width, inlineWidth(l));
                }
            }
            if (columns && (labelWidth > 0 || !cellWidths.isEmpty())) {
                int table = labelWidth;
                for (int cw : cellWidths) {
                    table += COLUMN_GAP + cw;
                }
                width = Math.max(width, table);
            }
            return width;
        }

        /** Right edge of figure column i, measured from the content's left edge. */
        private int cellRight(int i) {
            int x = labelWidth;
            for (int c = 0; c <= i; c++) {
                x += COLUMN_GAP + cellWidths.get(c);
            }
            return x;
        }

        private int rowHeight(Line l) {
            return switch (l.kind()) {
                case HEADER -> switch (style.titles()) {
                    case PLAIN -> TEXT_H;
                    case UNDERLINE -> TEXT_H + 3;
                    case BAND -> TEXT_H + 4;
                };
                case BAR -> Math.max(barHeight(style.bar()), percent == HudStyle.Percent.AFTER ? 8 : 0) + 2;
                default -> TEXT_H;
            };
        }

        // -------------------------------------------------------------- paint

        Painted paint() {
            int content = contentWidth();
            int width = content + pad * 2;

            // Row positions first: the background has to be drawn before them and needs the height.
            int[] ys = new int[lines.size()];
            int y = pad;
            for (int i = 0; i < lines.size(); i++) {
                Line l = lines.get(i);
                if (i == 0 && l.kind() == Kind.HEADER && style.titles() == HudStyle.Titles.BAND) {
                    y = frameInset(background);   // the band sits flush with the top
                }
                if (i > 0) {
                    y += 1 + style.spacing().gap;
                    Kind before = lines.get(i - 1).kind();
                    if (l.kind() == Kind.PROGRESS && before != Kind.HEADER) {
                        y += GROUP_GAP;   // a little air between one bar and the next
                    }
                    if (l.kind() == Kind.TOTAL && columns) {
                        y += RULE_GAP;
                    }
                }
                ys[i] = y;
                y += rowHeight(l);
            }
            int height = y + pad;

            background(width, height);
            for (int i = 0; i < lines.size(); i++) {
                row(lines.get(i), ys[i], width);
            }
            return new Painted(width, height, List.copyOf(ops));
        }

        private void background(int width, int height) {
            int frameTop = Theme.alpha(theme.frame(), 85);
            int frameBottom = Theme.alpha(theme.frame(), 40);
            switch (background) {
                case NONE -> {
                }
                case SHADE -> fill(0, 0, width, height, Theme.background(opacity));
                case FRAMED -> {
                    fill(0, 0, width, height, Theme.background(opacity));
                    int edge = Theme.alpha(theme.frame(), 80);
                    fill(0, 0, width, 1, edge);
                    fill(0, height - 1, width, height, edge);
                    fill(0, 1, 1, height - 1, edge);
                    fill(width - 1, 1, width, height - 1, edge);
                }
                case TOOLTIP -> {
                    // Minecraft's item tooltip: notched corners, a coloured edge fading downwards.
                    int bg = Theme.alpha(0xFF100010, opacity);
                    fill(1, 0, width - 1, height, bg);
                    fill(0, 1, 1, height - 1, bg);
                    fill(width - 1, 1, width, height - 1, bg);
                    fill(1, 1, width - 1, 2, frameTop);
                    fill(1, height - 2, width - 1, height - 1, frameBottom);
                    ops.add(new Gradient(1, 2, 2, height - 2, frameTop, frameBottom));
                    ops.add(new Gradient(width - 2, 2, width - 1, height - 2, frameTop, frameBottom));
                }
            }
        }

        private void row(Line l, int y, int width) {
            int x = pad;
            switch (l.kind()) {
                case TEXT -> text(l.text(), x, y, theme.color(l.style()));
                case HEADER -> header(l, y, width);
                case ROW, TOTAL -> costRow(l, x, y, width);
                case STATUS -> statusRow(l, x, y);
                case PROGRESS -> progressRow(l, x, y);
                case BAR -> bar(l.progress(), x, y, width - pad * 2);
            }
        }

        private void header(Line l, int y, int width) {
            int color = theme.color(l.style());
            switch (style.titles()) {
                case PLAIN -> text(l.text(), pad, y, color);
                case UNDERLINE -> {
                    text(l.text(), pad, y, color);
                    fill(pad, y + TEXT_H + 1, width - pad, y + TEXT_H + 2, Theme.alpha(theme.frame(), 70));
                }
                case BAND -> {
                    int inset = frameInset(background);
                    fill(inset, y, width - inset, y + TEXT_H + 4, Theme.alpha(theme.frame(), 30));
                    text(l.text(), Math.max(pad, 3), y + 2, color);
                }
            }
        }

        private void costRow(Line l, int x, int y, int width) {
            int color = theme.color(l.style());
            if (l.kind() == Kind.TOTAL && columns) {
                fill(pad, y - 2, width - pad, y - 1, Theme.alpha(theme.header(), 45));
            }
            int at = x;
            if (iconShown(l)) {
                ops.add(new Icon(l.icon(), at, y));
                at += ICON + ICON_GAP;
            }
            if (!columns) {
                text(labelReplaced(l) ? l.tail() : l.text(), at, y, color);
                return;
            }
            if (!labelReplaced(l)) {
                text(l.label(), at, y, theme.color(l.labelStyle()));
            }
            for (int i = 0; i < l.cells().size(); i++) {
                Cell c = l.cells().get(i);
                text(c.text(), x + cellRight(i) - w(c.text()), y, theme.color(c.style()));
            }
        }

        private void statusRow(Line l, int x, int y) {
            Cell state = l.cells().get(0);
            int stateColor = theme.color(state.style());
            if (style.bossStatus() == HudStyle.BossStatus.TEXT) {
                if (!columns) {
                    text(l.text(), x, y, theme.color(l.style()));
                    return;
                }
                text(l.label(), x, y, stateColor);
                text(state.text(), x + cellRight(0) - w(state.text()), y, stateColor);
                return;
            }
            int at = x;
            if (style.bossStatus() == HudStyle.BossStatus.DOTS) {
                fill(at + 1, y + 3, at + 1 + DOT, y + 3 + DOT, 0x73000000);
                fill(at, y + 2, at + DOT, y + 2 + DOT, stateColor);
                at += DOT + ICON_GAP;
            }
            int nameColor = theme.color(l.labelStyle());
            String name = columns ? l.label() : l.label() + ":";
            text(name, at, y, nameColor);
            int stateX = columns ? x + cellRight(0) - cellWidth(l, 0) : at + w(name) + w(" ");
            if (chip(state)) {
                fill(stateX, y - 1, stateX + w(state.chip()) + CHIP_PAD * 2, y + TEXT_H, stateColor);
                ops.add(new Text(state.chip(), stateX + CHIP_PAD, y, CHIP_INK, false));
            } else {
                text(state.text(), stateX, y, stateColor);
            }
        }

        private void progressRow(Line l, int x, int y) {
            int color = theme.color(l.style());
            String suffix = percentSuffix(l);
            int at = x;
            if (iconShown(l)) {
                ops.add(new Icon(l.icon(), at, y));
                at += ICON + ICON_GAP;
            }
            if (!columns) {
                text(l.text(), at, y, color);
                text(suffix, at + w(l.text()), y, fillColor(l.progress()));
                return;
            }
            text(l.label(), at, y, color);
            String amounts = l.cells().get(0).text();
            int right = x + cellRight(0);
            int start = right - w(amounts) - w(suffix);
            text(amounts, start, y, color);
            text(suffix, start + w(amounts), y, fillColor(l.progress()));
        }

        private int fillColor(double fraction) {
            if (fraction >= 1.0) {
                return theme.barDone();
            }
            return style.barColor() == HudStyle.BarColor.PROGRESS ? Theme.progressColor(fraction) : theme.barFill();
        }

        private void bar(double fraction, int x, int y, int span) {
            int rowH = rowHeight(new Line("", HudModel.Style.BAR, 0));
            int barH = barHeight(style.bar());
            String pct = HudStyle.percentText(fraction);
            int barW = span;
            if (percent == HudStyle.Percent.AFTER) {
                barW = span - 4 - w(WIDEST_PERCENT);
                text(pct, x + span - w(pct), y + (rowH - 8) / 2, fillColor(fraction));
            }
            barW = Math.max(1, barW);
            int by = y + (rowH - barH) / 2;
            int fill = fillColor(fraction);
            int filled = (int) Math.round(barW * fraction);
            switch (style.bar()) {
                case THIN -> {
                    fill(x, by, x + barW, by + barH, theme.barTrack());
                    fill(x, by, x + filled, by + barH, fill);
                }
                case THICK -> {
                    fill(x, by, x + barW, by + barH, theme.barTrack());
                    fill(x, by + barH - 1, x + barW, by + barH, 0x59000000);
                    if (filled > 0) {
                        fill(x, by, x + filled, by + barH, fill);
                        fill(x, by, x + filled, by + 1, 0x47FFFFFF);
                        fill(x, by + barH - 1, x + filled, by + barH, 0x40000000);
                    }
                    if (percent == HudStyle.Percent.INSIDE) {
                        text(pct, x + (barW - w(pct)) / 2, by + 1, theme.value());
                    }
                }
                case SEGMENTED -> {
                    for (int i = 0; i < 10; i++) {
                        int sx = x + (int) Math.round(i * (barW + 1) / 10.0);
                        int ex = x + (int) Math.round((i + 1) * (barW + 1) / 10.0) - 1;
                        if (ex <= sx) {
                            continue;
                        }
                        fill(sx, by, ex, by + barH, theme.barTrack());
                        double part = Math.max(0.0, Math.min(1.0, fraction * 10 - i));
                        fill(sx, by, sx + (int) Math.round((ex - sx) * part), by + barH, fill);
                    }
                }
                case NONE -> {
                }
            }
        }

        private void fill(int x1, int y1, int x2, int y2, int color) {
            if (x2 > x1 && y2 > y1 && (color >>> 24) != 0) {
                ops.add(new Fill(x1, y1, x2, y2, color));
            }
        }

        private void text(String s, int x, int y, int color) {
            if (s != null && !s.isEmpty()) {
                ops.add(new Text(s, x, y, color, style.shadow()));
            }
        }
    }
}
