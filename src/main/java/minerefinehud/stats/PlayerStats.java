package minerefinehud.stats;

import minerefinehud.halloween.HalloweenText;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The player's stats, read from the /stats menu.
 *
 * Every stat is one item whose name holds both its name and its value, and whose lore says what
 * kind of stat it is (screenshot, 2026-11-01):
 * <pre>
 *   ✤ Fortune 967,900
 *   MINING STAT
 *   How many drops you get from each block. ...
 *   CLICK to see breakdown
 * </pre>
 * An item only counts with such a "... STAT" line, so the menu's glass panes, page arrows and
 * close button are never read as stats.
 *
 * No Minecraft types.
 */
public final class PlayerStats {

    /** At least this many stats before a menu is taken for the stats menu. */
    public static final int MIN_STATS = 2;

    /**
     * @param name     "Fortune"
     * @param value    as the menu writes it: "967,900", "+12.5%", "x1.5"
     * @param category "Mining", from "MINING STAT"; empty when the line has no word before STAT
     */
    public record Stat(String name, String value, String category) {}

    /** "Fortune 967,900", "Crit Chance: 12.5%", "Multiplier x1.5". */
    private static final Pattern NAME_VALUE = Pattern.compile(
            "^(?<name>\\p{L}[\\p{L}\\p{M}'&. -]*?)\\s*:?\\s+(?<value>[+-]?x?[0-9][0-9,]*(?:\\.[0-9]+)?\\s?(?:%|x|[KkMmBbTt])?)$");
    /** "MINING STAT", "STAT", "COMBAT STAT", alone on its line. */
    private static final Pattern STAT_LINE = Pattern.compile("^(?:(?<kind>[A-Z][A-Z ]{0,20}?)\\s+)?STATS?$");

    private PlayerStats() {
    }

    /** One menu item as a stat, or empty for anything else. */
    public static Optional<Stat> read(String title, List<String> lore) {
        if (title == null || lore == null) {
            return Optional.empty();
        }
        String category = null;
        for (String line : lore) {
            Matcher kind = STAT_LINE.matcher(plain(line).toUpperCase(Locale.ROOT));
            if (kind.matches()) {
                category = kind.group("kind") == null ? "" : capitalise(kind.group("kind").trim());
                break;
            }
        }
        if (category == null) {
            return Optional.empty();
        }
        // The icon in front ("✤ ") and anything else that is not a letter goes.
        String t = plain(title).replaceFirst("^[^\\p{L}]+", "");
        Matcher m = NAME_VALUE.matcher(t);
        if (!m.matches()) {
            return Optional.empty();
        }
        String name = m.group("name").trim();
        return name.isEmpty() ? Optional.empty()
                : Optional.of(new Stat(name, m.group("value").replace(" ", ""), category));
    }

