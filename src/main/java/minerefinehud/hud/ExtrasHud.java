package minerefinehud.hud;

import minerefinehud.beacon.BeaconTimer;
import minerefinehud.drops.RareDrops;
import minerefinehud.halloween.HalloweenHud;
import minerefinehud.outpost.OutpostTimers;
import minerefinehud.hud.HudModel.Cell;
import minerefinehud.hud.HudModel.Line;
import minerefinehud.hud.HudModel.Style;
import minerefinehud.progress.ProgressPlanner;
import minerefinehud.shop.RomanNumerals;
import minerefinehud.stats.PlayerStats;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.Set;

/**
 * What the three optional panels say: mining per hour, the player's stats from /stats, and rare
 * drops. Plain Java like {@link HudModel}; the client gathers the figures.
 */
public final class ExtrasHud {

    private ExtrasHud() {
    }

    // ------------------------------------------------------------ mining

    /** How long until a tracked bar fills at the current rate. */
    public record Eta(String label, long millis) {}

    /**
     * @param mine      the mine being mined, as the panel names it
     * @param balance   its balance from the action bar, when known
     * @param perHour   mined per hour, NaN while too little is measured
     * @param session   mined since joining
     * @param miningMs  time spent mining since joining
     * @param etas      the progress bars paid in this mine's resource, still short
     */
    public record Mining(String mine, OptionalLong balance, double perHour, double session, long miningMs,
                         List<Eta> etas) {}

    public static List<Line> miningPanel(Mining m) {
        return miningPanel(m, null);
    }

    /** The mining panel with a row of EXP per hour under the session, when {@code xp} is given. */
    public static List<Line> miningPanel(Mining m, Xp xp) {
        List<Line> lines = new ArrayList<>();
        lines.add(Line.header("Mining", "pickaxe"));
        if (m == null || m.mine() == null || m.mine().isBlank()) {
            lines.add(Line.of("start mining to measure", Style.DIM));
            return lines;
        }
        String balance = m.balance().isPresent() ? Formatting.blocks(m.balance().getAsLong()) : "";
        String rate = Double.isNaN(m.perHour()) ? "…/h" : HalloweenHud.compact(Math.max(0.0, m.perHour())) + "/h";
        lines.add(HalloweenHud.figures(HudModel.Kind.ROW, m.mine(), "total", balance, rate));
        if (m.session() > 0) {
            lines.add(HalloweenHud.figures(HudModel.Kind.ROW, "Session", "", "+" + HalloweenHud.compact(m.session()),
                    m.miningMs() >= 60_000L ? eta(m.miningMs()) : ""));
        }
        if (xp != null && xp.session() > 0.0) {
            String xpRate = Double.isNaN(xp.perHour()) ? "…/h"
                    : HalloweenHud.compact(Math.max(0.0, xp.perHour())) + "/h";
            lines.add(HalloweenHud.figures(HudModel.Kind.ROW, "EXP", "", "+" + HalloweenHud.compact(xp.session()), xpRate));
        }
        if (!m.etas().isEmpty()) {
            lines.add(Line.section("AT THIS RATE"));
            for (Eta e : m.etas()) {
                lines.add(Line.row(e.label(), "", Style.VALUE,
                        Cell.of(e.millis() <= 0L ? "now" : "in " + eta(e.millis()), Style.VALUE)));
            }
        }
        return lines;
    }

    /**
     * @param perHour EXP per hour, NaN while too little is measured
     * @param session EXP since joining
     */
    public record Xp(double perHour, double session) {}

    /**
     * Every progress bar paid in {@code currency} that is still short, with how long the rest takes
     * at {@code perHour}. A bar whose time cannot be told (no rate yet) is left out.
     */
    public static List<Eta> etas(List<ProgressPlanner.ProgressView> views, String currency, double perHour) {
        List<Eta> out = new ArrayList<>();
        if (currency == null || currency.isBlank()) {
            return out;
        }
        for (ProgressPlanner.ProgressView v : views) {
            if (v.state() != ProgressPlanner.State.TRACKING || v.cost().isEmpty() || v.have().isEmpty()
                    || v.currency() == null || !v.currency().equalsIgnoreCase(currency.trim())) {
                continue;
            }
            long left = v.cost().getAsLong() - v.have().getAsLong();
            OptionalLong ms = timeTo(left, perHour);
            if (ms.isPresent()) {
                out.add(new Eta(barName(v), ms.getAsLong()));
            }
        }
        return out;
    }

