package minerefinehud.halloween;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Halloween buildings (Barn, Forge, Belfry...): their level, what the next level grants and
 * costs, and the upgrade timer.
 *
 * Read from the building's item in its menu, exactly as the lore shows it (headings in small
 * capitals, which {@link HalloweenText#plainLetters} folds):
 *
 *   Barn
 *    ▍ LEVEL 4 / 10
 *    ▍ Grants x1.8 more crops
 *   ▼↓↓⇓—[ UPGRADE ]—⇓↓↓▼
 *    ▍ LEVEL 5 / 10
 *    ▍ Grants x2 more crops
 *   COST
 *   ⇢ 1,500,000 Carrots ✗
 *   ⇢ Upgrade Time 3m
 *   EACH UPGRADE ALSO GRANTS +10 EVENT SPEED
 *   (!) You need 1,443,800 more Carrots.
 *
 * Not every building's boost starts "Grants": the Windmill's is "Sweep +2.1 adjacent crops", the
 * Minion's "Auto Harvest every 2.3s". The /upgrades menu (Luck, More Damage...) looks the same but
 * its upgrades are instant, so only an item with an "Upgrade Time" is a building.
 *
 * Only the next level is ever shown, so after an upgrade the next cost is unknown until the
 * menu is opened again. An upgrade is known from the chat, "Upgrading Barn to Lv 5. Done in 3m."
 * then "COMPLETE! Barn is now Lv 5! Crop Multiplier x2.", or else by its cost leaving the sidebar
 * balance while the building's menu is open or was just closed.
 *
 * No Minecraft types; times are epoch milliseconds from the caller.
 */
public final class Buildings {

    /** One boost: "x1.8" of "crops", "+10" of "Event Health". */
    public static final class Grant {
        public String stat = "";
        public String value = "";

        public Grant() {
        }

        public Grant(String stat, String value) {
            this.stat = stat;
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Grant g && stat.equals(g.stat) && value.equals(g.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(stat, value);
        }
    }

    /** One building as last read, kept in the config. */
    public static final class Saved {
        public int level;
        public int max;
        public List<Grant> now = new ArrayList<>();
        public List<Grant> next = new ArrayList<>();
        /** What the next level costs; empty when maxed or not known since the last upgrade. */
        public Map<String, Double> cost = new LinkedHashMap<>();
        /** The currency it was last paid in, for its icon when the cost is not known. */
        public String currency = "";
        public long upgradeMs;
        /** The upgrade running now, or 0. */
        public long upgradeStart;
        public long upgradeEnd;
        public int upgradeTo;
    }

    /** A building's menu item, read. */
    public record Info(String name, int level, int max, List<Grant> now, List<Grant> next,
                       Map<String, Double> cost, long upgradeMs, long remainingMs) {

        public boolean maxed() {
            return max > 0 && level >= max;
        }

        /** It takes time to upgrade, as buildings do; the /upgrades menu's stats are instant. */
        public boolean timed() {
            return upgradeMs > 0L || remainingMs > 0L;
        }
    }

    /** "Upgrading Barn to Lv 5. Done in 3m." ({@code done} false) or "COMPLETE! Barn is now Lv 5!". */
    public record Chat(String name, int level, long ms, boolean done) {}

    public enum State {
        /** Saving up for the next level. */
        SAVING,
        UPGRADING,
        /** Upgraded since the menu was last opened: the next cost is not known. */
        OPEN_MENU,
        MAXED
    }

    /**
     * One building's row.
     *
     * @param have     of the currency furthest from paid, and its cost; 0 and 0 when not saving
     * @param fraction how far along: the money for SAVING, the time for UPGRADING
     */
    public record View(String name, State state, int level, int target, String currency,
                       double have, double need, double fraction, boolean affordable,
                       long msLeft, String perks) {}

    /**
     * A finished upgrade, for the bell and the alert: {@code at} is when it was noticed, and
     * {@code ring} is true when it only just finished.
     */
    public record Finished(String name, int level, long at, boolean ring) {}

    /** The menu was open this recently: a balance falling by its cost then is the upgrade. */
    public static final long SPEND_WINDOW_MS = 20_000L;
    /** A finished upgrade older than this when noticed (the game was closed) rings nothing. */
    static final long RING_WITHIN_MS = 5L * 60_000L;

    private static final Pattern LEVEL = Pattern.compile("level\\s*([0-9]+)\\s*/\\s*([0-9]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern GRANTS = Pattern.compile(
            "^[^\\p{L}]*grants\\s+(?<value>[x×+\\-]?\\s*[0-9][0-9.,]*%?)\\s*(?<rest>.*)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COST_LINE = Pattern.compile(
            "^([0-9][0-9.,]*[kKmMbB]?)\\s+([\\p{L}][\\p{L} ']*)$");
    private static final Pattern DURATION_PART = Pattern.compile("([0-9]+)\\s*([dhms])(?![a-z])",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IN_PROGRESS = Pattern.compile(
            "upgrading|remaining|time left|finishes|ready in", Pattern.CASE_INSENSITIVE);
    /** The number in a boost that is not a "Grants" line: "+2.1", "2.3s", "x1.8", "25%". */
    private static final Pattern PERK_VALUE = Pattern.compile(
            "(?<![\\p{L}\\p{N}.,])([x×+\\-]?[0-9][0-9.,]*(?:%|[smh]\\b)?)");
    /** Anchored, so a player pasting the message in chat is not an upgrade. */
    private static final Pattern CHAT_UPGRADING = Pattern.compile(
            "^upgrading\\s+(?<name>.+?)\\s+to\\s+lv\\.?\\s*(?<level>[0-9]+)\\b.*?\\bdone in\\s+(?<time>.+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CHAT_COMPLETE = Pattern.compile(
            "^complete!?\\s+(?<name>.+?)\\s+is now\\s+lv\\.?\\s*(?<level>[0-9]+)\\b", Pattern.CASE_INSENSITIVE);

    /** When each building's menu was last seen; not saved. */
    private final Map<String, Long> seenAt = new HashMap<>();
    /** When our own timer last made each building its new level; not saved. */
    private final Map<String, Long> finishedAt = new HashMap<>();

    /** After our timer ends, a menu still showing the old level is not believed for this long. */
    static final long STALE_MENU_MS = 10L * 60_000L;

    // ------------------------------------------------------------- reading

    /**
     * A building's item, or empty for anything else. A building has a "LEVEL n / m" line and a
     * line starting "Grants"; a maxed one has no upgrade part.
     */
    public static Optional<Info> read(String title, List<String> lore) {
        // "[item/carrot@items]" sprites go; brackets round the name itself, "[Barn]", only lose the brackets.
        String name = HalloweenText.plainLetters(HalloweenText.strip(title)).replaceAll("\\[[^\\]]*/[^\\]]*\\]", " ")
                .replaceAll("[^\\p{L}\\p{N} '\\-]", " ").replaceAll("\\s+", " ").trim();
        if (name.equals(name.toLowerCase(Locale.ROOT))) {
            name = capitalised(name);   // small capitals fold to lower case: "ʙᴀʀɴ" is "Barn"
        }
        if (name.isEmpty() || lore == null) {
            return Optional.empty();
        }
        int level = -1;
        int max = 0;
        int nextLevel = -1;
        boolean pastUpgrade = false;
        boolean inCost = false;
        boolean anyGrant = false;
        List<Grant> now = new ArrayList<>();
        List<Grant> next = new ArrayList<>();
        Map<String, Double> cost = new LinkedHashMap<>();
        long upgradeMs = 0L;
        long remainingMs = 0L;
        for (String raw : lore) {
            String line = HalloweenText.plainLetters(HalloweenText.strip(raw));
            String letters = line.replaceAll("[^\\p{L}]", "").toUpperCase(Locale.ROOT);
            if (letters.equals("UPGRADE")) {
                pastUpgrade = true;
                inCost = false;
                continue;
            }
            if (letters.equals("COST")) {
                inCost = true;
                continue;
            }
            if (inCost) {
                String cleaned = line.replaceAll("\\[[^\\]]*\\]", " ")
                        .replaceAll("[^\\p{L}\\p{N}.,' ]", " ").replaceAll("\\s+", " ").trim();
                if (cleaned.isEmpty()) {
                    if (!cost.isEmpty() || upgradeMs > 0L) {
                        inCost = false;   // a blank line after the costs ends them
                    }
                    continue;
                }
                if (cleaned.toLowerCase(Locale.ROOT).startsWith("upgrade time")) {
                    upgradeMs = duration(cleaned.substring("upgrade time".length()));
                    continue;
                }
                Matcher m = COST_LINE.matcher(cleaned);
                if (m.matches()) {
                    OptionalDouble v = HalloweenText.number(m.group(1));
                    if (v.isPresent()) {
                        cost.put(HalloweenText.canonical(m.group(2)), v.getAsDouble());
                    }
                    continue;
                }
                inCost = false;   // "EACH UPGRADE ALSO GRANTS ..." and on are not costs
            }
            Matcher lv = LEVEL.matcher(line);
            if (lv.find() && letters.startsWith("LEVEL")) {
                int n = Integer.parseInt(lv.group(1));
                if (!pastUpgrade) {
                    level = n;
                } else {
                    nextLevel = n;
                }
                max = Math.max(max, Integer.parseInt(lv.group(2)));
                continue;
            }
            Matcher g = GRANTS.matcher(line);
            if (g.matches()) {
                Grant grant = new Grant(stat(g.group("rest")), g.group("value").replace("×", "x").replaceAll("\\s", ""));
                (pastUpgrade ? next : now).add(grant);
                anyGrant = true;
                continue;
            }
            // A boost worded otherwise, in the level's "▍" lines: "Auto Harvest every 2.3s".
            Optional<Grant> perk = (level >= 0 || nextLevel >= 0) && line.trim().startsWith("▍")
                    ? perk(line) : Optional.empty();
            if (perk.isPresent()) {
                (pastUpgrade ? next : now).add(perk.get());
                anyGrant = true;
                continue;
            }
            if (IN_PROGRESS.matcher(line).find()) {
                long left = duration(line);
                if (left > 0L) {
                    remainingMs = left;
                }
            }
        }
        if (level < 0 && nextLevel > 0) {
            level = nextLevel - 1;   // not built yet: only the first level is shown
        }
        if (level < 0 || max <= 0 || !anyGrant) {
            return Optional.empty();
        }
        return Optional.of(new Info(name, level, max, now, next, cost, upgradeMs, remainingMs));
    }

    private static String capitalised(String s) {
        StringBuilder out = new StringBuilder(s.length());
        boolean start = true;
        for (char c : s.toCharArray()) {
            out.append(start ? Character.toUpperCase(c) : c);
            start = c == ' ';
        }
        return out.toString();
    }

    /** "▍ Sweep +2.1 adjacent crops" is +2.1 of "Sweep adjacent crops"; empty without a number or a word. */
    static Optional<Grant> perk(String line) {
        String s = line.replaceAll("\\[[^\\]]*/[^\\]]*\\]", " ").replace('▍', ' ').trim();
        Matcher v = PERK_VALUE.matcher(s);
        if (!v.find()) {
            return Optional.empty();
        }
        String stat = stat(s.substring(0, v.start()) + " " + s.substring(v.end()));
        if (!stat.matches(".*\\p{L}.*")) {
            return Optional.empty();
        }
        return Optional.of(new Grant(stat, v.group(1).replace("×", "x")));
    }

    /** The upgrade messages in chat; empty for anything else. */
    public static Optional<Chat> chat(String text) {
        String line = HalloweenText.plainLetters(HalloweenText.strip(text));
        Matcher up = CHAT_UPGRADING.matcher(line);
        if (up.find()) {
            return Optional.of(new Chat(up.group("name").trim(), Integer.parseInt(up.group("level")),
                    duration(up.group("time")), false));
        }
        Matcher done = CHAT_COMPLETE.matcher(line);
        if (done.find()) {
            return Optional.of(new Chat(done.group("name").trim(), Integer.parseInt(done.group("level")), 0L, true));
        }
        return Optional.empty();
    }

    /** A lore with a "LEVEL n / m" line, for the debug output when it is not read as a building. */
    public static boolean hasLevelLine(List<String> lore) {
        if (lore == null) {
            return false;
        }
        for (String raw : lore) {
            if (LEVEL.matcher(HalloweenText.plainLetters(HalloweenText.strip(raw))).find()) {
                return true;
            }
        }
        return false;
    }

    /** The saved name for a building whatever its case: the chat says "Candy Jar", the menu may not. */
    public static String key(Map<String, Saved> saved, String name) {
        if (saved.containsKey(name)) {
            return name;
        }
        for (String k : saved.keySet()) {
            if (k.equalsIgnoreCase(name)) {
                return k;
            }
        }
        return name;
    }

    public static boolean known(Map<String, Saved> saved, String name) {
        return saved.containsKey(key(saved, name));
    }

    /** "☽ Hex (event damage)" is "Hex", "more crops" is "crops". */
    private static String stat(String rest) {
        String s = rest.replaceAll("\\([^)]*\\)", " ").replaceAll("[^\\p{L}\\p{N}% ']", " ")
                .replaceAll("\\s+", " ").trim();
        if (s.toLowerCase(Locale.ROOT).startsWith("more ")) {
            s = s.substring(5);
        }
        return s;
    }

    /** "1m", "1h 30m", "45s" in milliseconds; 0 for none. */
    public static long duration(String text) {
        Matcher m = DURATION_PART.matcher(text);
        long ms = 0L;
        while (m.find()) {
            long n = Long.parseLong(m.group(1));
            ms += switch (m.group(2).toLowerCase(Locale.ROOT)) {
                case "d" -> n * 86_400_000L;
                case "h" -> n * 3_600_000L;
                case "m" -> n * 60_000L;
                default -> n * 1000L;
            };
        }
        return ms;
    }

    // ------------------------------------------------------------ tracking

    /**
     * The building's menu, seen now. Returns true when what is saved changed. An upgrade timer
     * already running is kept: the menu may show the new level while it runs.
     */
    public boolean learn(Map<String, Saved> saved, Info info, long now) {
        String name = key(saved, info.name());
        Long lastSeen = seenAt.put(name, now);
        Saved s = saved.get(name);
        boolean added = s == null;
        if (added) {
            s = new Saved();
            saved.put(name, s);
        }
        // Our timer has just made it the new level and the menu has not caught up: keep ours, or
        // the old level would come back and its catching up would look like a second purchase.
        Long finished = finishedAt.get(info.name());
        if (!added && info.level() < s.level && finished != null && now - finished <= STALE_MENU_MS) {
            return false;
        }
        // The level went up while the menu stayed open: the upgrade was just bought, even if the
        // menu showed it before the balance fell (or the price was not seen leave it).
        boolean bought = !added && lastSeen != null && now - lastSeen <= SPEND_WINDOW_MS
                && info.level() > s.level && s.upgradeEnd <= now && info.remainingMs() <= 0L;
        if (bought) {
            s.upgradeStart = now;
            s.upgradeEnd = now + Math.max(0L, s.upgradeMs);   // the time the bought level showed
            s.upgradeTo = info.level();
        }
        boolean changed = added || bought || s.level != info.level() || s.max != info.max()
                || !s.now.equals(info.now()) || !s.next.equals(info.next()) || !s.cost.equals(info.cost())
                || (info.upgradeMs() > 0L && s.upgradeMs != info.upgradeMs());
        s.level = info.level();
        s.max = info.max();
        s.now = new ArrayList<>(info.now());
        s.next = new ArrayList<>(info.next());
        s.cost = new LinkedHashMap<>(info.cost());
        if (!info.cost().isEmpty()) {
            String currency = info.cost().keySet().iterator().next();
            changed |= !currency.equals(s.currency);
            s.currency = currency;
        }
        if (info.upgradeMs() > 0L) {
            s.upgradeMs = info.upgradeMs();
        }
        if (info.remainingMs() > 0L) {
            // The menu says how long is left: that is better than our own count.
            long end = now + info.remainingMs();
            if (s.upgradeEnd <= now) {
                s.upgradeTo = info.level() + 1;
                s.upgradeStart = s.upgradeMs >= info.remainingMs() ? end - s.upgradeMs : now;
            }
            // Shown to the minute, the time left drifts on every look: only a real change counts.
            if (s.upgradeEnd <= now || Math.abs(s.upgradeEnd - end) > 60_000L) {
                changed = true;
                s.upgradeEnd = end;
            }
        }
        return changed;
    }

    /**
     * A balance fell by {@code amount}. When that is, within 5%, the price of a building whose
     * menu is open or was just closed, its upgrade has started. Returns its name.
     */
    public Optional<String> spent(Map<String, Saved> saved, String currency, double amount, long now) {
        String best = null;
        long bestSeen = Long.MIN_VALUE;
        for (Map.Entry<String, Saved> e : saved.entrySet()) {
            Saved s = e.getValue();
            Double price = s.cost.get(currency);
            long seen = seenAt.getOrDefault(e.getKey(), Long.MIN_VALUE);
            if (price == null || price <= 0 || seen == Long.MIN_VALUE || now - seen > SPEND_WINDOW_MS
                    || s.upgradeEnd > now || (s.max > 0 && s.level >= s.max)
                    || Math.abs(amount - price) > price * 0.05) {
                continue;
            }
            if (seen > bestSeen) {
                best = e.getKey();
                bestSeen = seen;
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        Saved s = saved.get(best);
        s.upgradeTo = s.level + 1;
        s.upgradeStart = now;
        s.upgradeEnd = now + Math.max(0L, s.upgradeMs);
        if (s.upgradeMs <= 0L) {
            finish(s);   // no time known: count it done, the next cost needs the menu anyway
        }
        return Optional.of(best);
    }

    /**
     * An upgrade message from the chat. "Upgrading" starts the timer with the time it gives, the
     * exact one; "COMPLETE!" ends it now, so {@link #finish(Map, long)} rings for it. A building
     * never read from its menu is added, so every upgrade shows. Returns true when what is saved changed.
     */
    public boolean chat(Map<String, Saved> saved, Chat chat, long now) {
        String name = key(saved, chat.name());
        Saved s = saved.get(name);
        if (s == null) {
            s = new Saved();
            s.level = chat.done() ? chat.level() : chat.level() - 1;
            saved.put(name, s);
            if (chat.done()) {
                return true;   // no timer of ours to ring for
            }
        }
        if (!chat.done()) {
            if (s.level != chat.level() - 1) {
                // Upgraded where we did not see it: what it grants and costs now is not known.
                s.level = chat.level() - 1;
                s.now = new ArrayList<>();
                s.next = new ArrayList<>();
                s.cost = new LinkedHashMap<>();
            }
            s.upgradeTo = chat.level();
            s.upgradeStart = now;
            s.upgradeEnd = now + Math.max(0L, chat.ms());
            if (chat.ms() > 0L) {
                s.upgradeMs = chat.ms();
            }
            return true;
        }
        if (s.level >= chat.level() && s.upgradeEnd <= 0L) {
            return false;   // our own timer already finished it
        }
        if (s.upgradeEnd <= 0L) {
            s.upgradeStart = now;
            s.upgradeEnd = now;
        } else {
            s.upgradeEnd = Math.min(now, s.upgradeEnd);
        }
        s.upgradeTo = Math.max(s.upgradeTo, chat.level());
        return true;
    }

    /**
     * Upgrades whose time is up, made the new level: its grants become what the building has now,
     * and the next ones are unknown until the menu is opened. Returns every one finished now;
     * those that ended while the game was closed do not ring.
     */
    public List<Finished> finish(Map<String, Saved> saved, long now) {
        List<Finished> out = new ArrayList<>();
        for (Map.Entry<String, Saved> e : saved.entrySet()) {
            Saved s = e.getValue();
            if (s.upgradeEnd <= 0L || now < s.upgradeEnd) {
                continue;
            }
            long end = s.upgradeEnd;
            int to = s.upgradeTo;
            finish(s);
            finishedAt.put(e.getKey(), now);
            // Noticed now, so its alert shows now even when it ended while the game was away.
            out.add(new Finished(e.getKey(), to, now, now - end <= RING_WITHIN_MS));
        }
        return out;
    }

    private static void finish(Saved s) {
        if (s.level < s.upgradeTo) {
            s.level = s.upgradeTo;
            s.now = s.next;
            s.next = new ArrayList<>();
            s.cost = new LinkedHashMap<>();
        }
        s.upgradeStart = 0L;
        s.upgradeEnd = 0L;
        s.upgradeTo = 0;
    }

    // --------------------------------------------------------------- views

    /**
     * The buildings not maxed, with the balances to measure the cost against: those upgrading
     * first, then those being saved for, then those whose next cost is not known, each in the
     * order first seen.
     */
    public static List<View> views(Map<String, Saved> saved, Map<String, Double> balances, long now) {
        List<View> out = new ArrayList<>();
        saved.forEach((name, s) -> {
            View v = view(name, s, balances, now);
            if (v.state() != State.MAXED) {
                out.add(v);
            }
        });
        out.sort(java.util.Comparator.comparingInt(v -> switch (v.state()) {
            case UPGRADING -> 0;
            case SAVING -> 1;
            default -> 2;
        }));
        return out;
    }

    static View view(String name, Saved s, Map<String, Double> balances, long now) {
        if (s.upgradeEnd > now) {
            long total = Math.max(1L, s.upgradeEnd - s.upgradeStart);
            double done = Math.max(0.0, Math.min(1.0, (now - s.upgradeStart) / (double) total));
            return new View(name, State.UPGRADING, s.level, s.upgradeTo, s.currency, 0, 0, done, false,
                    s.upgradeEnd - now, "");
        }
        if (s.max > 0 && s.level >= s.max) {
            return new View(name, State.MAXED, s.level, s.level, s.currency, 0, 0, 1.0, false, 0L,
                    perks(s.now, List.of()));
        }
        if (s.cost.isEmpty()) {
            return new View(name, State.OPEN_MENU, s.level, s.level + 1, s.currency, 0, 0, 0.0, false, 0L,
                    perks(s.now, List.of()));
        }
        // Each currency counts the same; the amounts shown are the one furthest from paid.
        double sum = 0.0;
        String worst = null;
        double worstFraction = 2.0;
        for (Map.Entry<String, Double> c : s.cost.entrySet()) {
            double have = balances.getOrDefault(c.getKey(), 0.0);
            double f = c.getValue() <= 0 ? 1.0 : Math.min(1.0, have / c.getValue());
            sum += f;
            if (f < worstFraction) {
                worstFraction = f;
                worst = c.getKey();
            }
        }
        double fraction = sum / s.cost.size();
        double have = balances.getOrDefault(worst, 0.0);
        return new View(name, State.SAVING, s.level, s.level + 1, worst, have, s.cost.get(worst), fraction,
                worstFraction >= 1.0, 0L, perks(s.now, s.next));
    }

    /**
     * What the next level changes: "crops x1.8 → x2", or "Hex x1.2 → x1.3 · Health +10 → +15".
     * With no next level, what the building grants now.
     */
    static String perks(List<Grant> now, List<Grant> next) {
        List<String> parts = new ArrayList<>();
        if (next.isEmpty()) {
            for (Grant g : now) {
                parts.add(shortStat(g.stat) + " " + g.value);
            }
            return String.join(" · ", parts);
        }
        for (Grant g : next) {
            String before = now.stream().filter(n -> n.stat.equalsIgnoreCase(g.stat)).map(n -> n.value)
                    .findFirst().orElse("");
            parts.add(shortStat(g.stat) + " " + (before.isEmpty() || before.equals(g.value)
                    ? g.value : before + " → " + g.value));
        }
        return String.join(" · ", parts);
    }

    /** "Event Health" is "Health": every stat here is an event one. */
    private static String shortStat(String stat) {
        return stat.toLowerCase(Locale.ROOT).startsWith("event ") && stat.length() > 6 ? stat.substring(6) : stat;
    }
}
