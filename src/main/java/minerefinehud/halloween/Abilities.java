package minerefinehud.halloween;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The hoe's two abilities and when each can be used again.
 *
 * The player picks a class in the Farming Abilities menu. Each has an ability on drop and one on
 * shift-drop, with their cooldowns in the tooltip:
 *
 *   Jack o'Lantern
 *   Every 100 crops: Candlelight
 *   Drop [Q]: Will-o'-Wisps (7s)
 *   Shift drop [Shift+Q]: Harvest Moon (20s)
 *   ✔ SELECTED
 *
 * The server says nothing when an ability fires, only "Will-o'-Wisps is recharging (2.31s)" when
 * it is pressed too early and "Will-o'-Wisps has recharged! [DROP]" when it is back. So a cooldown
 * starts when the player presses drop with a hoe, is corrected by "recharging", and ends at
 * "recharged". Pressed and nothing fired, the timer simply runs out at the cooldown.
 *
 * No Minecraft types.
 */
public final class Abilities {

    public enum Key { DROP, SHIFT_DROP }

    public record Ability(String name, double cooldownSeconds) {}

    public record ClassSpec(String name, Ability drop, Ability shiftDrop) {

        Ability on(Key key) {
            return key == Key.DROP ? drop : shiftDrop;
        }
    }

    /** The two classes as first seen. The menu overrides these, so changed cooldowns follow it. */
    public static List<ClassSpec> bundled() {
        return List.of(
                new ClassSpec("Jack o'Lantern", new Ability("Will-o'-Wisps", 7), new Ability("Harvest Moon", 20)),
                new ClassSpec("Witch", new Ability("Homing Hex", 7), new Ability("Coven Circle", 20)));
    }

    private static final Pattern DROP_LINE = Pattern.compile(
            "^drop\\s*(?:\\[[^\\]]*\\])?\\s*:\\s*(?<name>.+?)\\s*\\((?<secs>[0-9]+(?:\\.[0-9]+)?)s\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SHIFT_LINE = Pattern.compile(
            "^shift[ -]?drop\\s*(?:\\[[^\\]]*\\])?\\s*:\\s*(?<name>.+?)\\s*\\((?<secs>[0-9]+(?:\\.[0-9]+)?)s\\)",
            Pattern.CASE_INSENSITIVE);

    private final Map<String, ClassSpec> classes = new LinkedHashMap<>();
    private String selected = "";
    /** Per ability name (key()), when it is ready again. Absent or past means ready. */
    private final Map<String, Long> readyAt = new HashMap<>();
    /** Abilities whose "ready" has not been announced yet, for the sound. */
    private final Map<String, Boolean> waiting = new HashMap<>();

    public Abilities() {
        for (ClassSpec c : bundled()) {
            classes.put(key(c.name()), c);
        }
    }

    static String key(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    // ------------------------------------------------------------- learning

    /**
     * Reads one item of the Farming Abilities menu. Returns true if anything changed, so the
     * caller knows to save.
     */
    public boolean learnFromMenu(String title, List<String> lore) {
        if (lore == null) {
            return false;
        }
        Ability drop = null;
        Ability shift = null;
        boolean isSelected = false;
        for (String raw : lore) {
            String line = HalloweenText.strip(raw);
            Matcher s = SHIFT_LINE.matcher(line);
            if (s.find()) {
                shift = new Ability(s.group("name").trim(), Double.parseDouble(s.group("secs")));
                continue;
            }
            Matcher d = DROP_LINE.matcher(line);
            if (d.find()) {
                drop = new Ability(d.group("name").trim(), Double.parseDouble(d.group("secs")));
                continue;
            }
            if (line.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "").equals("SELECTED")) {
                isSelected = true;
            }
        }
        if (drop == null || shift == null) {
            return false;
        }
        String name = HalloweenText.strip(title).replaceAll("^[^\\p{L}]+|[^\\p{L}']+$", "");
        ClassSpec spec = new ClassSpec(name, drop, shift);
        boolean changed = !spec.equals(classes.get(key(name)));
        classes.put(key(name), spec);
        if (isSelected && !key(name).equals(selected)) {
            selected = key(name);
            changed = true;
        }
        return changed;
    }

