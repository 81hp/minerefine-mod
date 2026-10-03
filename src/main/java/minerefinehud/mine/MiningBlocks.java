package minerefinehud.mine;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Which mine a block belongs to, so the mine is recognised just by mining it.
 *
 * Nothing the server shows names the mine: the sidebar is all stats, special tools such as
 * "Khan's Spoil 3" carry no mine name, and the action bar shows only a sprite of the block,
 * "[block/cobblestone]". The resource pack is global, so each texture belongs to exactly one mine,
 * and the mapping only has to be found once per mine.
 *
 * Two ways to find it:
 *
 *   Learned. A shop prints the exact balance, "Rubble x919.4M (1,570,112,004)". The action bar
 *   prints the same balance rounded, "1.57B[block/cobblestone]". When exactly one shop balance
 *   matches a mining total, that block is that mine's. This survives any naming at all, which
 *   matters because Rubble is mined as cobblestone.
 *
 *   By name. "block/suspicious_sand" is Suspicious Sand. Only an exact match counts, so this
 *   never guesses; it just saves a shop visit where the server's naming is obvious.
 *
 * Learned beats by-name, and learned mappings are meant to be persisted by the caller.
 *
 * An icon is not always one mine's. Woodland Copper and Rust can show the same block, and a single
 * icon-to-mine entry then flips between them with every shop visit, taking the panel along. Once
 * a resource pickup proves an icon belongs to two mines, it is marked shared: it no longer names a
 * mine by itself, is never learned again, and the resource picked up decides instead.
 */
public final class MiningBlocks {

    public enum How { LEARNED, BY_NAME }

    public record Match(String mine, How how) {}

    /**
     * A shop balance and a mining total this far apart in time are not compared. It was ten
     * minutes, long enough to mine Zircon, teleport to Debris and open its shop; the Debris
     * balance happened to be within 1% of the Zircon total, and Zircon's icon was relabelled
     * Debris. Two minutes still covers mining a block and then opening that mine's shop.
     */
    static final long WINDOW_MS = 2 * 60 * 1000L;

    /**
     * Allowed gap between the exact and the rounded figure. The action bar shows three
     * significant figures, under 1% error, and a few blocks may be mined between the two.
     */
    private static final double TOLERANCE = 0.01;

    private record Snapshot(long amount, long at) {}

    private record Mined(String sprite, long amount, long at) {}

    private final Map<String, String> learned = new LinkedHashMap<>();
    private final java.util.Set<String> shared = new java.util.LinkedHashSet<>();
    private final Map<String, Snapshot> shopBalances = new HashMap<>();
    private Mined lastMined;

    /** The mine for a block sprite, if learned or obvious from its name. */
    public Optional<Match> lookup(String sprite, MineCatalog catalog) {
        if (sprite == null || sprite.isBlank() || shared.contains(sprite)) {
            return Optional.empty();
        }
        String mine = learned.get(sprite);
        if (mine != null) {
            return Optional.of(new Match(mine, How.LEARNED));
        }
        String block = letters(blockName(sprite));
        for (Mine m : catalog.mines()) {
            if (!block.isEmpty() && block.equals(letters(m.name()))) {
                return Optional.of(new Match(catalog.serverName(m.name()), How.BY_NAME));
            }
        }
        return Optional.empty();
    }

    /** A mining total from the action bar. Returns true if it taught a new mapping. */
    public boolean onMining(String sprite, long amount, long nowMs) {
        lastMined = new Mined(sprite, amount, nowMs);
        String match = null;
        for (Map.Entry<String, Snapshot> e : shopBalances.entrySet()) {
            Snapshot s = e.getValue();
            if (nowMs - s.at() <= WINDOW_MS && close(s.amount(), amount)) {
                if (match != null) {
                    return false;   // two currencies fit, so neither is trusted
                }
                match = e.getKey();
            }
        }
        return match != null && claim(sprite, match);
    }

