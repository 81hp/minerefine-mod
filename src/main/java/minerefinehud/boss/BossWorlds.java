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
        Optional<String> fromSheet = sheetBoss(bossName, catalog).map(Mine::world).filter(w -> !w.isBlank());
        if (fromSheet.isPresent()) {
            return fromSheet;
        }
        return Optional.ofNullable(learned.get(key(bossName))).map(Learned::world);
    }

    /** Words that say nothing about which boss it is: "The Watcher" is the sheet's Watcher. */
    private static final java.util.Set<String> FILLER = java.util.Set.of("the", "angry");

    /**
     * The spreadsheet's entry for a boss, however the chat spells it. The chat and the sheet
     * disagree more than they agree: "The Watcher" for Watcher, "Atheris" for Aetheris, "Björn
     * Ironside" for Bjorn Rebjorn. Unmatched, such a boss had no world, so it showed in every
     * world, and a broadcast heard after a warp could teach it the wrong one.
     *
     * Tried in order, each only when exactly one boss fits: the same letters; the same without
     * "the" and "angry"; one letter off; a shared word of four letters or more.
     */
    static Optional<Mine> sheetBoss(String bossName, MineCatalog catalog) {
        String k = key(bossName);
        if (k.isEmpty()) {
            return Optional.empty();
        }
        List<Mine> bosses = catalog.bosses();
        Optional<Mine> found = only(bosses, b -> key(b.name()).equals(k));
        if (found.isEmpty()) {
            String core = core(bossName);
            found = only(bosses, b -> !core.isEmpty() && core(b.name()).equals(core));
            if (found.isEmpty() && core.length() >= 5) {
                found = only(bosses, b -> MineCatalog.oneLetterOff(core, core(b.name())));
            }
            if (found.isEmpty()) {
                List<String> words = words(bossName);
                found = only(bosses, b -> words(b.name()).stream().anyMatch(words::contains));
            }
        }
        return found;
    }

    private static Optional<Mine> only(List<Mine> bosses, java.util.function.Predicate<Mine> test) {
        Mine found = null;
        for (Mine b : bosses) {
            if (test.test(b)) {
                if (found != null) {
                    return Optional.empty();
                }
                found = b;
            }
        }
        return Optional.ofNullable(found);
    }

    /** The name's words, accents folded and lower case, the filler words left out. */
    private static List<String> allWords(String name) {
        String folded = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String w : folded.split("[^\\p{L}\\p{N}']+")) {
            String word = w.replace("'", "");
            if (!word.isEmpty() && !FILLER.contains(word)) {
                out.add(word);
            }
        }
        return out;
    }

    private static String core(String name) {
        return String.join("", allWords(name));
    }

    /** Words long enough to say which boss it is: "bjorn" yes, the "o" of Guardian o' Toole no. */
    private static List<String> words(String name) {
        return allWords(name).stream().filter(w -> w.length() >= 4).toList();
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
        if (sheetBoss(bossName, catalog).isPresent()) {
            return false;
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
