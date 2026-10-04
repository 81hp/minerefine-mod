package minerefinehud.progress;

import minerefinehud.mine.Mine;
import minerefinehud.mine.MineCatalog;
import minerefinehud.shop.PriceLedger;
import minerefinehud.shop.ShopItemParser;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Works out the single upgrade the progress bar should show.
 *
 * Stateless on purpose: it is recomputed from the player's inventory every time, so the bar
 * advances by itself when they buy a tier, moves to the next mine when the piece is maxed, and
 * can never get out of step with what they actually own after a relog or a crash.
 *
 * The rules:
 *
 *   1. Start from the most advanced item of the chosen kind the player holds (latest mine in
 *      progression, then highest tier). With none, start at the mine they are standing in.
 *   2. If that item is at its last tier, move to the next mine that sells this kind of item and
 *      start from whatever tier is owned there, usually none.
 *   3. The target is one tier above that.
 */
public final class ProgressPlanner {

    public enum State {
        /** Price known, not yet affordable (or balance not yet seen). */
        TRACKING,
        /** The player has enough for the target tier. */
        FINISHED,
        /** Target known, but the shop for it has not been opened, so no price. */
        PRICE_UNKNOWN,
        /** Last tier of the last known mine for this kind of item. */
        ALL_MAXED,
        /** No item owned and no mine detected, so there is nothing to start from. */
        NO_MINE
    }

    /** What a bar measures. */
    public enum Goal {
        /** The next tier only. */
        NEXT_TIER,
        /** Every tier still to buy at this mine, up to the last: a chestplate at II of IV counts III and IV. */
        TO_MAX
    }

    /**
     * @param targetLevel the next tier to buy
     * @param toLevel     the last tier the cost covers: {@code targetLevel} for the next tier only,
     *                    the item's last tier when tracking the way to max
     * @param quantity    how many of this piece are being bought together, e.g. 12 chestplates;
     *                    {@code cost} is already multiplied by it
     */
    public record ProgressView(ProgressSlot slot, String mine, String gear, int targetLevel,
                               State state, OptionalLong cost, OptionalLong have, String currency,
                               int quantity, int toLevel, boolean boss) {

        /** Most anyone buys at once. Far beyond 12, and keeps the multiplication well inside a long. */
        public static final int MAX_QUANTITY = 999;

        /** Mine gear, the usual bar. */
        public ProgressView(ProgressSlot slot, String mine, String gear, int targetLevel,
                            State state, OptionalLong cost, OptionalLong have, String currency,
                            int quantity, int toLevel) {
            this(slot, mine, gear, targetLevel, state, cost, have, currency, quantity, toLevel, false);
        }

        /**
         * The same upgrade bought this many times over: one bar for the combined cost rather than
         * one bar per piece. Saturates instead of overflowing, though no real price gets close.
         */
        public ProgressView withQuantity(int count) {
            int q = Math.max(1, Math.min(MAX_QUANTITY, count));
            if (q == quantity || cost.isEmpty()) {
                return new ProgressView(slot, mine, gear, targetLevel, state, cost, have, currency, q, toLevel,
                        boss);
            }
            long total;
            try {
                total = Math.multiplyExact(cost.getAsLong() / quantity, (long) q);
            } catch (ArithmeticException e) {
                total = Long.MAX_VALUE;
            }
            State s = state;
            if (state == State.TRACKING || state == State.FINISHED) {
                s = have.isPresent() && have.getAsLong() >= total ? State.FINISHED : State.TRACKING;
            }
            return new ProgressView(slot, mine, gear, targetLevel, s, OptionalLong.of(total), have, currency, q,
                    toLevel, boss);
        }

        /** 0 to 1, for the bar. Zero when either side is unknown. */
        public double fraction() {
            if (state == State.FINISHED) {
                return 1.0;
            }
            if (cost.isEmpty() || have.isEmpty() || cost.getAsLong() <= 0L) {
                return 0.0;
            }
            return Math.min(1.0, (double) have.getAsLong() / (double) cost.getAsLong());
        }
    }

    private ProgressPlanner() {
    }

    public static ProgressView plan(ProgressSlot slot,
                                    List<ShopItemParser.GearRef> owned,
                                    MineCatalog catalog,
                                    Optional<Mine> currentMine,
                                    PriceLedger ledger,
                                    ResourceBalances balances) {
        return plan(slot, owned, catalog, new ProgressionLinks(), currentMine, ledger, balances);
    }

    /**
     * @param links mine order learned from shop prerequisites, which carries the bar past the end
     *              of the bundled data into dimensions added since
     */
    public static ProgressView plan(ProgressSlot slot,
                                    List<ShopItemParser.GearRef> owned,
                                    MineCatalog catalog,
                                    ProgressionLinks links,
                                    Optional<Mine> currentMine,
                                    PriceLedger ledger,
                                    ResourceBalances balances) {
        return plan(slot, owned, catalog, links, currentMine, ledger, balances, Goal.NEXT_TIER);
    }

