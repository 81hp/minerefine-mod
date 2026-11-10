package minerefinehud.job;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Which dimension a mine belongs to when the bundled data does not list it, by Arreba.
 *
 * Mines added after the spreadsheet (the Sift's) are only known from their shop. Their dimension
 * is learned from the world tag on what they drop ("SIFTBOUND" is the Sift), and a few are known
 * ahead so they sort right before the player has mined there.
 */
public final class JobWorlds {

    private JobWorlds() {
    }

    /**
     * Where an unlisted mine goes when no dimension was learned for it. The Sift is the one area
     * added after the bundled data, so a mine only the shop knows is its.
     */
    public static final String DEFAULT = "Sift";

    /**
     * The Sift's mines in the order the game lists them. A group is one mine under the spellings
     * seen for it, so the shop's own spelling is found whichever it is.
     */
    public static final List<List<String>> SIFT = List.of(
            List.of("Shale"), List.of("Sulfer", "Sulfur"), List.of("Charoite"), List.of("Amber"),
            List.of("Thicket"), List.of("Bulb", "Blub"), List.of("Petal"), List.of("Jade"),
            List.of("Ichor"), List.of("Rosewood"), List.of("Siftite"));

    private static final Map<String, String> KNOWN = new HashMap<>();

    static {
        for (List<String> group : SIFT) {
            for (String name : group) {
                KNOWN.put(name.toLowerCase(Locale.ROOT), "Sift");
            }
        }
    }

    /** "SIFTBOUND" as "Sift"; empty for anything that is not a world tag. */
    public static Optional<String> fromTag(String tag) {
        if (tag == null) {
            return Optional.empty();
        }
        String t = tag.trim().toUpperCase(Locale.ROOT);
        if (!t.endsWith("BOUND") || t.length() <= 5) {
            return Optional.empty();
        }
        String word = t.substring(0, t.length() - 5);
        if (!word.matches("[A-Z]+")) {
            return Optional.empty();
        }
        return Optional.of(word.charAt(0) + word.substring(1).toLowerCase(Locale.ROOT));
    }

    /** Whether the mine is known to be a mine: named here, or one the player has mined in. */
    public static boolean known(String mine, Map<String, String> learned) {
        String key = mine == null ? "" : mine.trim().toLowerCase(Locale.ROOT);
        return KNOWN.containsKey(key) || (learned != null && learned.containsKey(key));
    }

    /** The learned dimension, else the known one, else {@link #DEFAULT}. */
    public static String of(String mine, Map<String, String> learned) {
        String key = mine == null ? "" : mine.trim().toLowerCase(Locale.ROOT);
        String l = learned == null ? null : learned.get(key);
        if (l != null && !l.isBlank()) {
            return l;
        }
        return KNOWN.getOrDefault(key, DEFAULT);
    }

    /** "ancient debris" as "Ancient Debris", for a mine known only by its shop name. */
    public static String title(String name) {
        StringBuilder out = new StringBuilder();
        for (String w : (name == null ? "" : name.trim()).split("\\s+")) {
            if (w.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return out.toString();
    }
}
