package minerefinehud.progress;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Which mine comes before which, per kind of item, as read from shop prerequisites.
 *
 * The bundled data knows the order of every mine that existed when the spreadsheet was made. A
 * dimension added after that is unknown to it, so the progress bar would stop at the last known
 * mine. The shop fills the gap: tier I of a new mine's shovel names the shovel it requires, and
 * that is exactly the link the planner needs. Per item kind, because tools chain by type: Debris
 * Shovel follows Suspicious Sand Shovel, skipping the pickaxe mines in between.
 *
 * No Minecraft types. Persisted by the caller as a plain map.
 */
public final class ProgressionLinks {

    /** One learned link, spelled as the server spells it. */
    private record Link(String gear, String mine, String previousMine) {}

    /** Keyed by gear and mine, normalised, so spacing and case never create duplicates. */
    private final Map<String, Link> links = new LinkedHashMap<>();

    private static String normalise(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static String key(String gear, String mine) {
        return normalise(gear) + '|' + normalise(mine);
    }

    /** @return true if this is new or different, so the caller knows to save */
    public boolean learn(String mine, String gear, String previousMine) {
        if (mine == null || mine.isBlank() || gear == null || gear.isBlank()
                || previousMine == null || previousMine.isBlank()) {
            return false;
        }
        Link fresh = new Link(normalise(gear), mine.trim(), previousMine.trim());
        Link old = links.put(key(gear, mine), fresh);
        return old == null || !normalise(old.previousMine()).equals(normalise(fresh.previousMine()));
    }

    public Optional<String> previous(String mine, String gear) {
        return Optional.ofNullable(links.get(key(gear, mine))).map(Link::previousMine);
    }

    /** The mine whose item of this kind requires this mine's, if one has been seen. */
    public Optional<String> next(String mine, String gear) {
        String g = normalise(gear);
        String m = normalise(mine);
        for (Link link : links.values()) {
            if (link.gear().equals(g) && normalise(link.previousMine()).equals(m)) {
                return Optional.of(link.mine());
            }
        }
        return Optional.empty();
    }

    public int size() {
        return links.size();
    }

    // ---------------------------------------------------------- persistence

    /** "shovel|Debris" -> "Suspicious Sand", so the saved file reads naturally. */
    public Map<String, String> export() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Link link : links.values()) {
            out.put(link.gear() + '|' + link.mine(), link.previousMine());
        }
        return out;
    }

    public void importAll(Map<String, String> saved) {
        if (saved == null) {
            return;
        }
        saved.forEach((k, v) -> {
            int bar = k == null ? -1 : k.indexOf('|');
            if (bar > 0) {
                learn(k.substring(bar + 1), k.substring(0, bar), v);
            }
        });
    }
}
