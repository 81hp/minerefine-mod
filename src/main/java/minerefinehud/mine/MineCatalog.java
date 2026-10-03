package minerefinehud.mine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The parsed progression, with lookup helpers.
 *
 * The important method is {@link #detectFrom(String)}. Rather than depending on one exact
 * scoreboard layout, it scans arbitrary text for any known mine name and returns the longest
 * match. That survives the server reformatting its sidebar, and it correctly prefers
 * "Dark Prismarine" over "Prismarine" and "Pale Tree" over "Tree".
 */
public final class MineCatalog {

    private final List<Mine> all;
    private final List<Mine> mines;
    private final List<Mine> bosses;
    private final Map<String, Mine> byKey;

    /** Name patterns, longest name first so the greediest legitimate match wins. */
    private final List<Map.Entry<String, Pattern>> detectors;

    public MineCatalog(List<Mine> entries) {
        this.all = List.copyOf(entries);

        List<Mine> m = new ArrayList<>();
        List<Mine> b = new ArrayList<>();
        Map<String, Mine> index = new LinkedHashMap<>();

        for (Mine entry : all) {
            if (entry.isMine()) {
                m.add(entry);
            } else if (entry.isBoss()) {
                b.add(entry);
            }
            index.putIfAbsent(entry.key(), entry);
        }

        this.mines = List.copyOf(m);
        this.bosses = List.copyOf(b);
        this.byKey = Collections.unmodifiableMap(index);

        // Every spelling that should resolve to a catalog name: the name itself, plus any server
        // spelling that differs from it. Sorted longest first so "Rusty" is tried before "Rust".
        List<Map.Entry<String, String>> spellings = new ArrayList<>();
        for (Mine entry : mines) {
            if (!entry.name().isEmpty()) {
                spellings.add(Map.entry(entry.name(), entry.name()));
            }
        }
        for (Map.Entry<String, String> alias : SERVER_SPELLINGS.entrySet()) {
            if (index.containsKey(keyOf(alias.getValue()))) {
                spellings.add(alias);
            }
        }
        spellings.sort((x, y) -> Comparators.BY_LENGTH_DESC.compare(x.getKey(), y.getKey()));

        List<Map.Entry<String, Pattern>> built = new ArrayList<>(spellings.size());
        for (Map.Entry<String, String> spelling : spellings) {
            built.add(Map.entry(spelling.getValue(), Pattern.compile(
                    "(?<![\\p{L}\\p{N}])" + Pattern.quote(fold(spelling.getKey())) + "(?![\\p{L}\\p{N}])",
                    Pattern.CASE_INSENSITIVE)));
        }
        this.detectors = List.copyOf(built);
    }

    /**
     * Gear spelling to spreadsheet spelling, where the two differ.
     *
     * Shop items and the gear in the inventory are named "[Rust Pickaxe]", "[Pine Sword]" and
     * "[Pale Wood Helmet]", while the spreadsheet says Rusty, Pine Tree and Pale Tree. Shop
     * prices are stored under the gear spelling, so without these the mine panel and progress
     * bar looked under the spreadsheet name and found nothing. Seen in game, October 2026.
     *
     * The currency can differ again: Pine and Pale Wood gear is paid for in "Pine Tree" and
     * "Pale Tree". Prices carry their own currency, so that needs no entry here.
     *
     * Entries whose spreadsheet side is missing are ignored. Bosses are deliberately not listed
     * yet ("Archaeologist" is Angry Archaeologist): an entry here also makes the name detectable
     * as a mine, and a boss is not one.
     */
    private static final Map<String, String> SERVER_SPELLINGS = Map.of(
            "Rust", "Rusty",
            "Pine", "Pine Tree",
            "Pale Wood", "Pale Tree");

    /**
     * The mine whose name is the whole of this text, in any known spelling, rather than merely
     * appearing in it. "Zircon" is Zircon; "Zircon Pickaxe" is not.
     */
    public Optional<Mine> exactly(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String folded = fold(text).replaceAll("\\s+", " ").trim();
        for (Map.Entry<String, Pattern> detector : detectors) {
            Matcher m = detector.getValue().matcher(folded);
            if (m.find() && m.start() == 0 && m.end() == folded.length()) {
                return byName(detector.getKey());
            }
        }
        return Optional.empty();
    }

    /**
     * The name as the server spells it, for any spelling of a known mine. Shop prices and item
     * names are keyed by the server spelling, so anything that looks them up from a catalog name
     * must go through this first. Unknown names come back unchanged.
     */
    public String serverName(String name) {
        Optional<Mine> known = detectFrom(name);
        if (known.isEmpty()) {
            return name == null ? "" : name.trim();
        }
        String catalogName = known.get().name();
        for (Map.Entry<String, String> alias : SERVER_SPELLINGS.entrySet()) {
            if (alias.getValue().equals(catalogName)) {
                return alias.getKey();
            }
        }
        return catalogName;
    }

    /** Strips accents, so "Björn" in the data and "Bjorn" on the server compare equal. */
    private static String fold(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
    }

    private static String keyOf(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    /**
     * The spreadsheet's whole-piece cost for one item at one mine, armour pieces included, or
     * empty for a mine or item it does not list.
     */
    public java.util.OptionalLong pieceTotal(String mine, String gear) {
        long v = exactly(mine).or(() -> boss(mine)).map(m -> m.pieceCost(gear)).orElse(0L);
        return v > 0L ? java.util.OptionalLong.of(v) : java.util.OptionalLong.empty();
    }

    /**
     * The boss whose gear the shop sells under this name. Boss gear is named with a short form:
     * "Archaeologist" for Angry Archaeologist, "Guardian o'" for Guardian o' Toole, "Bjorn" for
     * Bjorn Rebjorn. Matched on letters only, where the full name is, starts with or ends with
     * the short one, and only when exactly one boss fits. Not used for mine detection: a boss is
     * not a place to stand.
     */
    public Optional<Mine> boss(String shopName) {
        String k = letters(shopName);
        if (k.length() < 4) {
            return Optional.empty();
        }
        Mine found = null;
        for (Mine b : bosses) {
            String full = letters(b.name());
            if (full.equals(k) || full.startsWith(k) || full.endsWith(k)) {
                if (found != null) {
                    return Optional.empty();
                }
                found = b;
            }
        }
        if (found != null || k.length() < 5) {
            return Optional.ofNullable(found);
        }
        // The server and the sheet can differ by a letter: "Atheris" gear for the sheet's Aetheris.
        for (Mine b : bosses) {
            if (oneLetterOff(k, letters(b.name()))) {
                if (found != null) {
                    return Optional.empty();
                }
                found = b;
            }
        }
        return Optional.ofNullable(found);
    }

    /** One letter added, dropped or changed, and no more. */
    public static boolean oneLetterOff(String a, String b) {
        if (a.equals(b) || Math.abs(a.length() - b.length()) > 1) {
            return false;
        }
        int i = 0;
        int j = 0;
        int edits = 0;
        while (i < a.length() && j < b.length()) {
            if (a.charAt(i) == b.charAt(j)) {
                i++;
                j++;
                continue;
            }
            if (++edits > 1) {
                return false;
            }
            if (a.length() > b.length()) {
                i++;
            } else if (b.length() > a.length()) {
                j++;
            } else {
                i++;
                j++;
            }
        }
        return edits + (a.length() - i) + (b.length() - j) <= 1;
    }

    private static String letters(String s) {
        return s == null ? "" : fold(s).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    public List<Mine> all() {
        return all;
    }

    public List<Mine> mines() {
        return mines;
    }

    public List<Mine> bosses() {
        return bosses;
    }

    public boolean isEmpty() {
        return all.isEmpty();
    }

    public Optional<Mine> byName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String k = name.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        return Optional.ofNullable(byKey.get(k));
    }

    /**
     * Finds the mine named anywhere in the supplied text, such as a scoreboard sidebar or an
     * action bar line. Returns the longest matching name, or empty if none is present.
     */
    public Optional<Mine> detectFrom(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String haystack = fold(text).replaceAll("\\s+", " ");
        for (Map.Entry<String, Pattern> detector : detectors) {
            Matcher matcher = detector.getValue().matcher(haystack);
            if (matcher.find()) {
                return byName(detector.getKey());
            }
        }
        return Optional.empty();
    }

    /** Same as {@link #detectFrom(String)} but across several lines, joined before scanning. */
    public Optional<Mine> detectFrom(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return Optional.empty();
        }
        return detectFrom(String.join(" \n ", lines));
    }

    private static final class Comparators {
        static final java.util.Comparator<String> BY_LENGTH_DESC =
                java.util.Comparator.comparingInt(String::length).reversed()
                        .thenComparing(java.util.Comparator.naturalOrder());
    }
}
