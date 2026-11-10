package minerefinehud.job;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The arithmetic of the job calculator, by Arreba.
 *
 * A job is a list of mines. Each mine needs a number of mats (blocks), and the price of a job is
 * those mats divided by the rate, which the player types in freely: "1B", "1.25B", "1.5b/1c".
 *
 * No Minecraft types.
 */
public final class JobCalc {

    private JobCalc() {
    }

    /** The rate a new job starts with, 1B mats for 1 credit. */
    public static final String DEFAULT_RATE = "1B";

    private static final Pattern AMOUNT = Pattern.compile(
            "(?<num>[0-9][0-9,]*(?:\\.[0-9]+)?|\\.[0-9]+)\\s*(?<suffix>[KMBTQkmbtq])?");
    /** "1B", "1.25B", "1.5b/1c", "1B per credit", "2b / 2c". */
    private static final Pattern RATE = Pattern.compile(
            "\\s*" + AMOUNT.pattern() + "(?:\\s*(?:/|per)\\s*(?<per>[0-9.,]*)\\s*(?:c|cr|credits?)?)?\\s*",
            Pattern.CASE_INSENSITIVE);

    private static double scale(String suffix) {
        if (suffix == null) {
            return 1.0;
        }
        return switch (suffix.toUpperCase(Locale.ROOT)) {
            case "K" -> 1e3;
            case "M" -> 1e6;
            case "B" -> 1e9;
            case "T" -> 1e12;
            default -> 1e15;
        };
    }

    /** "250M" as 2.5e8, "1.25b" as 1.25e9, plain numbers as they are; empty for anything else. */
    public static OptionalDouble parseAmount(String text) {
        if (text == null) {
            return OptionalDouble.empty();
        }
        Matcher m = AMOUNT.matcher(text.trim());
        if (!m.matches()) {
            return OptionalDouble.empty();
        }
        return value(m);
    }

    /**
     * "1,000" is a thousand and "1,5" is one and a half: a comma followed by exactly three digits
     * is a thousands separator, any other comma is a decimal one. Null for a number that is neither.
     */
    static String plain(String num) {
        if (!num.contains(",")) {
            return num;
        }
        if (num.matches("[0-9]{1,3}(,[0-9]{3})+(\\.[0-9]+)?")) {
            return num.replace(",", "");
        }
        if (num.matches("[0-9]+,[0-9]+")) {
            return num.replace(',', '.');
        }
        return null;
    }

    private static OptionalDouble value(Matcher m) {
        try {
            String plain = plain(m.group("num"));
            if (plain == null) {
                return OptionalDouble.empty();
            }
            double v = Double.parseDouble(plain) * scale(m.group("suffix"));
            return v > 0.0 && !Double.isInfinite(v) ? OptionalDouble.of(v) : OptionalDouble.empty();
        } catch (NumberFormatException e) {
            return OptionalDouble.empty();
        }
    }

    /**
     * Mats per credit from what the player wrote: "1B", "1.25B/1c", "2B/2c" (a billion each).
     * Empty while it is half-typed or means nothing.
     */
    public static OptionalDouble parseRate(String text) {
        if (text == null) {
            return OptionalDouble.empty();
        }
        Matcher m = RATE.matcher(text);
        if (!m.matches()) {
            return OptionalDouble.empty();
        }
        OptionalDouble mats = value(m);
        if (mats.isEmpty()) {
            return mats;
        }
        String per = m.group("per");
        if (per != null && !per.isBlank()) {
            try {
                String plainPer = plain(per);
                if (plainPer == null) {
                    return OptionalDouble.empty();
                }
                double credits = Double.parseDouble(plainPer);
                if (credits <= 0.0) {
                    return OptionalDouble.empty();
                }
                return OptionalDouble.of(mats.getAsDouble() / credits);
            } catch (NumberFormatException e) {
                return OptionalDouble.empty();
            }
        }
        return mats;
    }

    /**
     * One piece of gear at a mine and what it costs, empty when that is not known.
     *
     * @param partial only some of the piece's tiers have been read from the shop, so {@code cost}
     *                is a part of the price: it is counted, and the mine is marked as short
     */
    public record Part(String gear, OptionalLong cost, boolean partial) {

        public Part(String gear, OptionalLong cost) {
            this(gear, cost, false);
        }
    }