    /** "Total left", "Pickaxe IV", "12x Chestplate II-IV", as short as fits the panel. */
    static String barName(ProgressPlanner.ProgressView v) {
        String count = v.quantity() > 1 ? v.quantity() + "x " : "";
        if (v.slot().isTotal()) {
            return count + (v.toLevel() > v.targetLevel() ? "Total" : "Total left");
        }
        String gear = v.gear() == null || v.gear().isEmpty() ? ""
                : Character.toUpperCase(v.gear().charAt(0)) + v.gear().substring(1);
        String tiers = v.toLevel() > v.targetLevel()
                ? RomanNumerals.toRoman(v.targetLevel()) + "-" + RomanNumerals.toRoman(v.toLevel())
                : RomanNumerals.toRoman(v.targetLevel());
        return count + gear + " " + tiers;
    }

    /**
     * Time to save {@code left} more at {@code perHour}, or empty when the rate is not known yet
     * or so slow it would take over a year.
     */
    public static OptionalLong timeTo(long left, double perHour) {
        if (left <= 0L) {
            return OptionalLong.of(0L);
        }
        if (Double.isNaN(perHour) || perHour <= 0.0) {
            return OptionalLong.empty();
        }
        double ms = left / perHour * 3_600_000.0;
        return ms > 365.0 * 24 * 3_600_000.0 ? OptionalLong.empty() : OptionalLong.of((long) Math.ceil(ms));
    }

    /** "45s", "12m", "2h 13m", "3d 4h". */
    public static String eta(long ms) {
        long s = Math.max(0L, ms) / 1000L;
        if (s < 60L) {
            return s + "s";
        }
        long m = s / 60L;
        if (m < 60L) {
            return m + "m";
        }
        long h = m / 60L;
        if (h < 48L) {
            return h + "h " + (m % 60L) + "m";
        }
        return (h / 24L) + "d " + (h % 24L) + "h";
    }

    // ------------------------------------------------------------- stats