    /**
     * @param goal the next tier, or everything still to buy for this item at this mine. A Total
     *             bar leaves out what is owned when tracking the next tier, and is the whole mine
     *             at full price when tracking to max, as before the choice existed.
     */
    public static ProgressView plan(ProgressSlot slot,
                                    List<ShopItemParser.GearRef> owned,
                                    MineCatalog catalog,
                                    ProgressionLinks links,
                                    Optional<Mine> currentMine,
                                    PriceLedger ledger,
                                    ResourceBalances balances,
                                    Goal goal) {
        return plan(slot, owned, catalog, links, currentMine, ledger, balances, goal, goal == Goal.NEXT_TIER);
    }

    /**
     * @param totalMinusOwned for a Total bar: leave out the tiers already bought, so buying the
     *                        pickaxe takes what it cost off the Total, the way a piece's bar moves
     *                        on. Off, the Total is the whole mine at full price, the mine panel's
     *                        figure. Ignored by every other bar.
     */
    public static ProgressView plan(ProgressSlot slot,
                                    List<ShopItemParser.GearRef> owned,
                                    MineCatalog catalog,
                                    ProgressionLinks links,
                                    Optional<Mine> currentMine,
                                    PriceLedger ledger,
                                    ResourceBalances balances,
                                    Goal goal,
                                    boolean totalMinusOwned) {

        if (slot.isTotal()) {
            return planMineTotal(slot, owned, catalog, links, currentMine, ledger, balances, totalMinusOwned);
        }
        if (slot.isArmorSet()) {
            return planArmorSet(slot, owned, catalog, links, currentMine, ledger, balances, goal, false);
        }

        List<ShopItemParser.GearRef> mine = owned.stream()
                .filter(g -> slot.gear().equals(g.gear().toLowerCase(Locale.ROOT)))
                .toList();

        // A copy still being upgraded wins over every maxed one, wherever each is from: players
        // keep their maxed main set on while they upgrade an older one in the inventory (a
        // Woodland set behind a maxed set from the next world). Following the furthest copy
        // instead sent the bar on to the next world and left the set being upgraded untracked.
        List<ShopItemParser.GearRef> unfinished = mine.stream()
                .filter(g -> g.level() < maxLevel(ledger, catalog, catalog.serverName(g.mine()), slot.gear()))
                .toList();
        // Boss gear short of its last tier is usually just worn, not being upgraded: it is bought
        // with boss fragments, not mined resources. Ranked by its world, an Archaeologist
        // chestplate at I outranked the End chestplates actually being upgraded. It only leads
        // when no mine-bought copy is unfinished.
        List<ShopItemParser.GearRef> unfinishedFromMines = unfinished.stream()
                .filter(g -> !isBossGear(catalog, g.mine()))
                .toList();
        List<ShopItemParser.GearRef> candidates = !unfinishedFromMines.isEmpty() ? unfinishedFromMines
                : !unfinished.isEmpty() ? unfinished : mine;

        String at;
        if (!candidates.isEmpty()) {
            ShopItemParser.GearRef best = candidates.get(0);
            for (ShopItemParser.GearRef g : candidates) {
                int byOrder = Double.compare(orderOf(catalog, links, slot.gear(), g.mine()),
                        orderOf(catalog, links, slot.gear(), best.mine()));
                if (byOrder > 0 || (byOrder == 0 && g.level() > best.level())) {
                    best = g;
                }
            }
            at = catalog.serverName(best.mine());
            // Nothing being upgraded: the bar is for a piece not owned yet, and the mine the
            // player is in says which, when it is further on. Moving on from the furthest maxed
            // copy alone sent a player in Throne, wearing a maxed Ruins chestplate, to Frost, the
            // first mine after Ruins, instead of the Throne chestplate they were mining for.
            // A copy from a mine not placed yet cannot be shown to be behind, so it is left alone.
            double ownedOrder = orderOf(catalog, links, slot.gear(), at);
            if (unfinishedFromMines.isEmpty() && currentMine.isPresent() && ownedOrder >= 0) {
                Optional<String> here = startMine(slot, catalog, currentMine.get());
                if (here.isPresent() && orderOf(catalog, links, slot.gear(), here.get()) > ownedOrder) {
                    at = here.get();
                }
            }
        } else if (currentMine.isPresent()) {
            at = startMine(slot, catalog, currentMine.get()).orElse(null);
            if (at == null) {
                return empty(slot, State.ALL_MAXED, currentMine.get().name());
            }
        } else {
            return empty(slot, State.NO_MINE, "");
        }

        int level = ownedLevel(catalog, mine, at, maxLevel(ledger, catalog, at, slot.gear()));
        // Bounded: learned links come from saved data, and a loop in them must not hang a tick.
        int hops = 0;
        while (level >= maxLevel(ledger, catalog, at, slot.gear())) {
            Optional<String> next = nextMine(slot, catalog, links, at);
            if (next.isEmpty() || ++hops > catalog.mines().size() + MAX_LINK_STEPS) {
                return new ProgressView(slot, at, slot.gear(), level, State.ALL_MAXED,
                        OptionalLong.empty(), OptionalLong.empty(), at, 1, level);
            }
            at = next.get();
            level = ownedLevel(catalog, mine, at, maxLevel(ledger, catalog, at, slot.gear()));
        }

        // A price names its currency. Without one, the mine's own name is the best guess, which
        // is what every observed tooltip so far has used ("Debris x1.96B" at Debris).
        int max = maxLevel(ledger, catalog, at, slot.gear());
        String currency = currencyOf(ledger, at, slot.gear(), max).orElse(at);
        return priced(slot, at, level, max, currency, catalog, ledger, balances, goal, false);
    }

