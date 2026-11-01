package minerefinehud.drops;

import minerefinehud.halloween.HalloweenText;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rare drops found while mining, read from chat, counted per item.
 *
 * The server writes a drop two ways, both seen in the player's logs:
 * <pre>
 *   SPECIAL! You found x1 Nebula! (+321.9  Luck)
 *   RARE! You found Netherrack Turret /plot x1 (+640.6  Turret Luck)
 * </pre>
 * and for the rarest ones also tells everyone "SPECIAL! 81hp_ found Nebula x1!", which is never
 * counted: only "You found" is the player's own drop.
 *
 * A player can type the same words, but chat puts their rank and name first:
 * "[★644] ELITE aMatya: EXOTIC! You found LEGENDARY key x1 (+56.08  Luck)". So the line must start
 * with the rarity, and nothing typed by a player ever does.
 *
 * No Minecraft types; times are epoch milliseconds from the caller.
 */
public final class RareDrops {

    /** Rarities seen in the logs, lowest first as far as the server's colours tell. */
    public static final List<String> RARITIES = List.of(
            "RARE", "EPIC", "ELITE", "EXOTIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "TRANSCENDENT");

    /** Recent drops kept, newest first. */
    public static final int RECENT_KEPT = 20;

    public record Drop(String rarity, String item, long count) {}

    /** "RARITY! You found ..." from the very first character. */
    private static final Pattern LINE = Pattern.compile("^(?<rarity>[A-Z]{3,16})! You found (?<rest>.+)$");
    /** Newer wording: "x1 Nebula! (+321.9  Luck)". */
    private static final Pattern COUNT_FIRST = Pattern.compile(
            "^x(?<n>[0-9][0-9,]*) (?<item>.+?)!(?:\\s*\\((?<luck>[^()]*)\\))?$");
    /** Older wording: "Nebula x1 (+321.9  Luck)". */
    private static final Pattern COUNT_LAST = Pattern.compile(
            "^(?<item>.+?) x(?<n>[0-9][0-9,]*)!?(?:\\s*\\((?<luck>[^()]*)\\))?$");
    /** "+321.9  Luck", "+133.9 🍀 Luck", "+640.6  Turret Luck"; any glyph between. */
    private static final Pattern LUCK = Pattern.compile("^\\+[0-9][0-9.,]*[^A-Za-z0-9]*(?:Turret )?Luck$");
    /** "/plot" after a turret's name is how it is placed, not part of the name. */
    private static final Pattern PLOT = Pattern.compile("\\s*/plot\\b");
    private static final Pattern FORMATTING = Pattern.compile("§.");

    private RareDrops() {
    }

    /**
     * The drop a chat line announces, or empty for anything else, including the same words typed
     * by a player and "found" lines about someone else.
     */
    public static Optional<Drop> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        // Without colour codes, the rarity must be the very first character: a leading space is
        // some other message's indent, and a player's line starts with their rank and name.
        String noCodes = FORMATTING.matcher(raw).replaceAll("");
        if (noCodes.isEmpty() || !Character.isUpperCase(noCodes.charAt(0))) {
            return Optional.empty();
        }
        String s = HalloweenText.strip(noCodes);
        Matcher m = LINE.matcher(s);
        if (!m.matches()) {
            return Optional.empty();
        }
        String rarity = m.group("rarity");
        String rest = m.group("rest").trim();
        Matcher item = COUNT_FIRST.matcher(rest);
        if (!item.matches()) {
            item = COUNT_LAST.matcher(rest);
            if (!item.matches()) {
                return Optional.empty();
            }
        }
        String luck = item.group("luck");
        boolean hasLuck = luck != null && LUCK.matcher(luck.trim()).matches();
        if (luck != null && !hasLuck) {
            // "(30s)", "(1/5 in this area)": something else in brackets is not a drop.
            return Optional.empty();
        }
        // A rarity never seen before only counts with the Luck it was found with, so a new
        // all-caps message ("ESSENCE! You found x10 Ice Essence!") is not taken for a drop.
        if (!RARITIES.contains(rarity) && !hasLuck) {
            return Optional.empty();
        }
        long count;
        try {
            count = Long.parseLong(item.group("n").replace(",", ""));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        String name = cleanName(item.group("item"));
        if (count <= 0 || name.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Drop(rarity, name, count));
    }

    /**
     * "« Sculk » - Charm" as "Sculk Charm", "RARE ᴋᴇʏ" as "RARE Key", "Netherrack Turret /plot" as
     * "Netherrack Turret".
     */
    static String cleanName(String raw) {
        String s = PLOT.matcher(raw).replaceAll("");
        s = s.replace("«", " ").replace("»", " ").replaceAll("\\s+-\\s+", " ");
        StringBuilder out = new StringBuilder();
        for (String word : s.trim().split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            String plain = HalloweenText.plainLetters(word);
            if (!plain.equals(word)) {
                // Small caps: "ᴋᴇʏ" becomes "Key".
                plain = Character.toUpperCase(plain.charAt(0)) + plain.substring(1);
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(plain);
        }
        return out.toString();
    }

    /** The key one item is counted under, so "Nebula" and "nebula" are one row. */
    public static String key(String item) {
        return item == null ? "" : item.trim().toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------ counting

    /** What is saved: every item's all-time count and the latest drops. */
    public static final class Saved {
        public Map<String, Item> items = new LinkedHashMap<>();
        public List<Recent> recent = new ArrayList<>();
    }

    public static final class Item {
        public String name = "";
        public String rarity = "";
        public long allTime;
        public long lastAt;
    }

    public static final class Recent {
        public String name = "";
        public String rarity = "";
        public long count;
        public long at;

        public Recent() {
        }

        Recent(String name, String rarity, long count, long at) {
            this.name = name;
            this.rarity = rarity;
            this.count = count;
            this.at = at;
        }
    }

    /** One row of the panel. */
    public record Row(String name, String rarity, long session, long allTime, long lastAt) {}

    /** Counts drops into {@code saved} (all time) and this session's own counts. */
    public static final class Counter {
        private final Saved saved;
        private final Map<String, Long> session = new LinkedHashMap<>();

        public Counter(Saved saved) {
            this.saved = repair(saved);
        }

        public Saved saved() {
            return saved;
        }

        public void add(Drop drop, long now) {
            String key = key(drop.item());
            Item item = saved.items.computeIfAbsent(key, k -> new Item());
            item.name = drop.item();
            item.rarity = drop.rarity();
            item.allTime += drop.count();
            item.lastAt = now;
            session.merge(key, drop.count(), Long::sum);
            saved.recent.add(0, new Recent(drop.item(), drop.rarity(), drop.count(), now));
            while (saved.recent.size() > RECENT_KEPT) {
                saved.recent.remove(saved.recent.size() - 1);
            }
        }

        public void resetSession() {
            session.clear();
        }

        public void resetAll() {
            session.clear();
            saved.items.clear();
            saved.recent.clear();
        }

        /**
         * Items of the rarities shown, found this session first and the latest found first, so a
         * new drop is always on top and a flood of one kind cannot push it off.
         */
        public List<Row> rows(Set<String> hiddenRarities, int max) {
            List<Row> out = new ArrayList<>();
            for (Map.Entry<String, Item> e : saved.items.entrySet()) {
                Item item = e.getValue();
                if (hidden(hiddenRarities, item.rarity)) {
                    continue;
                }
                out.add(new Row(item.name, item.rarity, session.getOrDefault(e.getKey(), 0L), item.allTime,
                        item.lastAt));
            }
            out.sort((a, b) -> {
                boolean sa = a.session() > 0;
                boolean sb = b.session() > 0;
                if (sa != sb) {
                    return sa ? -1 : 1;
                }
                return Long.compare(b.lastAt(), a.lastAt());
            });
            return out.size() > max ? new ArrayList<>(out.subList(0, Math.max(0, max))) : out;
        }

        /** The latest drops of the rarities shown, newest first. */
        public List<Recent> recent(Set<String> hiddenRarities, int max) {
            List<Recent> out = new ArrayList<>();
            for (Recent r : saved.recent) {
                if (out.size() >= max) {
                    break;
                }
                if (!hidden(hiddenRarities, r.rarity)) {
                    out.add(r);
                }
            }
            return out;
        }

        /** Every rarity found so far, the known ones in order, then any new ones. */
        public List<String> raritiesSeen() {
            List<String> out = new ArrayList<>(RARITIES);
            for (Item item : saved.items.values()) {
                if (item.rarity != null && !item.rarity.isEmpty() && !out.contains(item.rarity)) {
                    out.add(item.rarity);
                }
            }
            return out;
        }
    }

    public static boolean hidden(Set<String> hiddenRarities, String rarity) {
        return hiddenRarities != null && rarity != null && hiddenRarities.contains(rarity.toUpperCase(Locale.ROOT));
    }

    /** A hand-edited or older config with holes in it: fill them rather than crash. */
    public static Saved repair(Saved saved) {
        Saved s = saved == null ? new Saved() : saved;
        if (s.items == null) {
            s.items = new LinkedHashMap<>();
        }
        if (s.recent == null) {
            s.recent = new ArrayList<>();
        }
        s.items.values().removeIf(i -> i == null || i.name == null || i.name.isBlank() || i.allTime <= 0);
        for (Item i : s.items.values()) {
            if (i.rarity == null) {
                i.rarity = "";
            }
        }
        s.recent.removeIf(r -> r == null || r.name == null || r.name.isBlank() || r.count <= 0);
        for (Recent r : s.recent) {
            if (r.rarity == null) {
                r.rarity = "";
            }
        }
        while (s.recent.size() > RECENT_KEPT) {
            s.recent.remove(s.recent.size() - 1);
        }
        return s;
    }
}
