package minerefinehud.halloween;

/**
 * A server event that comes back on a fixed cycle: the Reforger, the Halloween caravan.
 *
 * The next start is known exactly when the server says so ("Time Until Reforger: 1h 57m 17s",
 * "Starting in 1 minute!") and otherwise estimated from the last start plus the cycle. The cycle
 * is learned from two starts in a row, and starts at a default that matches what was observed.
 * When the estimate passes unseen (the player was elsewhere and missed the broadcast) it rolls on
 * by whole cycles, still marked as an estimate.
 *
 * No Minecraft types; times are epoch milliseconds from the caller. Every field is plain so the
 * state can go straight into config.json.
 */
public final class RecurringEvent {

    public enum State { UNKNOWN, WAITING, ACTIVE }

    /** What the panel shows. {@code ms} is time to the start while WAITING, time left while ACTIVE. */
    public record View(State state, long ms, boolean exact) {}

    /** Saved state. 0 means unknown for every time. */
    public static final class Saved {
        public long nextAt;
        public boolean nextExact;
        public long lastStart;
        public long activeUntil;
        public long cycleMs;
    }

    private final long defaultCycleMs;
    private final long minCycleMs;
    private final long maxCycleMs;
    /** How long the event lasts when no end message is seen. */
    private final long defaultDurationMs;

    private Saved s = new Saved();

    public RecurringEvent(long defaultCycleMs, long minCycleMs, long maxCycleMs, long defaultDurationMs) {
        this.defaultCycleMs = defaultCycleMs;
        this.minCycleMs = minCycleMs;
        this.maxCycleMs = maxCycleMs;
        this.defaultDurationMs = defaultDurationMs;
    }

    public long cycleMs() {
        return s.cycleMs >= minCycleMs && s.cycleMs <= maxCycleMs ? s.cycleMs : defaultCycleMs;
    }

    /** The server named the time to the next start. */
    public void startsIn(long ms, long now) {
        // The current visit is left alone: the server counts to the next one while this one is on.
        s.nextAt = now + Math.max(0L, ms);
        s.nextExact = true;
    }

    /**
     * The event started now. Two starts teach the cycle. Starts missed in between (the player was
     * away) make the gap a few cycles long, so it is split into as many cycles as fit: 90 minutes
     * after a 45 minute cycle is two caravans, not a 90 minute cycle.
     */
    public void started(long now) {
        if (s.lastStart > 0L && now - s.lastStart > 60_000L) {
            long gap = now - s.lastStart;
            long cycle = cycleMs();
            long cycles = Math.max(1L, Math.round((double) gap / cycle));
            long each = gap / cycles;
            // Split only when the pieces come out close to the cycle: 68 minutes is one late
            // caravan, not two of 34.
            boolean fits = cycles == 1L || Math.abs(each - cycle) <= cycle / 6;
            if (fits && each >= minCycleMs && each <= maxCycleMs) {
                s.cycleMs = each;
            }
        }
        // The same start announced twice (two lines of one broadcast) is one start.
        if (s.lastStart <= 0L || now - s.lastStart > 60_000L) {
            s.lastStart = now;
        }
        s.activeUntil = s.lastStart + defaultDurationMs;
        s.nextAt = s.lastStart + cycleMs();
        s.nextExact = false;
    }

    /** The event ended now. */
    public void ended(long now) {
        s.activeUntil = now;
    }

    public View view(long now) {
        if (s.activeUntil > now) {
            return new View(State.ACTIVE, s.activeUntil - now, true);
        }
        if (s.nextAt <= 0L) {
            return new View(State.UNKNOWN, 0L, false);
        }
        long next = s.nextAt;
        boolean exact = s.nextExact;
        // A start that should have happened a while ago and was not seen: roll on by cycles.
        long grace = 2L * 60_000L;
        if (next + grace < now) {
            long cycle = cycleMs();
            long behind = (now - next) / cycle + 1;
            next += behind * cycle;
            exact = false;
        }
        return new View(State.WAITING, Math.max(0L, next - now), exact);
    }