    /** An exact balance from a shop. Returns true if it taught a new mapping. */
    public boolean onShopBalance(String currency, long amount, long nowMs) {
        shopBalances.put(currency, new Snapshot(amount, nowMs));
        if (lastMined == null || nowMs - lastMined.at() > WINDOW_MS || !close(amount, lastMined.amount())) {
            return false;
        }
        // Rule out the same total fitting another currency's balance just as well.
        for (Map.Entry<String, Snapshot> e : shopBalances.entrySet()) {
            if (!e.getKey().equals(currency) && nowMs - e.getValue().at() <= WINDOW_MS
                    && close(e.getValue().amount(), lastMined.amount())) {
                return false;
            }
        }
        return claim(lastMined.sprite(), currency);
    }

    /**
     * Learns an unknown block from balances already known, without a shop visit.
     *
     * On this server mined resources are a balance, not inventory items, so pickups almost never
     * teach a block, and before this every new mine sat unrecognised (panel and progress bar
     * both frozen) until its shop was opened at the right moment. But the balance of the mine
     * being worked on is usually known already, from an earlier shop visit, and mining only adds
     * to it. So an unknown block whose total sits just above exactly one known balance is that
     * mine's block. Three guards:
     *
     *   - a currency whose mine already has a block is skipped: its block would have been
     *     recognised, so it is not the one being mined;
     *   - the total may be at most 10% above the balance (a long session), or 1% below it
     *     (the action bar rounds to three figures);
     *   - two candidates means nothing is learned.
     *
     * A later shop visit overrides whatever this learned, so a rare wrong guess does not stick.
     *
     * @param known balance per currency, as last seen
     * @return true if a mapping was learned
     */
    public boolean inferFromBalances(String sprite, long total, Map<String, Long> known) {
        if (sprite == null || sprite.isBlank() || learned.containsKey(sprite) || shared.contains(sprite)
                || total <= 0L) {
            return false;
        }
        java.util.Set<String> taken = new java.util.HashSet<>();
        for (String mine : learned.values()) {
            taken.add(mine.toLowerCase(Locale.ROOT).trim());
        }
        String match = null;
        for (Map.Entry<String, Long> e : known.entrySet()) {
            long balance = e.getValue() == null ? 0L : e.getValue();
            if (balance <= 0L || taken.contains(e.getKey().toLowerCase(Locale.ROOT).trim())) {
                continue;
            }
            if (total >= balance - balance / 100L && total <= balance + balance / 10L) {
                if (match != null) {
                    return false;
                }
                match = e.getKey();
            }
        }
        return match != null && learn(sprite, match);
    }

    /**
     * Learns from a resource pickup at the same moment as a mining total: picking up Zircon while
     * the action bar shows "[block/x]" means x is Zircon's block. Returns true if it was new.
     */
    public boolean teach(String sprite, String mine) {
        if (sprite == null || sprite.isBlank() || mine == null || mine.isBlank()) {
            return false;
        }
        // Only fills a gap, never overwrites. A pickup is weak evidence: logs left lying at an
        // axe mine are picked up after walking on, and one such pickup alongside Sniffer Egg's
        // block relabelled that block as Scaffold. A shop balance, exact to the block, may still
        // correct a mapping; a pickup may not.
        if (learned.containsKey(sprite) || shared.contains(sprite) || hasIcon(mine)) {
            return false;
        }
        return learn(sprite, mine);
    }

    /**
     * Pickups of another mine's resource needed before an icon counts as shared, and the time
     * they must span. A pile of leftover logs from the mine just left is picked up in a second or
     * two; mining a mine that really shows this icon keeps disagreeing block after block.
     */
    static final int SHARED_AFTER_PICKUPS = 3;
    static final long SHARED_AFTER_MS = 20_000L;

    private record Conflict(String mine, long firstAt, int count) {}

    private final Map<String, Conflict> conflicts = new HashMap<>();

