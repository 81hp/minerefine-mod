package minerefinehud.halloween;

import minerefinehud.hud.Formatting;
import minerefinehud.hud.HudModel;
import minerefinehud.hud.HudModel.Cell;
import minerefinehud.hud.HudModel.Line;
import minerefinehud.hud.HudModel.Style;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What the Halloween panels say, as plain lines, like {@link HudModel} for the mining panels.
 *
 * The stats panel is in three parts under small headings: Rates, Buildings and Events. Rows name
 * their crop with its item ("item:carrot"), drawn whatever the gear label setting. No Minecraft
 * types.
 */
public final class HalloweenHud {

    private HalloweenHud() {
    }

    public static final String PUMPKIN_ICON = "item:jack_o_lantern";

    /** Which rows the stats panel shows. */
    public record StatsLines(boolean candyRate, boolean cropRates, boolean perCrop, boolean pests,
                             boolean caravan, boolean refine, boolean buildings) {

        public static StatsLines all() {
            return new StatsLines(true, true, true, true, true, true, true);
        }
    }

    /** Everything the stats panel needs, gathered by the caller. */
    public record Stats(Map<String, Double> balances,
                        Rates rates,
                        long windowMs,
                        double pestCandy,
                        int pestCharms,
                        RecurringEvent.View caravan,
                        double refineCost,
                        int refineLevel,
                        List<Buildings.View> buildings) {}

    /** The item drawn for a currency, or none. */
    public static String icon(String currency) {
        if (currency == null) {
            return "";
        }
        return switch (currency) {
            case HalloweenText.CANDY -> "item:candy";
            case "Carrots" -> "item:carrot";
            case "Beetroots" -> "item:beetroot";
            case "Nether Wart" -> "item:nether_wart";
            case "Fire Coral" -> "item:fire_coral";
            case "Hallowed" -> "item:hallowed";
            case "Potatoes" -> "item:potato";
            case "Wheat" -> "item:wheat";
            case "Pumpkins" -> "item:pumpkin";
            case "Melons" -> "item:melon_slice";
            default -> "";
        };
    }

    /** Candy keeps its decimal, "21.5"; crops are whole, "45,395" or "2.5m". */
    public static String amount(double v) {
        if (v >= 1_000_000.0 || (v >= 1000.0 && v == Math.rint(v))) {
            return Formatting.blocks(Math.round(v));
        }
        if (v == Math.rint(v)) {
            return String.format(Locale.ROOT, "%,d", Math.round(v));
        }
        return String.format(Locale.ROOT, "%,.1f", v);
    }

    /** "45.2k", or "…" while there is too little time measured. */
    static String rate(double perHour) {
        if (Double.isNaN(perHour)) {
            return "…";
        }
        if (perHour >= 1000.0) {
            return compact(perHour);
        }
        return String.format(Locale.ROOT, "%.1f", perHour);
    }

    /** 45,200 as "45.2k", 2,500,000 as "2.5m". */
    public static String compact(double v) {
        if (v >= 1_000_000.0) {
            return Formatting.blocks(Math.round(v));
        }
        if (v >= 1000.0) {
            String s = String.format(Locale.ROOT, "%.1f", v / 1000.0);
            return (s.endsWith(".0") ? s.substring(0, s.length() - 2) : s) + "k";
        }
        return amount(v);
    }

    /**
     * A balance and its rate: "Carrots 45,395  20.1k/h" written out, or label, amount and rate
     * in columns.
     */
    public static Line figures(HudModel.Kind kind, String label, String icon, String amount, String rate) {
        StringBuilder text = new StringBuilder(label);
        if (!amount.isEmpty()) {
            text.append(' ').append(amount);
        }
        if (!rate.isEmpty()) {
            text.append(amount.isEmpty() ? " " : "  ").append(rate);
        }
        return new Line(text.toString(), Style.VALUE, 0.0, kind, label, Style.LABEL,
                List.of(Cell.of(amount, Style.VALUE), Cell.of(rate, Style.HEADER)), icon);
    }