    /** The view for an item owned at {@code level} of {@code max}: the next tier, or all of them. */
    private static ProgressView priced(ProgressSlot slot, String at, int level, int max, String currency,
                                       MineCatalog catalog, PriceLedger ledger, ResourceBalances balances,
                                       Goal goal, boolean boss) {
        int target = level + 1;
        OptionalLong sheet = catalog.pieceTotal(at, slot.gear());
        boolean toMax = goal == Goal.TO_MAX && max > target;
        int toLevel = toMax ? max : target;

        OptionalLong cost = toMax
                ? costToMax(ledger, at, slot.gear(), level, max, sheet)
                : tierPrice(ledger, at, slot.gear(), target, max, sheet);

        OptionalLong have = balances.get(currency)
                .map(r -> OptionalLong.of(r.amount())).orElse(OptionalLong.empty());

        if (cost.isEmpty()) {
            return new ProgressView(slot, at, slot.gear(), target, State.PRICE_UNKNOWN,
                    OptionalLong.empty(), have, currency, 1, toLevel, boss);
        }

        State state = have.isPresent() && have.getAsLong() >= cost.getAsLong() ? State.FINISHED : State.TRACKING;
        return new ProgressView(slot, at, slot.gear(), target, state, cost, have, currency, 1, toLevel, boss);
    }

    /**
     * The bar for boss gear: the boss piece of this kind being upgraded, priced in that boss's
     * fragments rather than a mine's resource.
     *
     * The same rules as mine gear, along the bosses in world order instead of the mines: a copy
     * short of its last tier first, else the latest boss owned from, moving on to the next boss
     * once that one is maxed. With no boss piece owned, the boss of the world the player is
     * mining in. Only bosses that sell this kind of item count; none sells an axe or a shovel.
     */
    public static ProgressView planBoss(ProgressSlot slot,
                                        List<ShopItemParser.GearRef> owned,
                                        MineCatalog catalog,
                                        ProgressionLinks links,
                                        Optional<Mine> currentMine,
                                        PriceLedger ledger,
                                        ResourceBalances balances,
                                        Goal goal) {
        if (slot.isArmorSet()) {
            return planArmorSet(slot, owned, catalog, links, currentMine, ledger, balances, goal, true);
        }
        List<Mine> bosses = slot.isTotal() ? List.of() : catalog.bosses().stream()
                .filter(b -> b.pieceCost(slot.gear()) > 0L)
                .toList();
        if (bosses.isEmpty()) {
            return emptyBoss(slot, State.ALL_MAXED, "");
        }

        List<ShopItemParser.GearRef> ofGear = owned.stream()
                .filter(g -> slot.gear().equals(g.gear().toLowerCase(Locale.ROOT)))
                .filter(g -> isBossGear(catalog, g.mine()))
                .filter(g -> bossIndex(catalog, bosses, g.mine()) >= 0)
                .toList();
        List<ShopItemParser.GearRef> ownedBossGear = owned.stream()
                .filter(g -> isBossGear(catalog, g.mine()))
                .toList();
        List<ShopItemParser.GearRef> unfinished = ofGear.stream()
                .filter(g -> g.level() < maxLevel(ledger, catalog, g.mine(), slot.gear()))
                .toList();
        List<ShopItemParser.GearRef> candidates = unfinished.isEmpty() ? ofGear : unfinished;

        int index;
        if (!candidates.isEmpty()) {
            ShopItemParser.GearRef best = candidates.get(0);
            for (ShopItemParser.GearRef g : candidates) {
                int byOrder = Integer.compare(bossIndex(catalog, bosses, g.mine()),
                        bossIndex(catalog, bosses, best.mine()));
                if (byOrder > 0 || (byOrder == 0 && g.level() > best.level())) {
                    best = g;
                }
            }
            index = bossIndex(catalog, bosses, best.mine());
            // As for mine gear: with nothing being upgraded, a later world's boss wins.
            if (unfinished.isEmpty() && currentMine.isPresent()) {
                index = Math.max(index, bossOfWorld(catalog, bosses, currentMine.get()));
            }
        } else if (currentMine.isPresent()) {
            index = bossOfWorld(catalog, bosses, currentMine.get());
            if (index < 0) {
                return emptyBoss(slot, State.ALL_MAXED, currentMine.get().name());
            }
        } else {
            return emptyBoss(slot, State.NO_MINE, "");
        }

        while (true) {
            Mine boss = bosses.get(index);
            // Any piece owned from this boss gives the shop's name for it, not only this kind.
            String name = bossName(boss, ownedBossGear, catalog, links, ledger);
            int max = maxLevel(ledger, catalog, name, slot.gear());
            int level = bossLevel(catalog, ofGear, boss, max);
            if (level < max) {
                String currency = currencyOf(ledger, name, slot.gear(), max)
                        .orElseGet(() -> fragmentCurrency(catalog, balances, boss, name));
                return priced(slot, name, level, max, currency, catalog, ledger, balances, goal, true);
            }
            if (++index >= bosses.size()) {
                return new ProgressView(slot, name, slot.gear(), level, State.ALL_MAXED,
                        OptionalLong.empty(), OptionalLong.empty(), name, 1, level, true);
            }
        }
    }