    /**
     * A resource picked up while this icon was on screen. Teaches the icon when it is unknown.
     * When it is known as a different mine, that is counted, and once it keeps happening the
     * icon is marked shared.
     *
     * @param pickupMine the resource's name, which is its mine's server spelling
     * @return true if what is learned changed, so the caller knows to save
     */
    public boolean onPickup(String sprite, String pickupMine, MineCatalog catalog, long nowMs) {
        if (sprite == null || sprite.isBlank() || pickupMine == null || pickupMine.isBlank()
                || shared.contains(sprite)) {
            return false;
        }
        Optional<Match> known = lookup(sprite, catalog);
        if (known.isEmpty()) {
            return teach(sprite, pickupMine);
        }
        if (catalog.serverName(known.get().mine()).equalsIgnoreCase(catalog.serverName(pickupMine))) {
            conflicts.remove(sprite);
            return false;
        }
        Conflict c = conflicts.get(sprite);
        if (c == null || !c.mine().equalsIgnoreCase(pickupMine)) {
            c = new Conflict(pickupMine, nowMs, 0);
        }
        c = new Conflict(c.mine(), c.firstAt(), c.count() + 1);
        if (c.count() >= SHARED_AFTER_PICKUPS && nowMs - c.firstAt() >= SHARED_AFTER_MS) {
            conflicts.remove(sprite);
            return markShared(sprite);
        }
        conflicts.put(sprite, c);
        return false;
    }

    /** Forgets one icon, so it is learned again from scratch. For /mrhud forget. */
    public boolean forget(String sprite) {
        if (sprite == null) {
            return false;
        }
        boolean wasShared = shared.remove(sprite);
        return learned.remove(sprite) != null || wasShared;
    }

    /**
     * Marks an icon as shown by more than one mine, because a resource from another mine was
     * picked up while mining it. Drops what it was learned as. Returns true if it was new.
     */
    public boolean markShared(String sprite) {
        if (sprite == null || sprite.isBlank()) {
            return false;
        }
        learned.remove(sprite);
        return shared.add(sprite);
    }

    public boolean isShared(String sprite) {
        return sprite != null && shared.contains(sprite);
    }

    public java.util.Set<String> shared() {
        return java.util.Set.copyOf(shared);
    }

    public void importShared(java.util.Collection<String> saved) {
        if (saved != null) {
            for (String sprite : saved) {
                if (sprite != null && !sprite.isBlank()) {
                    shared.add(sprite);
                    learned.remove(sprite);
                }
            }
        }
    }

    private boolean hasIcon(String mine) {
        String m = mine.toLowerCase(Locale.ROOT).trim();
        return learned.values().stream().anyMatch(v -> v.toLowerCase(Locale.ROOT).trim().equals(m));
    }

    public Map<String, String> learned() {
        return Map.copyOf(learned);
    }

    public void importLearned(Map<String, String> saved) {
        if (saved != null) {
            saved.forEach((sprite, mine) -> {
                if (sprite != null && mine != null && !mine.isBlank() && !shared.contains(sprite)) {
                    learned.put(sprite, mine);
                }
            });
        }
    }

    private boolean learn(String sprite, String mine) {
        return !mine.equals(learned.put(sprite, mine));
    }

    /**
     * Learns from an exact shop balance, the strongest evidence there is, and keeps one icon per
     * mine: any other icon filed under this mine is dropped. Each mine shows one icon, so a second
     * one is a mistake from earlier, and dropping it lets that icon be learned again correctly.
     * Learning Debris's real icon this way frees Zircon's, which had been filed under Debris.
     */
    private boolean claim(String sprite, String mine) {
        if (shared.contains(sprite)) {
            return false;
        }
        String m = mine.toLowerCase(Locale.ROOT).trim();
        boolean dropped = learned.entrySet().removeIf(e -> !e.getKey().equals(sprite)
                && e.getValue().toLowerCase(Locale.ROOT).trim().equals(m));
        return learn(sprite, mine) || dropped;
    }

    private static boolean close(long exact, long shown) {
        long big = Math.max(exact, shown);
        return big == 0L || Math.abs(exact - shown) <= big * TOLERANCE;
    }

    /** "block/amethyst_block" -> "amethyst". */
    private static String blockName(String sprite) {
        String name = sprite.substring(sprite.lastIndexOf('/') + 1);
        return name.endsWith("_block") ? name.substring(0, name.length() - "_block".length()) : name;
    }

    private static String letters(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z]", "");
    }
}
