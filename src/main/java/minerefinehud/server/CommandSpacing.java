package minerefinehud.server;

/**
 * Keeps the mod's own commands apart from every other command and chat message. The server kicks
 * a player who sends two commands in the same tick, so one of ours goes only once nothing at all
 * has been sent for a while: whatever the player typed, a click in chat, another mod.
 *
 * No Minecraft types.
 */
public final class CommandSpacing {

    /** Nothing sent this long before one of ours. Many server ticks, so lag never bunches them. */
    public static final long QUIET_MS = 1_500L;

    private long lastSentAt;

    /** A command or chat message went to the server. */
    public void sent(long now) {
        lastSentAt = now;
    }

    /** Long enough since the last one for ours to go. A clock set back never blocks for long. */
    public boolean quiet(long now) {
        return lastSentAt == 0L || Math.abs(now - lastSentAt) >= QUIET_MS;
    }

    public void reset() {
        lastSentAt = 0L;
    }
}
