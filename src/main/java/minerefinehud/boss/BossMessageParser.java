package minerefinehud.boss;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the server's boss broadcasts out of a plain chat line.
 *
 * Deliberately contains no Minecraft types. That keeps it unit-testable without a running game,
 * which matters because this is the one piece that silently breaks when the server reformats
 * its messages.
 *
 * Handles, from observed server output:
 *   "BOSS ALERT"
 *   "Angry Archaeologist has spawned!"
 *   "(X) Angry Archaeologist has been slain! (X)"
 *   "The King & Queen have spawned!" (a pair takes "have")
 */
public final class BossMessageParser {

    public enum Kind { SPAWNED, SLAIN }

    /** A recognised broadcast. {@code bossName} is cleaned of colour codes and decoration. */
    public record Event(Kind kind, String bossName) {}

    /** Legacy section-sign and ampersand colour codes. */
    private static final Pattern FORMATTING = Pattern.compile("[§&][0-9A-Fa-fK-Ok-oRr]");

    /**
     * Anything that cannot legitimately appear inside a boss name. Unicode letters are kept so
     * names like "Bjorn Gear" survive, as are apostrophes and hyphens for "Guardian 'o Toole"
     * and "T-Gardener", and ampersands for "The King & Queen". The exclamation mark is kept
     * because the trigger phrase needs it.
     */
    private static final Pattern DECORATION = Pattern.compile("[^\\p{L}\\p{N} '\\-.!&]");

    /** "has" for one boss, "have" for a pair like "The King & Queen". */
    private static final Pattern SPAWNED =
            Pattern.compile("^(?<name>.+?)\\s+ha(?:s|ve)\\s+spawned!", Pattern.CASE_INSENSITIVE);

    private static final Pattern SLAIN =
            Pattern.compile("^(?<name>.+?)\\s+ha(?:s|ve)\\s+been\\s+slain!", Pattern.CASE_INSENSITIVE);

    /**
     * Banner words the server puts on the same line as the name. Order matters: longest first,
     * so "BOSS ALERT" is consumed before the bare "BOSS" rule can bite into it.
     */
    private static final String[] BANNER_PREFIXES = { "BOSS ALERT", "ALERT", "BOSS" };

    private BossMessageParser() {
    }

    /** Returns the event this line represents, or empty if the line is not a boss broadcast. */
    public static Optional<Event> parse(String rawLine) {
        if (rawLine == null || rawLine.isEmpty()) {
            return Optional.empty();
        }

        String line = clean(rawLine);
        if (line.isEmpty()) {
            return Optional.empty();
        }

        Matcher slain = SLAIN.matcher(line);
        if (slain.find()) {
            return event(Kind.SLAIN, slain.group("name"));
        }

        Matcher spawned = SPAWNED.matcher(line);
        if (spawned.find()) {
            return event(Kind.SPAWNED, spawned.group("name"));
        }

        return Optional.empty();
    }

    private static Optional<Event> event(Kind kind, String rawName) {
        String name = stripBanner(rawName);
        return name.isEmpty() ? Optional.empty() : Optional.of(new Event(kind, name));
    }

    /** Strips colour codes and decorative glyphs, then collapses whitespace. */
    private static String clean(String raw) {
        String s = FORMATTING.matcher(raw).replaceAll("");
        s = DECORATION.matcher(s).replaceAll(" ");
        return s.replaceAll("\\s+", " ").trim();
    }

    private static String stripBanner(String rawName) {
        String name = rawName.trim();
        boolean changed = true;
        while (changed) {
            changed = false;
            String upper = name.toUpperCase(Locale.ROOT);
            for (String prefix : BANNER_PREFIXES) {
                if (upper.startsWith(prefix)) {
                    String rest = name.substring(prefix.length()).trim();
                    // Never strip the whole name away: a boss genuinely called "Boss" would
                    // otherwise vanish.
                    if (!rest.isEmpty()) {
                        name = rest;
                        changed = true;
                    }
                    break;
                }
            }
        }
        return name;
    }

    /** Case- and spacing-insensitive key, so "angry  archaeologist" matches the stored record. */
    public static String key(String bossName) {
        return bossName.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
