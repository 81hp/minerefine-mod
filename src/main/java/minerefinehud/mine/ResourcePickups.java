package minerefinehud.mine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Recognises the mine from the resource items mining puts in the inventory.
 *
 * Mining Zircon gives an item named "Zircon", compressed later into "Compressed Zircon", "Very
 * Compressed Zircon" and so on, always with the mine's name in it. So whichever resource's count
 * just went up is the mine being mined. No shop visit, no learning, and it works at any mine.
 *
 * What counts as a resource:
 *
 *   - its name, minus any compression prefix, is exactly a known mine name; or
 *   - its lore carries a single-word world tag such as "RUINBOUND", which is how resources are
 *     marked. That covers mines the price data does not list. Gear says "RUINBOUND SHOVEL",
 *     two words, so a "[Zircon Pickaxe] [II]" is never mistaken for Zircon.
 *
 * Only an increase counts. Owning Zircon says nothing about where the player is; gaining it does.
 * The first scan after joining only records what is there.
 */
public final class ResourcePickups {

    public record Item(String name, List<String> lore, int count) {}

    /** "Compressed", "Very Compressed", "Super Compressed" and similar, in front of the name. */
    private static final Pattern COMPRESSION = Pattern.compile(
            "^(?:(?:very|super|ultra|mega|hyper|extremely|highly|double|triple)\\s+)*compressed\\s+",
            Pattern.CASE_INSENSITIVE);

    /** Sprite text a name may carry, e.g. "Khan's Spoil 3[item/book@items]". */
    private static final Pattern SPRITE = Pattern.compile("\\[(?:item|block)/[^\\]]*\\]");

    private static final Pattern FORMATTING = Pattern.compile("[§&][0-9A-Fa-fK-Ok-oRr]");

    private static final Pattern WORLD_TAG = Pattern.compile("^[A-Z]+BOUND$");

    private Map<String, Integer> baseline;
    private Map<String, String> baselineResources = new HashMap<>();

    /** World tag of the resource that last went up, e.g. "RUINBOUND". */
    private Optional<String> lastWorldTag = Optional.empty();

