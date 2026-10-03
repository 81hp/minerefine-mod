package minerefinehud.mine;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
 * An icon is not always one mine's. Woodland Copper and Rust both show deepslate copper ore (Rust's
 * resource item is that block), and a single icon-to-mine entry flipped between them with every
 * shop visit, taking the panel along: open the Rust shop, walk back to Woodland Copper, and it
 * still said Rust. An icon two mines have claimed is kept as shared, with the mines that use it,
 * and each block is then decided among those (see {@link #resolveShared}).
 */
public final class MiningBlocks {

    public enum How { LEARNED, BY_NAME, SHARED }

    /**
     * Icons known to belong to more than one mine, seen in game. Learned ones are added to these.
     * Rust's resource item is deepslate copper ore, Woodland Copper's block.
     */
    public static final Map<String, List<String>> KNOWN_SHARED = Map.of(
            "block/deepslate_copper_ore", List.of("Woodland Copper", "Rust"));

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
    /** Shared icon to the mines that use it, server spelling. */
    private final Map<String, Set<String>> shared = new LinkedHashMap<>();
    private final Map<String, Snapshot> shopBalances = new HashMap<>();
    private Mined lastMined;

    /** The mine for a block sprite, if learned or obvious from its name. */
    public Optional<Match> lookup(String sprite, MineCatalog catalog) {
        if (sprite == null || sprite.isBlank() || shared.containsKey(sprite)) {
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
        if (sprite == null || sprite.isBlank() || learned.containsKey(sprite) || shared.containsKey(sprite)
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
        if (learned.containsKey(sprite) || shared.containsKey(sprite) || hasIcon(mine)) {
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
                || shared.containsKey(sprite)) {
            return shared.containsKey(sprite) && addCandidate(sprite, pickupMine);
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
            return markShared(sprite, List.of(known.get().mine(), pickupMine));
        }
        conflicts.put(sprite, c);
        return false;
    }

    /** Forgets one icon, so it is learned again from scratch. For /mrhud forget. */
    public boolean forget(String sprite) {
        if (sprite == null) {
            return false;
        }
        boolean wasShared = shared.remove(sprite) != null;
        return learned.remove(sprite) != null || wasShared;
    }

    /**
     * Marks an icon as shown by these mines, plus any it already had. Drops what it was learned
     * as. Returns true if anything changed.
     */
    public boolean markShared(String sprite, java.util.Collection<String> mines) {
        if (sprite == null || sprite.isBlank()) {
            return false;
        }
        boolean changed = learned.remove(sprite) != null;
        if (!shared.containsKey(sprite)) {
            shared.put(sprite, new LinkedHashSet<>());
            changed = true;
        }
        if (mines != null) {
            for (String mine : mines) {
                changed |= addCandidate(sprite, mine);
            }
        }
        return changed;
    }

    private boolean addCandidate(String sprite, String mine) {
        if (mine == null || mine.isBlank()) {
            return false;
        }
        Set<String> mines = shared.get(sprite);
        for (String m : mines) {
            if (m.equalsIgnoreCase(mine.trim())) {
                return false;
            }
        }
        return mines.add(mine.trim());
    }

    public boolean isShared(String sprite) {
        return sprite != null && shared.containsKey(sprite);
    }

    /** Shared icon to the mines that use it. */
    public Map<String, List<String>> shared() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        shared.forEach((sprite, mines) -> out.put(sprite, List.copyOf(mines)));
        return out;
    }

    public void importShared(Map<String, List<String>> saved) {
        if (saved != null) {
            saved.forEach(this::markShared);
        }
    }

    /**
     * Which of a shared icon's mines this block is, from its balance. The total on the action bar
     * is that mine's balance, so the mine whose last known balance it continues is the one: at
     * most 10% above it, or 1% below for the rounding. Two mines' balances are practically never
     * that close. Empty while none of their balances is known this session.
     *
     * @param known balance per currency, as last seen
     */
    public Optional<String> sharedByBalance(String sprite, long total, Map<String, Long> known,
                                            MineCatalog catalog) {
        Set<String> mines = sprite == null ? null : shared.get(sprite);
        if (mines == null || mines.isEmpty()) {
            return Optional.empty();
        }
        String best = null;
        long bestGap = Long.MAX_VALUE;
        for (String mine : mines) {
            Long balance = balanceOf(known, mine, catalog);
            if (balance == null || balance <= 0L) {
                continue;
            }
            if (total >= balance - balance / 100L && total <= balance + balance / 10L) {
                long gap = Math.abs(total - balance);
                if (gap < bestGap) {
                    best = mine;
                    bestGap = gap;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Which of a shared icon's mines this block probably is, when its balance cannot say: the tool
     * in hand if it is one of theirs (each mine wants its own tool), else the mine last mined if
     * it is. Good enough for the panel, never for filing a balance: a wrong guess filed would make
     * the balance check agree with it from then on.
     */
    public Optional<String> sharedByHint(String sprite, Optional<String> toolMine, Optional<String> previous,
                                         MineCatalog catalog) {
        Set<String> mines = sprite == null ? null : shared.get(sprite);
        if (mines == null) {
            return Optional.empty();
        }
        for (Optional<String> hint : List.of(toolMine, previous)) {
            if (hint.isPresent()) {
                for (String mine : mines) {
                    if (sameMine(mine, hint.get(), catalog)) {
                        return Optional.of(mine);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Long balanceOf(Map<String, Long> known, String mine, MineCatalog catalog) {
        for (Map.Entry<String, Long> e : known.entrySet()) {
            if (sameMine(e.getKey(), mine, catalog)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static boolean sameMine(String a, String b, MineCatalog catalog) {
        return catalog.serverName(a).equalsIgnoreCase(catalog.serverName(b));
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
                if (sprite != null && mine != null && !mine.isBlank() && !shared.containsKey(sprite)) {
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
     *
     * An icon already learned as another mine is not taken over but marked shared by both: the
     * Woodland Copper shop and then the Rust shop each proved deepslate copper ore theirs. A wrong
     * earlier guess ends up shared too, which costs nothing, as each block is still decided by
     * its balance.
     */
    private boolean claim(String sprite, String mine) {
        if (shared.containsKey(sprite)) {
            return addCandidate(sprite, mine);
        }
        String was = learned.get(sprite);
        if (was != null && !was.equalsIgnoreCase(mine.trim())) {
            return markShared(sprite, List.of(was, mine));
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
