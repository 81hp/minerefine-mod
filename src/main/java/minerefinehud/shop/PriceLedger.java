package minerefinehud.shop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * What every upgrade costs, with prices the client saw itself ranked above anything configured.
 *
 * Two layers:
 *
 *   OBSERVED  read from a shop tooltip the player opened. Current by construction.
 *   PREFILL   seeded from the community spreadsheet, so mines you have never visited still show
 *             something.
 *
 * Observed always wins. That is the whole point: when the server nerfed prices by roughly a
 * quarter, every spreadsheet in existence was wrong until a human got round to updating it, while
 * anything read from the game was right immediately. A price the client watched the server print
 * five seconds ago does not need a second opinion.
 */
public final class PriceLedger {

    public enum Source { OBSERVED, PREFILL }

    /** Outcome of recording an observation, so a price change can be reported rather than hidden. */
    public enum Result { NEW, UNCHANGED, CHANGED }

    public record Price(long amount, String currency, Source source, long seenAt) {}

    /** Levels per item seen so far. Six is what the server uses, but nothing depends on that. */
    public static final int DEFAULT_MAX_LEVEL = 6;

    private final Map<String, Price> observed = new HashMap<>();
    private final Map<String, Price> prefill = new HashMap<>();

    /** Reported alongside a CHANGED result so the caller can tell the player what moved. */
    private final List<PriceChange> recentChanges = new ArrayList<>();

    public record PriceChange(String mine, String gear, int level, long from, long to, long at) {}

    public static String key(String mine, String gear, int level) {
        return normalise(mine) + '|' + normalise(gear) + '|' + level;
    }