    public static List<Line> statsPanel(PlayerStats.Saved saved, Set<String> hidden) {
        List<Line> lines = new ArrayList<>();
        lines.add(Line.header("Stats", "item:nether_star"));
        if (saved == null || saved.stats == null || saved.stats.isEmpty()) {
            lines.add(Line.of("open /stats once to read them", Style.DIM));
            return lines;
        }
        int shown = 0;
        for (PlayerStats.SavedStat s : saved.stats) {
            if (hidden != null && hidden.contains(s.name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            int trend = PlayerStats.trend(s);
            String arrow = trend > 0 ? " ▲" : trend < 0 ? " ▼" : "";
            Style style = trend > 0 ? Style.GOOD : trend < 0 ? Style.WARN : Style.VALUE;
            lines.add(Line.row(s.name, "", style, Cell.of(s.value + arrow, style)));
            shown++;
        }
        if (shown == 0) {
            lines.add(Line.of("every stat is hidden in Settings", Style.DIM));
        }
        return lines;
    }

    // ------------------------------------------------------------ beacon

    /**
     * The Beacon Mine timer. {@code remainingMs} is -1 until the first reset has been seen;
     * {@code urgent} turns the time red.
     */
    public static List<Line> beaconPanel(long remainingMs, boolean urgent) {
        List<Line> lines = new ArrayList<>();
        lines.add(Line.header("Beacon Mine", "item:beacon"));
        if (remainingMs < 0L) {
            lines.add(Line.of("waiting for a reset", Style.DIM));
            return lines;
        }
        long secs = remainingMs / 1000L;
        String time = String.format(Locale.ROOT, "%d:%02d", secs / 60L, secs % 60L);
        Style style = urgent ? Style.WARN : Style.VALUE;
        lines.add(Line.row("Next reset", "", style, Cell.of(time, style)));
        lines.add(Line.bar(remainingMs / (double) BeaconTimer.CYCLE_MS));
        return lines;
    }

    /** The reminder text for the title: the mine just reset, or is about to. */
    public static List<String> beaconAlerts(long remainingMs, boolean justReset, long warnMs) {
        List<String> out = new ArrayList<>();
        if (justReset) {
            out.add("Beacon Mine has reset!");
        } else if (remainingMs >= 0L && warnMs > 0L && remainingMs <= warnMs) {
            out.add("Beacon Mine resets in " + Math.max(1L, remainingMs / 1000L) + "s");
        }
        return out;
    }

    // ---------------------------------------------------------- outposts

    /** Each outpost and when it opens, or how long its battle has left. */
    public static List<Line> outpostPanel(List<OutpostTimers.View> views, long warnMs) {
        List<Line> lines = new ArrayList<>();
        lines.add(Line.header("Outposts", "item:nether_star"));
        if (views.isEmpty()) {
            lines.add(Line.of("open /outpost once to read the times", Style.DIM));
            return lines;
        }
        for (OutpostTimers.View v : views) {
            String name = OutpostTimers.shortName(v.name());
            String guess = v.estimated() ? "~" : "";
            if (v.phase() == OutpostTimers.Phase.BATTLE) {
                String text = "OPEN " + guess + OutpostTimers.clock(v.ms());
                lines.add(Line.status(name, Style.LABEL, new Cell(text, Style.GOOD, text)));
            } else {
                Style style = warnMs > 0L && v.ms() <= warnMs ? Style.WARN : Style.VALUE;
                String text = guess + OutpostTimers.clock(v.ms());
                lines.add(Line.status(name, Style.LABEL, new Cell(text, style, text)));
            }
        }
        return lines;
    }

    // ------------------------------------------------------------- drops

    public static List<Line> dropsPanel(List<RareDrops.Row> rows, List<RareDrops.Recent> recent, long now) {
        List<Line> lines = new ArrayList<>();
        lines.add(Line.header("Rare drops", "item:chest"));
        if (rows.isEmpty() && recent.isEmpty()) {
            lines.add(Line.of("none yet", Style.DIM));
            return lines;
        }
        for (RareDrops.Row r : rows) {
            Style style = style(r.rarity());
            String session = r.session() > 0 ? "x" + r.session() : "";
            lines.add(new Line(r.name() + (session.isEmpty() ? "" : " " + session) + "  " + r.allTime() + " total",
                    style, 0.0, HudModel.Kind.ROW, r.name(), style,
                    List.of(Cell.of(session, Style.VALUE), Cell.of(r.allTime() + " total", Style.DIM)), ""));
        }
        if (!recent.isEmpty()) {
            lines.add(Line.section("LATEST"));
            for (RareDrops.Recent d : recent) {
                Style style = style(d.rarity);
                String name = (d.count > 1 ? d.count + "x " : "") + d.name;
                lines.add(Line.row(name, "", style, Cell.of(ago(now - d.at), Style.DIM)).withStyle(style));
            }
        }
        return lines;
    }

    /** The rarest drops stand out; the everyday ones stay plain. */
    static Style style(String rarity) {
        if (rarity == null) {
            return Style.VALUE;
        }
        return switch (rarity.toUpperCase(Locale.ROOT)) {
            case "SPECIAL", "DIVINE", "TRANSCENDENT", "MYTHIC" -> Style.GOOD;
            case "LEGENDARY", "EXOTIC", "ELITE" -> Style.HEADER;
            case "RARE", "EPIC" -> Style.VALUE;
            default -> Style.HEADER;
        };
    }

    /** "now", "45s ago", "12m ago", "3h ago", "2d ago". */
    public static String ago(long ms) {
        long s = Math.max(0L, ms) / 1000L;
        if (s < 5L) {
            return "now";
        }
        if (s < 60L) {
            return s + "s ago";
        }
        if (s < 3600L) {
            return (s / 60L) + "m ago";
        }
        if (s < 86_400L) {
            return (s / 3600L) + "h ago";
        }
        return (s / 86_400L) + "d ago";
    }
}
