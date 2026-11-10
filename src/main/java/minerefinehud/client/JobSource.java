package minerefinehud.client;

import minerefinehud.mine.Mine;
import minerefinehud.mine.MineCatalog;
import minerefinehud.progress.MineCosts;
import minerefinehud.shop.PriceLedger;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Where the job calculator and the price lookup get the mines and their prices, by Arreba: the
 * same catalog and shop prices the Mine panel uses. The client sets it once at start-up.
 */
final class JobSource {

    private static Supplier<MineCatalog> catalog = () -> new MineCatalog(List.of());
    private static PriceLedger prices = new PriceLedger();

    private JobSource() {
    }

    static void set(Supplier<MineCatalog> catalogSource, PriceLedger ledger) {
        catalog = catalogSource;
        prices = ledger;
    }

    /** Every dimension that has a mine, in the order the data lists them. */
    static List<String> worlds() {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (Mine m : catalog.get().mines()) {
            if (!m.world().isBlank()) {
                out.add(m.world());
            }
        }
        return new ArrayList<>(out);
    }

    static List<Mine> mines(String world) {
        List<Mine> out = new ArrayList<>();
        for (Mine m : catalog.get().mines()) {
            if (m.world().equals(world)) {
                out.add(m);
            }
        }
        return out;
    }

    static Optional<Mine> mine(String name) {
        return catalog.get().byName(name);
    }

    /** The mine's gear and total exactly as the Mine panel works them out, armour as four pieces. */
    static MineCosts.View view(Mine m) {
        var cat = catalog.get();
        return MineCosts.of(cat.serverName(m.name()), Optional.of(m), prices, true);
    }
}
