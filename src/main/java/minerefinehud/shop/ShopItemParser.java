package minerefinehud.shop;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads one shop entry out of an item's name and lore.
 *
 * Observed tooltip shape:
 *
 *   [Debris Shovel] [VI]
 *   Efficiency: +200
 *   ...
 *   Cost:
 *     [Debris Shovel] [V] x1 (0)        <- prerequisite item, ignored for pricing
 *     Debris x5B (0)                    <- the material price we want
 *   CLICK to purchase!
 *
 * Pure string handling, no Minecraft types, so it is testable against the exact strings seen in
 * game. This is the piece most likely to break when the server restyles its menus, which is
 * precisely why it is isolated and covered by tests.
 */
public final class ShopItemParser {

    /** Gear slots, longest first so "Chestplate" is matched before any shorter accidental tail. */
    private static final Set<String> GEAR = new LinkedHashSet<>(List.of(
            "sword", "pickaxe", "shovel", "axe",
            "helmet", "chestplate", "leggings", "boots", "charm"));

    /** "[Debris Shovel] [VI]" and also the bare "Debris Shovel [VI]" if brackets are dropped. */
    private static final Pattern TITLE = Pattern.compile(
            "^\\[?\\s*(?<name>[^\\[\\]]+?)\\s*\\]?\\s*\\[\\s*(?<level>[IVXLCDM]+)\\s*\\]\\s*$",
            Pattern.CASE_INSENSITIVE);

    /**
     * "[Ruby Charm I]" or "Ruby Charm I": the numeral inside the name rather than in its own
     * brackets. Only accepted when a gear word sits right before the numeral, so a name that
     * merely ends in something numeral-like is never misread.
     */
    private static final Pattern TITLE_INLINE_LEVEL = Pattern.compile(
            "^\\[?\\s*(?<name>[^\\[\\]]+?)\\s+(?<level>[IVXLCDM]+)\\s*\\]?$",
            Pattern.CASE_INSENSITIVE);

    /**
     * "Honeystone Charm": a charm has one tier and no numeral at all. Only accepted for charms,
     * so that nothing else without a tier is taken for gear.
     */
    private static final Pattern TITLE_NO_LEVEL = Pattern.compile("^\\[?\\s*(?<name>[^\\[\\]]+?)\\s*\\]?$");

    /** "Debris x5B (0)" or "Debris x1.96B (7,725,607,214)". Not an item line, which starts "[". */
    private static final Pattern MATERIAL_COST = Pattern.compile(
            "^(?<currency>[\\p{L}][\\p{L} '\\-]*?)\\s*[x×]\\s*(?<amount>[0-9][0-9,.]*\\s*[KMBTkmbt]?)\\s*(?:\\((?<have>[0-9,]+)\\))?\\s*$");

    private static final Pattern COST_HEADER = Pattern.compile("^cost\\s*:?$", Pattern.CASE_INSENSITIVE);

    /** Decoration the server puts in front of lore rows, plus colour codes. */
    private static final Pattern FORMATTING = Pattern.compile("[§&][0-9A-Fa-fK-Ok-oRr]");
    private static final Pattern LEADING_JUNK = Pattern.compile("^[^\\p{L}\\p{N}\\[]+");
    private static final Pattern TRAILING_MARK = Pattern.compile("[^\\p{L}\\p{N}\\)\\]]+$");

    /** One shop entry, priced. */
    public record Entry(String mine, String gear, int level, String currency, long price) {

        /** Stable key for the ledger. */
        public String key() {
            return PriceLedger.key(mine, gear, level);
        }
    }

    private ShopItemParser() {
    }

    /**
     * @param title the item's display name, e.g. "[Debris Shovel] [VI]"
     * @param lore  the item's lore lines, in order
     * @return the entry, or empty if this stack is not a priced upgrade
     */
    public static Optional<Entry> parse(String title, List<String> lore) {
        return parseTitle(title).flatMap(ref -> materialCost(lore).map(cost ->
                new Entry(ref.mine(), ref.gear(), ref.level(), cost.currency(), cost.amount())));
    }

    private record Cost(String currency, long amount) {}

