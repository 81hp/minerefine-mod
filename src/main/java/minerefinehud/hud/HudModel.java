package minerefinehud.hud;

import minerefinehud.boss.BossTracker;
import minerefinehud.mine.Mine;
import minerefinehud.progress.MineCosts;
import minerefinehud.progress.ProgressPlanner;
import minerefinehud.shop.PriceLedger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Builds exactly what the overlay should say, as plain strings.
 *
 * All the decisions live here rather than in the renderer, so the overlay can be tested without
 * a game running and so a Minecraft rendering API change never touches the logic.
 */
public final class HudModel {

    public enum Style {
        HEADER,
        LABEL,
        VALUE,
        GOOD,
        WARN,
        DIM,
        /** A progress bar rather than text. The fill is {@link Line#progress()}. */
        BAR
    }

    /** @param progress 0 to 1 for a BAR line, ignored otherwise */
    public record Line(String text, Style style, double progress) {

        public Line(String text, Style style) {
            this(text, style, 0.0);
        }

        public static Line of(String text, Style style) {
            return new Line(text, style);
        }

        public Line withStyle(Style newStyle) {
            return new Line(text, newStyle, progress);
        }

        public static Line bar(double progress) {
            return new Line("", Style.BAR, Math.max(0.0, Math.min(1.0, progress)));
        }
    }

    /** Which rows each panel shows. Every one defaults to on, so nothing disappears by upgrading. */
    public record MineLines(boolean sword, boolean tool, boolean armor, boolean charm,
                            boolean total, boolean upgrade) {

        public static MineLines all() {
            return new MineLines(true, true, true, true, true, true);
        }
    }

    /**
     * @param upTimers  "Boss: UP (1:20)" rows for bosses currently alive
     * @param learning  rows for bosses whose timer is not known yet ("learning...", "unknown")
     */
    public record BossLines(boolean upTimers, boolean learning) {

        public static BossLines all() {
            return new BossLines(true, true);
        }
    }

    /** @param text the "Debris Shovel IV  3.1b/4.39b" row; @param bar the bar under it */
    public record ProgressLines(boolean text, boolean bar) {

        public static ProgressLines all() {
            return new ProgressLines(true, true);
        }
    }

    public record Options(boolean showMine,
                          boolean showArmorPieces,
                          boolean showCredits,
                          double blocksPerCreditMillions,
                          boolean showBosses,
                          int maxBosses,
                          MineLines mineLines,
                          BossLines bossLines,
                          ProgressLines progressLines) {

        public static Options defaults() {
            return new Options(true, false, false, 200.0, true, 5,
                    MineLines.all(), BossLines.all(), ProgressLines.all());
        }
    }

    /**
     * The player's progress on one gear slot, from the item they are carrying and the prices the
     * client has observed.
     */
    public record UpgradeView(String mine, String gear, int currentLevel, int maxLevel,
                              java.util.OptionalLong nextCost,
                              java.util.OptionalLong remaining,
                              boolean fromObservation) {

        public boolean maxed() {
            return currentLevel >= maxLevel;
        }
    }

    private HudModel() {
    }

    public static List<Line> build(Optional<Mine> currentMine,
                                   List<BossTracker.BossView> bosses,
                                   Options options,
                                   long nowMs) {
        return build(currentMine, Optional.empty(), bosses, options, nowMs);
    }

    /** Both panels stacked as one, separated by a blank line. */
    public static List<Line> build(Optional<Mine> currentMine,
                                   Optional<UpgradeView> upgrade,
                                   List<BossTracker.BossView> bosses,
                                   Options options,
                                   long nowMs) {
        List<Line> lines = new ArrayList<>(minePanel(currentMine, upgrade, options));
        List<Line> boss = bossPanel(bosses, options);
        if (!lines.isEmpty() && !boss.isEmpty()) {
            lines.add(Line.of("", Style.DIM));
        }
        lines.addAll(boss);
        return lines;
    }

    /**
     * The mine costs and the upgrade for the gear in hand. Kept together because both answer
     * "what does progressing here cost", and separate from the bosses so each can be placed on
     * its own.
     */
    public static List<Line> minePanel(Optional<Mine> currentMine,
                                       Optional<UpgradeView> upgrade,
                                       Options options) {
        // From the bundled data alone, with no shop prices. The game passes the full view instead.
        return costPanel(currentMine.map(m -> MineCosts.of(m.name(), Optional.of(m),
                new PriceLedger(), options.showArmorPieces())), upgrade, options);
    }

    /** The mine panel from costs already combined with shop prices, see {@link MineCosts}. */
    public static List<Line> costPanel(Optional<MineCosts.View> costs,
                                       Optional<UpgradeView> upgrade,
                                       Options options) {
        List<Line> lines = new ArrayList<>();

        if (options.showMine()) {
            appendMine(lines, costs, options);
        }

        upgrade.filter(u -> options.mineLines().upgrade()).ifPresent(u -> {
            if (!lines.isEmpty()) {
                lines.add(Line.of("", Style.DIM));
            }
            appendUpgrade(lines, u);
        });

        return lines;
    }