    public static List<Line> statsPanel(Stats s, StatsLines show) {
        List<Line> lines = new ArrayList<>();
        lines.add(Line.header("Halloween", PUMPKIN_ICON));

        Set<String> crops = new HashSet<>(HalloweenText.CROPS);

        List<Line> rates = new ArrayList<>();
        if (show.candyRate()) {
            String candy = s.balances().containsKey(HalloweenText.CANDY)
                    ? amount(s.balances().get(HalloweenText.CANDY)) : "?";
            rates.add(figures(HudModel.Kind.ROW, "Candy", icon(HalloweenText.CANDY), candy,
                    rate(s.rates().perHour(HalloweenText.CANDY, s.windowMs())) + "/h"));
        }
        if (show.cropRates()) {
            int each = 0;
            if (show.perCrop()) {
                for (String crop : HalloweenText.CROPS) {
                    if (!s.balances().containsKey(crop)) {
                        continue;
                    }
                    double r = s.rates().perHour(crop, s.windowMs());
                    rates.add(figures(HudModel.Kind.ROW, crop, icon(crop), amount(s.balances().get(crop)),
                            Double.isNaN(r) || r <= 0 ? "" : rate(r) + "/h"));
                    each++;
                }
            }
            // Under the crops it adds up, set apart by a rule; on its own it is a plain row.
            rates.add(figures(each > 0 ? HudModel.Kind.TOTAL : HudModel.Kind.ROW, "Crops", "item:hay_block", "",
                    rate(s.rates().perHour(crops, s.windowMs())) + "/h"));
        }
        section(lines, "RATES", rates);

        if (show.buildings()) {
            section(lines, "BUILDINGS", buildingLines(s.buildings()));
        }

        List<Line> events = new ArrayList<>();
        if (show.caravan()) {
            events.add(caravanLine(s.caravan()));
        }
        if (show.pests() && (s.pestCandy() > 0 || s.pestCharms() > 0)) {
            StringBuilder sb = new StringBuilder("+").append(amount(s.pestCandy())).append(" candy");
            if (s.pestCharms() > 0) {
                sb.append(", ").append(s.pestCharms()).append(s.pestCharms() == 1 ? " charm" : " charms");
            }
            // One figure to the right edge, so it does not widen the balance column.
            events.add(Line.status("Pests", Style.LABEL, Cell.of(sb.toString(), Style.VALUE))
                    .withIcon("item:spider_eye"));
        }
        if (show.refine() && s.refineCost() > 0) {
            double have = s.balances().getOrDefault(HalloweenText.CANDY, 0.0);
            String level = s.refineLevel() > 0
                    ? " " + minerefinehud.shop.RomanNumerals.toRoman(s.refineLevel() + 1) : "";
            Style style = have >= s.refineCost() ? Style.GOOD : Style.VALUE;
            String amounts = amount(have) + "/" + amount(s.refineCost()) + " candy";
            // A bar title, so the percentage can sit in it; written "Refine II: ..." as before.
            events.add(new Line("Refine" + level + ": " + amounts, style, Math.min(1.0, have / s.refineCost()),
                    HudModel.Kind.PROGRESS, "Refine" + level, style, List.of(Cell.of(amounts, style)),
                    "item:netherite_hoe"));
            events.add(Line.bar(have / s.refineCost()));
        }
        section(lines, "EVENTS", events);
        return lines;
    }

    /** A heading and its rows, or nothing when there are no rows. */
    private static void section(List<Line> lines, String title, List<Line> rows) {
        if (!rows.isEmpty()) {
            lines.add(Line.section(title));
            lines.addAll(rows);
        }
    }

    /**
     * One bar per building: "Barn → Lv 5  56.2k/1.5m", the bar, and what the level gives,
     * "crops x1.8 → x2". An upgrade under way counts down instead.
     */
    public static List<Line> buildingLines(List<Buildings.View> views) {
        List<Line> lines = new ArrayList<>();
        for (Buildings.View v : views) {
            String icon = icon(v.currency());
            switch (v.state()) {
                case SAVING -> {
                    Style style = v.affordable() ? Style.GOOD : Style.VALUE;
                    lines.add(Line.progressTitle(v.name() + " → Lv " + v.target(),
                            compact(v.have()) + "/" + compact(v.need()), style, v.fraction(), icon));
                    lines.add(Line.bar(v.fraction()));
                    // One boost a line, so a building with several does not widen the panel.
                    for (String perk : v.perks().split(" · ")) {
                        if (!perk.isEmpty()) {
                            lines.add(Line.of("   " + perk, Style.DIM));
                        }
                    }
                }
                case UPGRADING -> {
                    lines.add(Line.progressTitle(v.name() + " → Lv " + v.target(),
                            "upgrading " + Formatting.duration(v.msLeft()), Style.HEADER, v.fraction(), icon));
                    lines.add(Line.bar(v.fraction()));
                }
                case OPEN_MENU -> lines.add(Line.status(v.name() + " Lv " + v.level(), Style.LABEL,
                        Cell.of("open for next cost", Style.DIM)).withIcon(icon));
                case MAXED -> lines.add(Line.status(v.name() + " Lv " + v.level(), Style.LABEL,
                        new Cell("MAX", Style.GOOD, "MAX")).withIcon(icon));
            }
        }
        return lines;
    }