    /**
     * Finds the material cost line. Lines beginning with "[" are prerequisite items rather than
     * materials, so they are skipped: those carry a count, not a price.
     */
    private static Optional<Cost> materialCost(List<String> lore) {
        if (lore == null) {
            return Optional.empty();
        }

        boolean inCostBlock = false;
        for (String rawLine : lore) {
            String line = clean(rawLine);
            if (line.isEmpty()) {
                continue;
            }

            if (COST_HEADER.matcher(line).matches()) {
                inCostBlock = true;
                continue;
            }
            if (!inCostBlock) {
                continue;
            }
            if (line.startsWith("[")) {
                continue;   // prerequisite item, e.g. "[Debris Shovel] [V] x1 (0)"
            }

            Matcher m = MATERIAL_COST.matcher(line);
            if (m.matches() && isItem(m.group("currency"))) {
                continue;   // prerequisite charm, "Vase Charm x1 (0)", which has no brackets
            }
            if (m.matches()) {
                OptionalLong amount = Amounts.parse(m.group("amount"));
                if (amount.isPresent()) {
                    return Optional.of(new Cost(m.group("currency").trim(), amount.getAsLong()));
                }
            }
        }
        return Optional.empty();
    }

    /** "Suspicious Sand Shovel" -> {"Suspicious Sand", "shovel"}. The gear word is always last. */
    private static String[] splitGear(String name) {
        String trimmed = name.trim().replaceAll("\\s+", " ");
        int space = trimmed.lastIndexOf(' ');
        if (space <= 0) {
            return null;
        }
        String tail = trimmed.substring(space + 1).toLowerCase(Locale.ROOT);
        if (!GEAR.contains(tail)) {
            return null;
        }
        return new String[] { trimmed.substring(0, space).trim(), tail };
    }

    private static String clean(String raw) {
        String s = FORMATTING.matcher(raw).replaceAll("");
        s = LEADING_JUNK.matcher(s).replaceFirst("");
        s = TRAILING_MARK.matcher(s).replaceFirst("");
        return s.replaceAll("\\s+", " ").trim();
    }

    /** The item itself, without any price. Used to read the gear the player is carrying. */
    public record GearRef(String mine, String gear, int level) {}

    /**
     * Reads just the name, for items that carry no cost block.
     *
     * The gear in the player's inventory is named exactly like the shop entries, so the shovel in
     * hand says which mine the player is geared for and which level they already hold. That makes
     * "your next upgrade costs X" answerable without asking them to configure anything.
     */
    public static Optional<GearRef> parseTitle(String title) {
        if (title == null) {
            return Optional.empty();
        }
        String cleaned = clean(title);
        Optional<GearRef> ref = titleWith(TITLE, cleaned);
        if (ref.isEmpty()) {
            ref = titleWith(TITLE_INLINE_LEVEL, cleaned);
        }
        if (ref.isEmpty()) {
            ref = charmWithoutLevel(cleaned);
        }
        return ref;
    }

    private static Optional<GearRef> charmWithoutLevel(String cleaned) {
        Matcher t = TITLE_NO_LEVEL.matcher(cleaned);
        if (!t.matches()) {
            return Optional.empty();
        }
        String[] split = splitGear(t.group("name"));
        return split != null && split[1].equals("charm")
                ? Optional.of(new GearRef(split[0], split[1], 1))
                : Optional.empty();
    }

    private static boolean namesGear(String title) {
        if (title == null) {
            return false;
        }
        String lower = clean(title).toLowerCase(Locale.ROOT);
        return GEAR.stream().anyMatch(g -> lower.matches(".*\\b" + g + "\\b.*"));
    }

    /** "Vase Charm" or "Rust Pickaxe": a currency-shaped name that is really an item. */
    private static boolean isItem(String name) {
        return name != null && splitGear(name.trim()) != null;
    }

    private static Optional<GearRef> titleWith(Pattern pattern, String cleaned) {
        Matcher t = pattern.matcher(cleaned);
        if (!t.matches()) {
            return Optional.empty();
        }
        OptionalInt level = RomanNumerals.parse(t.group("level"));
        if (level.isEmpty()) {
            return Optional.empty();
        }
        String[] split = splitGear(t.group("name"));
        return split == null
                ? Optional.empty()
                : Optional.of(new GearRef(split[0], split[1], level.getAsInt()));
    }

    /**
     * Shop items that ask for something ("Cost:") but could not be read as a priced upgrade. A
     * tooltip in a shape the parser has never seen would otherwise be skipped without a trace;
     * the caller saves these so the exact text can be looked at and the parser fixed.
     */
    public static List<ItemView> unrecognised(List<ItemView> stacks) {
        List<ItemView> out = new ArrayList<>();
        if (stacks == null) {
            return out;
        }
        for (ItemView stack : stacks) {
            if (stack.lore() == null || parse(stack.title(), stack.lore()).isPresent()) {
                continue;
            }
            boolean hasCost = stack.lore().stream()
                    .anyMatch(l -> l != null && COST_HEADER.matcher(clean(l)).matches());
            // Only gear is worth reporting. Area unlocks, books and slayer items are not upgrades.
            if (hasCost && namesGear(stack.title())) {
                out.add(stack);
            }
        }
        return out;
    }

