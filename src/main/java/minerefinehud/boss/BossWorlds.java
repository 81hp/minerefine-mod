package minerefinehud.boss;

import minerefinehud.mine.Mine;
import minerefinehud.mine.MineCatalog;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Which world each boss lives in, so the boss panel can show only the dimension the player is in.
 *
 * Two sources. The bundled spreadsheet lists every boss under its world's tab. For a boss it does
 * not list, the world is learned: the server only sends a boss broadcast to players in that area,
 * so whatever world the player is in when the broadcast arrives is the boss's world. Nothing is
 * hardcoded, and a new dimension's boss is placed after its first spawn or kill.
 *
 * A boss whose world is not known yet is always shown, in whatever state the tracker has it. Hiding
 * it would make a freshly seen boss look like a missed broadcast.
 *
 * No Minecraft types.
 */
public final class BossWorlds {

    private record Learned(String displayName, String world) {}

    private final Map<String, Learned> learned = new LinkedHashMap<>();

    /**
     * Letters and digits only, accents folded, lower case. The spreadsheet writes "Guardian o'
     * Toole" and the chat "Guardian 'o Toole"; this makes them one boss.
     */
    public static String key(String name) {
        if (name == null) {
            return "";
        }
        String folded = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return folded.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    private static String sameWorld(String world) {
        return world == null ? "" : world.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * The world the player is in: the world of the mine they are at, or for a mine the data does
     * not know, the world tag on what they just mined ("RUINBOUND"). Empty when neither is known.
     */
    public static Optional<String> currentWorld(Optional<Mine> currentMine, Optional<String> resourceWorldTag) {
        Optional<String> fromMine = currentMine.map(Mine::world).filter(w -> !w.isBlank());
        if (fromMine.isPresent()) {
            return fromMine;
        }
        return resourceWorldTag.filter(w -> !w.isBlank());
    }

    /** The spreadsheet's world for this boss, else the learned one. */
    public Optional<String> worldOf(String bossName, MineCatalog catalog) {
        String k = key(bossName);
        for (Mine boss : catalog.bosses()) {
            if (key(boss.name()).equals(k) && !boss.world().isBlank()) {
                return Optional.of(boss.world());
            }
        }
        return Optional.ofNullable(learned.get(k)).map(Learned::world);
    }

    /**
     * Records the world a broadcast was heard in. Ignored for bosses the spreadsheet places, which
     * are already certain, and when the player's world is unknown.
     *
     * @return true if something new was learned, so the caller knows to save
     */
    public boolean learn(String bossName, Optional<String> world, MineCatalog catalog) {
        if (world.isEmpty() || world.get().isBlank() || key(bossName).isEmpty()) {
            return false;
        }
        for (Mine boss : catalog.bosses()) {
            if (key(boss.name()).equals(key(bossName))) {
                return false;
            }
        }
        Learned old = learned.get(key(bossName));
        if (old != null && sameWorld(old.world()).equals(sameWorld(world.get()))) {
            return false;
        }
        learned.put(key(bossName), new Learned(bossName.trim(), world.get().trim()));
        return true;
    }

    /**
     * The bosses to show. Everything when showing all worlds or when the player's world is not
     * known yet; otherwise this world's bosses plus any whose world is still unknown.
     */
    public List<BossTracker.BossView> filter(List<BossTracker.BossView> views, Optional<String> currentWorld,
                                             MineCatalog catalog, boolean showAllWorlds) {
        if (showAllWorlds || currentWorld.isEmpty()) {
            return views;
        }
        String here = sameWorld(currentWorld.get());
        List<BossTracker.BossView> out = new ArrayList<>();
        for (BossTracker.BossView v : views) {
            Optional<String> world = worldOf(v.displayName(), catalog);
            if (world.isEmpty() || sameWorld(world.get()).equals(here)) {
                out.add(v);
            }
        }
        return out;
    }

    public int learnedCount() {
        return learned.size();
    }

    // ---------------------------------------------------------- persistence

    /** Boss name, as first heard, to world. */
    public Map<String, String> export() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Learned l : learned.values()) {
            out.put(l.displayName(), l.world());
        }
        return out;
    }

    public void importAll(Map<String, String> saved) {
        if (saved == null) {
            return;
        }
        saved.forEach((name, world) -> {
            if (name != null && world != null && !key(name).isEmpty() && !world.isBlank()) {
                learned.put(key(name), new Learned(name.trim(), world.trim()));
            }
        });
    }
}