    /**
     * Every stat in a menu, in the menu's order, or empty when it is not the stats menu (fewer
     * than {@link #MIN_STATS}). A stat in the menu twice is kept once.
     */
    public static List<Stat> readAll(List<String> titles, List<List<String>> lores) {
        List<Stat> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < titles.size() && i < lores.size(); i++) {
            read(titles.get(i), lores.get(i)).ifPresent(s -> {
                if (seen.add(s.name().toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            });
        }
        return out.size() >= MIN_STATS ? out : List.of();
    }

    /** Colour codes, small caps and the invisible padding gone. */
    static String plain(String raw) {
        return HalloweenText.plainLetters(HalloweenText.strip(raw == null ? "" : raw)).trim();
    }

    private static String capitalise(String s) {
        String lower = s.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        for (String w : lower.split(" +")) {
            if (w.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return out.toString();
    }

    // ------------------------------------------------------------ saved

    /** What is kept between sessions: the last reading, so the panel is full right after joining. */
    public static final class Saved {
        public List<SavedStat> stats = new ArrayList<>();
        public long readAt;
    }

    public static final class SavedStat {
        public String name = "";
        public String value = "";
        public String category = "";
        /** The value before the last change, for the arrow; empty when it has not changed. */
        public String previous = "";

        public SavedStat() {
        }

        SavedStat(String name, String value, String category, String previous) {
            this.name = name;
            this.value = value;
            this.category = category;
            this.previous = previous;
        }
    }

    /**
     * A new reading over the old: same order as the menu, and each stat that changed since the
     * reading before keeps what it was, so the panel can show it went up or down.
     */
    public static Saved update(Saved old, List<Stat> read, long now) {
        Saved out = new Saved();
        out.readAt = now;
        for (Stat s : read) {
            String previous = "";
            if (old != null && old.stats != null) {
                for (SavedStat o : old.stats) {
                    if (o != null && o.name != null && o.name.equalsIgnoreCase(s.name())) {
                        // Until the next reading: an arrow that stayed for ever would mean nothing.
                        previous = o.value != null && !o.value.equals(s.value()) ? o.value : "";
                        break;
                    }
                }
            }
            out.stats.add(new SavedStat(s.name(), s.value(), s.category(), previous));
        }
        return out;
    }

    /** Whether a reading has exactly the saved stats, in the same order with the same values. */
    public static boolean same(Saved saved, List<Stat> read) {
        if (saved == null || saved.stats == null || saved.stats.size() != read.size()) {
            return false;
        }
        for (int i = 0; i < read.size(); i++) {
            SavedStat s = saved.stats.get(i);
            Stat r = read.get(i);
            if (s == null || !r.name().equals(s.name) || !r.value().equals(s.value)
                    || !r.category().equals(s.category)) {
                return false;
            }
        }
        return true;
    }

    /** "967,900" as 967900, "12.5%" as 12.5, "x1.5" as 1.5, "2.5M" as 2,500,000. NaN otherwise. */
    public static double number(String value) {
        if (value == null) {
            return Double.NaN;
        }
        String s = value.trim().replace("%", "");
        if (s.startsWith("+")) {
            s = s.substring(1);
        }
        if (s.startsWith("x") || s.startsWith("X")) {
            s = s.substring(1);
        } else if (s.endsWith("x") || s.endsWith("X")) {
            s = s.substring(0, s.length() - 1);
        }
        boolean negative = s.startsWith("-");
        if (negative) {
            s = s.substring(1);
        }
        double multiplier = 1.0;
        if (!s.isEmpty()) {
            switch (Character.toUpperCase(s.charAt(s.length() - 1))) {
                case 'K' -> multiplier = 1e3;
                case 'M' -> multiplier = 1e6;
                case 'B' -> multiplier = 1e9;
                case 'T' -> multiplier = 1e12;
                default -> { }
            }
            if (multiplier != 1.0) {
                s = s.substring(0, s.length() - 1);
            }
        }
        try {
            double v = Double.parseDouble(s.replace(",", "")) * multiplier;
            return negative ? -v : v;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** +1 when the stat went up since the reading before, -1 when down, 0 when the same or unknown. */
    public static int trend(SavedStat s) {
        if (s == null || s.previous == null || s.previous.isEmpty()) {
            return 0;
        }
        double now = number(s.value);
        double before = number(s.previous);
        if (Double.isNaN(now) || Double.isNaN(before) || now == before) {
            return 0;
        }
        return now > before ? 1 : -1;
    }

    /** A hand-edited or older config with holes in it: fill them rather than crash. */
    public static Saved repair(Saved saved) {
        Saved s = saved == null ? new Saved() : saved;
        if (s.stats == null) {
            s.stats = new ArrayList<>();
        }
        s.stats.removeIf(x -> x == null || x.name == null || x.name.isBlank() || x.value == null);
        for (SavedStat x : s.stats) {
            if (x.category == null) {
                x.category = "";
            }
            if (x.previous == null) {
                x.previous = "";
            }
        }
        return s;
    }
}