    public Saved export() {
        Saved out = new Saved();
        out.nextAt = s.nextAt;
        out.nextExact = s.nextExact;
        out.lastStart = s.lastStart;
        out.activeUntil = s.activeUntil;
        out.cycleMs = s.cycleMs;
        return out;
    }

    public void importFrom(Saved saved) {
        s = new Saved();
        if (saved != null) {
            s.nextAt = Math.max(0L, saved.nextAt);
            s.nextExact = saved.nextExact;
            s.lastStart = Math.max(0L, saved.lastStart);
            s.activeUntil = Math.max(0L, saved.activeUntil);
            s.cycleMs = Math.max(0L, saved.cycleMs);
        }
    }

    // ----------------------------------------------------------------- text

    private static final java.util.regex.Pattern DURATION = java.util.regex.Pattern.compile(
            "^(?:(\\d+)\\s*h)?\\s*(?:(\\d+)\\s*m)?\\s*(?:(\\d+)\\s*s)?$", java.util.regex.Pattern.CASE_INSENSITIVE);

    /** "1h 57m 17s", "9m 40s", "33s" in milliseconds; -1 for anything else. */
    public static long duration(String text) {
        String t = text == null ? "" : text.trim();
        java.util.regex.Matcher m = DURATION.matcher(t);
        if (t.isEmpty() || !m.matches() || (m.group(1) == null && m.group(2) == null && m.group(3) == null)) {
            return -1L;
        }
        long h = m.group(1) == null ? 0 : Long.parseLong(m.group(1));
        long min = m.group(2) == null ? 0 : Long.parseLong(m.group(2));
        long sec = m.group(3) == null ? 0 : Long.parseLong(m.group(3));
        return ((h * 60 + min) * 60 + sec) * 1000L;
    }

    private static final java.util.regex.Pattern REFORGER_IN = java.util.regex.Pattern.compile(
            "^Time Until Reforger:\\s*(.+)$", java.util.regex.Pattern.CASE_INSENSITIVE);

    /** The time in "Time Until Reforger: 1h 57m 17s", or -1. */
    public static long reforgerCountdown(String line) {
        java.util.regex.Matcher m = REFORGER_IN.matcher(HalloweenText.strip(line));
        return m.matches() ? duration(m.group(1)) : -1L;
    }

    public static boolean reforgerArrived(String line) {
        return HalloweenText.strip(line).equalsIgnoreCase("The Reforger has arrived to town!");
    }

    public static boolean reforgerDeparted(String line) {
        return HalloweenText.strip(line).equalsIgnoreCase("The Reforger has departed!");
    }

    /** "PROTECT THE CARAVAN", in capitals: the one-minute warning, not the start. */
    public static boolean caravanWarning(String line) {
        return HalloweenText.strip(line).equals("PROTECT THE CARAVAN");
    }

    public static boolean caravanStarted(String line) {
        return HalloweenText.strip(line).equalsIgnoreCase("The caravan is leaving for the nether portal!");
    }

    public static boolean caravanEnded(String line) {
        String s = HalloweenText.strip(line);
        return s.equalsIgnoreCase("Caravan Delivered!") || s.equalsIgnoreCase("Caravan Destroyed!");
    }

    /** Reforger: there for 15 minutes, back about 3h15m after it arrived (observed October 2026). */
    public static RecurringEvent reforger() {
        return new RecurringEvent(195L * 60_000L, 60L * 60_000L, 6L * 3_600_000L, 15L * 60_000L);
    }

    /**
     * Caravan: every 45 minutes; a run took 2.5 to 4 minutes in the October 2026 logs, so one with
     * no end heard is taken as over after 6. The learned cycle stays within 30 to 60 minutes, so a
     * stray gap can never stretch the countdown to an hour and a half.
     */
    public static RecurringEvent caravan() {
        return new RecurringEvent(45L * 60_000L, 30L * 60_000L, 60L * 60_000L, 6L * 60_000L);
    }
}
