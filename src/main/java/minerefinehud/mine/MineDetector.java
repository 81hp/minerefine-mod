package minerefinehud.mine;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Decides which mine the player is at, from every source the client can see.
 *
 * Sources, strongest first:
 *
 *   1. The block last mined, via {@link MiningBlocks}, or the resource it dropped. Mining a block
 *      is the strongest evidence there is of which mine the player is standing in, and on this
 *      server it is the main source.
 *   2. The MineRefine item in hand, e.g. "[Debris Shovel] [VI]". Players mine with the tool for
 *      the mine they are in, so this is right far more often than not.
 *   3. The shop most recently opened. Shops sit at their mine, so this still works when the
 *      player is holding something unrelated.
 *
 * On-screen text (sidebar, action bar) is deliberately not a source. This server never names
 * the mine there, and the spreadsheet has mines called Gear, Wind, Ice, Robe and Chamber: plain
 * words that turn up in messages. Text matching only ever produced wrong answers, Darkwater's
 * "Prismarine" inside a texture id at Ocean, and "Gear" after buying gear at Chamber.
 *
 * A name that comes from an item or the shop is trusted even when the price data has never heard
 * of it. The calculator's data is missing whole worlds, and "Nether Quartz, no price data" is far
 * more useful than "unknown", which looks like the mod is broken.
 */
public final class MineDetector {

    public enum Source { MINING, HELD_ITEM, SHOP, NONE }

    /**
     * @param mine   catalog entry, or a placeholder with no prices when only the name is known
     * @param source where it came from, shown by the debug command
     */
    public record Detection(Optional<Mine> mine, Source source) {

        public static final Detection NONE = new Detection(Optional.empty(), Source.NONE);

        public boolean found() {
            return mine.isPresent();
        }
    }

    private MineDetector() {
    }

    public static Detection detect(MineCatalog catalog,
                                   Optional<String> minedBlockMine,
                                   Optional<String> heldItemMine,
                                   Optional<String> shopMine) {
        return detect(catalog, minedBlockMine, heldItemMine, shopMine, java.util.Set.of());
    }

    /**
     * @param ruledOut mines that cannot be the one being mined, as any spelling. While the player
     *                 mines a block nobody has placed yet, that is every mine whose own block is
     *                 known: its block would have been recognised. Without this a Suspicious Sand
     *                 chestplate in hand held the panel on Suspicious Sand while mining Marrow.
     */
    public static Detection detect(MineCatalog catalog,
                                   Optional<String> minedBlockMine,
                                   Optional<String> heldItemMine,
                                   Optional<String> shopMine,
                                   java.util.Set<String> ruledOut) {

        Optional<Mine> fromMining = byItemName(catalog, minedBlockMine);
        if (fromMining.isPresent()) {
            return new Detection(fromMining, Source.MINING);
        }

        Optional<Mine> fromHeld = byItemName(catalog, heldItemMine).filter(m -> !isRuledOut(catalog, m, ruledOut));
        if (fromHeld.isPresent()) {
            return new Detection(fromHeld, Source.HELD_ITEM);
        }

        Optional<Mine> fromShop = byItemName(catalog, shopMine).filter(m -> !isRuledOut(catalog, m, ruledOut));
        if (fromShop.isPresent()) {
            return new Detection(fromShop, Source.SHOP);
        }

        return Detection.NONE;
    }

    private static boolean isRuledOut(MineCatalog catalog, Mine candidate, java.util.Set<String> ruledOut) {
        for (String name : ruledOut) {
            Optional<Mine> other = catalog.exactly(name);
            String a = other.map(Mine::name).orElse(name);
            if (a.equalsIgnoreCase(candidate.name())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The mined-block evidence, unless a shop has been opened since. Then the shop is the fresher
     * answer: walking from Sniffer Egg to Debris, finding the pickaxe is "the wrong tool" and
     * opening the Debris shop to buy a shovel left the panel on Sniffer Egg, because the last
     * block mined anywhere outranked a shop opened afterwards. The next block mined takes over
     * again.
     *
     * @param minedAt when the last mining line was seen, 0 for never
     * @param shopAt  when a shop was last opened, 0 for never
     */
    public static Optional<String> stillMining(Optional<String> minedBlockMine, long minedAt, long shopAt) {
        return shopAt > minedAt ? Optional.empty() : minedBlockMine;
    }

    /**
     * Which mine a mined block belongs to: the resource picked up with this same icon on screen,
     * else what the icon is known as. The resource is named after its mine, so it wins; an icon
     * can be shared, Woodland Copper's with Rust's. A pickup made under another icon is from the
     * block before, possibly at the mine just left, and says nothing about this one.
     *
     * @param pickupWithThisIcon the resource last picked up, only if this icon was on screen then
     */
    public static Optional<String> minedMine(Optional<String> blockMine, Optional<String> pickupWithThisIcon) {
        return pickupWithThisIcon.filter(m -> !m.isBlank()).or(() -> blockMine);
    }

    /**
     * The mine last seen being mined, updated for one more mined block.
     *
     * A known block names its mine. An unknown block cannot name one, but it does prove the
     * player is no longer mining the remembered mine, so that is dropped rather than kept: kept,
     * it outranked everything else and the panel sat on Lodestone while the player mined Wind,
     * until Wind's block happened to be learned. A resource pickup at the same moment is the
     * exception, since that names the mine by itself and teaches the block.
     *
     * @param blockMine      the mine this block is known to belong to, if any
     * @param pickupJustNow  a resource pickup happened alongside this block
     */
    public static Optional<String> afterMining(Optional<String> previous, Optional<String> blockMine,
                                               boolean pickupJustNow) {
        if (blockMine.isPresent()) {
            return blockMine;
        }
        return pickupJustNow ? previous : Optional.empty();
    }

    /**
     * The mine a shop belongs to, from the mine names on its priced entries.
     *
     * Majority rather than first, because a container screen also shows the player's own
     * inventory, and a tool from another mine in there must not outvote the shop's own rows.
     */
    public static Optional<String> mostCommon(List<String> mineNames) {
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        for (String name : mineNames) {
            if (name != null && !name.isBlank()) {
                counts.merge(name.trim(), 1L, Long::sum);
            }
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey);
    }

    /**
     * Item and shop names are the mine name exactly, so a catalog miss means missing data rather
     * than a misread, and the name is kept as a placeholder instead of being thrown away.
     */
    private static Optional<Mine> byItemName(MineCatalog catalog, Optional<String> name) {
        if (name.isEmpty() || name.get().isBlank()) {
            return Optional.empty();
        }
        Optional<Mine> known = catalog.detectFrom(name.get());
        if (known.isPresent()) {
            return known;
        }
        return Optional.of(new Mine(null, "mine", "", name.get().trim(), Map.of(), null));
    }
}