    private static String normalise(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    // ------------------------------------------------------------- recording

    public Result record(ShopItemParser.Entry entry, long nowMs) {
        String k = entry.key();
        Price existing = observed.get(k);
        Price fresh = new Price(entry.price(), entry.currency(), Source.OBSERVED, nowMs);

        if (existing == null) {
            observed.put(k, fresh);
            return Result.NEW;
        }
        if (existing.amount() == entry.price()) {
            // Refresh the timestamp so staleness reporting stays honest, but report no change.
            observed.put(k, fresh);
            return Result.UNCHANGED;
        }

        observed.put(k, fresh);
        recentChanges.add(new PriceChange(entry.mine(), entry.gear(), entry.level(),
                existing.amount(), entry.price(), nowMs));
        return Result.CHANGED;
    }

    public List<PriceChange> drainChanges() {
        List<PriceChange> out = List.copyOf(recentChanges);
        recentChanges.clear();
        return out;
    }

    /** Tier counts known in advance, keyed "mine|gear"; see {@link #knowTierCount}. */
    private final Map<String, Integer> knownTiers = new HashMap<>();

    /**
     * How many tiers an item has, from the bundled table, so a piece is known to be maxed (or
     * not) before its shop has ever been opened. Woodland Copper armour has three tiers; guessing
     * six left a maxed set looking half done. What the shop shows still raises it.
     */
    public void knowTierCount(String mine, String gear, int tiers) {
        if (tiers > 0) {
            knownTiers.put(normalise(mine) + '|' + normalise(gear), tiers);
        }
    }

    /** Whether the bundled table gives this item's tier count. */
    public boolean knowsTierCount(String mine, String gear) {
        return knownTiers.containsKey(normalise(mine) + '|' + normalise(gear));
    }

    /** Seeds a fallback price. Never overwrites something the client observed. */
    public void seed(String mine, String gear, int level, long amount, String currency) {
        prefill.put(key(mine, gear, level), new Price(amount, currency, Source.PREFILL, 0L));
    }

    // -------------------------------------------------------------- lookup

    public Optional<Price> price(String mine, String gear, int level) {
        Price p = observed.get(key(mine, gear, level));
        if (p != null) {
            return Optional.of(p);
        }
        return Optional.ofNullable(prefill.get(key(mine, gear, level)));
    }

    /** The upgrade the player would buy next, given the level they currently hold. */
    public Optional<Price> nextUpgrade(String mine, String gear, int currentLevel) {
        return price(mine, gear, currentLevel + 1);
    }

    /** Every level from 1 up, with gaps left empty. */
    public List<Optional<Price>> ladder(String mine, String gear, int maxLevel) {
        List<Optional<Price>> out = new ArrayList<>(maxLevel);
        for (int level = 1; level <= maxLevel; level++) {
            out.add(price(mine, gear, level));
        }
        return out;
    }

    /**
     * Total still to pay to go from the current level to max.
     *
     * Empty rather than a partial sum when any step in between is unknown. A number that silently
     * omits two levels is worse than no number.
     */
    public OptionalLong remainingCost(String mine, String gear, int currentLevel, int maxLevel) {
        long sum = 0L;
        for (int level = currentLevel + 1; level <= maxLevel; level++) {
            Optional<Price> p = price(mine, gear, level);
            if (p.isEmpty()) {
                return OptionalLong.empty();
            }
            sum += p.get().amount();
        }
        return OptionalLong.of(sum);
    }

    /** Above this the ledger is not probed for extra tiers. Far beyond anything seen in game. */
    private static final int MAX_PROBED_LEVEL = 20;

    /**
     * The bundled tier count, else six, unless the shop has shown more. Only ever raised by
     * observation, never lowered, because a shop that lists just the next tier would otherwise
     * look like a short ladder.
     */
    public int maxLevel(String mine, String gear) {
        // A charm is a single item per mine, never a ladder.
        Integer known = knownTiers.get(normalise(mine) + '|' + normalise(gear));
        int max = known != null ? known : "charm".equals(normalise(gear)) ? 1 : DEFAULT_MAX_LEVEL;
        while (max < MAX_PROBED_LEVEL && price(mine, gear, max + 1).isPresent()) {
            max++;
        }
        return max;
    }

    /**
     * How many tiers this item has, using the spreadsheet's whole-piece total as a check.
     *
     * Tier counts vary: armour has three tiers at Ocean and four in most worlds, Rubble's sword
     * five, Rafter's axe seven. Assuming six left a maxed Ocean chestplate waiting forever for a
     * tier IV that does not exist. The spreadsheet total is the sum of every tier (true to 0.2%
     * for every complete ladder seen in game), and every tier costs more than the one below it,
     * which gives two rules:
     *
     *   - If what the total leaves over, after the tiers seen, is less than the dearest tier
     *     seen, no further tier fits: the dearest seen is the last. This holds even with gaps
     *     below, such as tier I never having been seen.
     *   - Otherwise each tier still to come costs more than the dearest seen, so at most
     *     leftover / dearest of them fit, which caps the usual guess of six.
     *
     * With no total, or no tier seen, it is {@link #maxLevel(String, String)}.
     */
    public int maxLevel(String mine, String gear, OptionalLong sheetTotal) {
        int fallback = maxLevel(mine, gear);
        if (sheetTotal.isEmpty() || sheetTotal.getAsLong() <= 0L) {
            return shopTierCount(mine, gear).orElse(fallback);
        }
        int highest = 0;
        long seen = 0L;
        for (int level = 1; level <= MAX_PROBED_LEVEL; level++) {
            Optional<Price> p = price(mine, gear, level);
            if (p.isPresent()) {
                highest = level;
                seen += p.get().amount();
            }
        }
        if (highest == 0) {
            return fallback;
        }
        long top = price(mine, gear, highest).orElseThrow().amount();
        // Tiers seen already costing more than the sheet says the whole piece does means the sheet
        // is out of date, after a price rise for instance. Then it proves nothing either way.
        // Shop prices are rounded to three figures, hence the margin.
        if (top <= 0L || seen > sheetTotal.getAsLong() + sheetTotal.getAsLong() / 50L) {
            return fallback;
        }
        long rest = sheetTotal.getAsLong() - seen;
        if (rest < top) {
            return highest;
        }
        long room = Math.min(MAX_PROBED_LEVEL, rest / top);
        return Math.max(highest, Math.min(fallback, highest + (int) room));
    }

    /**
     * The tier count from the shop alone, for an item the spreadsheet does not list (a new area).
     * A shop lists every tier above the ones owned, so the highest tier it has shown is the last
     * one: Frost's pickaxe was listed II to V, so it has five. Only trusted once two or more tiers
     * have been seen, so a single item seen on its own (a purchase screen, a chat link) cannot
     * pass for a whole listing.
     */
    public java.util.OptionalInt shopTierCount(String mine, String gear) {
        List<Integer> seen = observedTiers(mine, gear);
        return seen.size() >= 2 ? java.util.OptionalInt.of(seen.get(seen.size() - 1)) : java.util.OptionalInt.empty();
    }

    /** The tiers of this item read from the shop, lowest first. */
    public List<Integer> observedTiers(String mine, String gear) {
        List<Integer> out = new ArrayList<>();
        for (int level = 1; level <= MAX_PROBED_LEVEL; level++) {
            if (observed.containsKey(key(mine, gear, level))) {
                out.add(level);
            }
        }
        return out;
    }

    /** What the tiers that have been seen add up to, whether or not that is every tier. */
    public long observedSum(String mine, String gear) {
        long sum = 0L;
        for (int level : observedTiers(mine, gear)) {
            sum += observed.get(key(mine, gear, level)).amount();
        }
        return sum;
    }

    /**
     * What every tier of this item adds up to, from shop prices alone. Empty until every tier has
     * been seen, for the same reason as {@link #remainingCost}.
     */
    public OptionalLong observedTotal(String mine, String gear) {
        return observedTotal(mine, gear, OptionalLong.empty());
    }

    /** Same, with the tier count checked against the spreadsheet total; see {@link #maxLevel(String, String, OptionalLong)}. */
    public OptionalLong observedTotal(String mine, String gear, OptionalLong sheetTotal) {
        int max = maxLevel(mine, gear, sheetTotal);
        long sum = 0L;
        for (int level = 1; level <= max; level++) {
            Price p = observed.get(key(mine, gear, level));
            if (p == null) {
                return OptionalLong.empty();
            }
            sum += p.amount();
        }
        return OptionalLong.of(sum);
    }

    /** Gear words seen in this mine's shop, e.g. "axe", so a mine missing from the data still lists its tool. */
    public java.util.Set<String> observedGears(String mine) {
        String prefix = normalise(mine) + '|';
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (String k : observed.keySet()) {
            if (k.startsWith(prefix)) {
                out.add(k.substring(prefix.length(), k.lastIndexOf('|')));
            }
        }
        return out;
    }

    /**
     * Every mine, or boss, the ledger knows by the name its shop uses, from prices seen or seeded
     * and from the bundled tier counts. Lower case, as stored.
     */
    public java.util.Set<String> knownMines() {
        java.util.Set<String> out = new java.util.TreeSet<>();
        for (java.util.Set<String> keys : List.of(observed.keySet(), prefill.keySet(), knownTiers.keySet())) {
            for (String k : keys) {
                int bar = k.indexOf('|');
                if (bar > 0) {
                    out.add(k.substring(0, bar));
                }
            }
        }
        return out;
    }

    /** How many of the levels for this item have been seen in game rather than seeded. */
    public int observedLevels(String mine, String gear, int maxLevel) {
        int count = 0;
        for (int level = 1; level <= maxLevel; level++) {
            if (observed.containsKey(key(mine, gear, level))) {
                count++;
            }
        }
        return count;
    }

    public int observedCount() {
        return observed.size();
    }

    public int prefillCount() {
        return prefill.size();
    }

    // ---------------------------------------------------------- persistence

    public record Persisted(String key, long amount, String currency, long seenAt) {}

    public List<Persisted> export() {
        List<Persisted> out = new ArrayList<>(observed.size());
        observed.forEach((k, p) -> out.add(new Persisted(k, p.amount(), p.currency(), p.seenAt())));
        return out;
    }

    /** Only observations are persisted. Prefill is reloaded from the spreadsheet each start. */
    public void importAll(List<Persisted> saved) {
        if (saved == null) {
            return;
        }
        for (Persisted p : saved) {
            if (p != null && p.key() != null) {
                observed.put(p.key(), new Price(p.amount(), p.currency(), Source.OBSERVED, p.seenAt()));
            }
        }
    }
}