    /**
     * Helmet, chestplate, leggings and boots as one bar: each piece planned exactly as its own bar
     * would be, then added up for the set they belong to.
     *
     * The set is the earliest mine (or boss) any piece is still being bought at. With the helmet
     * maxed at Icicle and the other three not, the helmet's own bar has moved on to the next mine,
     * but the set is still Icicle's, and the bar counts the three pieces left there. Once all four
     * are maxed the set moves on with them.
     *
     * Unknown when any piece of the set is, rather than a total that quietly leaves one out.
     */
    private static ProgressView planArmorSet(ProgressSlot slot, List<ShopItemParser.GearRef> owned,
                                             MineCatalog catalog, ProgressionLinks links,
                                             Optional<Mine> currentMine, PriceLedger ledger,
                                             ResourceBalances balances, Goal goal, boolean boss) {
        List<ProgressView> pieces = armorPieces(owned, catalog, links, currentMine, ledger, balances, goal, boss);
        Optional<String> at = earliestSetMine(pieces, catalog, links);

        // Away from any mine, a piece not owned has nowhere to start, and the set would leave it
        // out. The set's own mine is where it is bought, so plan it from there.
        if (at.isPresent() && currentMine.isEmpty()
                && pieces.stream().anyMatch(v -> v.state() == State.NO_MINE)) {
            Optional<Mine> setMine = boss ? mineBefore(catalog, at.get()) : placed(catalog, at.get());
            if (setMine.isPresent()) {
                pieces = armorPieces(owned, catalog, links, setMine, ledger, balances, goal, boss);
                at = earliestSetMine(pieces, catalog, links);
            }
        }

        if (at.isEmpty()) {
            boolean noMine = pieces.stream().anyMatch(v -> v.state() == State.NO_MINE);
            String mine = noMine ? "" : pieces.get(0).mine();
            return new ProgressView(slot, mine, slot.gear(), 0, noMine ? State.NO_MINE : State.ALL_MAXED,
                    OptionalLong.empty(), OptionalLong.empty(), mine, 1, 0, boss);
        }

        String first = at.get();
        List<ProgressView> set = pieces.stream()
                .filter(v -> stillToBuy(v) && (boss ? sameBoss(catalog, v.mine(), first)
                        : sameMine(catalog, v.mine(), first)))
                .toList();
        // A boss goes by its shop name ("Archaeologist") once any piece has given it.
        String name = set.stream().map(ProgressView::mine)
                .filter(m -> !boss || catalog.boss(m).map(b -> !b.name().equals(m)).orElse(false))
                .findFirst().orElse(first);
        // A piece whose price is known names the currency; an unseen one only guesses it.
        String currency = set.stream().filter(v -> v.state() != State.PRICE_UNKNOWN)
                .map(ProgressView::currency).findFirst().orElse(set.get(0).currency());
        OptionalLong have = balances.get(currency)
                .map(r -> OptionalLong.of(r.amount())).orElse(OptionalLong.empty());
        int target = set.stream().mapToInt(ProgressView::targetLevel).min().orElse(1);
        int toLevel = set.stream().mapToInt(ProgressView::toLevel).max().orElse(target);

        if (set.stream().anyMatch(v -> v.cost().isEmpty())) {
            return new ProgressView(slot, name, slot.gear(), target, State.PRICE_UNKNOWN,
                    OptionalLong.empty(), have, currency, 1, toLevel, boss);
        }
        long cost = 0L;
        for (ProgressView v : set) {
            try {
                cost = Math.addExact(cost, v.cost().getAsLong());
            } catch (ArithmeticException e) {
                cost = Long.MAX_VALUE;
            }
        }
        State state = have.isPresent() && have.getAsLong() >= cost ? State.FINISHED : State.TRACKING;
        return new ProgressView(slot, name, slot.gear(), target, state, OptionalLong.of(cost), have, currency,
                1, toLevel, boss);
    }

    private static List<ProgressView> armorPieces(List<ShopItemParser.GearRef> owned, MineCatalog catalog,
                                                  ProgressionLinks links, Optional<Mine> currentMine,
                                                  PriceLedger ledger, ResourceBalances balances, Goal goal,
                                                  boolean boss) {
        List<ProgressView> out = new java.util.ArrayList<>();
        for (ProgressSlot piece : ProgressSlot.ARMOR_PIECES) {
            out.add(boss
                    ? planBoss(piece, owned, catalog, links, currentMine, ledger, balances, goal)
                    : plan(piece, owned, catalog, links, currentMine, ledger, balances, goal));
        }
        return out;
    }

    private static boolean sameBoss(MineCatalog catalog, String a, String b) {
        Optional<Mine> other = catalog.boss(b);
        return other.isPresent() && isBoss(catalog, a, other.get());
    }

    private static Optional<Mine> placed(MineCatalog catalog, String mine) {
        return catalog.exactly(mine).or(() -> catalog.detectFrom(mine));
    }

    /** The last mine before this boss in the data: mining there, its gear is that boss's. */
    private static Optional<Mine> mineBefore(MineCatalog catalog, String bossGear) {
        List<Mine> all = catalog.all();
        int at = catalog.boss(bossGear).map(b -> indexById(all, b)).orElse(-1);
        for (int i = at - 1; i >= 0; i--) {
            if (indexById(catalog.bosses(), all.get(i)) < 0) {
                return Optional.of(all.get(i));
            }
        }
        return Optional.empty();
    }

