package minerefinehud.hud;

import minerefinehud.boss.BossTracker;
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
        lines.add(Line.of("Halloween", Style.HEADER));
        lines.add(Line.of("Candy 21.5  1.2k/h", Style.VALUE));
        lines.add(Line.of("Crops 45.2k/h", Style.VALUE));
        lines.add(Line.of(" Pumpkin 12,400  20.1k/h", Style.LABEL));
        lines.add(Line.of("Caravan in 10:00", Style.VALUE));
        String amounts = "21.5/50 candy";
        lines.add(new Line("Refine II: " + amounts, Style.VALUE, 0.43, HudModel.Kind.PROGRESS, "Refine II",
                Style.VALUE, List.of(Cell.of(amounts, Style.VALUE)), ""));
        lines.add(Line.bar(0.43));
        return lines;
    }

    public static List<Line> pumpkin() {
        return List.of(
                Line.of("Pumpkin Helmet", Style.HEADER),
                Line.of(" Candy 300/500", Style.VALUE),
                Line.of(" Pumpkin 2.5m/2.5m", Style.GOOD),
                Line.bar(0.8));
    }

    public static List<Line> abilities() {
        return List.of(Line.of("Will-o'-Wisps  READY", Style.GOOD),
                Line.of("Harvest Moon  12.3s", Style.WARN));
    }
}
