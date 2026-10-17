package minerefinehud.boss;

import java.util.Locale;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A time typed by the player: "1s", "1 second", "90 sec", "5m", "5 minutes", "1h 30m",
 * "1 hour and 30 minutes", "1.5h". A plain number is seconds. No Minecraft types.
 */
public final class Interval {

    private static final Pattern PART = Pattern.compile(
            "([0-9]+(?:[.,][0-9]+)?)\\s*(s|secs?|seconds?|m|mins?|minutes?|h|hrs?|hours?|d|days?)?(?![\\p{L}])");
    /** What may sit between the parts: spaces, commas and "and". */
    private static final Pattern FILLER = Pattern.compile("(?:\\s|,|\\band\\b)*");

    private Interval() {
    }

    /** The time in milliseconds, or empty when the text is not one. */
    public static OptionalLong parse(String text) {
        String t = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty()) {
            return OptionalLong.empty();
        }
        Matcher m = PART.matcher(t);
        int at = 0;
        double ms = 0.0;
        boolean any = false;
        while (m.find()) {
            if (!FILLER.matcher(t.substring(at, m.start())).matches()) {
                return OptionalLong.empty();
            }
            double n;
            try {
                n = Double.parseDouble(m.group(1).replace(',', '.'));
            } catch (NumberFormatException e) {
                return OptionalLong.empty();
            }
            String unit = m.group(2) == null ? "s" : m.group(2);
            ms += n * switch (unit.charAt(0)) {
                case 'm' -> 60_000.0;
                case 'h' -> 3_600_000.0;
                case 'd' -> 86_400_000.0;
                default -> 1_000.0;
            };
            any = true;
            at = m.end();
        }
        if (!any || !FILLER.matcher(t.substring(at)).matches() || ms > 30.0 * 86_400_000.0) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(Math.round(ms));
    }

    /** "45s", "5m", "1h 30m", "2m 5s": the short form, which {@link #parse} reads back. */
    public static String format(long ms) {
        long s = Math.max(0L, ms) / 1000L;
        long h = s / 3600L;
        long m = (s % 3600L) / 60L;
        long sec = s % 60L;
        StringBuilder out = new StringBuilder();
        if (h > 0) {
            out.append(h).append('h');
        }
        if (m > 0) {
            out.append(out.isEmpty() ? "" : " ").append(m).append('m');
        }
        if (sec > 0 || out.isEmpty()) {
            out.append(out.isEmpty() ? "" : " ").append(sec).append('s');
        }
        return out.toString();
    }
}