    public static Line caravanLine(RecurringEvent.View v) {
        Line line = switch (v.state()) {
            case ACTIVE -> Line.status("Caravan", Style.LABEL, new Cell("running", Style.GOOD, "LIVE"));
            case UNKNOWN -> Line.status("Caravan", Style.LABEL, Cell.of("not seen yet", Style.DIM));
            case WAITING -> {
                String in = "in " + (v.exact() ? "" : "~") + Formatting.duration(v.ms());
                yield Line.status("Caravan", Style.LABEL,
                        new Cell(in, v.ms() <= 60_000L ? Style.WARN : Style.VALUE, in));
            }
        };
        return line.withIcon("item:chest");
    }

    /** Pumpkin set bars, each "Pumpkin Helmet" then its currencies and a bar, or one line each. */
    public static List<Line> pumpkinPanel(List<PumpkinSet.View> views, boolean detailed) {
        List<Line> lines = new ArrayList<>();
        for (PumpkinSet.View v : views) {
            String name = v.pieces() > 1 ? v.name() + " (" + v.pieces() + " pieces)" : v.name();
            if (v.affordable()) {
                lines.add(Line.progressTitle(name, "affordable", Style.GOOD, 1.0, "item:carved_pumpkin"));
                lines.add(Line.bar(1.0));
                continue;
            }
            if (!detailed) {
                // Plain text: a bar title would get the percentage added a second time.
                lines.add(Line.of(name + "  " + Math.round(v.fraction() * 100) + "%", Style.VALUE));
                lines.add(Line.bar(v.fraction()));
                continue;
            }
            lines.add(Line.header(name, "item:carved_pumpkin"));
            for (PumpkinSet.Part p : v.parts()) {
                Style style = p.done() ? Style.GOOD : Style.VALUE;
                lines.add(Line.row(p.currency(), icon(p.currency()), style,
                        Cell.of(compact(p.have()) + "/" + compact(p.cost()), style)));
            }
            lines.add(Line.bar(v.fraction()));
        }
        return lines;
    }

    /** "Will-o'-Wisps: READY", "Harvest Moon: 12.3s"; chips in the Halloween look. */
    public static List<Line> abilityPanel(List<Abilities.Timer> timers) {
        List<Line> lines = new ArrayList<>();
        for (Abilities.Timer t : timers) {
            if (t.ready()) {
                lines.add(Line.status(t.ability(), Style.LABEL, new Cell("READY", Style.GOOD, "READY")));
            } else {
                String left = String.format(Locale.ROOT, "%.1fs", t.msLeft() / 1000.0);
                lines.add(Line.status(t.ability(), Style.LABEL, new Cell(left, Style.HEADER, left)));
            }
        }
        return lines;
    }

    /** The Reforger's row in the boss panel. */
    public static Line reforgerLine(RecurringEvent.View v) {
        return switch (v.state()) {
            case ACTIVE -> Line.status("Reforger", Style.VALUE, new HudModel.Cell(
                    "HERE  (" + Formatting.duration(v.ms()) + " left)", Style.GOOD, "HERE " + Formatting.duration(v.ms())));
            case UNKNOWN -> Line.status("Reforger", Style.DIM, HudModel.Cell.of("unknown", Style.DIM));
            case WAITING -> Line.status("Reforger", Style.VALUE, HudModel.Cell.of(
                    (v.exact() ? "" : "~") + Formatting.duration(v.ms()),
                    v.ms() <= 60_000L ? Style.WARN : Style.VALUE));
        };
    }
}
