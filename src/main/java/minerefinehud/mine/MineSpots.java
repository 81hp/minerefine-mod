package minerefinehud.mine;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Where each mine is, so a block icon two mines share can be told apart by where the player stands.
 *
 * Woodland Copper and Rust both show deepslate copper ore, and nothing in the action bar or the
 * inventory says which one is being mined: resources go to a balance, not the inventory. But one
 * is in Woodland and the other in Trials. Every block of a mine the icon does identify (Podzol,
 * Vase, Lodestone...) records where that mine is, so the areas are known without ever opening a
 * shop, and a shared icon goes to the candidate whose area the player is in.
 *
 * A candidate is placed by its own spot, or else by the spots of the other mines in its world.
 * Spots in another dimension never count. Saved by the caller.
 *
 * No Minecraft types.
 */
public final class MineSpots {

    /** Where a mine was last mined: the dimension id and the block coordinates. */
    public record Spot(String dimension, double x, double z) {

        double distanceTo(Spot o) {
            if (dimension == null || !dimension.equals(o.dimension)) {
                return Double.POSITIVE_INFINITY;
            }
            return Math.hypot(x - o.x, z - o.z);
        }
    }

    /** Further than this from every known spot of a candidate's area, and it is not that one. */
    static final double MAX_DISTANCE = 400.0;

    /** Moving less than this does not count as a new spot, so saving stays rare. */
    static final double MOVED = 24.0;

    private final Map<String, Spot> spots = new LinkedHashMap<>();

    /**
     * Records that this mine is being mined here.
     *
     * @return true if that is new or the spot moved, so the caller knows to save
     */
    public boolean record(String mine, Spot here) {
        if (mine == null || mine.isBlank() || here == null || here.dimension() == null) {
            return false;
        }
        String key = key(mine);
        Spot old = spots.get(key);
        if (old != null && old.distanceTo(here) < MOVED) {
            return false;
        }
        spots.put(key, here);
        return true;
    }

    /**
     * The candidate whose area the player is in: nearest by its own spot or its world's, within
     * {@link #MAX_DISTANCE}, and clearly nearer than the next one (half the distance or less).
     * Empty when nothing is known nearby or two are about as close.
     */
    public Optional<String> nearest(Collection<String> candidates, Spot here, MineCatalog catalog) {
        if (here == null || candidates == null) {
            return Optional.empty();
        }
        String best = null;
        double bestD = Double.POSITIVE_INFINITY;
        double secondD = Double.POSITIVE_INFINITY;
        for (String candidate : candidates) {
            double d = distance(candidate, here, catalog);
            if (d < bestD) {
                secondD = bestD;
                bestD = d;
                best = candidate;
            } else if (d < secondD) {
                secondD = d;
            }
        }
        if (best == null || bestD > MAX_DISTANCE || bestD * 2.0 > secondD) {
            return Optional.empty();
        }
        return Optional.of(best);
    }

    /** Distance to the candidate's own spot, else to the nearest spot of a mine in its world. */
    private double distance(String candidate, Spot here, MineCatalog catalog) {
        Spot own = spots.get(key(candidate));
        if (own != null) {
            return own.distanceTo(here);
        }
        String world = worldOf(candidate, catalog);
        if (world.isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        double best = Double.POSITIVE_INFINITY;
        for (Map.Entry<String, Spot> e : spots.entrySet()) {
            if (world.equalsIgnoreCase(worldOf(e.getKey(), catalog))) {
                best = Math.min(best, e.getValue().distanceTo(here));
            }
        }
        return best;
    }

    private static String worldOf(String mine, MineCatalog catalog) {
        return catalog.exactly(mine).map(Mine::world).orElse("");
    }

    private static String key(String mine) {
        return mine.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public int size() {
        return spots.size();
    }

    // ---------------------------------------------------------- persistence

    public Map<String, Spot> export() {
        return new LinkedHashMap<>(spots);
    }

    public void importAll(Map<String, Spot> saved) {
        if (saved != null) {
            saved.forEach((mine, spot) -> {
                if (mine != null && !mine.isBlank() && spot != null && spot.dimension() != null) {
                    spots.put(key(mine), spot);
                }
            });
        }
    }
}