    /**
     * The single-word world tag in an item's lore, such as "RUINBOUND". It names the dimension
     * even for a mine the data does not list, which is what lets bosses be grouped by dimension
     * in a world added after the spreadsheet.
     */
    public static Optional<String> worldTag(List<String> lore) {
        if (lore == null) {
            return Optional.empty();
        }
        for (String line : lore) {
            String l = FORMATTING.matcher(line == null ? "" : line).replaceAll("").trim();
            if (WORLD_TAG.matcher(l).matches()) {
                return Optional.of(l);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether a gain is mining at all. Resources also arrive by compressing, by moving them out
     * of a backpack or chest, from a sale or a refund. Taken as mining, a Rust stack shuffled after
     * walking on to Ironclad sent the panel back to Rust. Only a gain alongside a mining line in
     * the action bar counts.
     *
     * @param lastMiningLineAt when the action bar last showed a mined block, or 0 for never
     */
    public static boolean fromMining(long gainAt, long lastMiningLineAt, long windowMs) {
        return lastMiningLineAt > 0L && Math.abs(gainAt - lastMiningLineAt) <= windowMs;
    }

    /**
     * Whether a gain should say which mine the player is at: whenever it comes from mining, even
     * when the block's icon is known. The resource is named after its mine; an icon is not always
     * one mine's, and a known icon used to win here, so mining Woodland Copper showed Rust, whose
     * icon it shares. Leftover items from the last mine (wood logs, typically) can still name the
     * old mine for a moment, until the next block's icon or pickup takes over.
     */
    public static boolean namesTheMine(long gainAt, long lastMiningLineAt, long windowMs) {
        return fromMining(gainAt, lastMiningLineAt, windowMs);
    }

    public Optional<String> lastWorldTag() {
        return lastWorldTag;
    }

    /** The resource an item is, by its server-spelt mine name, or empty if it is not one. */
    public static Optional<String> resourceName(String name, List<String> lore, MineCatalog catalog) {
        if (name == null) {
            return Optional.empty();
        }
        String clean = cleanName(name);
        String base = COMPRESSION.matcher(clean).replaceFirst("").trim();
        if (base.isEmpty() || base.contains("[")) {
            return Optional.empty();
        }

        // The resource's own name, which is also the currency the shop charges in: "Pine Tree"
        // is paid in Pine Tree even though the gear is called Pine. Mine detection resolves any
        // spelling, and a balance filed under the currency's name is one the bar can find.
        if (catalog.exactly(base).isPresent()) {
            return Optional.of(base);
        }
        return worldTag(lore).map(tag -> base);
    }

    /**
     * Compares this scan with the last one. Returns the resource of the item that increased the
     * most, if any: "Very Compressed Rust" going up is Rust.
     *
     * Counted per item name, so per compression level, not summed per resource. Mining here gives
     * compressed items, and the server merges them up a level as they fill: 64 Compressed Rust
     * become 1 Very Compressed Rust. Summed, that is a drop of 63 and no pickup at all, which hid
     * nearly every pickup. Per level, the Very Compressed count went up, and that is Rust.
     */
    public Optional<String> observe(List<Item> inventory, MineCatalog catalog) {
        Map<String, Integer> counts = new HashMap<>();
        Map<String, String> resourceOf = new HashMap<>();
        Map<String, String> tags = new HashMap<>();
        for (Item item : inventory) {
            resourceName(item.name(), item.lore(), catalog).ifPresent(r -> {
                String level = itemKey(item.name());
                counts.merge(level, Math.max(0, item.count()), Integer::sum);
                resourceOf.put(level, r);
                worldTag(item.lore()).ifPresent(tag -> tags.putIfAbsent(r, tag));
            });
        }

        Map<String, Integer> previous = baseline;
        Map<String, String> previousResources = baselineResources;
        baseline = counts;
        baselineResources = resourceOf;
        if (previous == null) {
            return Optional.empty();
        }

        // A level of a resource going down in the same scan means a stack was compressed, by the
        // player or the server, not mined, so that resource's rise this scan says nothing. Without
        // this, compressing a Rust stack while mining Woodland Copper would name Rust.
        java.util.Set<String> compressedNow = new java.util.HashSet<>();
        for (Map.Entry<String, Integer> e : previous.entrySet()) {
            String r = previousResources.get(e.getKey());
            if (r != null && counts.getOrDefault(e.getKey(), 0) < e.getValue()) {
                compressedNow.add(r);
            }
        }

        String best = null;
        int bestGain = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (compressedNow.contains(resourceOf.get(e.getKey()))) {
                continue;
            }
            int gain = e.getValue() - previous.getOrDefault(e.getKey(), 0);
            if (gain > bestGain) {
                bestGain = gain;
                best = resourceOf.get(e.getKey());
            }
        }
        if (best != null && tags.containsKey(best)) {
            lastWorldTag = Optional.of(tags.get(best));
        }
        return Optional.ofNullable(best);
    }

    /** The item's name as shown, colours and sprites removed: one key per compression level. */
    private static String itemKey(String name) {
        return cleanName(name).toLowerCase(java.util.Locale.ROOT);
    }

    /** Glyphs in front of a name, like the diamond before "Very Compressed Rust". */
    private static final Pattern LEADING_GLYPHS = Pattern.compile("^[^\\p{L}\\p{N}\\[]+");

    /** Colour codes, sprites and leading glyphs removed, spaces collapsed. */
    private static String cleanName(String name) {
        String s = SPRITE.matcher(FORMATTING.matcher(name).replaceAll("")).replaceAll("")
                .replaceAll("\\s+", " ").trim();
        return LEADING_GLYPHS.matcher(s).replaceFirst("").trim();
    }

    /** Forget the inventory, so a relog or world change is not read as a pickup. */
    public void reset() {
        baseline = null;
        lastWorldTag = Optional.empty();
    }
}
