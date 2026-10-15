package minerefinehud;

import minerefinehud.hud.HudModel;
import minerefinehud.hud.HudStyle;
import minerefinehud.hud.PanelPainter;
import minerefinehud.hud.SamplePanels;
import minerefinehud.hud.Theme;

import java.util.List;
import java.util.function.ToIntFunction;

/** Tests for the HUD's looks: colour maths, the style choices and how panels are painted. */
public final class StyleTests {

    private static int passed = 0;
    private static int failed = 0;

    /** Stands in for Minecraft's font: 6 pixels a character, close to its average. */
    private static final ToIntFunction<String> FONT = s -> s.length() * 6;
    private static final Theme THEME = Theme.defaults();
    private static final HudModel.Options OPTIONS = HudModel.Options.defaults();
    private static final HudModel.Options CREDITS = new HudModel.Options(true, false, true, 200.0, true, 5,
            HudModel.MineLines.all(), HudModel.BossLines.all(), HudModel.ProgressLines.all());

    public static void main(String[] args) {
        colourMaths();
        percentages();
        linesKeepTheirText();
        neededLine();
        inlineIsTheClassicLook();
        columnsLineUp();
        iconsReplaceGearNames();
        bossStatusStyles();
        barStyles();
        percentPlaces();
        barColours();
        backgroundsAndTitles();
        presets();
        everythingStaysInsideThePanel();

        System.out.println();
        System.out.println("passed: " + passed + "   failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static PanelPainter.Painted paint(List<HudModel.Line> lines, HudStyle style, HudStyle.Background bg) {
        return PanelPainter.paint(lines, style, bg, 60, THEME, FONT);
    }

    private static List<PanelPainter.Text> texts(PanelPainter.Painted p) {
        return p.ops().stream().filter(o -> o instanceof PanelPainter.Text).map(o -> (PanelPainter.Text) o).toList();
    }

    private static PanelPainter.Text text(PanelPainter.Painted p, String s) {
        return texts(p).stream().filter(t -> t.text().equals(s)).findFirst().orElse(null);
    }

    private static List<PanelPainter.Fill> fills(PanelPainter.Painted p) {
        return p.ops().stream().filter(o -> o instanceof PanelPainter.Fill).map(o -> (PanelPainter.Fill) o).toList();
    }

    // ------------------------------------------------------------------ tests

    private static void colourMaths() {
        eq("hsv red", 0xFFFF0000, Theme.hsv(0, 1, 1));
        eq("hsv green", 0xFF00FF00, Theme.hsv(120, 1, 1));
        eq("hsv blue", 0xFF0000FF, Theme.hsv(240, 1, 1));
        eq("hsv black", 0xFF000000, Theme.hsv(77, 0.5, 0));
        eq("hsv white", 0xFFFFFFFF, Theme.hsv(200, 0, 1));
        eq("hue wraps", Theme.hsv(10, 1, 1), Theme.hsv(370, 1, 1));
        for (int c : new int[] { 0xFFFFD24A, 0xFF7A3CFF, 0xFF4FC3D9, 0xFF3A3A3A, 0xFF101010, 0xFFFFFFFF, 0xFF5CE65C }) {
            double[] hsv = Theme.toHsv(c);
            eq("hsv round trip " + Theme.format(c), c, Theme.hsv(hsv[0], hsv[1], hsv[2]));
        }
        eq("hsl red", 0xFFFF0000, Theme.hsl(0, 1, 0.5));
        int empty = Theme.progressColor(0);
        yes("empty bar is red", ((empty >> 16) & 0xFF) > ((empty >> 8) & 0xFF));
        int full = Theme.progressColor(0.99);
        yes("nearly full bar is green", ((full >> 8) & 0xFF) > ((full >> 16) & 0xFF));
        eq("alpha keeps the colour", 0x80FFD24A, Theme.alpha(0xFFFFD24A, 50));
        eq("alpha clamps", 0xFFFFD24A, Theme.alpha(0x00FFD24A, 200));
        eq("frame default", 0xFFFFD24A, Theme.defaults().frame());
    }

    private static void percentages() {
        eq("rounded down", "99%", HudStyle.percentText(0.999));
        eq("full", "100%", HudStyle.percentText(1.0));
        eq("90", "90%", HudStyle.percentText(0.902));
        eq("nan", "0%", HudStyle.percentText(Double.NaN));
        eq("float noise", "29%", HudStyle.percentText(0.29));

        HudStyle s = HudStyle.defaults();
        eq("inside needs thick", HudStyle.Percent.AFTER,
                s.withBar(HudStyle.BarStyle.THIN).withPercent(HudStyle.Percent.INSIDE).effectivePercent());
        eq("inside with thick", HudStyle.Percent.INSIDE,
                s.withBar(HudStyle.BarStyle.THICK).withPercent(HudStyle.Percent.INSIDE).effectivePercent());
        eq("no bar moves it into the text", HudStyle.Percent.TEXT,
                s.withBar(HudStyle.BarStyle.NONE).withPercent(HudStyle.Percent.AFTER).effectivePercent());
        eq("off stays off", HudStyle.Percent.OFF,
                s.withBar(HudStyle.BarStyle.NONE).withPercent(HudStyle.Percent.OFF).effectivePercent());
        yes("inside fits thick", HudStyle.percentFits(HudStyle.Percent.INSIDE, HudStyle.BarStyle.THICK));
        yes("inside does not fit segments", !HudStyle.percentFits(HudStyle.Percent.INSIDE, HudStyle.BarStyle.SEGMENTED));
        yes("after needs a bar", !HudStyle.percentFits(HudStyle.Percent.AFTER, HudStyle.BarStyle.NONE));
        eq("tolerant parse", HudStyle.Titles.BAND, HudStyle.parse(HudStyle.Titles.class, " band ", HudStyle.Titles.PLAIN));
        eq("bad parse falls back", HudStyle.Titles.PLAIN, HudStyle.parse(HudStyle.Titles.class, "stripes", HudStyle.Titles.PLAIN));
        eq("null parse falls back", HudStyle.Spacing.ROOMY, HudStyle.parse(HudStyle.Spacing.class, null, HudStyle.Spacing.ROOMY));
    }

    private static void linesKeepTheirText() {
        List<HudModel.Line> mine = SamplePanels.mine(CREDITS);
        eq("cost row text", "Sword: 20.24b*  (101.2c)", mine.get(1).text());
        eq("cost row label", "Sword", mine.get(1).label());
        eq("cost row figure", "20.24b*", mine.get(1).cells().get(0).text());
        eq("cost row credits", "101.2c", mine.get(1).cells().get(1).text());
        eq("tail without the label", "20.24b*  (101.2c)", mine.get(1).tail());
        HudModel.Line total = mine.get(mine.size() - 1);
        eq("total kind", HudModel.Kind.TOTAL, total.kind());
        eq("total text", "Total: 134.22b  (671.1c)", total.text());
        eq("total figures in title colour", HudModel.Style.HEADER, total.cells().get(1).style());

        List<HudModel.Line> boss = SamplePanels.bosses(OPTIONS);
        eq("boss up text", "Angry Archaeologist: UP  (1:20)", boss.get(1).text());
        eq("boss up chip", "UP 1:20", boss.get(1).cells().get(0).chip());
        eq("boss due text", "Nekronos: due now", boss.get(2).text());
        eq("boss due chip", "DUE NOW", boss.get(2).cells().get(0).chip());
        eq("countdown has no chip", "", boss.get(3).cells().get(0).chip());
        eq("learning name dim", HudModel.Style.DIM, boss.get(4).labelStyle());

        List<HudModel.Line> bars = SamplePanels.progress(HudModel.ProgressLines.all());
        eq("bar title", "Cobalt Pickaxe I  1.66b/1.84b", bars.get(0).text());
        eq("bar title kind", HudModel.Kind.PROGRESS, bars.get(0).kind());
        eq("bar title icon", "pickaxe", bars.get(0).icon());
        eq("quantity title", "12x Icicle Chestplate IV  8.41b/26.6b", bars.get(2).text());
        eq("finished title", "Icicle Sword II  finished  21.03b", bars.get(4).text());
        eq("finished fraction", 1.0, bars.get(4).progress());
    }

    private static void neededLine() {
        List<HudModel.Line> with = SamplePanels.progress(new HudModel.ProgressLines(true, true, true));
        eq("needed under the bar", "180m still needed", with.get(2).text());
        eq("needed is dim", HudModel.Style.DIM, with.get(2).style());
        yes("finished bar has no needed line", with.stream().noneMatch(l -> l.text().startsWith("0 ")));
        List<HudModel.Line> without = SamplePanels.progress(HudModel.ProgressLines.all());
        yes("off by default", without.stream().noneMatch(l -> l.text().endsWith("still needed")));
    }

    private static void inlineIsTheClassicLook() {
        HudStyle style = HudStyle.defaults();
        PanelPainter.Painted p = paint(SamplePanels.mine(CREDITS), style, HudStyle.Background.SHADE);
        yes("whole line drawn as one", text(p, "Sword: 20.24b*  (101.2c)") != null);
        eq("background first", PanelPainter.Fill.class, p.ops().get(0).getClass());
        PanelPainter.Fill bg = (PanelPainter.Fill) p.ops().get(0);
        eq("shade covers the panel", p.width(), bg.x2());
        eq("shade at the opacity", 0x99000000, bg.color());
        int widest = SamplePanels.mine(CREDITS).stream().mapToInt(l -> FONT.applyAsInt(l.text())).max().orElse(0);
        eq("width is the widest line plus padding", widest + 2 * PanelPainter.padding(style, HudStyle.Background.SHADE), p.width());
        yes("empty panel paints nothing", PanelPainter.paint(List.of(), style, HudStyle.Background.SHADE, 50, THEME, FONT).isEmpty());
    }

    private static void columnsLineUp() {
        HudStyle style = HudStyle.defaults().withNumbers(HudStyle.Numbers.COLUMNS);
        PanelPainter.Painted p = paint(SamplePanels.mine(CREDITS), style, HudStyle.Background.SHADE);
        PanelPainter.Text sword = text(p, "20.24b*");
        PanelPainter.Text charm = text(p, "13.3b*");
        yes("figures drawn on their own", sword != null && charm != null);
        eq("figures right-aligned", sword.x() + FONT.applyAsInt(sword.text()), charm.x() + FONT.applyAsInt(charm.text()));
        PanelPainter.Text c1 = text(p, "101.2c");
        PanelPainter.Text c2 = text(p, "671.1c");
        eq("credits right-aligned", c1.x() + FONT.applyAsInt(c1.text()), c2.x() + FONT.applyAsInt(c2.text()));
        yes("credits in their own column", c1.x() > sword.x() + FONT.applyAsInt(sword.text()));
        eq("credits dim", THEME.dim(), c1.color());
        eq("total figures in the title colour", THEME.header(), c2.color());
        PanelPainter.Text label = text(p, "Sword");
        eq("label in the label colour", THEME.label(), label.color());
        yes("a rule above the total", fills(p).stream().anyMatch(f -> f.y2() - f.y1() == 1
                && f.color() == Theme.alpha(THEME.header(), 45)));
        PanelPainter.Painted inline = paint(SamplePanels.mine(OPTIONS), HudStyle.defaults(), HudStyle.Background.SHADE);
        PanelPainter.Painted cols = paint(SamplePanels.mine(OPTIONS), style, HudStyle.Background.SHADE);
        yes("lined up is no wider than written out by much", cols.width() <= inline.width() + 20);
    }

    private static void iconsReplaceGearNames() {
        HudStyle icons = HudStyle.defaults().withNumbers(HudStyle.Numbers.COLUMNS).withGearLabels(HudStyle.GearLabels.ICONS);
        PanelPainter.Painted p = paint(SamplePanels.mine(OPTIONS), icons, HudStyle.Background.SHADE);
        long iconCount = p.ops().stream().filter(o -> o instanceof PanelPainter.Icon).count();
        eq("an icon per gear row and the total", 5L, iconCount);
        yes("gear names gone", text(p, "Sword") == null);
        PanelPainter.Painted words = paint(SamplePanels.mine(OPTIONS),
                icons.withGearLabels(HudStyle.GearLabels.WORDS), HudStyle.Background.SHADE);
        yes("icons make it narrower", p.width() < words.width());

        PanelPainter.Painted inline = paint(SamplePanels.mine(OPTIONS),
                HudStyle.defaults().withGearLabels(HudStyle.GearLabels.ICONS), HudStyle.Background.SHADE);
        yes("inline drops the label", text(inline, "20.24b*") != null);
        PanelPainter.Painted both = paint(SamplePanels.mine(OPTIONS),
                HudStyle.defaults().withGearLabels(HudStyle.GearLabels.BOTH), HudStyle.Background.SHADE);
        yes("both keeps the label", text(both, "Sword: 20.24b*") != null);

        PanelPainter.Painted bars = paint(SamplePanels.progress(HudModel.ProgressLines.all()), icons, HudStyle.Background.SHADE);
        yes("bars keep their names with icons", text(bars, "Cobalt Pickaxe I") != null);
        yes("bars get an icon", bars.ops().stream().anyMatch(o -> o instanceof PanelPainter.Icon i && i.key().equals("pickaxe")));
    }

    private static void bossStatusStyles() {
        List<HudModel.Line> boss = SamplePanels.bosses(OPTIONS);
        PanelPainter.Painted plain = paint(boss, HudStyle.defaults(), HudStyle.Background.SHADE);
        PanelPainter.Text up = text(plain, "Angry Archaeologist: UP  (1:20)");
        yes("text: the row as one", up != null);
        eq("text: in the status colour", THEME.good(), up.color());

        PanelPainter.Painted chips = paint(boss, HudStyle.defaults().withBossStatus(HudStyle.BossStatus.CHIPS), HudStyle.Background.SHADE);
        PanelPainter.Text chip = text(chips, "UP 1:20");
        yes("chip text", chip != null);
        yes("chip text is dark and unshadowed", chip.color() == 0xFF101010 && !chip.shadow());
        yes("chip behind it", fills(chips).stream().anyMatch(f -> f.color() == THEME.good() && f.x1() <= chip.x() - 2
                && f.x2() >= chip.x() + FONT.applyAsInt("UP 1:20")));
        yes("a countdown stays text", text(chips, "1:35") != null);
        eq("name in the value colour", THEME.value(), text(chips, "Angry Archaeologist:").color());

        PanelPainter.Painted dots = paint(boss, HudStyle.defaults().withBossStatus(HudStyle.BossStatus.DOTS), HudStyle.Background.SHADE);
        yes("a dot in the status colour", fills(dots).stream().anyMatch(f -> f.color() == THEME.warn()
                && f.x2() - f.x1() == 4 && f.y2() - f.y1() == 4));
        yes("dot rows name then status", text(dots, "Nekronos:") != null && text(dots, "due now") != null);

        PanelPainter.Painted cols = paint(boss, HudStyle.defaults().withNumbers(HudStyle.Numbers.COLUMNS), HudStyle.Background.SHADE);
        PanelPainter.Text a = text(cols, "1:35");
        PanelPainter.Text b = text(cols, "learning...");
        eq("statuses right-aligned", a.x() + FONT.applyAsInt(a.text()), b.x() + FONT.applyAsInt(b.text()));
    }

    private static void barStyles() {
        List<HudModel.Line> bars = SamplePanels.progress(HudModel.ProgressLines.all());
        HudStyle base = HudStyle.defaults().withPercent(HudStyle.Percent.OFF);
        PanelPainter.Painted thin = paint(bars, base, HudStyle.Background.NONE);
        PanelPainter.Painted thick = paint(bars, base.withBar(HudStyle.BarStyle.THICK), HudStyle.Background.NONE);
        yes("thick is taller", thick.height() > thin.height());
        PanelPainter.Painted seg = paint(bars, base.withBar(HudStyle.BarStyle.SEGMENTED), HudStyle.Background.NONE);
        long tracks = fills(seg).stream().filter(f -> f.color() == THEME.barTrack()).count();
        eq("ten segments a bar", 30L, tracks);
        PanelPainter.Painted none = paint(bars, base.withBar(HudStyle.BarStyle.NONE), HudStyle.Background.NONE);
        yes("no bar, no bar fills", fills(none).stream().noneMatch(f -> f.color() == THEME.barTrack()));
        yes("no bar is shorter", none.height() < thin.height());
        List<HudModel.Line> onlyBars = SamplePanels.progress(HudModel.ProgressLines.all()).stream()
                .filter(l -> l.kind() == HudModel.Kind.BAR).toList();
        yes("bars alone with bars off paint nothing",
                paint(onlyBars, base.withBar(HudStyle.BarStyle.NONE), HudStyle.Background.SHADE).isEmpty());
        yes("bars are at least the minimum width", thin.width() >= PanelPainter.BAR_MIN_WIDTH);
    }

    private static void percentPlaces() {
        List<HudModel.Line> bars = SamplePanels.progress(HudModel.ProgressLines.all());
        HudStyle base = HudStyle.defaults();
        PanelPainter.Painted after = paint(bars, base.withPercent(HudStyle.Percent.AFTER), HudStyle.Background.SHADE);
        PanelPainter.Text ninety = text(after, "90%");
        yes("after the bar", ninety != null);
        int pad = PanelPainter.padding(base, HudStyle.Background.SHADE);
        eq("right-aligned to the panel", after.width() - pad, ninety.x() + FONT.applyAsInt("90%"));
        PanelPainter.Text hundred = text(after, "100%");
        eq("finished at 100%", THEME.barDone(), hundred.color());

        PanelPainter.Painted inside = paint(bars, base.withBar(HudStyle.BarStyle.THICK).withPercent(HudStyle.Percent.INSIDE),
                HudStyle.Background.SHADE);
        PanelPainter.Text in = text(inside, "31%");
        yes("inside the bar", in != null && in.color() == THEME.value());

        PanelPainter.Painted title = paint(bars, base.withPercent(HudStyle.Percent.TEXT), HudStyle.Background.SHADE);
        yes("in the text", text(title, "  90%") != null);
        PanelPainter.Text t = text(title, "Cobalt Pickaxe I  1.66b/1.84b");
        eq("straight after the amounts", t.x() + FONT.applyAsInt(t.text()), text(title, "  90%").x());

        PanelPainter.Painted off = paint(bars, base.withPercent(HudStyle.Percent.OFF), HudStyle.Background.SHADE);
        yes("off shows none", texts(off).stream().noneMatch(x -> x.text().trim().endsWith("%")));

        PanelPainter.Painted cols = paint(bars, base.withNumbers(HudStyle.Numbers.COLUMNS).withPercent(HudStyle.Percent.TEXT),
                HudStyle.Background.SHADE);
        PanelPainter.Text amt1 = text(cols, "1.66b/1.84b");
        PanelPainter.Text pct1 = text(cols, "  90%");
        PanelPainter.Text pct2 = text(cols, "  31%");
        eq("lined-up percentages end together", pct1.x() + FONT.applyAsInt(pct1.text()), pct2.x() + FONT.applyAsInt(pct2.text()));
        eq("percentage follows its amount", amt1.x() + FONT.applyAsInt(amt1.text()), pct1.x());
    }

    private static void barColours() {
        List<HudModel.Line> bars = SamplePanels.progress(HudModel.ProgressLines.all());
        HudStyle accent = HudStyle.defaults();
        PanelPainter.Painted a = paint(bars, accent, HudStyle.Background.SHADE);
        yes("one colour", fills(a).stream().anyMatch(f -> f.color() == THEME.barFill()));
        yes("finished bar in its own colour", fills(a).stream().anyMatch(f -> f.color() == THEME.barDone()));
        PanelPainter.Painted r = paint(bars, accent.withBarColor(HudStyle.BarColor.PROGRESS), HudStyle.Background.SHADE);
        yes("red to green uses the progress colour", fills(r).stream().anyMatch(f -> f.color() == Theme.progressColor(0.316)));
        yes("red to green still finishes in the done colour", fills(r).stream().anyMatch(f -> f.color() == THEME.barDone()));
    }

    private static void backgroundsAndTitles() {
        List<HudModel.Line> mine = SamplePanels.mine(OPTIONS);
        PanelPainter.Painted none = paint(mine, HudStyle.defaults(), HudStyle.Background.NONE);
        eq("no background draws no panel", PanelPainter.Text.class, none.ops().get(0).getClass());
        PanelPainter.Painted tip = paint(mine, HudStyle.defaults(), HudStyle.Background.TOOLTIP);
        eq("tooltip has a fading edge", 2L, tip.ops().stream().filter(o -> o instanceof PanelPainter.Gradient).count());
        yes("tooltip corners are notched", fills(tip).stream().noneMatch(f -> f.x1() == 0 && f.y1() == 0));
        PanelPainter.Painted framed = paint(mine, HudStyle.defaults(), HudStyle.Background.FRAMED);
        yes("framed has a border in the frame colour", fills(framed).stream().anyMatch(f -> f.color() == Theme.alpha(THEME.frame(), 80)));
        yes("more padding with a frame", framed.width() > paint(mine, HudStyle.defaults(), HudStyle.Background.SHADE).width());

        PanelPainter.Painted band = paint(mine, HudStyle.defaults().withTitles(HudStyle.Titles.BAND), HudStyle.Background.SHADE);
        yes("band behind the title", fills(band).stream().anyMatch(f -> f.color() == Theme.alpha(THEME.frame(), 30)
                && f.x1() == 0 && f.x2() == band.width() && f.y1() == 0));
        PanelPainter.Painted under = paint(mine, HudStyle.defaults().withTitles(HudStyle.Titles.UNDERLINE), HudStyle.Background.SHADE);
        yes("line under the title", fills(under).stream().anyMatch(f -> f.color() == Theme.alpha(THEME.frame(), 70)
                && f.y2() - f.y1() == 1));
        PanelPainter.Painted noShadow = paint(mine, HudStyle.defaults().withShadow(false), HudStyle.Background.SHADE);
        yes("shadow off", texts(noShadow).stream().noneMatch(PanelPainter.Text::shadow));
        PanelPainter.Painted roomy = paint(mine, HudStyle.defaults().withSpacing(HudStyle.Spacing.ROOMY), HudStyle.Background.SHADE);
        yes("roomy is taller", roomy.height() > paint(mine, HudStyle.defaults(), HudStyle.Background.SHADE).height());
    }

    private static void presets() {
        for (HudStyle.Preset p : HudStyle.Preset.values()) {
            yes(p.label + " percentage fits its bar", HudStyle.percentFits(p.style.percent(), p.style.bar()));
            yes(p.label + " has a description", !p.description.isEmpty());
        }
        eq("five designs", 5, HudStyle.Preset.values().length);
        eq("fork is the classic layout", HudStyle.Numbers.INLINE, HudStyle.Preset.FORK.style.numbers());
        eq("ledger goes red to green", HudStyle.BarColor.PROGRESS, HudStyle.Preset.LEDGER.style.barColor());
        eq("minimal has no panel", HudStyle.Background.NONE, HudStyle.Preset.MINIMAL.background);
    }

    /**
     * Every combination of every choice, on every example panel and background: nothing drawn
     * outside the panel's own size, which is what the position screen grabs and keeps on screen.
     */
    private static void everythingStaysInsideThePanel() {
        List<List<HudModel.Line>> panels = List.of(SamplePanels.mine(CREDITS), SamplePanels.mine(OPTIONS),
                SamplePanels.bosses(OPTIONS), SamplePanels.progress(new HudModel.ProgressLines(true, true, true)),
                SamplePanels.alert(), SamplePanels.halloween(), SamplePanels.pumpkin(), SamplePanels.abilities());
        int checked = 0;
        String problem = null;
        for (HudStyle.Titles t : HudStyle.Titles.values())
            for (HudStyle.Numbers n : HudStyle.Numbers.values())
                for (HudStyle.GearLabels g : HudStyle.GearLabels.values())
                    for (HudStyle.BossStatus b : HudStyle.BossStatus.values())
                        for (HudStyle.BarStyle bar : HudStyle.BarStyle.values())
                            for (HudStyle.Percent pc : HudStyle.Percent.values())
                                for (HudStyle.Spacing sp : HudStyle.Spacing.values())
                                    for (HudStyle.Background bg : HudStyle.Background.values()) {
                                        HudStyle style = new HudStyle(t, n, g, b, bar, pc, HudStyle.BarColor.PROGRESS, sp, true);
                                        for (List<HudModel.Line> lines : panels) {
                                            PanelPainter.Painted p = paint(lines, style, bg);
                                            checked++;
                                            String bad = outside(p);
                                            if (bad != null && problem == null) {
                                                problem = style + " " + bg + " " + lines.get(0).text() + ": " + bad;
                                            }
                                        }
                                    }
        yes("checked " + checked + " panels", checked > 10000);
        eq("nothing drawn outside its panel", null, problem);
    }

    private static String outside(PanelPainter.Painted p) {
        int w = p.width();
        int h = p.height();
        for (PanelPainter.Op op : p.ops()) {
            switch (op) {
                case PanelPainter.Fill f -> {
                    if (f.x1() < 0 || f.y1() < 0 || f.x2() > w || f.y2() > h) return "fill " + f + " in " + w + "x" + h;
                }
                case PanelPainter.Gradient g -> {
                    if (g.x1() < 0 || g.y1() < 0 || g.x2() > w || g.y2() > h) return "gradient " + g;
                }
                case PanelPainter.Text t -> {
                    if (t.x() < 0 || t.y() < 0 || t.x() + FONT.applyAsInt(t.text()) > w || t.y() + 8 > h) {
                        return "text " + t + " in " + w + "x" + h;
                    }
                }
                case PanelPainter.Icon i -> {
                    if (i.x() < 0 || i.y() < 0 || i.x() + PanelPainter.ICON > w || i.y() + PanelPainter.ICON > h) return "icon " + i;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ util

    private static void eq(String what, Object expected, Object actual) {
        if (expected == null ? actual == null : expected.equals(actual)) pass(what); else fail(what, expected, actual);
    }

    private static void yes(String what, boolean c) {
        if (c) pass(what); else fail(what, "true", "false");
    }

    private static void pass(String what) {
        passed++;
        System.out.println("  ok   " + what);
    }

    private static void fail(String what, Object e, Object a) {
        failed++;
        System.out.println("  FAIL " + what + "  expected <" + e + "> got <" + a + ">");
    }
}
