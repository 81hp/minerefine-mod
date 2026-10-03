package minerefinehud.progress;

import minerefinehud.mine.ResourcePickups;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Boss fragments carried in the inventory, the currency boss gear is bought with.
 *
 * Counted from the stacks themselves, so a boss bar knows the balance without the boss shop ever
 * being opened. Only changes are reported: a shop's figure (which also counts fragments kept
 * elsewhere) stands until the inventory count actually moves. A kind of fragment seen this
 * session and then gone is reported as 0, so spending the last of them empties the bar.
 */
public final class FragmentCounts {

    /** "Archaeologist Fragment", or "Archaeologist Fragments". */
    private static final Pattern FRAGMENT = Pattern.compile("^(?<boss>.+?)\\s+fragments?$",
            Pattern.CASE_INSENSITIVE);

    /** Last count per fragment, keyed lower case, with the name as the item spells it. */
    private final Map<String, Long> last = new LinkedHashMap<>();
    private final Map<String, String> spelling = new LinkedHashMap<>();

    /** The fragment totals that changed since the last look, by currency name. */
    public Map<String, Long> observe(List<ResourcePickups.Item> items) {
        Map<String, Long> now = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        if (items != null) {
            for (ResourcePickups.Item item : items) {
                if (item == null || item.name() == null || item.count() <= 0) {
                    continue;
                }
                String clean = ResourcePickups.cleanName(item.name());
                Matcher m = FRAGMENT.matcher(clean);
                if (!m.matches() || m.group("boss").contains("[")) {
                    continue;
                }
                String currency = m.group("boss").trim() + " Fragment";
                String key = currency.toLowerCase(Locale.ROOT);
                now.merge(key, (long) item.count(), Long::sum);
                names.putIfAbsent(key, currency);
            }
        }

        Map<String, Long> changed = new LinkedHashMap<>();
        now.forEach((key, count) -> {
            if (!count.equals(last.get(key))) {
                changed.put(names.get(key), count);
            }
        });
        for (Map.Entry<String, Long> before : last.entrySet()) {
            if (!now.containsKey(before.getKey()) && before.getValue() != 0L) {
                changed.put(spelling.get(before.getKey()), 0L);
            }
        }

        for (String key : last.keySet()) {
            last.put(key, 0L);
        }
        last.putAll(now);
        spelling.putAll(names);
        return changed;
    }

    /** Forget what was carried, so a relog is not read as fragments spent. */
    public void reset() {
        last.clear();
        spelling.clear();
    }
}
