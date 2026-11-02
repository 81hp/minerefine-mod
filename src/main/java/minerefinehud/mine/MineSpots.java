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

        /** Blocks between the two on x/z, or infinite in another dimension. */
        public double distanceTo(Spot o) {
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
     * Empty when nothing is known nearby or two are about as close, and also when a candidate
     * cannot be placed at all: a new mine may be right here. Shale, in an area the price data
     * does not know yet, lost every block to a mine placed next to it.
     */
    public Optional<String> nearest(Collection<String> candidates, Spot here, MineCatalog catalog) {
        if (here == null || candidates == null) {
            return Optional.empty();
        }
        String best = null;
        double bestD = Double.POSITIVE_INFINITY;
        boolean bestOwn = false;
        Map<String, Double> dist = new LinkedHashMap<>();
        for (String candidate : candidates) {
            double d = distance(candidate, here, catalog);
            if (Double.isNaN(d)) {
                return Optional.empty();
            }
            boolean own = hasOwnSpot(candidate, here, catalog);
            dist.put(candidate, d);
            // On a tie, a mine's own spot beats one only placed by its world-mates: that estimate
            // is the distance to the nearest of them, so it ties with each of them standing on it.
            if (d < bestD || (d == bestD && own && !bestOwn)) {
                bestD = d;
                best = candidate;
                bestOwn = own;
            }
        }
        if (best == null || bestD > MAX_DISTANCE) {
            return Optional.empty();
        }
        // Standing where this mine itself is mined: only another mine's own spot as near argues.
        // Otherwise a mine placed by its world-mates is as near as any of them, and only clearly
        // nearer counts; Frost and a Crystalite placed by Frost's spot are both 0 away at Frost.
        boolean onItsSpot = bestOwn && bestD <= MOVED;
        double secondD = Double.POSITIVE_INFINITY;
        for (Map.Entry<String, Double> e : dist.entrySet()) {
            if (!e.getKey().equals(best) && (!onItsSpot || hasOwnSpot(e.getKey(), here, catalog))) {
                secondD = Math.min(secondD, e.getValue());
            }
        }
        if (bestD * 2.0 >= secondD) {
            return Optional.empty();
        }
        return Optional.of(best);
    }

    private boolean hasOwnSpot(String candidate, Spot here, MineCatalog catalog) {
        return spots.containsKey(key(candidate)) && !outOfArea(candidate, here, catalog);
    }

    /**
     * Distance to the candidate's own spot, else to the nearest spot of a mine in its world.
     * NaN for a mine with no spot that the price data does not know either, a new area's: it
     * could be anywhere, right here included.
     */
    private double distance(String candidate, Spot here, MineCatalog catalog) {
        if (outOfArea(candidate, here, catalog)) {
            return Double.POSITIVE_INFINITY;
        }
        Spot own = spots.get(key(candidate));
        if (own != null) {
            return own.distanceTo(here);
        }
        String world = worldOf(candidate, catalog);
        if (world.isEmpty()) {
            return Double.NaN;
        }
        double best = Double.POSITIVE_INFINITY;
        for (Map.Entry<String, Spot> e : spots.entrySet()) {
            if (world.equalsIgnoreCase(worldOf(e.getKey(), catalog))) {
                best = Math.min(best, e.getValue().distanceTo(here));
            }
        }
        return best;
    }

    /**
     * How far a mine may be from the other mines of its world. Each world's mines sit within
     * about 300 blocks of each other, and the worlds are thousands of blocks apart (Darkwater
     * Caves and Palace, the closest two, about 600).
     */
    static final double AREA = 500.0;

    /**
     * Whether the price data puts this mine in a world whose known mines are mostly far from
     * here, so the player cannot be mining it. Judged by the other mines of that world, never by
     * the mine's own spot: that spot is simply wherever its icon was last mined, so once an icon
     * shown by a new mine was taken for an old one, the old mine's spot moved to the new area and
     * agreed with the mistake from then on. The Sift's blocks were all read as mines from other
     * worlds this way, and Crystalite's spot ended up in the Sift.
     *
     * A mine the price data has no world for, a new area's, has only its own spot to go by: far
     * from it, it is not here. Such a spot cannot wander off the same way, as nothing of another
     * area is ever recorded for a mine that is out of area. False when nothing places the mine.
     */
    public boolean outOfArea(String mine, Spot here, MineCatalog catalog) {
        if (mine == null || mine.isBlank() || here == null || here.dimension() == null || catalog == null) {
            return false;
        }
        if (!hasWorld(mine, catalog)) {
            Spot own = spots.get(key(mine));
            return own != null && own.distanceTo(here) > AREA;
        }
        return !inArea(mine, here, catalog);
    }

    private boolean hasWorld(String mine, MineCatalog catalog) {
        return !worldOf(mine, catalog).isEmpty();
    }

    /**
     * Whether a majority of the known spots of this mine's world-mates are within {@link #AREA}
     * of the spot, true when that cannot be judged (no world, or no mate placed yet).
     */
    private boolean inArea(String mine, Spot at, MineCatalog catalog) {
        Optional<Mine> entry = entry(mine, catalog);
        String world = entry.map(Mine::world).orElse("");
        if (world.isEmpty()) {
            return true;
        }
        String self = entry.get().name();
        int mates = 0;
        int near = 0;
        for (Map.Entry<String, Spot> e : spots.entrySet()) {
            Optional<Mine> other = entry(e.getKey(), catalog);
            if (other.isEmpty() || other.get().name().equalsIgnoreCase(self)
                    || !world.equalsIgnoreCase(other.get().world())) {
                continue;
            }
            mates++;
            if (e.getValue().distanceTo(at) <= AREA) {
                near++;
            }
        }
        return mates == 0 || near * 2 >= mates;
    }

    /**
     * Forgets every spot that lies outside its own world's area, judged by at least two other
     * mines of that world: recorded while another area's block was taken for this mine.
     *
     * @return the mines forgotten
     */
    public java.util.Set<String> dropStrays(MineCatalog catalog) {
        java.util.Set<String> strays = new java.util.LinkedHashSet<>();
        if (catalog == null) {
            return strays;
        }
        for (Map.Entry<String, Spot> e : spots.entrySet()) {
            if (worldMates(e.getKey(), catalog) >= 2 && !inArea(e.getKey(), e.getValue(), catalog)) {
                strays.add(e.getKey());
            }
        }
        strays.forEach(spots::remove);
        return strays;
    }

    private int worldMates(String mine, MineCatalog catalog) {
        Optional<Mine> entry = entry(mine, catalog);
        if (entry.isEmpty() || entry.get().world() == null || entry.get().world().isEmpty()) {
            return 0;
        }
        int n = 0;
        for (String key : spots.keySet()) {
            Optional<Mine> other = entry(key, catalog);
            if (other.isPresent() && !other.get().name().equalsIgnoreCase(entry.get().name())
                    && entry.get().world().equalsIgnoreCase(other.get().world())) {
                n++;
            }
        }
        return n;
    }

    /**
     * {@link #record(String, Spot)}, except where the mine cannot be (see {@link #outOfArea}):
     * that block was another mine's, and its spot would put this mine in the wrong area.
     */
    public boolean record(String mine, Spot here, MineCatalog catalog) {
        if (outOfArea(mine, here, catalog)) {
            return false;
        }
        return record(mine, here);
    }

    /**
     * Mines sit a few dozen blocks apart (the Sift's 50 to 80), and a spot follows the player
     * within {@link #MOVED} while they mine there: further than this from a mine's own spot and
     * the player is at another mine.
     */
    public static final double NEAR = 64.0;

    /**
     * Whether this mine's own spot is known and further than {@link #NEAR}: the player is at
     * another mine, even in the same area. Mining Jade with a Blub pickaxe showed Blub.
     */
    public boolean awayFrom(String mine, Spot here) {
        Spot own = mine == null || here == null ? null : spots.get(key(mine));
        return own != null && own.distanceTo(here) > NEAR;
    }

    /** Whether this mine's own spot is known and within {@link #NEAR}. */
    public boolean near(String mine, Spot here) {
        Spot own = mine == null || here == null ? null : spots.get(key(mine));
        return own != null && own.distanceTo(here) <= NEAR;
    }

    /** Whether this mine's own spot is known and the player is far from it, or in another dimension. */
    public boolean elsewhere(String mine, Spot here) {
        Spot own = mine == null || here == null ? null : spots.get(key(mine));
        return own != null && own.distanceTo(here) > MAX_DISTANCE;
    }

    /** Forgets where a mine is. Returns true if a spot was known. */
    public boolean forget(String mine) {
        return mine != null && spots.remove(key(mine)) != null;
    }

    private String worldOf(String mine, MineCatalog catalog) {
        String world = entry(mine, catalog).map(Mine::world).orElse("");
        return world == null ? "" : world;
    }

    private MineCatalog cachedFor;
    private final Map<String, Optional<Mine>> entries = new java.util.HashMap<>();

    /**
     * The price data's entry for a mine, remembered per catalog: every block mined asks this for
     * every spot known, and the catalog matches names by pattern.
     */
    private Optional<Mine> entry(String mine, MineCatalog catalog) {
        if (catalog != cachedFor) {
            entries.clear();
            cachedFor = catalog;
        }
        return entries.computeIfAbsent(key(mine), catalog::exactly);
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