    /**
     * A mine in the job.
     *
     * @param mats    what the pieces still in the job cost, for one copy; unknown pieces add nothing
     * @param times   how many copies of the mine are in the job
     * @param missing how many of the pieces still in the job have no price yet
     */
    public record Entry(String mine, long mats, int times, int missing) {}

    /** The key a left-out piece is saved under: "jade|charm". */
    public static String skipKey(String mine, String gear) {
        return JobWorlds.canonical(mine) + "|"
                + (gear == null ? "" : gear.trim().toLowerCase(Locale.ROOT));
    }

    /** A saved skip key under today's spelling of the mine, so an older save still matches. */
    private static String normal(String key) {
        int bar = key.lastIndexOf('|');
        return bar < 0 ? key : skipKey(key.substring(0, bar), key.substring(bar + 1));
    }

    /** Whether the piece is left out of the job. */
    public static boolean isSkipped(java.util.Collection<String> skipped, String mine, String gear) {
        String key = skipKey(mine, gear);
        return skipped.stream().anyMatch(k -> normal(k).equals(key));
    }

    /** The list with the piece switched: left out if it was in, back in if it was left out. */
    public static List<String> toggleSkip(List<String> skipped, String mine, String gear) {
        String key = skipKey(mine, gear);
        List<String> out = new ArrayList<>(skipped);
        if (!out.removeIf(k -> normal(k).equals(key))) {
            out.add(key);
        }
        return out;
    }

    /** The mine with the pieces in {@code skipped} left out. */
    public static Entry entry(String mine, List<Part> parts, java.util.Collection<String> skipped, int times) {
        long sum = 0L;
        int missing = 0;
        for (Part p : parts) {
            if (isSkipped(skipped, mine, p.gear())) {
                continue;
            }
            if (p.cost().isPresent()) {
                sum += p.cost().getAsLong();
            }
            if (p.cost().isEmpty() || p.partial()) {
                missing++;
            }
        }
        return new Entry(mine, sum, Math.max(1, times), missing);
    }

    /**
     * @param mats    everything known, added up
     * @param credits {@code mats} at the rate, NaN when there is no rate
     * @param unknown how many mines have a piece with no price and are short in the totals
     */
    public record Total(double mats, double credits, int unknown) {}

    public static Total total(List<Entry> entries, OptionalDouble matsPerCredit) {
        double sum = 0.0;
        int unknown = 0;
        for (Entry e : entries) {
            sum += (double) e.mats() * e.times();
            if (e.missing() > 0) {
                unknown++;
            }
        }
        double credits = matsPerCredit.isPresent() ? sum / matsPerCredit.getAsDouble() : Double.NaN;
        return new Total(sum, credits, unknown);
    }

    /**
     * The same mine added more than once, in order of first appearance: mine to times. A mine
     * saved under one spelling and added under another ("Sulfer", "Sulfur") is one mine.
     */
    public static Map<String, Integer> counts(List<String> mines) {
        Map<String, String> first = new LinkedHashMap<>();
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String m : mines) {
            String shown = first.computeIfAbsent(JobWorlds.canonical(m), k -> m);
            out.merge(shown, 1, Integer::sum);
        }
        return out;
    }

    /** One copy of {@code mine} removed, the last one added; the list as it was when it is not there. */
    public static List<String> removeOne(List<String> mines, String mine) {
        List<String> out = new ArrayList<>(mines);
        String want = JobWorlds.canonical(mine);
        for (int i = out.size() - 1; i >= 0; i--) {
            if (JobWorlds.canonical(out.get(i)).equals(want)) {
                out.remove(i);
                break;
            }
        }
        return out;
    }

    /** Mats the way the site writes them: 1.5b, 250m, 12,345. */
    public static String mats(double value) {
        double[] units = {1e15, 1e12, 1e9, 1e6};
        String[] names = {"q", "t", "b", "m"};
        for (int i = 0; i < units.length; i++) {
            if (value >= units[i] - units[i] / 200_000.0) {
                return trim(value / units[i]) + names[i];
            }
        }
        return String.format(Locale.ROOT, "%,d", Math.round(value));
    }

    /** Credits to two decimals, trailing zeroes dropped, "?" with no rate. */
    public static String credits(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "?";
        }
        return trim(value) + "c";
    }

    private static String trim(double v) {
        String s = String.format(Locale.ROOT, "%,.2f", v);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return s;
    }
}