    /** A piece with a tier left to buy, priced or not. */
    private static boolean stillToBuy(ProgressView v) {
        return v.state() == State.TRACKING || v.state() == State.FINISHED || v.state() == State.PRICE_UNKNOWN;
    }

    /**
     * The earliest mine or boss in progression that any piece still has a tier to buy at. A mine
     * the data cannot place only wins when no piece's mine can be placed. Empty when every piece
     * is maxed or has nowhere to start.
     */
    private static Optional<String> earliestSetMine(List<ProgressView> pieces, MineCatalog catalog,
                                                    ProgressionLinks links) {
        String best = null;
        double bestOrder = -1;
        for (ProgressView v : pieces) {
            if (!stillToBuy(v)) {
                continue;
            }
            double order = v.boss()
                    ? catalog.boss(v.mine()).map(b -> (double) indexById(catalog.all(), b)).orElse(-1.0)
                    : orderOf(catalog, links, v.gear(), v.mine());
            if (best == null || (order >= 0 && (bestOrder < 0 || order < bestOrder))) {
                best = v.mine();
                bestOrder = order;
            }
        }
        return Optional.ofNullable(best);
    }

    private static ProgressView emptyBoss(ProgressSlot slot, State state, String mine) {
        return new ProgressView(slot, mine, slot.gear(), 0, state,
                OptionalLong.empty(), OptionalLong.empty(), mine, 1, 0, true);
    }

    /** Position among the bosses that sell this item, or -1 for gear that is not theirs. */
    private static int bossIndex(MineCatalog catalog, List<Mine> bosses, String gearMine) {
        return catalog.boss(gearMine).map(b -> indexById(bosses, b)).orElse(-1);
    }

