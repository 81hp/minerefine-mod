package minerefinehud.beacon;

import java.util.Locale;

/**
 * The Beacon Mine reset timer by Arreba. The mine resets every five minutes and says so in chat,
 * so the timer starts from that line and counts on from there. Plain Java: the client feeds it
 * the chat lines and the clock.
 */
public final class BeaconTimer {

    public static final long CYCLE_MS = 5L * 60_000L;

    private static final String TRIGGER = "beacon mine has reset";

    /** How long the "reset" notice stays up. */
    public static final long NOTICE_MS = 4_000L;

    private long lastReset = -1L;
    private boolean warned;

    /**
     * True for the server's "Beacon Mine has reset!" line. A line of player chat that happens to
     * say it does not count: anything with a colon or an angle bracket before the words is a
     * player talking.
     */
    public static boolean isReset(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        int at = lower.indexOf(TRIGGER);
        if (at < 0) {
            return false;
        }
        String before = lower.substring(0, at);
        return before.indexOf(':') < 0 && before.indexOf('<') < 0 && before.indexOf('>') < 0;
    }

    public void onReset(long now) {
        lastReset = now;
        warned = false;
    }

    public void clear() {
        lastReset = -1L;
        warned = false;
    }

    public boolean known() {
        return lastReset >= 0L;
    }

    /** Milliseconds until the next reset, or -1 before the first one was seen. */
    public long remaining(long now) {
        if (lastReset < 0L) {
            return -1L;
        }
        long elapsed = Math.max(0L, now - lastReset);
        return CYCLE_MS - (elapsed % CYCLE_MS);
    }

    /** True while the "reset" notice should show. */
    public boolean justReset(long now) {
        return lastReset >= 0L && now - lastReset >= 0L && now - lastReset < NOTICE_MS;
    }

    /**
     * True once per cycle, the first time the countdown is within {@code warnMs} of the reset.
     * Ask every tick.
     */
    public boolean warnNow(long now, long warnMs) {
        long rem = remaining(now);
        if (warnMs <= 0L || rem < 0L || rem > warnMs) {
            warned = false;
            return false;
        }
        if (warned) {
            return false;
        }
        warned = true;
        return true;
    }
}
