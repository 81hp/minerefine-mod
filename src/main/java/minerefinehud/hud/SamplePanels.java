package minerefinehud.hud;

import minerefinehud.boss.BossTracker;
import minerefinehud.halloween.Buildings;
import minerefinehud.halloween.HalloweenHud;
import minerefinehud.halloween.RecurringEvent;
import minerefinehud.halloween.Resurrection;
import minerefinehud.hud.HudModel.Cell;
import minerefinehud.hud.HudModel.Line;
import minerefinehud.hud.HudModel.Style;
import minerefinehud.progress.ProgressPlanner;
import minerefinehud.progress.ProgressSlot;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

/**
 * Made-up panels for the settings previews, so every look can be judged before the player has
 * seen a boss or set up a bar. Built by the same code as the real panels, so they show exactly
 * what the real ones will.
 */
public final class SamplePanels {

    private SamplePanels() {
    }

    public static List<Line> mine(HudModel.Options options) {
        List<Line> lines = new ArrayList<>();
        lines.add(Line.of("Icicle", Style.HEADER));
        lines.add(HudModel.costLine("Sword", "sword", 20_240_000_000L, "*", options));
        lines.add(HudModel.costLine("Pickaxe", "pickaxe", 15_180_000_000L, "*", options));
        lines.add(HudModel.costLine("Armor", "armor", 85_500_000_000L, "*", options));
        lines.add(HudModel.costLine("Charm", "charm", 13_300_000_000L, "*", options));
        lines.add(HudModel.totalLine(134_220_000_000L, options));
        return lines;
    }

    public static List<Line> bosses(HudModel.Options options) {
        List<BossTracker.BossView> views = List.of(
                new BossTracker.BossView("Angry Archaeologist", BossTracker.Status.ALIVE, 80_000L,
                        OptionalLong.empty(), 3, OptionalLong.of(3_600_000L), false),
                new BossTracker.BossView("Nekronos", BossTracker.Status.DEAD, 0L,
                        OptionalLong.of(-5_000L), 3, OptionalLong.of(3_600_000L), false),
                new BossTracker.BossView("The King & Queen", BossTracker.Status.DEAD, 0L,
                        OptionalLong.of(95_000L), 3, OptionalLong.of(3_600_000L), false),
                new BossTracker.BossView("Bluzon", BossTracker.Status.DEAD, 0L,
                        OptionalLong.empty(), 1, OptionalLong.empty(), false));
        HudModel.Options all = new HudModel.Options(options.showMine(), options.showArmorPieces(),
                options.showCredits(), options.blocksPerCreditMillions(), true, 15,
                options.mineLines(), HudModel.BossLines.all(), options.progressLines());
        return HudModel.bossPanel(views, all);
    }

    public static List<Line> progress(HudModel.ProgressLines show) {
        List<ProgressPlanner.ProgressView> views = List.of(
                new ProgressPlanner.ProgressView(ProgressSlot.PICKAXE, "Cobalt", "pickaxe", 1,
                        ProgressPlanner.State.TRACKING, OptionalLong.of(1_840_000_000L),
                        OptionalLong.of(1_660_000_000L), "Cobalt", 1, 1),
                new ProgressPlanner.ProgressView(ProgressSlot.CHESTPLATE, "Icicle", "chestplate", 4,
                        ProgressPlanner.State.TRACKING, OptionalLong.of(26_600_000_000L),
                        OptionalLong.of(8_410_000_000L), "Icicle", 12, 4),
                new ProgressPlanner.ProgressView(ProgressSlot.SWORD, "Icicle", "sword", 2,
                        ProgressPlanner.State.FINISHED, OptionalLong.of(20_240_000_000L),
                        OptionalLong.of(21_030_000_000L), "Icicle", 1, 2));
        // Text on even when switched off, or there would be nothing to judge the bars against.
        return HudModel.progressPanel(views, new HudModel.ProgressLines(true, show.bar(), show.needed()));
    }

    public static List<Line> alert() {
        return HudModel.alertPanel(List.of("Angry Archaeologist"));
    }

    public static List<Line> halloween() {
        List<Line> lines = new ArrayList<>();
        lines.add(Line.header("Halloween", HalloweenHud.PUMPKIN_ICON));
        lines.add(Line.section("RATES"));
        lines.add(HalloweenHud.figures(HudModel.Kind.ROW, "Candy", HalloweenHud.icon("Candy"), "21.5", "1.2k/h"));
        lines.add(HalloweenHud.figures(HudModel.Kind.ROW, "Carrots", HalloweenHud.icon("Carrots"), "56,200", "20.1k/h"));
        lines.add(HalloweenHud.figures(HudModel.Kind.ROW, "Beetroots", HalloweenHud.icon("Beetroots"), "59,713", "12k/h"));
        lines.add(HalloweenHud.figures(HudModel.Kind.TOTAL, "Crops", "item:hay_block", "", "32.1k/h"));
        lines.add(Line.section("BUILDINGS"));
        lines.addAll(HalloweenHud.buildingLines(List.of(
                new Buildings.View("Barn", Buildings.State.SAVING, 4, 5, "Carrots", 56_200, 1_500_000,
                        56_200 / 1_500_000.0, false, 0L, "crops x1.8 → x2"),
                new Buildings.View("Forge", Buildings.State.UPGRADING, 2, 3, "Beetroots", 0, 0, 0.58, false,
                        151_000L, ""))));
        lines.add(Line.section("RESURRECTION"));
        lines.addAll(HalloweenHud.resurrectionLines(new Resurrection.View(Resurrection.State.SAVING, 6,
                303_808_740, 350_000_000, "Fire Coral Hoe VII", Boolean.TRUE, "", "", -1L)));
        lines.add(Line.section("EVENTS"));
        lines.add(HalloweenHud.caravanLine(new RecurringEvent.View(RecurringEvent.State.WAITING, 600_000L, true)));
        String amounts = "21.5/50 candy";
        lines.add(new Line("Refine II: " + amounts, Style.VALUE, 0.43, HudModel.Kind.PROGRESS, "Refine II",
                Style.VALUE, List.of(Cell.of(amounts, Style.VALUE)), "item:netherite_hoe"));
        lines.add(Line.bar(0.43));
        return lines;
    }

