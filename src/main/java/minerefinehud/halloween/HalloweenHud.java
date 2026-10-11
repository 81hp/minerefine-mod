package minerefinehud.halloween;

import minerefinehud.hud.Formatting;
import minerefinehud.hud.HudModel;
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
 * No Minecraft types.
 */
public final class HalloweenHud {

    private HalloweenHud() {
    }

    /** Which rows the stats panel shows. */
    public record StatsLines(boolean candyRate, boolean cropRates, boolean perCrop, boolean pests,
                             boolean caravan, boolean refine) {

        public static StatsLines all() {
            return new StatsLines(true, true, true, true, true, true);
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
                        int refineLevel) {}

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
    static String compact(double v) {
        if (v >= 1_000_000.0) {
            return Formatting.blocks(Math.round(v));
        }
        if (v >= 1000.0) {
            String s = String.format(Locale.ROOT, "%.1f", v / 1000.0);
            return (s.endsWith(".0") ? s.substring(0, s.length() - 2) : s) + "k";
        }
        return amount(v);
    }

    public static List<Line> statsPanel(Stats s, StatsLines show) {
        List<Line> lines = new ArrayList<>();
        lines.add(Line.of("Halloween", Style.HEADER));

        Set<String> crops = new HashSet<>(HalloweenText.CROPS);

        if (show.candyRate()) {
            String candy = s.balances().containsKey(HalloweenText.CANDY)
                    ? amount(s.balances().get(HalloweenText.CANDY)) : "?";
            lines.add(Line.of("Candy " + candy + "  " + rate(s.rates().perHour(HalloweenText.CANDY, s.windowMs()))
                    + "/h", Style.VALUE));
        }
        if (show.cropRates()) {
            lines.add(Line.of("Crops " + rate(s.rates().perHour(crops, s.windowMs())) + "/h", Style.VALUE));
            if (show.perCrop()) {
                for (String crop : HalloweenText.CROPS) {
                    if (!s.balances().containsKey(crop)) {
                        continue;
                    }
                    double r = s.rates().perHour(crop, s.windowMs());
                    lines.add(Line.of(" " + crop + " " + amount(s.balances().get(crop))
                            + (Double.isNaN(r) || r <= 0 ? "" : "  " + rate(r) + "/h"), Style.LABEL));
                }
            }
        }
        if (show.pests() && (s.pestCandy() > 0 || s.pestCharms() > 0)) {
            StringBuilder sb = new StringBuilder("Pests: +").append(amount(s.pestCandy())).append(" candy");
            if (s.pestCharms() > 0) {
                sb.append(", ").append(s.pestCharms()).append(s.pestCharms() == 1 ? " charm" : " charms");
            }
            lines.add(Line.of(sb.toString(), Style.LABEL));
        }
        if (show.caravan()) {
            lines.add(caravanLine(s.caravan()));
        }
        if (show.refine() && s.refineCost() > 0) {
            double have = s.balances().getOrDefault(HalloweenText.CANDY, 0.0);
            String level = s.refineLevel() > 0
                    ? " " + minerefinehud.shop.RomanNumerals.toRoman(s.refineLevel() + 1) : "";
            lines.add(Line.of("Refine" + level + ": " + amount(have) + "/" + amount(s.refineCost()) + " candy",
                    have >= s.refineCost() ? Style.GOOD : Style.VALUE));
            lines.add(Line.bar(have / s.refineCost()));
        }
        return lines;
    }

    static Line caravanLine(RecurringEvent.View v) {
        return switch (v.state()) {
            case ACTIVE -> Line.of("Caravan: running", Style.GOOD);
            case UNKNOWN -> Line.of("Caravan: not seen yet", Style.DIM);
            case WAITING -> Line.of("Caravan in " + (v.exact() ? "" : "~") + Formatting.duration(v.ms()),
                    v.ms() <= 60_000L ? Style.WARN : Style.VALUE);
        };
    }

    /** Pumpkin set bars, each "Pumpkin Helmet" then its currencies and a bar, or one line each. */
    public static List<Line> pumpkinPanel(List<PumpkinSet.View> views, boolean detailed) {
        List<Line> lines = new ArrayList<>();
        for (PumpkinSet.View v : views) {
            String name = v.pieces() > 1 ? v.name() + " (" + v.pieces() + " pieces)" : v.name();
            if (v.affordable()) {
                lines.add(Line.of(name + "  affordable", Style.GOOD));
                lines.add(Line.bar(1.0));
                continue;
            }
            if (!detailed) {
                lines.add(Line.of(name + "  " + Math.round(v.fraction() * 100) + "%", Style.VALUE));
                lines.add(Line.bar(v.fraction()));
                continue;
            }
            lines.add(Line.of(name, Style.HEADER));
            for (PumpkinSet.Part p : v.parts()) {
                lines.add(Line.of(" " + p.currency() + " " + compact(p.have()) + "/" + compact(p.cost()),
                        p.done() ? Style.GOOD : Style.VALUE));
            }
            lines.add(Line.bar(v.fraction()));
        }
        return lines;
    }

    /** "Will-o'-Wisps  READY", "Harvest Moon  12.3s". */
    public static List<Line> abilityPanel(List<Abilities.Timer> timers) {
        List<Line> lines = new ArrayList<>();
        for (Abilities.Timer t : timers) {
            if (t.ready()) {
                lines.add(Line.of(t.ability() + "  READY", Style.GOOD));
            } else {
                lines.add(Line.of(t.ability() + "  "
                        + String.format(Locale.ROOT, "%.1fs", t.msLeft() / 1000.0), Style.WARN));
            }
        }
        return lines;
    }

    /** The Reforger's row in the boss panel. */
    public static Line reforgerLine(RecurringEvent.View v) {
        return switch (v.state()) {
            case ACTIVE -> Line.of("Reforger: HERE  (" + Formatting.duration(v.ms()) + " left)", Style.GOOD);
            case UNKNOWN -> Line.of("Reforger: unknown", Style.DIM);
            case WAITING -> Line.of("Reforger: " + (v.exact() ? "" : "~") + Formatting.duration(v.ms()),
                    v.ms() <= 60_000L ? Style.WARN : Style.VALUE);
        };
    }
}
