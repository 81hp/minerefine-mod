package minerefinehud.halloween;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Halloween shop's pumpkin armour: what each piece costs and how close the player is.
 *
 * Read from the shop exactly as it shows a piece (plain text):
 *
 *   Pumpkin Helmet
 *    Event Health: +3❤
 *   Cost:
 *   ➼ 250 🍬 Candy ❌
 *   ➼ [item/carrot@items] 2.5M Carrots ❌
 *   ➼ [item/beetroot@items] 2.5M Beetroots ❌
 *   ➼ [item/nether_wart@items] 250k Nether Wart ❌
 *   1/1 Lifetime Stock
 *
 * "Pumpkin Helmet" is also the Pumpkin mine's helmet, so a piece only counts as the Halloween one
 * when its lore has the event's "Event Health" or a cost in Candy. The bundled prices are the
 * latest seen; the shop overrides them, so a price change needs only one visit.
 *
 * No Minecraft types.
 */
public final class PumpkinSet {

    public static final List<String> PIECES = List.of("Helmet", "Chestplate", "Leggings", "Boots");

    /** The whole set, for the "all pieces left" bar. */
    public static final String SET = "Set";

    /** Prices as last seen in the shop (October 2026). Every piece costs the same. */
    public static Map<String, Map<String, Double>> bundledPrices() {
        Map<String, Map<String, Double>> out = new LinkedHashMap<>();
        for (String piece : PIECES) {
            Map<String, Double> cost = new LinkedHashMap<>();
            cost.put("Candy", 250.0);
            cost.put("Carrots", 2_500_000.0);
            cost.put("Beetroots", 2_500_000.0);
            cost.put("Nether Wart", 250_000.0);
            out.put(piece, cost);
        }
        return out;
    }

    private static final Pattern PIECE = Pattern.compile(
            "pumpkin\\s+(helmet|chestplate|leggings|boots)", Pattern.CASE_INSENSITIVE);
    private static final Pattern COST_HEADER = Pattern.compile("^\\W*cost\\W*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern COST_LINE = Pattern.compile(
            "^([0-9][0-9.,]*[kKmMbB]?)\\s+([\\p{L}][\\p{L} ']*)$");
    private static final Pattern STOCK = Pattern.compile("([0-9]+)\\s*/\\s*([0-9]+)\\s+lifetime stock",
            Pattern.CASE_INSENSITIVE);

    /** Which piece an item name is, "Chestplate" for "[Pumpkin Chestplate] [I]". */
    public static Optional<String> piece(String title) {
        Matcher m = PIECE.matcher(HalloweenText.strip(title));
        if (!m.find()) {
            return Optional.empty();
        }
        String p = m.group(1).toLowerCase(Locale.ROOT);
        return Optional.of(Character.toUpperCase(p.charAt(0)) + p.substring(1));
    }

    /** The event's piece rather than the Pumpkin mine's: event stats or a candy price. */
    public static boolean isEventItem(String title, List<String> lore) {
        if (piece(title).isEmpty() || lore == null) {
            return false;
        }
        for (String line : lore) {
            String s = HalloweenText.strip(line).toLowerCase(Locale.ROOT);
            if (s.contains("event health") || s.contains("halloween")) {
                return true;
            }
        }
        return costs(lore).containsKey("Candy");
    }

    /**
     * Every "amount currency" line under "Cost:". Icons, arrows and the ✔/❌ marks are dropped
     * first. Also used for any other Halloween shop item paid in candy.
     */
    public static Map<String, Double> costs(List<String> lore) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (lore == null) {
            return out;
        }
        boolean inCost = false;
        for (String raw : lore) {
            String line = HalloweenText.strip(raw);
            if (COST_HEADER.matcher(line).matches()) {
                inCost = true;
                continue;
            }
            if (!inCost) {
                continue;
            }
            String cleaned = line.replaceAll("\\[[^\\]]*\\]", " ")
                    .replaceAll("[^\\p{L}\\p{N}.,' ]", " ")
                    .replaceAll("\\s+", " ").trim();
            if (cleaned.isEmpty()) {
                if (!out.isEmpty()) {
                    break;   // a blank line ends the cost block
                }
                continue;
            }
            Matcher m = COST_LINE.matcher(cleaned);
            if (m.matches()) {
                OptionalDouble v = HalloweenText.number(m.group(1));
                if (v.isPresent()) {
                    out.put(HalloweenText.canonical(m.group(2)), v.getAsDouble());
                }
            }
        }
        return out;
    }

    /** "0/1 Lifetime Stock" is a piece already bought. Empty when the line is missing. */
    public static Optional<Boolean> soldOut(List<String> lore) {
        if (lore == null) {
            return Optional.empty();
        }
        for (String raw : lore) {
            Matcher m = STOCK.matcher(HalloweenText.strip(raw));
            if (m.find()) {
                return Optional.of(Integer.parseInt(m.group(1)) == 0);
            }
        }
        return Optional.empty();
    }

    // ----------------------------------------------------------------- views

    /** One currency of a bar: what the player has against what it costs. */
    public record Part(String currency, double have, double cost) {

        public boolean done() {
            return have >= cost;
        }

        public double fraction() {
            return cost <= 0 ? 1.0 : Math.min(1.0, have / cost);
        }
    }

    /** One bar: a piece, or the whole set's pieces still to buy. {@code pieces} is how many. */
    public record View(String name, List<Part> parts, int pieces) {

        /** Each currency counts the same, so the bar reads "how much of the way there". */
        public double fraction() {
            if (parts.isEmpty()) {
                return 0.0;
            }
            double sum = 0.0;
            for (Part p : parts) {
                sum += p.fraction();
            }
            return sum / parts.size();
        }

        public boolean affordable() {
            return !parts.isEmpty() && parts.stream().allMatch(Part::done);
        }
    }

    /**
     * The bars for the chosen pieces, leaving out pieces already owned. SET adds up every piece
     * not owned yet. A piece with no known price is left out rather than shown as free.
     */
    public static List<View> plan(List<String> chosen, Set<String> owned,
                                  Map<String, Map<String, Double>> prices, Map<String, Double> balances) {
        List<View> out = new ArrayList<>();
        for (String c : chosen) {
            if (c.equals(SET)) {
                Map<String, Double> total = new LinkedHashMap<>();
                int count = 0;
                for (String piece : PIECES) {
                    Map<String, Double> cost = prices.get(piece);
                    if (owned.contains(piece) || cost == null || cost.isEmpty()) {
                        continue;
                    }
                    count++;
                    cost.forEach((cur, amt) -> total.merge(cur, amt, Double::sum));
                }
                if (count > 0) {
                    out.add(new View("Pumpkin set", parts(total, balances), count));
                }
                continue;
            }
            Map<String, Double> cost = prices.get(c);
            if (owned.contains(c) || cost == null || cost.isEmpty()) {
                continue;
            }
            out.add(new View("Pumpkin " + c, parts(cost, balances), 1));
        }
        return out;
    }

    private static List<Part> parts(Map<String, Double> cost, Map<String, Double> balances) {
        List<Part> out = new ArrayList<>();
        cost.forEach((cur, amt) -> out.add(new Part(cur, balances.getOrDefault(cur, 0.0), amt)));
        return out;
    }
}