    public static List<Line> pumpkin() {
        return List.of(
                Line.header("Pumpkin Helmet", "item:carved_pumpkin"),
                Line.row("Candy", HalloweenHud.icon("Candy"), Style.VALUE, Cell.of("300/500", Style.VALUE)),
                Line.row("Carrots", HalloweenHud.icon("Carrots"), Style.GOOD, Cell.of("2.5m/2.5m", Style.GOOD)),
                Line.bar(0.8));
    }

    public static List<Line> abilities() {
        return List.of(
                Line.status("Will-o'-Wisps", Style.LABEL, new Cell("READY", Style.GOOD, "READY")),
                Line.status("Harvest Moon", Style.LABEL, new Cell("12.3s", Style.HEADER, "12.3s")));
    }

    /** Shale at 1.25b an hour and 1.2m EXP an hour, with a bar due in two hours. */
    public static List<Line> mining() {
        return ExtrasHud.miningPanel(new ExtrasHud.Mining("Shale", OptionalLong.of(36_420_000_000L), 1_250_000_000.0,
                4_510_000_000.0, 3L * 3_600_000L + 25L * 60_000L,
                List.of(new ExtrasHud.Eta("Total left", (2L * 60L + 13L) * 60_000L),
                        new ExtrasHud.Eta("Pickaxe IV", 41L * 60_000L))),
                new ExtrasHud.Xp(1_200_000.0, 4_100_000.0));
    }

    /** Two minutes forty to the next Beacon Mine reset. */
    public static List<Line> beacon() {
        return ExtrasHud.beaconPanel(160_000L, false);
    }

    /** The two outposts from the /outpost screenshots. */
    public static List<Line> outposts() {
        long hour = 3_600_000L;
        minerefinehud.outpost.OutpostTimers.Saved saved = minerefinehud.outpost.OutpostTimers.update(null, List.of(
                new minerefinehud.outpost.OutpostTimers.Reading("Ruins", 3 * hour + 58 * 60_000L + 40_000L),
                new minerefinehud.outpost.OutpostTimers.Reading("Sift", hour + 17 * 60_000L + 55_000L)), 0L);
        return ExtrasHud.outpostPanel(minerefinehud.outpost.OutpostTimers.views(saved, 0L), 60_000L);
    }

    /** The stats from the /stats screenshot, Fortune just gone up. */
    public static List<Line> stats() {
        minerefinehud.stats.PlayerStats.Saved before = minerefinehud.stats.PlayerStats.update(null, List.of(
                new minerefinehud.stats.PlayerStats.Stat("Fortune", "951,200", "Mining")), 0L);
        return ExtrasHud.statsPanel(minerefinehud.stats.PlayerStats.update(before, List.of(
                new minerefinehud.stats.PlayerStats.Stat("Fortune", "967,900", "Mining"),
                new minerefinehud.stats.PlayerStats.Stat("Luck", "321.9", "Mining"),
                new minerefinehud.stats.PlayerStats.Stat("Turret Luck", "640.6", "Mining"),
                new minerefinehud.stats.PlayerStats.Stat("Speed", "x2.4", "Mining")), 1L), java.util.Set.of());
    }

    /** A Nebula a minute ago among the usual charms and turrets. */
    public static List<Line> drops() {
        minerefinehud.drops.RareDrops.Counter c =
                new minerefinehud.drops.RareDrops.Counter(new minerefinehud.drops.RareDrops.Saved());
        c.add(new minerefinehud.drops.RareDrops.Drop("RARE", "Sculk Charm", 41), 1_000L);
        c.add(new minerefinehud.drops.RareDrops.Drop("RARE", "Netherrack Turret", 3), 2_000L);
        c.add(new minerefinehud.drops.RareDrops.Drop("EXOTIC", "LEGENDARY Key", 1), 3_000L);
        c.add(new minerefinehud.drops.RareDrops.Drop("SPECIAL", "Nebula", 1), 600_000L);
        return ExtrasHud.dropsPanel(c.rows(java.util.Set.of(), 8), c.recent(java.util.Set.of(), 3), 660_000L);
    }
}
