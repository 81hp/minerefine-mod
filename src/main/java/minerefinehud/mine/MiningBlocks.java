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
     * Blocks whose mine is known from the server itself, never relearned. Verified with a player
     * 2026-11-02: in the Sift, Rosewood is cherry logs and stripped cherry logs, Ichor shroomlight
     * and Jade emerald blocks, each used by no other mine there. Learned, they ended up shared
     * (cherry logs by Ichor and Rosewood, shroomlight by Jade and Ichor), and as nothing ever
     * takes a mine off a shared icon, Rosewood showed Ichor and Ichor showed Jade for good.
     */
    public static final Map<String, String> VERIFIED = Map.of(
            "block/cherry_log", "Rosewood",
            "block/stripped_cherry_log", "Rosewood",
            "block/shroomlight", "Ichor",
            "block/emerald_block", "Jade");

    /** Whether this is a mine whose every block is in {@link #VERIFIED}: no other icon is its. */
    static boolean verifiedMine(String mine) {
        return mine != null && VERIFIED.values().stream().anyMatch(v -> v.equalsIgnoreCase(mine.trim()));
    }

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

    {
        applyVerified();
    }

    /**
     * Files every {@link #VERIFIED} block under its own mine alone, over whatever was learned, and
     * takes those mines off every other icon: all their blocks are known, so any other was filed
     * while some other mine was mined.
     */
    private void applyVerified() {
        learned.entrySet().removeIf(e -> !VERIFIED.containsKey(e.getKey()) && verifiedMine(e.getValue()));
        for (var it = shared.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            e.getValue().removeIf(MiningBlocks::verifiedMine);
            if (e.getValue().size() <= 1) {
                it.remove();
                if (e.getValue().size() == 1) {
                    learned.putIfAbsent(e.getKey(), e.getValue().iterator().next());
                }
            }
        }
        VERIFIED.forEach((sprite, mine) -> {
            shared.remove(sprite);
            learned.put(sprite, mine);
        });
    }

    /** The mine for a block sprite, if learned or obvious from its name. */
    public Optional<Match> lookup(String sprite, MineCatalog catalog) {
        String verified = sprite == null ? null : VERIFIED.get(sprite);
        if (verified != null) {
            return Optional.of(new Match(verified, How.LEARNED));
        }
        if (sprite == null || sprite.isBlank() || shared.containsKey(sprite)) {
            return Optional.empty();
        }
        String mine = learned.get(sprite);
        if (mine != null) {
            return Optional.of(new Match(mine, How.LEARNED));
        }
        return byName(sprite, catalog).map(m -> new Match(m, How.BY_NAME));
    }

    /** The mine whose name is this block's, "block/diorite" is Diorite, server spelling. */
    private static Optional<String> byName(String sprite, MineCatalog catalog) {
        if (sprite == null || catalog == null) {
            return Optional.empty();
        }
        String block = letters(blockName(sprite));
        for (Mine m : catalog.mines()) {
            if (!block.isEmpty() && block.equals(letters(m.name()))) {
                return Optional.of(catalog.serverName(m.name()));
            }
        }
        return Optional.empty();
    }

    private java.util.function.Supplier<MineCatalog> names = () -> null;

    /**
     * The price data, for icons named after their mine. With it, a shop proving such an icon to
     * be another mine's marks it shared by both instead of taking it over: a new area's mine
     * showing diorite must not turn the Aether's Diorite into that mine.
     */
    public void nameMinesFrom(java.util.function.Supplier<MineCatalog> catalog) {
        names = catalog == null ? () -> null : catalog;
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

    /** Shops that showed a balance of 0, and when: mines not started yet. */
    private final Map<String, Long> newShops = new LinkedHashMap<>();

    /**
     * Any balance a shop shows, exact or rounded, or -1 for one that is not the shop's own mine's
     * (a fragment shop's 0 says nothing). A balance of 0 is a mine not started yet, and its first
     * block cannot be matched by balance: 0 matches no total, so a new mine's block went
     * unrecognised until the shop was opened again (see {@link #inferFromNewShop}).
     */
    public void onShopSeen(String currency, long amount, long nowMs) {
        if (currency == null || currency.isBlank() || amount < 0L) {
            return;
        }
        String c = currency.trim();
        newShops.keySet().removeIf(k -> k.equalsIgnoreCase(c));
        if (amount == 0L) {
            newShops.put(c, nowMs);
        }
    }

    /**
     * Learns a block no mine here has from the shop of a mine not started yet, read at most
     * {@link #WINDOW_MS} ago: reading a new mine's shop and then mining an unknown block is
     * starting that mine. Only when exactly one such shop was read, and its mine has no block yet.
     * An icon known only as mines of other areas is shared with it rather than taken over.
     *
     * @param notHere mines that cannot be the one being mined, from where the player is
     * @return the mine learned
     */
    public Optional<String> inferFromNewShop(String sprite, long nowMs, MineCatalog catalog,
                                             java.util.function.Predicate<String> notHere) {
        if (sprite == null || sprite.isBlank() || VERIFIED.containsKey(sprite)) {
            return Optional.empty();
        }
        Set<String> sharedBy = shared.get(sprite);
        Optional<Match> known = sharedBy == null ? lookup(sprite, catalog) : Optional.empty();
        if (sharedBy != null ? !sharedBy.stream().allMatch(notHere)
                : known.isPresent() && !notHere.test(known.get().mine())) {
            return Optional.empty();
        }
        String match = null;
        for (Map.Entry<String, Long> e : newShops.entrySet()) {
            if (nowMs - e.getValue() > WINDOW_MS || hasIcon(e.getKey()) || isCandidate(e.getKey())
                    || notHere.test(e.getKey())) {
                continue;
            }
            if (match != null) {
                return Optional.empty();   // two new mines' shops read: which one is unclear
            }
            match = e.getKey();
        }
        if (match == null) {
            return Optional.empty();
        }
        newShops.remove(match);
        if (sharedBy != null) {
            addCandidate(sprite, match);
        } else if (known.isPresent() && known.get().how() == How.LEARNED && isGuess(known.get().mine())) {
            learn(sprite, match);   // a guess for a mine of another area: replaced, not kept
        } else if (known.isPresent()) {
            markShared(sprite, List.of(known.get().mine(), match));
        } else {
            learn(sprite, match);
        }
        return Optional.of(match);
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
     *   - the total may be at most 3% above the balance, or 1% below it (the action bar
     *     rounds to three figures);
     *   - two candidates means nothing is learned.
     *
     * A later shop visit overrides whatever this learned, so a rare wrong guess does not stick.
     *
     * It is still a guess, and with a hundred or so balances saved one of them often sits near a
     * new mine's total by chance: Shale's first blocks landed just above the Wood balance, and
     * from then on Shale's icon said Wood. So the window above the balance is narrow (the guess
     * is tried from the very first block, when the two are a few blocks apart), a mine known to
     * be mined somewhere else is skipped, and the guess is filed in lower case, the way balances
     * are keyed, which is what marks it as a guess (see {@link #isGuess}).
     *
     * @param known     balance per currency, as last seen
     * @param elsewhere mines known to be mined far from where the player is now
     * @return true if a mapping was learned
     */
    public boolean inferFromBalances(String sprite, long total, Map<String, Long> known,
                                     java.util.function.Predicate<String> elsewhere) {
        // An earlier guess for a mine that cannot be here is no reason to stop: Siftite's block
        // had been guessed as Blackstone, a Sculk mine, so in the Sift it stayed unknown and the
        // bar stood still until a shop visit replaced the guess.
        // A block proven for a mine of another area is shared with the one matched here instead.
        String was = sprite == null ? null : learned.get(sprite);
        boolean otherArea = was != null && elsewhere.test(was);
        if (sprite == null || sprite.isBlank() || VERIFIED.containsKey(sprite) || (was != null && !otherArea) || shared.containsKey(sprite)
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
            String currency = e.getKey().toLowerCase(Locale.ROOT).trim();
            if (balance <= 0L || taken.contains(currency) || currency.endsWith(" fragment")
                    || elsewhere.test(currency)) {
                continue;
            }
            if (total >= balance - balance / 100L && total <= balance + balance * 3L / 100L) {
                if (match != null) {
                    return false;
                }
                match = currency;
            }
        }
        if (match == null || match.equalsIgnoreCase(was == null ? "" : was.trim())) {
            return false;
        }
        return was != null && !isGuess(was) ? markShared(sprite, List.of(was, match)) : learn(sprite, match);
    }

    public boolean inferFromBalances(String sprite, long total, Map<String, Long> known) {
        return inferFromBalances(sprite, total, known, mine -> false);
    }

    /**
     * Whether a mine name was filed by {@link #inferFromBalances}: those are spelt in lower case,
     * as balances are keyed, while shops and pickups spell a mine the way the server does. Saved
     * data from before this was a rule says the same, as only that guess ever filed lower case.
     */
    public static boolean isGuess(String mine) {
        return mine != null && mine.chars().anyMatch(Character::isLetter)
                && mine.equals(mine.toLowerCase(Locale.ROOT));
    }

    /**
     * Learns from a resource pickup at the same moment as a mining total: picking up Zircon while
     * the action bar shows "[block/x]" means x is Zircon's block. Returns true if it was new.
     */
    public boolean teach(String sprite, String mine) {
        if (sprite == null || sprite.isBlank() || mine == null || mine.isBlank() || VERIFIED.containsKey(sprite)) {
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
        return onPickup(sprite, pickupMine, catalog, nowMs, mine -> false);
    }

    /**
     * {@link #onPickup(String, String, MineCatalog, long)}, where an icon known as a mine
     * {@code notHere} rules out is marked shared by the first pickup: that mine is in another
     * world, so this resource cannot be a leftover from it.
     */
    public boolean onPickup(String sprite, String pickupMine, MineCatalog catalog, long nowMs,
                            java.util.function.Predicate<String> notHere) {
        if (sprite != null && VERIFIED.containsKey(sprite)) {
            return false;
        }
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
        // A guess from a close balance gives way to the resource itself at once: a user mining
        // Rosewood kept seeing "sulfur", guessed for one of Rosewood's blocks.
        if (isGuess(known.get().mine()) && !notHere.test(pickupMine)) {
            conflicts.remove(sprite);
            return learn(sprite, pickupMine.trim());
        }
        if (notHere.test(known.get().mine()) && !notHere.test(pickupMine)) {
            conflicts.remove(sprite);
            // Shared even when the icon was only known by its name: diorite is still Diorite's
            // block in the Aether.
            boolean changed = markShared(sprite, List.of(known.get().mine(), pickupMine));
            dropGuesses(sprite);
            return changed;
        }
        Conflict c = conflicts.get(sprite);
        if (c == null || !c.mine().equalsIgnoreCase(pickupMine)) {
            c = new Conflict(pickupMine, nowMs, 0);
        }
        c = new Conflict(c.mine(), c.firstAt(), c.count() + 1);
        if (c.count() >= SHARED_AFTER_PICKUPS && nowMs - c.firstAt() >= SHARED_AFTER_MS) {
            conflicts.remove(sprite);
            boolean changed = markShared(sprite, List.of(known.get().mine(), pickupMine));
            dropGuesses(sprite);   // disagreeing this long with a guess: the guess was wrong
            return changed;
        }
        conflicts.put(sprite, c);
        return false;
    }

    /** Forgets one icon, so it is learned again from scratch. For /mrhud forget. */
    public boolean forget(String sprite) {
        if (sprite == null) {
            return false;
        }
        if (VERIFIED.containsKey(sprite)) {
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
        if (sprite == null || sprite.isBlank() || VERIFIED.containsKey(sprite)) {
            return false;
        }
        String learnedBefore = learned.get(sprite);
        Set<String> sharedBefore = shared.containsKey(sprite) ? new LinkedHashSet<>(shared.get(sprite)) : null;
        learned.remove(sprite);
        shared.computeIfAbsent(sprite, s -> new LinkedHashSet<>());
        if (mines != null) {
            for (String mine : mines) {
                addCandidate(sprite, mine);
            }
        }
        // A verified mine is never added, so one mine may be all there is: then it is just its.
        // None at all, every one offered being verified: the icon stays as it was.
        Set<String> left = shared.get(sprite);
        if (left.size() == 1) {
            shared.remove(sprite);
            learned.put(sprite, left.iterator().next());
        } else if (left.isEmpty()) {
            shared.remove(sprite);
            if (learnedBefore != null) {
                learned.put(sprite, learnedBefore);
            }
        }
        return !java.util.Objects.equals(learnedBefore, learned.get(sprite))
                || !java.util.Objects.equals(sharedBefore, shared.get(sprite));
    }

    private boolean addCandidate(String sprite, String mine) {
        if (mine == null || mine.isBlank() || verifiedMine(mine)) {
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

    /**
     * Restores saved shared icons, dropping every guess that a shop or a pickup has since
     * contradicted (see {@link #dropGuesses}). Saved data from before 1.5.4 is full of these:
     * Shale's icon was shared by "wood" and Shale, and the guess won every block.
     *
     * @return the mines dropped as wrong guesses, so the caller can forget where they were
     *         "seen": those spots were recorded while mining the other mine
     */
    public Set<String> importShared(Map<String, List<String>> saved) {
        Set<String> dropped = new LinkedHashSet<>();
        if (saved != null) {
            saved.forEach((sprite, mines) -> {
                markShared(sprite, mines);
                dropped.addAll(dropGuesses(sprite));
            });
        }
        applyVerified();
        return dropped;
    }

    /**
     * Removes the guesses from a shared icon that also has a proven mine, and files the icon under
     * that mine alone when it is the only one left. A guess is the weakest evidence there is, a
     * balance that happened to be close; kept as an equal candidate it was decided by where its
     * own guess had placed it, which was right here, so it won every time.
     *
     * @return the guesses dropped
     */
    private Set<String> dropGuesses(String sprite) {
        Set<String> mines = shared.get(sprite);
        if (mines == null || mines.stream().allMatch(MiningBlocks::isGuess)) {
            return Set.of();
        }
        Set<String> dropped = new LinkedHashSet<>();
        mines.removeIf(m -> isGuess(m) && dropped.add(m));
        if (mines.size() == 1) {
            shared.remove(sprite);
            learned.put(sprite, mines.iterator().next());
        }
        return dropped;
    }

    /**
     * Which of a shared icon's mines this block is, from its balance. The total on the action bar
     * is that mine's balance, so the mine whose last known balance it continues is the one: at
     * most 10% above it, or 1% below for the rounding. Empty while none of their balances is known
     * this session, or while two of them fit about as well.
     *
     * @param known balance per currency, as last seen
     */
    public Optional<String> sharedByBalance(String sprite, long total, Map<String, Long> known,
                                            MineCatalog catalog) {
        return sharedByBalance(sprite, total, known, catalog, mine -> false);
    }

    /** {@link #sharedByBalance}, never choosing a mine {@code notHere} rules out. */
    public Optional<String> sharedByBalance(String sprite, long total, Map<String, Long> known,
                                            MineCatalog catalog, java.util.function.Predicate<String> notHere) {
        Set<String> mines = sprite == null ? null : shared.get(sprite);
        if (mines == null || mines.isEmpty()) {
            return Optional.empty();
        }
        String best = null;
        long bestGap = Long.MAX_VALUE;
        long secondGap = Long.MAX_VALUE;
        for (String mine : mines) {
            if (notHere.test(mine)) {
                continue;
            }
            Long balance = balanceOf(known, mine, catalog);
            if (balance == null || balance <= 0L) {
                continue;
            }
            if (total >= balance - balance / 100L && total <= balance + balance / 10L) {
                long gap = Math.abs(total - balance);
                if (gap < bestGap) {
                    secondGap = bestGap;
                    best = mine;
                    bestGap = gap;
                } else if (gap < secondGap) {
                    secondGap = gap;
                }
            }
        }
        // Two balances this total could both continue say nothing. A user's Rosewood and Ichor
        // were both 553.4B, one filed from the other; Ichor won about every other block at
        // Rosewood, each win was filed and placed Ichor at Rosewood, and it never let go. Only a
        // clearly closer one counts, gaps within the bar's rounding (0.1%) being the same.
        if (best != null && secondGap != Long.MAX_VALUE && secondGap <= bestGap * 2L + total / 1_000L) {
            return Optional.empty();
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
        return sharedByHint(sprite, toolMine, previous, catalog, mine -> false);
    }

    /** {@link #sharedByHint}, never choosing a mine {@code notHere} rules out. */
    public Optional<String> sharedByHint(String sprite, Optional<String> toolMine, Optional<String> previous,
                                         MineCatalog catalog, java.util.function.Predicate<String> notHere) {
        Set<String> mines = sprite == null ? null : shared.get(sprite);
        if (mines == null) {
            return Optional.empty();
        }
        for (Optional<String> hint : List.of(toolMine, previous)) {
            if (hint.isPresent()) {
                for (String mine : mines) {
                    if (!notHere.test(mine) && sameMine(mine, hint.get(), catalog)) {
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

    /** Whether some shared icon already counts this mine among its own. */
    private boolean isCandidate(String mine) {
        return shared.values().stream().anyMatch(ms -> ms.stream().anyMatch(m -> m.equalsIgnoreCase(mine.trim())));
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
                // Never over what is already filed: a shared icon repaired on import came first.
                if (sprite != null && mine != null && !mine.isBlank() && !shared.containsKey(sprite)) {
                    learned.putIfAbsent(sprite, mine);
                }
            });
        }
        applyVerified();
    }

    private boolean learn(String sprite, String mine) {
        if (VERIFIED.containsKey(sprite) || verifiedMine(mine)) {
            return false;
        }
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
     * earlier claim ends up shared too, which costs little, as each block is still decided by
     * its balance. A guess from {@link #inferFromBalances} is not shared but replaced: kept, it
     * placed itself where the proven mine is and won every block there.
     */
    private boolean claim(String sprite, String mine) {
        if (VERIFIED.containsKey(sprite) || verifiedMine(mine)) {
            return false;
        }
        if (shared.containsKey(sprite)) {
            boolean added = addCandidate(sprite, mine);
            return !dropGuesses(sprite).isEmpty() || added;
        }
        String was = learned.get(sprite);
        if (was != null && !was.equalsIgnoreCase(mine.trim()) && !isGuess(was)) {
            return markShared(sprite, List.of(was, mine));
        }
        Optional<String> named = was == null ? byName(sprite, names.get()) : Optional.empty();
        if (named.isPresent() && !named.get().equalsIgnoreCase(mine.trim())) {
            return markShared(sprite, List.of(named.get(), mine));
        }
        String m = mine.toLowerCase(Locale.ROOT).trim();
        boolean dropped = learned.entrySet().removeIf(e -> !e.getKey().equals(sprite) && !VERIFIED.containsKey(e.getKey())
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