    /**
     * Boss timers on their own. Empty until a boss has been seen, and empty again if every row
     * is switched off, so a lone header is never drawn.
     */
    public static List<Line> bossPanel(List<BossTracker.BossView> bosses, Options options) {
        List<Line> lines = new ArrayList<>();
        if (options.showBosses() && !bosses.isEmpty()) {
            appendBosses(lines, bosses, options);
        }
        return lines.size() > 1 ? lines : new ArrayList<>();
    }

    /**
     * The respawn reminder, one line per boss, drawn as its own panel so it can be placed and
     * sized apart from the timers. Usually one boss; two only if their windows overlap.
     */
    public static List<Line> alertPanel(List<String> bossNames) {
        List<Line> lines = new ArrayList<>();
        for (String name : bossNames) {
            lines.add(Line.of(name + " is about to respawn!", Style.WARN));
        }
        return lines;
    }

    // -------------------------------------------------------------- progress

    /**
     * One upgrade and how close the player is to affording it:
     *
     *   Debris Shovel IV  3.1b/4.39b
     *   [=========-----]
     *
     * and once affordable:
     *
     *   Debris Shovel IV  finished  5.02b
     *   [==============]
     *
     * Empty when the bar is switched off, so nothing is drawn.
     */
    public static List<Line> progressPanel(Optional<ProgressPlanner.ProgressView> view) {
        return progressPanel(view.map(List::of).orElse(List.of()), ProgressLines.all());
    }

    /**
     * Every tracked upgrade stacked in one panel, each as its own row and bar. A quantity shows
     * in front, "12x Rafter Chestplate IV", and the figures are for all twelve together.
     */
    public static List<Line> progressPanel(List<ProgressPlanner.ProgressView> views, ProgressLines show) {
        List<Line> lines = new ArrayList<>();
        for (ProgressPlanner.ProgressView v : views) {
            appendProgress(lines, v, show);
        }
        return lines;
    }

    private static void appendProgress(List<Line> lines, ProgressPlanner.ProgressView v, ProgressLines show) {
        String count = v.quantity() > 1 ? v.quantity() + "x " : "";
        // "III-IV" when the bar covers every tier still to buy, not just the next one.
        String tiers = v.toLevel() > v.targetLevel()
                ? romanish(v.targetLevel()) + "-" + romanish(v.toLevel())
                : romanish(v.targetLevel());
        // "Total" only for the whole mine, the mine panel's own figure; what is left after the
        // gear already owned is "still to buy", so two different figures never share a name.
        String piece = v.slot().isTotal()
            ? count + v.mine() + (v.toLevel() > v.targetLevel() ? " Total" : " still to buy")
            : count + v.mine() + " " + capitalise(v.gear()) + " " + tiers;

        switch (v.state()) {
            case TRACKING -> {
                String have = v.have().isPresent() ? Formatting.blocks(v.have().getAsLong()) : "?";
                if (show.text()) {
                    lines.add(Line.of(piece + "  " + have + "/" + Formatting.blocks(v.cost().getAsLong()),
                            Style.VALUE));
                }
                if (show.bar()) {
                    lines.add(Line.bar(v.fraction()));
                }
            }
            case FINISHED -> {
                if (show.text()) {
                    lines.add(Line.of(piece + "  finished  " + Formatting.blocks(v.have().getAsLong()),
                            Style.GOOD));
                }
                if (show.bar()) {
                    lines.add(Line.bar(1.0));
                }
            }
            case PRICE_UNKNOWN -> {
                lines.add(Line.of(piece, Style.VALUE));
                // The tier is known from the inventory; only its price is missing, and opening
                // that shop once fixes it for good.
                lines.add(Line.of("open the " + v.mine() + " shop once for the "
                        + (v.slot().isTotal() ? "prices" : "price"), Style.DIM));
            }
            case ALL_MAXED -> lines.add(Line.of(
                    v.slot().label() + ": every known tier done", Style.GOOD));
            case NO_MINE -> lines.add(Line.of(
                    v.slot().label() + ": go to a mine to start", Style.DIM));
        }
    }

    // ------------------------------------------------------------------ mine