    /** The class that has this ability, so a chat message alone tells which class is picked. */
    private Optional<ClassSpec> classWith(String ability) {
        String k = key(ability);
        for (ClassSpec c : classes.values()) {
            if (key(c.drop().name()).equals(k) || key(c.shiftDrop().name()).equals(k)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    public Optional<ClassSpec> selected() {
        return Optional.ofNullable(classes.get(selected));
    }

    // --------------------------------------------------------------- events

    /** Drop or shift-drop pressed with a hoe. Starts the cooldown unless one is running. */
    public void pressed(Key key, long now) {
        selected().map(c -> c.on(key)).ifPresent(a -> {
            Long ready = readyAt.get(key(a.name()));
            if (ready == null || ready <= now) {
                start(a.name(), (long) (a.cooldownSeconds() * 1000.0), now);
            }
        });
    }

    /** "X is recharging (2.31s)": the exact time left. Returns true if the class was learned from it. */
    public boolean recharging(String ability, double secondsLeft, long now) {
        boolean learned = learnSelectionFrom(ability);
        start(ability, (long) (secondsLeft * 1000.0), now);
        return learned;
    }

    /** "X has recharged!": usable now. Returns true if the class was learned from it. */
    public boolean recharged(String ability, long now) {
        boolean learned = learnSelectionFrom(ability);
        readyAt.put(key(ability), now);
        return learned;
    }

    private boolean learnSelectionFrom(String ability) {
        Optional<ClassSpec> c = classWith(ability);
        if (c.isPresent() && !key(c.get().name()).equals(selected)) {
            selected = key(c.get().name());
            return true;
        }
        return false;
    }

    private void start(String ability, long ms, long now) {
        readyAt.put(key(ability), now + Math.max(0L, ms));
        waiting.put(key(ability), true);
    }

    /** Abilities that came off cooldown since the last call, each once, for the sound. */
    public List<String> newlyReady(long now) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Boolean> e : waiting.entrySet()) {
            Long ready = readyAt.get(e.getKey());
            if (e.getValue() && (ready == null || ready <= now)) {
                e.setValue(false);
                out.add(e.getKey());
            }
        }
        return out;
    }

    // ----------------------------------------------------------------- view

    /** @param msLeft 0 when ready */
    public record Timer(String ability, Key key, long msLeft) {

        public boolean ready() {
            return msLeft <= 0L;
        }
    }

    /** The selected class's two abilities. Empty until the class is known. */
    public List<Timer> timers(long now) {
        List<Timer> out = new ArrayList<>();
        selected().ifPresent(c -> {
            for (Key k : Key.values()) {
                Ability a = c.on(k);
                Long ready = readyAt.get(key(a.name()));
                out.add(new Timer(a.name(), k, ready == null ? 0L : Math.max(0L, ready - now)));
            }
        });
        return out;
    }

    // ---------------------------------------------------------- persistence

    /** Learned classes as "drop name|seconds|shift name|seconds" by class name, for config.json. */
    public Map<String, String> exportClasses() {
        Map<String, String> out = new LinkedHashMap<>();
        for (ClassSpec c : classes.values()) {
            out.put(c.name(), c.drop().name() + "|" + c.drop().cooldownSeconds() + "|"
                    + c.shiftDrop().name() + "|" + c.shiftDrop().cooldownSeconds());
        }
        return out;
    }

    public void importClasses(Map<String, String> saved, String selectedClass) {
        if (saved != null) {
            saved.forEach((name, value) -> {
                try {
                    String[] p = value.split("\\|");
                    if (name != null && p.length == 4) {
                        classes.put(key(name), new ClassSpec(name,
                                new Ability(p[0], Double.parseDouble(p[1])),
                                new Ability(p[2], Double.parseDouble(p[3]))));
                    }
                } catch (RuntimeException ignored) {
                    // A hand-edited entry that does not parse is skipped, not fatal.
                }
            });
        }
        if (selectedClass != null && classes.containsKey(key(selectedClass))) {
            selected = key(selectedClass);
        }
    }

    public String selectedName() {
        return selected().map(ClassSpec::name).orElse("");
    }
}
