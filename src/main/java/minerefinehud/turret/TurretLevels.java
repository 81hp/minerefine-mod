package minerefinehud.turret;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turret level-ups read from chat, kept as the last few lines for the HUD.
 *
 * <pre>
 *   Jade Turret has leveled up to 117 -&gt; Level 118
 * </pre>
 * shown as {@code jade 117->118}. Only the last {@link #KEPT} are remembered.
 */
public final class TurretLevels {

    public static final int KEPT = 5;

    /**
     * "Jade Turret has leveled up to 117 -> Level 118", as the whole line; the server ends it with
     * a full stop. Whole line, not anywhere in it: a player typing the same words has their rank
     * and name in front ("[★508] 81hp_: Jade Turret ..."), which a turret name of letters cannot
     * match, so it never counts.
     */
    private static final Pattern LINE = Pattern.compile(
            "^(?<name>[\\p{L}][\\p{L}'’ -]{0,40}?)\\s+turret has leveled up(?:\\s+(?:to|from))?(?:\\s+level)?\\s+"
                    + "(?<from>\\d[\\d,]*)\\s*(?:->|→|to)\\s*(?:level\\s*)?(?<to>\\d[\\d,]*)\\.?$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern FORMATTING = Pattern.compile("[§&][0-9A-Fa-fK-Ok-oRr]");
    private static final Pattern TRAILING_INVISIBLE = Pattern.compile("[\\s\\p{Cf}\\p{Co}]+$");

    private TurretLevels() {
    }

    public record LevelUp(String name, int from, int to) {
        /** {@code jade 117->118}. */
        public String label() {
            return name + " " + from + "->" + to;
        }
    }

    /** One saved row. Public fields so the config file can store the list. */
    public static final class Saved {
        public String name = "";
        public int from;
        public int to;
        public long at;
    }

    public static Optional<LevelUp> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String s = FORMATTING.matcher(raw).replaceAll("");
        // Only the end is trimmed: a line starting with a space is some other message's indent,
        // as RareDrops treats it.
        s = TRAILING_INVISIBLE.matcher(s).replaceAll("");
        Matcher m = LINE.matcher(s);
        if (!m.find()) {
            return Optional.empty();
        }
        int from;
        int to;
        try {
            // The server writes big numbers with commas elsewhere ("45,395"), so 1,000 may come too.
            from = Integer.parseInt(m.group("from").replace(",", ""));
            to = Integer.parseInt(m.group("to").replace(",", ""));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        String name = m.group("name").trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (name.isEmpty() || from < 0 || to <= from) {
            return Optional.empty();
        }
        return Optional.of(new LevelUp(name, from, to));
    }

    /** Newest first, capped at {@link #KEPT}. */
    public static final class Log {
        private final List<Saved> recent;

        public Log(List<Saved> recent) {
            this.recent = recent != null ? recent : new ArrayList<>();
            this.recent.removeIf(s -> s == null || s.name == null || s.name.isBlank() || s.to <= s.from);
            while (this.recent.size() > KEPT) {
                this.recent.remove(this.recent.size() - 1);
            }
        }

        public List<Saved> saved() {
            return recent;
        }

        /**
         * @return false when this is the same level-up again within a few seconds, so a line
         *         echoed into both chat windows is stored once
         */
        public boolean add(LevelUp up, long now) {
            if (!recent.isEmpty()) {
                Saved top = recent.get(0);
                if (top.name.equals(up.name()) && top.from == up.from() && top.to == up.to()
                        && now - top.at < 5_000L) {
                    return false;
                }
            }
            Saved row = new Saved();
            row.name = up.name();
            row.from = up.from();
            row.to = up.to();
            row.at = now;
            recent.add(0, row);
            while (recent.size() > KEPT) {
                recent.remove(recent.size() - 1);
            }
            return true;
        }
    }
}
