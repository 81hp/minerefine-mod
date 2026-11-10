package minerefinehud.outpost;

import minerefinehud.boss.Interval;
import minerefinehud.halloween.HalloweenText;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The outpost timers, by Arreba. The /outpost menu has one item per outpost, named "Ruins Outpost"
 * with a lore line "OPENS IN: 3h 58m 40s". An outpost opens for a five minute battle over its
 * seats, then the countdown starts again at four hours. The menu is read now and then; in between
 * the times are counted from the last reading.
 *
 * No Minecraft types.
 */
public final class OutpostTimers {

    public static final long BATTLE_MS = 5L * 60_000L;
    public static final long WAIT_MS = 4L * 3_600_000L;
    private static final long CYCLE_MS = BATTLE_MS + WAIT_MS;
    /** How long the "is open" notice stays up. */
    public static final long NOTICE_MS = 6_000L;

    private static final Pattern NAME = Pattern.compile("^(?<name>.+?)\\s+outpost$", Pattern.CASE_INSENSITIVE);
    private static final Pattern OPENS = Pattern.compile("^opens\\s+in\\s*:?\\s*(?<time>.+)$", Pattern.CASE_INSENSITIVE);

    private OutpostTimers() {
    }

    /** One outpost as the menu shows it right now. */
    public record Reading(String name, long opensInMs) {}

    /** One menu item as an outpost, or empty for anything else. */
    public static Optional<Reading> read(String title, List<String> lore) {
        if (title == null || lore == null) {
            return Optional.empty();
        }
        Matcher name = NAME.matcher(plain(title));
        if (!name.matches()) {
            return Optional.empty();
        }
        for (String line : lore) {
            Matcher opens = OPENS.matcher(plain(line));
            if (opens.matches()) {
                OptionalLong ms = Interval.parse(opens.group("time"));
                if (ms.isPresent()) {
                    return Optional.of(new Reading(name.group("name").trim(), ms.getAsLong()));
                }
            }
        }
        return Optional.empty();
    }

