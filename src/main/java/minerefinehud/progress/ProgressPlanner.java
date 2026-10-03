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
                               int quantity, int toLevel) {

        /** Most anyone buys at once. Far beyond 12, and keeps the multiplication well inside a long. */
        public static final int MAX_QUANTITY = 999;

        /**
         * The same upgrade bought this many times over: one bar for the combined cost rather than
         * one bar per piece. Saturates instead of overflowing, though no real price gets close.
         */
        public ProgressView withQuantity(int count) {
            int q = Math.max(1, Math.min(MAX_QUANTITY, count));
            if (q == quantity || cost.isEmpty()) {
                return new ProgressView(slot, mine, gear, targetLevel, state, cost, have, currency, q, toLevel);
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
                    toLevel);
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

    /** @param goal the next tier, or everything still to buy for this item at this mine */
    public static ProgressView plan(ProgressSlot slot,
                                    List<ShopItemParser.GearRef> owned,
                                    MineCatalog catalog,
                                    ProgressionLinks links,
                                    Optional<Mine> currentMine,
                                    PriceLedger ledger,
                                    ResourceBalances balances,
                                    Goal goal) {

        if (slot.isTotal()) {
            return planMineTotal(slot, owned, catalog, links, currentMine, ledger, balances);
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
        List<ShopItemParser.GearRef> candidates = unfinished.isEmpty() ? mine : unfinished;

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

        int target = level + 1;
        OptionalLong sheet = catalog.pieceTotal(at, slot.gear());
        int max = maxLevel(ledger, catalog, at, slot.gear());
        boolean toMax = goal == Goal.TO_MAX && max > target;
        int toLevel = toMax ? max : target;

        OptionalLong cost = toMax
                ? costToMax(ledger, at, slot.gear(), level, max, sheet)
                : tierPrice(ledger, at, slot.gear(), target, max, sheet);

        // A price names its currency. Without one, the mine's own name is the best guess, which
        // is what every observed tooltip so far has used ("Debris x1.96B" at Debris).
        String currency = currencyOf(ledger, at, slot.gear(), max).orElse(at);
        OptionalLong have = balances.get(currency)
                .map(r -> OptionalLong.of(r.amount())).orElse(OptionalLong.empty());

        if (cost.isEmpty()) {
            return new ProgressView(slot, at, slot.gear(), target, State.PRICE_UNKNOWN,
                    OptionalLong.empty(), have, currency, 1, toLevel);
        }

        State state = have.isPresent() && have.getAsLong() >= cost.getAsLong() ? State.FINISHED : State.TRACKING;
        return new ProgressView(slot, at, slot.gear(), target, state, cost, have, currency, 1, toLevel);
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
     */
    private static ProgressView planMineTotal(ProgressSlot slot, List<ShopItemParser.GearRef> owned,
                                              MineCatalog catalog, ProgressionLinks links,
                                              Optional<Mine> currentMine, PriceLedger ledger,
                                              ResourceBalances balances) {
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
            if (catalog.exactly(g.mine()).isEmpty() && catalog.boss(g.mine()).isPresent()) {
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

    private static int maxLevel(PriceLedger ledger, MineCatalog catalog, String mine, String gear) {
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