    private static int indexById(List<Mine> list, Mine m) {
        for (int i = 0; i < list.size(); i++) {
            if (java.util.Objects.equals(list.get(i).id(), m.id())
                    && list.get(i).name().equals(m.name())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The boss of the world this mine is in: the first boss the data lists after it, since each
     * world's bosses follow its mines. -1 for a mine the data does not list, or past the last boss.
     */
    private static int bossOfWorld(MineCatalog catalog, List<Mine> bosses, Mine current) {
        List<Mine> all = catalog.all();
        int from = indexById(all, current);
        if (from < 0) {
            from = catalog.detectFrom(current.name()).map(m -> indexById(all, m)).orElse(-1);
        }
        if (from < 0) {
            return -1;
        }
        for (int i = from + 1; i < all.size(); i++) {
            int at = indexById(bosses, all.get(i));
            if (at >= 0) {
                return at;
            }
        }
        return -1;
    }

    /**
     * The name the shop sells this boss's gear under, which is what prices are stored by:
     * "Archaeologist" for Angry Archaeologist. From gear owned, else from the learned order, else
     * from prices and tier counts the ledger holds, else the boss's full name, which still finds
     * the spreadsheet's figures.
     */
    private static String bossName(Mine boss, List<ShopItemParser.GearRef> owned, MineCatalog catalog,
                                   ProgressionLinks links, PriceLedger ledger) {
        for (ShopItemParser.GearRef g : owned) {
            if (isBoss(catalog, g.mine(), boss)) {
                return g.mine();
            }
        }
        for (String key : links.export().keySet()) {
            String mine = key.substring(key.indexOf('|') + 1);
            if (isBossGear(catalog, mine) && isBoss(catalog, mine, boss)) {
                return mine;
            }
        }
        for (String mine : ledger.knownMines()) {
            if (isBossGear(catalog, mine) && isBoss(catalog, mine, boss)) {
                return titleCase(mine);
            }
        }
        return boss.name();
    }

    private static boolean isBoss(MineCatalog catalog, String gearMine, Mine boss) {
        return catalog.boss(gearMine).filter(b -> java.util.Objects.equals(b.id(), boss.id())
                && b.name().equals(boss.name())).isPresent();
    }

    /** "archaeologist" as stored by the ledger, shown as "Archaeologist". */
    private static String titleCase(String s) {
        StringBuilder out = new StringBuilder(s.length());
        boolean start = true;
        for (char c : s.toCharArray()) {
            out.append(start ? Character.toUpperCase(c) : c);
            start = Character.isWhitespace(c);
        }
        return out.toString();
    }

    /**
     * What this boss's gear is paid in when no price has said: a fragment balance already read
     * for this boss, else "Archaeologist Fragment", the way the shop names it.
     */
    private static String fragmentCurrency(MineCatalog catalog, ResourceBalances balances, Mine boss, String name) {
        for (String currency : balances.all().keySet()) {
            String lower = currency.toLowerCase(Locale.ROOT);
            if (lower.endsWith(" fragment")
                    && isBoss(catalog, currency.substring(0, currency.length() - " fragment".length()), boss)) {
                return currency;
            }
        }
        return name + " Fragment";
    }

    /** The tier of the boss piece being upgraded, as {@link #ownedLevel} does for a mine's. */
    private static int bossLevel(MineCatalog catalog, List<ShopItemParser.GearRef> owned, Mine boss, int max) {
        int highest = 0;
        int unfinished = 0;
        for (ShopItemParser.GearRef g : owned) {
            if (isBoss(catalog, g.mine(), boss)) {
                highest = Math.max(highest, g.level());
                if (g.level() < max) {
                    unfinished = Math.max(unfinished, g.level());
                }
            }
        }
        return unfinished > 0 ? unfinished : highest;
    }

    private static final List<String> TOTAL_TOOLS = List.of("pickaxe", "axe", "shovel");
    private static final List<String> TOTAL_ARMOR = List.of("helmet", "chestplate", "leggings", "boots");

    /**
     * Everything still to buy at one mine: for each piece, the tiers above the one owned, as a
     * TO_MAX bar would count them. A piece owned only from a later mine is skipped, because
     * nobody buys an older sword. Counting what is left rather than the full price keeps the bar
     * moving forward as tiers are bought; against the full price, every purchase spent the
     * balance while the target stayed put, and the bar went backwards.
     *
     * The mine is the one the player stands in, or, away from any mine, the one of the set being
     * upgraded (else the furthest one they own gear from), so the bar does not blank out on the
     * way back from the hub.
     *
     * Unknown when any piece's remaining cost is, including a mine whose tool is not known yet:
     * a total that quietly leaves out a piece is worse than none, same rule as {@link MineCosts}.
     *
     * With {@code minusOwned} off it is instead the whole mine, every piece at full price whatever
     * is owned: the mine panel's Total, so the two always show the same figure. That view is marked
     * by {@code toLevel} above {@code targetLevel}, as a piece bar covering every tier is.
     */
    private static ProgressView planMineTotal(ProgressSlot slot, List<ShopItemParser.GearRef> owned,
                                              MineCatalog catalog, ProgressionLinks links,
                                              Optional<Mine> currentMine, PriceLedger ledger,
                                              ResourceBalances balances, boolean minusOwned) {
        Optional<String> at = currentMine.map(m -> catalog.serverName(m.name()))
                .or(() -> furthestOwnedMine(owned.stream()
                        .filter(g -> g.level() < maxLevel(ledger, catalog, catalog.serverName(g.mine()),
                                g.gear().toLowerCase(Locale.ROOT)))
                        .toList(), catalog, links))
                .or(() -> furthestOwnedMine(owned, catalog, links));
        if (at.isEmpty()) {
            return empty(slot, State.NO_MINE, "");
        }
        String name = at.get();

        if (!minusOwned) {
            Optional<Mine> data = currentMine.or(() -> catalog.exactly(name));
            MineCosts.View whole = MineCosts.of(name, data, ledger, false);
            String paidIn = whole.pieces().stream()
                    .map(p -> currencyOf(ledger, name, p.gear(), maxLevel(ledger, catalog, name, p.gear())))
                    .flatMap(Optional::stream).findFirst().orElse(name);
            OptionalLong have = balances.get(paidIn)
                    .map(r -> OptionalLong.of(r.amount())).orElse(OptionalLong.empty());
            if (whole.total().isEmpty()) {
                return new ProgressView(slot, name, slot.gear(), 1, State.PRICE_UNKNOWN,
                        OptionalLong.empty(), have, paidIn, 1, 2);
            }
            long cost = whole.total().getAsLong();
            State state = have.isPresent() && have.getAsLong() >= cost ? State.FINISHED : State.TRACKING;
            return new ProgressView(slot, name, slot.gear(), 1, state, OptionalLong.of(cost), have, paidIn, 1, 2);
        }

        List<String> gears = new java.util.ArrayList<>();
        gears.add("sword");
        java.util.Set<String> tools = new java.util.LinkedHashSet<>(currentMine
                .or(() -> catalog.exactly(name))
                .map(Mine::toolKeys).orElse(List.of()));
        for (String gear : ledger.observedGears(name)) {
            if (TOTAL_TOOLS.contains(gear)) {
                tools.add(gear);
            }
        }
        for (ShopItemParser.GearRef g : owned) {
            String gear = g.gear().toLowerCase(Locale.ROOT);
            if (TOTAL_TOOLS.contains(gear) && sameMine(catalog, g.mine(), name)) {
                tools.add(gear);
            }
        }
        boolean toolUnknown = tools.isEmpty();
        gears.addAll(tools);
        gears.addAll(TOTAL_ARMOR);
        gears.add("charm");

        long left = 0L;
        boolean unknown = toolUnknown;
        Optional<String> currency = Optional.empty();
        for (String gear : gears) {
            OptionalLong sheet = catalog.pieceTotal(name, gear);
            int max = maxLevel(ledger, catalog, name, gear);
            if (currency.isEmpty()) {
                currency = currencyOf(ledger, name, gear, max);
            }
            List<ShopItemParser.GearRef> ofGear = owned.stream()
                    .filter(g -> gear.equals(g.gear().toLowerCase(Locale.ROOT)))
                    .toList();
            // A copy from this mine is the one being upgraded here, even with a better one
            // from a later mine worn; only without one does the later copy make it pointless.
            boolean ownedHere = ofGear.stream().anyMatch(g -> sameMine(catalog, g.mine(), name));
            if (!ownedHere && ownsLater(owned, catalog, links, gear, name)) {
                continue;
            }
            int level = ownedLevel(catalog, ofGear, name, max);
            if (level >= max) {
                continue;
            }
            OptionalLong cost = costToMax(ledger, name, gear, level, max, sheet);
            if (cost.isEmpty()) {
                unknown = true;
            } else {
                left += cost.getAsLong();
            }
        }

        String paidIn = currency.orElse(name);
        OptionalLong have = balances.get(paidIn)
                .map(r -> OptionalLong.of(r.amount())).orElse(OptionalLong.empty());
        if (unknown) {
            return new ProgressView(slot, name, slot.gear(), 1, State.PRICE_UNKNOWN,
                    OptionalLong.empty(), have, paidIn, 1, 1);
        }
        if (left == 0L) {
            return new ProgressView(slot, name, slot.gear(), 1, State.ALL_MAXED,
                    OptionalLong.empty(), have, paidIn, 1, 1);
        }
        State state = have.isPresent() && have.getAsLong() >= left ? State.FINISHED : State.TRACKING;
        return new ProgressView(slot, name, slot.gear(), 1, state, OptionalLong.of(left), have, paidIn, 1, 1);
    }

    private static boolean sameMine(MineCatalog catalog, String a, String b) {
        return catalog.serverName(a).equalsIgnoreCase(catalog.serverName(b));
    }

    /** True when the player holds this kind of item from a mine after this one in progression. */
    private static boolean ownsLater(List<ShopItemParser.GearRef> owned, MineCatalog catalog,
                                     ProgressionLinks links, String gear, String mine) {
        double here = orderOf(catalog, links, gear, mine);
        if (here < 0) {
            // A mine the data does not place: nothing can be shown to come after it.
            return false;
        }
        for (ShopItemParser.GearRef g : owned) {
            if (gear.equals(g.gear().toLowerCase(Locale.ROOT)) && !sameMine(catalog, g.mine(), mine)
                    && orderOf(catalog, links, gear, g.mine()) > here) {
                return true;
            }
        }
        return false;
    }

    /** The latest mine in progression the player owns any gear from. */
    private static Optional<String> furthestOwnedMine(List<ShopItemParser.GearRef> owned,
                                                      MineCatalog catalog, ProgressionLinks links) {
        String best = null;
        double bestOrder = -1;
        for (ShopItemParser.GearRef g : owned) {
            // Boss gear is not a mine to stand in, so it cannot be the mine the total is for.
            if (isBossGear(catalog, g.mine())) {
                continue;
            }
            double order = orderOf(catalog, links, g.gear().toLowerCase(Locale.ROOT), g.mine());
            if (best == null || order > bestOrder) {
                best = g.mine();
                bestOrder = order;
            }
        }
        return Optional.ofNullable(best).map(catalog::serverName);
    }

    /** Gear named after a boss ("Archaeologist") rather than a mine. */
    private static boolean isBossGear(MineCatalog catalog, String mine) {
        return catalog.exactly(mine).isEmpty() && catalog.boss(mine).isPresent();
    }

    /**
     * Everything still to buy, tiers {@code level + 1} to {@code max}. From the shop when every one
     * of those tiers has been seen. Otherwise from the spreadsheet: the whole piece minus the tiers
     * already owned when their prices are known, else each missing tier estimated from the whole
     * piece by {@link TierCurve}, so a bar works without its shop ever being opened. Empty with
     * neither the shop nor the sheet, rather than a total that leaves something out.
     */
    static OptionalLong costToMax(PriceLedger ledger, String mine, String gear, int level, int max,
                                  OptionalLong sheet) {
        OptionalLong fromShop = ledger.remainingCost(mine, gear, level, max);
        if (fromShop.isPresent() || sheet.isEmpty()) {
            return fromShop;
        }
        long paid = 0L;
        boolean paidKnown = true;
        for (int l = 1; l <= level && paidKnown; l++) {
            Optional<PriceLedger.Price> p = ledger.price(mine, gear, l);
            paidKnown = p.isPresent();
            paid += p.map(PriceLedger.Price::amount).orElse(0L);
        }
        long left = sheet.getAsLong() - paid;
        // Owned tiers costing as much as the whole piece means the sheet is out of date.
        if (paidKnown && left > 0L) {
            return OptionalLong.of(left);
        }
        long sum = 0L;
        for (int l = level + 1; l <= max; l++) {
            OptionalLong p = tierPrice(ledger, mine, gear, l, max, sheet);
            if (p.isEmpty()) {
                return OptionalLong.empty();
            }
            sum += p.getAsLong();
        }
        return sum > 0L ? OptionalLong.of(sum) : OptionalLong.empty();
    }

    /** One tier's price: from the shop, else estimated from the sheet's whole-piece figure. */
    static OptionalLong tierPrice(PriceLedger ledger, String mine, String gear, int level, int max,
                                  OptionalLong sheet) {
        Optional<PriceLedger.Price> p = ledger.price(mine, gear, level);
        if (p.isPresent()) {
            return OptionalLong.of(p.get().amount());
        }
        if (sheet.isEmpty()) {
            return OptionalLong.empty();
        }
        long estimate = TierCurve.estimate(sheet.getAsLong(), max, level);
        return estimate > 0L ? OptionalLong.of(estimate) : OptionalLong.empty();
    }

    /** The currency this item is priced in, from whichever of its tiers has been seen. */
    private static Optional<String> currencyOf(PriceLedger ledger, String mine, String gear, int max) {
        for (int l = 1; l <= Math.max(max, PriceLedger.DEFAULT_MAX_LEVEL); l++) {
            Optional<String> c = ledger.price(mine, gear, l).map(PriceLedger.Price::currency)
                    .filter(s -> !s.isBlank());
            if (c.isPresent()) {
                return c;
            }
        }
        return Optional.empty();
    }

    private static ProgressView empty(ProgressSlot slot, State state, String mine) {
        return new ProgressView(slot, mine, slot.gear(), 0, state,
                OptionalLong.empty(), OptionalLong.empty(), mine, 1, 0);
    }

    /** Every boss piece the bundled table lists has two tiers. */
    private static final int BOSS_TIERS = 2;

    private static int maxLevel(PriceLedger ledger, MineCatalog catalog, String mine, String gear) {
        // A boss the table leaves out (Endor, Nekronos) would otherwise get the mines' guess of
        // six, and a maxed piece would wait for a tier III that does not exist.
        if (!"charm".equals(gear) && isBossGear(catalog, mine) && !ledger.knowsTierCount(mine, gear)
                && ledger.observedTiers(mine, gear).isEmpty()
                && ledger.price(mine, gear, BOSS_TIERS + 1).isEmpty()) {
            return BOSS_TIERS;
        }
        return ledger.maxLevel(mine, gear, catalog.pieceTotal(mine, gear));
    }

    /**
     * The tier of the copy being upgraded, among the player's copies of one item from this mine.
     * A copy short of {@code max} wins over a maxed one: players keep their maxed set on while
     * they upgrade a second one in the inventory, or the other way round, and the bar must
     * follow the unfinished set whichever of the two is worn. Taking the highest tier left the
     * worn maxed set in charge, so the bar moved on to the next mine. Of several unfinished
     * copies, the furthest along. 0 with none.
     */
    private static int ownedLevel(MineCatalog catalog, List<ShopItemParser.GearRef> owned, String mine,
                                  int max) {
        int highest = 0;
        int unfinished = 0;
        for (ShopItemParser.GearRef g : owned) {
            if (sameMine(catalog, g.mine(), mine)) {
                highest = Math.max(highest, g.level());
                if (g.level() < max) {
                    unfinished = Math.max(unfinished, g.level());
                }
            }
        }
        return unfinished > 0 ? unfinished : highest;
    }

    /** Position in the bundled data, or -1 for a mine it does not list. */
    private static int orderOf(MineCatalog catalog, String mineName) {
        Optional<Mine> known = catalog.detectFrom(mineName);
        return known.map(m -> catalog.mines().indexOf(m)).orElse(-1);
    }

    /** Longest chain of learned links followed, so a bad loop in saved data cannot hang a tick. */
    private static final int MAX_LINK_STEPS = 64;

    /**
     * Position in progression. A mine the data does not list is placed just after the listed mine
     * its learned links lead back to, a fraction further on for each link, so it sorts before the
     * next listed mine. That is right for a boss, whose gear sits between the last mine of one
     * world and the first of the next (Aurora, then Bjorn, then Darkstone), and for a newly added
     * dimension, which leads back to the last listed mine and so still sorts after everything.
     * Placing every such mine after all listed ones made a leftover boss chestplate outrank the
     * Darkstone one, and the bar followed the boss. Otherwise -1.
     */
    private static double orderOf(MineCatalog catalog, ProgressionLinks links, String gear, String mineName) {
        int known = orderOf(catalog, mineName);
        if (known >= 0) {
            return known;
        }
        String at = mineName;
        for (int steps = 1; steps <= MAX_LINK_STEPS; steps++) {
            Optional<String> before = links.previous(at, gear);
            if (before.isEmpty()) {
                return -1;
            }
            int anchor = orderOf(catalog, before.get());
            if (anchor >= 0) {
                return anchor + steps / (MAX_LINK_STEPS + 1.0);
            }
            at = before.get();
        }
        return -1;
    }

    private static boolean sells(ProgressSlot slot, Mine mine) {
        return !slot.isTool() || mine.toolKeys().contains(slot.gear());
    }

    /** The current mine if it sells this kind of item, otherwise the next one that does. */
    private static Optional<String> startMine(ProgressSlot slot, MineCatalog catalog, Mine current) {
        int index = orderOf(catalog, current.name());
        if (index < 0) {
            // Not in the price data, so its tool type is unknown. Take it at its word.
            return Optional.of(current.name());
        }
        List<Mine> all = catalog.mines();
        for (int i = index; i < all.size(); i++) {
            if (sells(slot, all.get(i))) {
                return Optional.of(catalog.serverName(all.get(i).name()));
            }
        }
        return Optional.empty();
    }

    /**
     * The next mine after this one that sells this kind of item. What the shop says wins over the
     * bundled order, because the shop is the server itself.
     */
    static Optional<String> nextMine(ProgressSlot slot, MineCatalog catalog, ProgressionLinks links,
                                     String after) {
        Optional<String> learned = links.next(catalog.serverName(after), slot.gear());
        if (learned.isPresent()) {
            return learned;
        }
        int index = orderOf(catalog, after);
        if (index < 0) {
            return Optional.empty();
        }
        List<Mine> all = catalog.mines();
        for (int i = index + 1; i < all.size(); i++) {
            if (sells(slot, all.get(i))) {
                return Optional.of(catalog.serverName(all.get(i).name()));
            }
        }
        return Optional.empty();
    }
}