    public static List<Reading> readAll(List<String> titles, List<List<String>> lores) {
        List<Reading> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < titles.size() && i < lores.size(); i++) {
            read(titles.get(i), lores.get(i)).ifPresent(r -> {
                if (seen.add(r.name().toLowerCase(Locale.ROOT))) {
                    out.add(r);
                }
            });
        }
        return out;
    }

    static String plain(String raw) {
        return HalloweenText.plainLetters(HalloweenText.strip(raw == null ? "" : raw)).trim();
    }

    // ------------------------------------------------------------- saved

    /** @param opensAt when it opens, from the clock; @param readAt when the menu last said so */
    public static final class Entry {
        public String name = "";
        public long opensAt;
        public long readAt;
    }

    /** What is kept between sessions, so a restart does not forget the times. */
    public static final class Saved {
        public List<Entry> outposts = new ArrayList<>();
    }

    /** The readings put in, each outpost's open time set from what the menu said. */
    public static Saved update(Saved saved, List<Reading> readings, long now) {
        Saved out = repair(saved);
        for (Reading r : readings) {
            Entry e = find(out, r.name());
            if (e == null) {
                e = new Entry();
                e.name = r.name();
                out.outposts.add(e);
            }
            e.opensAt = now + r.opensInMs();
            e.readAt = now;
        }
        return out;
    }

    private static Entry find(Saved saved, String name) {
        for (Entry e : saved.outposts) {
            if (e.name.equalsIgnoreCase(name)) {
                return e;
            }
        }
        return null;
    }

    /** A copy with anything half-written left out, so a hand-edited file never breaks the panel. */
    public static Saved repair(Saved saved) {
        Saved out = new Saved();
        if (saved == null || saved.outposts == null) {
            return out;
        }
        for (Entry e : saved.outposts) {
            if (e != null && e.name != null && !e.name.isBlank() && find(out, e.name) == null) {
                Entry copy = new Entry();
                copy.name = e.name;
                copy.opensAt = e.opensAt;
                copy.readAt = e.readAt;
                out.outposts.add(copy);
            }
        }
        return out;
    }

    // -------------------------------------------------------------- views

    public enum Phase { WAITING, BATTLE }

    /**
     * @param ms        until it opens (WAITING) or until the battle ends (BATTLE)
     * @param estimated counted forward from an old reading, not read from the menu
     */
    public record View(String name, Phase phase, long ms, boolean estimated) {}

    /** Every outpost as it stands now, the one that opens soonest first. */
    public static List<View> views(Saved saved, long now) {
        List<View> out = new ArrayList<>();
        if (saved != null && saved.outposts != null) {
            for (Entry e : saved.outposts) {
                if (e != null && e.name != null && !e.name.isBlank()) {
                    out.add(view(e, now));
                }
            }
        }
        out.sort((a, b) -> Long.compare(next(a), next(b)));
        return out;
    }

    private static long next(View v) {
        return v.phase() == Phase.BATTLE ? 0L : v.ms();
    }

    static View view(Entry e, long now) {
        long t = now - e.opensAt;
        if (t < 0L) {
            return new View(e.name, Phase.WAITING, -t, false);
        }
        long r = t % CYCLE_MS;
        boolean estimated = t >= BATTLE_MS;
        if (r < BATTLE_MS) {
            return new View(e.name, Phase.BATTLE, BATTLE_MS - r, estimated);
        }
        return new View(e.name, Phase.WAITING, CYCLE_MS - r, true);
    }

    /**
     * True when an outpost has been through its battle since the menu was last read, so its next
     * time is only a guess and the menu should be read again.
     */
    public static boolean needsRecheck(Saved saved, long now) {
        if (saved == null || saved.outposts == null) {
            return false;
        }
        for (Entry e : saved.outposts) {
            if (e != null && now >= e.opensAt + BATTLE_MS && e.readAt < e.opensAt + BATTLE_MS) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------- panel

    /** "3:58:40", "4:12": hours only when there are some. */
    public static String clock(long ms) {
        long s = Math.max(0L, ms + 999L) / 1000L;
        long h = s / 3600L;
        long m = (s % 3600L) / 60L;
        long sec = s % 60L;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec)
                : String.format(Locale.ROOT, "%d:%02d", m, sec);
    }

    /** "Ruins Outpost" is "Ruins" in a panel that is already called Outposts. */
    public static String shortName(String name) {
        Matcher m = NAME.matcher(name == null ? "" : name.trim());
        return m.matches() ? m.group("name").trim() : (name == null ? "" : name.trim());
    }

    /** The reminder text for the title: an outpost about to open, or just open. */
    public static List<String> alerts(List<View> views, long warnMs) {
        List<String> out = new ArrayList<>();
        for (View v : views) {
            if (v.phase() == Phase.WAITING && warnMs > 0L && v.ms() <= warnMs) {
                out.add(shortName(v.name()) + " Outpost opens in " + clock(v.ms()));
            } else if (v.phase() == Phase.BATTLE && BATTLE_MS - v.ms() < NOTICE_MS) {
                out.add(shortName(v.name()) + " Outpost is open!");
            }
        }
        return out;
    }

    /** Says once per outpost and cycle when to ping: before it opens, and as it opens. */
    public static final class Pings {
        private final Set<String> warned = new HashSet<>();
        private final Set<String> opened = new HashSet<>();

        public boolean due(List<View> views, long warnMs) {
            boolean ping = false;
            for (View v : views) {
                String key = v.name().toLowerCase(Locale.ROOT);
                boolean soon = v.phase() == Phase.WAITING && warnMs > 0L && v.ms() <= warnMs;
                if (soon) {
                    ping |= warned.add(key);
                } else {
                    warned.remove(key);
                }
                boolean justOpen = v.phase() == Phase.BATTLE && BATTLE_MS - v.ms() < NOTICE_MS;
                if (justOpen) {
                    ping |= opened.add(key);
                } else {
                    opened.remove(key);
                }
            }
            return ping;
        }

        public void clear() {
            warned.clear();
            opened.clear();
        }
    }

    /** The mod's own Interval writes a time the config can show: "15m". */
    public static String every(long ms) {
        return Interval.format(ms);
    }
}