    /** Convenience for scanning a whole container screen. */
    public static List<Entry> parseAll(List<ItemView> stacks) {
        List<Entry> out = new ArrayList<>();
        if (stacks == null) {
            return out;
        }
        for (ItemView stack : stacks) {
            parse(stack.title(), stack.lore()).ifPresent(out::add);
        }
        return out;
    }

    /** "[Debris Shovel] [V] x1 (0)": the item part, before the count. */
    private static final Pattern PREREQUISITE = Pattern.compile("^(?<item>\\[.+\\])\\s*[x×]\\s*[0-9].*$");

    /**
     * Tier I of one mine's item says which mine comes before it: buying "[Debris Shovel] [I]"
     * requires "[Suspicious Sand Shovel] [VI]". Read from the shop, this places a mine the data
     * has never heard of into the progression, so a new dimension needs no data update.
     */
    public record Link(String mine, String gear, String previousMine) {}

    public static List<Link> links(List<ItemView> stacks) {
        List<Link> out = new ArrayList<>();
        if (stacks == null) {
            return out;
        }
        for (ItemView stack : stacks) {
            Optional<GearRef> item = parseTitle(stack.title());
            if (item.isEmpty() || item.get().level() != 1) {
                continue;
            }
            for (GearRef needed : prerequisites(stack.lore())) {
                if (needed.gear().equals(item.get().gear())
                        && !needed.mine().equalsIgnoreCase(item.get().mine())) {
                    out.add(new Link(item.get().mine(), item.get().gear(), needed.mine()));
                }
            }
        }
        return out;
    }

    /** The items a cost block asks for, as opposed to the material it asks for. */
    static List<GearRef> prerequisites(List<String> lore) {
        List<GearRef> out = new ArrayList<>();
        if (lore == null) {
            return out;
        }
        boolean inCostBlock = false;
        for (String rawLine : lore) {
            String line = clean(rawLine);
            if (COST_HEADER.matcher(line).matches()) {
                inCostBlock = true;
                continue;
            }
            if (!inCostBlock) {
                continue;
            }
            Matcher m = PREREQUISITE.matcher(line);
            if (m.matches()) {
                parseTitle(m.group("item")).ifPresent(out::add);
                continue;
            }
            // "Vase Charm x1 (0)": a charm asked for by name, without brackets.
            Matcher charm = MATERIAL_COST.matcher(line);
            if (charm.matches() && isItem(charm.group("currency"))) {
                parseTitle(charm.group("currency")).ifPresent(out::add);
            }
        }
        return out;
    }

    /**
     * The player's balance of each currency, read from the bracketed figure on cost lines:
     * "Debris x1.96B (7,725,607,214)" means 7,725,607,214 Debris held.
     *
     * Prerequisite rows are skipped for the same reason as in pricing: "[Debris Shovel] [V] x1 (0)"
     * is a count of items owned, not a balance.
     */
    public static java.util.Map<String, Long> balances(List<ItemView> stacks) {
        java.util.Map<String, Long> out = new java.util.LinkedHashMap<>();
        if (stacks == null) {
            return out;
        }
        for (ItemView stack : stacks) {
            if (stack.lore() == null) {
                continue;
            }
            boolean inCostBlock = false;
            for (String rawLine : stack.lore()) {
                String line = clean(rawLine);
                if (COST_HEADER.matcher(line).matches()) {
                    inCostBlock = true;
                    continue;
                }
                if (!inCostBlock || line.isEmpty() || line.startsWith("[")) {
                    continue;
                }
                Matcher m = MATERIAL_COST.matcher(line);
                if (m.matches() && m.group("have") != null && !isItem(m.group("currency"))) {
                    try {
                        out.put(m.group("currency").trim(),
                                Long.parseLong(m.group("have").replace(",", "")));
                    } catch (NumberFormatException ignored) {
                        // Beyond a long is not a balance this mod can do anything useful with.
                    }
                }
            }
        }
        return out;
    }

    /** Minimal view of an item stack, so this package never imports Minecraft. */
    public record ItemView(String title, List<String> lore) {}
}
