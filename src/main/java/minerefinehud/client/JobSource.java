package minerefinehud.client;

import minerefinehud.job.JobCalc;
import minerefinehud.job.JobWorlds;
import minerefinehud.mine.Mine;
import minerefinehud.mine.MineCatalog;
import minerefinehud.progress.MineCosts;
import minerefinehud.shop.PriceLedger;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;

/**
 * Where the job calculator and the price lookup get the mines and their prices, by Arreba: the
 * same catalog and shop prices the Mine panel uses. Mines added after the bundled data (the
 * Sift's) are found from the shop prices the mod has read, grouped by the dimension it learned.
 * The client sets it once at start-up.
 */
final class JobSource {

    private static Supplier<MineCatalog> catalog = () -> new MineCatalog(List.of());
    private static PriceLedger prices = new PriceLedger();
    private static Supplier<Map<String, String>> learned = Map::of;

    private JobSource() {
    }

    static void set(Supplier<MineCatalog> catalogSource, PriceLedger ledger, Supplier<Map<String, String>> worlds) {
        catalog = catalogSource;
        prices = ledger;
        learned = worlds;
    }

    /** One mine the player can pick, listed in the data or only known from its shop. */
    record Item(String name, String world, Optional<Mine> data, String serverName) {

        /** Gear and total as the Mine panel works them out, armour as four pieces. */
        MineCosts.View view() {
            return MineCosts.of(serverName, data, prices, true);
        }

        List<JobCalc.Part> parts() {
            List<JobCalc.Part> out = new ArrayList<>();
            for (MineCosts.Piece p : view().pieces()) {
                out.add(new JobCalc.Part(p.gear(), p.cost()));
            }
            return out;
        }

        OptionalLong total() {
            return view().total();
        }
    }

    /** Every mine, the data's first and in its order, then the ones only the shop has shown. */
    static List<Item> items() {
        MineCatalog cat = catalog.get();
        List<Item> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (Mine m : cat.mines()) {
            if (!m.world().isBlank()) {
                String server = cat.serverName(m.name());
                out.add(new Item(m.name(), m.world(), Optional.of(m), server));
                seen.add(server.toLowerCase(Locale.ROOT));
                seen.add(m.name().toLowerCase(Locale.ROOT));
            }
        }
        for (String known : prices.knownMines()) {
            if (seen.contains(known) || cat.boss(known).isPresent() || cat.byName(known).isPresent()) {
                continue;
            }
            if (!isMine(known)) {
                continue;
            }
            out.add(new Item(JobWorlds.title(known), JobWorlds.of(known, learned.get()), Optional.empty(), known));
        }
        return out;
    }

    /**
     * Whether a shop name the data does not list is a mine. Boss gear is paid in that boss's
     * fragments and its name says so ("Harmonizer Fragment"), a mine's in its own resource, so
     * anything paid in fragments is left out. A mine also sells a sword.
     */
    private static boolean isMine(String shopName) {
        var gears = prices.observedGears(shopName);
        if (!gears.contains("sword")) {
            return false;
        }
        for (String gear : gears) {
            for (int level = 1; level <= 6; level++) {
                var price = prices.price(shopName, gear, level);
                if (price.isPresent()) {
                    String currency = price.get().currency() == null ? "" : price.get().currency().toLowerCase(Locale.ROOT);
                    return !currency.contains("fragment");
                }
            }
        }
        return true;
    }

    /** Dimensions in order, the data's first and the ones the shop found last. */
    static List<String> worlds(List<Item> items) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (Item i : items) {
            out.add(i.world());
        }
        return new ArrayList<>(out);
    }

    static List<Item> in(List<Item> items, String world) {
        List<Item> out = new ArrayList<>();
        for (Item i : items) {
            if (i.world().equals(world)) {
                out.add(i);
            }
        }
        return out;
    }

    static Optional<Item> find(List<Item> items, String name) {
        for (Item i : items) {
            if (i.name().equalsIgnoreCase(name)) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }
}