    private static void appendMine(List<Line> lines, Optional<MineCosts.View> costs, Options options) {
        if (costs.isEmpty()) {
            lines.add(Line.of("Mine: unknown", Style.DIM));
            lines.add(Line.of("open this mine's shop once to learn it", Style.DIM));
            return;
        }

        MineCosts.View view = costs.get();
        lines.add(Line.of((view.mine() + "  " + view.world()).trim(), Style.HEADER));

        if (!view.anyKnown()) {
            // A new mine, or one the data has no prices for. Saying so beats zeros, which would
            // read as "free", and beats "unknown", which reads as broken.
            lines.add(Line.of("no prices yet, open this mine's shop", Style.DIM));
            return;
        }

        for (MineCosts.Piece piece : view.pieces()) {
            if (!shown(piece.gear(), options.mineLines())) {
                continue;
            }
            String label = capitalise(piece.gear());
            if (!piece.shown()) {
                lines.add(Line.of(label + ": open the shop", Style.DIM));
                continue;
            }
            if (piece.source() == MineCosts.Source.PARTIAL) {
                // Only the tiers the shop has shown, so say which; never counted in the total.
                lines.add(costLine(label, piece.cost().getAsLong(), "* (" + piece.tiers() + ")", options));
                continue;
            }
            // A star marks a figure read from the shop rather than taken from the spreadsheet.
            String mark = piece.source() == MineCosts.Source.SHOP ? "*" : "";
            lines.add(costLine(label, piece.cost().getAsLong(), mark, options));
        }

        if (!options.mineLines().total()) {
            return;
        }
        // Always the whole mine, even with some rows hidden: hiding a row changes what is shown,
        // not what the mine costs.
        if (view.total().isPresent()) {
            lines.add(costLine("Total", view.total().getAsLong(), "", options).withStyle(Style.HEADER));
        } else {
            lines.add(Line.of("Total: incomplete", Style.DIM));
        }
    }

    private static boolean shown(String gear, MineLines show) {
        return switch (gear) {
            case "sword" -> show.sword();
            case "charm" -> show.charm();
            case "armor", "helmet", "chestplate", "leggings", "boots" -> show.armor();
            default -> show.tool();   // pickaxe, axe, shovel, or "tool" before it is known
        };
    }

    private static Line costLine(String label, long blocks, String mark, Options options) {
        StringBuilder sb = new StringBuilder();
        sb.append(label).append(": ").append(Formatting.blocks(blocks)).append(mark);
        if (options.showCredits()) {
            sb.append("  (").append(Formatting.credits(blocks, options.blocksPerCreditMillions()))
              .append(')');
        }
        return Line.of(sb.toString(), Style.VALUE);
    }

    // -------------------------------------------------------------- upgrade

    private static void appendUpgrade(List<Line> lines, UpgradeView u) {
        String slot = capitalise(u.gear());
        lines.add(Line.of(slot + " " + romanish(u.currentLevel()) + "/" + romanish(u.maxLevel()),
                Style.HEADER));

        if (u.maxed()) {
            lines.add(Line.of("maxed", Style.GOOD));
            return;
        }

        if (u.nextCost().isPresent()) {
            // A star marks a price the client read from a shop tooltip rather than one seeded
            // from the spreadsheet, so a stale seed never looks as authoritative as a real one.
            String mark = u.fromObservation() ? "*" : "";
            lines.add(Line.of("Next: " + Formatting.blocks(u.nextCost().getAsLong()) + mark,
                    Style.VALUE));
        } else {
            lines.add(Line.of("Next: open the shop once", Style.DIM));
        }

        if (u.remaining().isPresent()) {
            lines.add(Line.of("To max: " + Formatting.blocks(u.remaining().getAsLong()),
                    Style.VALUE));
        }
    }

    /** Levels are shown as Roman numerals in game, so the HUD matches. */
    private static String romanish(int level) {
        return minerefinehud.shop.RomanNumerals.toRoman(level);
    }

    // ----------------------------------------------------------------- boss

    private static void appendBosses(List<Line> lines,
                                     List<BossTracker.BossView> bosses,
                                     Options options) {
        lines.add(Line.of("Bosses", Style.HEADER));

        int shown = 0;
        for (BossTracker.BossView boss : bosses) {
            if (shown >= options.maxBosses()) {
                break;
            }
            if (!rowShown(boss, options.bossLines())) {
                continue;
            }
            lines.add(bossLine(boss));
            shown++;
        }
    }

    private static boolean rowShown(BossTracker.BossView boss, BossLines show) {
        return switch (boss.status()) {
            case ALIVE -> show.upTimers();
            case DEAD -> boss.intervalKnown() || show.learning();
            case UNKNOWN -> show.learning();
        };
    }

    private static Line bossLine(BossTracker.BossView boss) {
        String name = boss.displayName();

        return switch (boss.status()) {
            case ALIVE -> Line.of(
                    name + ": UP  (" + Formatting.duration(boss.sinceEventMs()) + ")",
                    Style.GOOD);

            case DEAD -> {
                if (!boss.intervalKnown()) {
                    // First kill seen. The gap to the next spawn is what teaches us the interval.
                    yield Line.of(name + ": learning...", Style.DIM);
                }
                if (boss.overdue()) {
                    yield Line.of(name + ": due now", Style.WARN);
                }
                String eta = Formatting.duration(boss.etaMs().orElse(0L));
                // A single sample is a guess, not a measurement. Say so rather than look certain.
                String marker = boss.intervalConfident() ? "" : " ?";
                yield Line.of(name + ": " + eta + marker, Style.VALUE);
            }

            case UNKNOWN -> Line.of(name + ": unknown", Style.DIM);
        };
    }

    private static String capitalise(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }
}
