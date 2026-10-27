package minerefinehud.client;

import minerefinehud.server.CommandSpacing;
import minerefinehud.server.Expedition;
import net.minecraft.client.Minecraft;

/**
 * The one way the mod sends a command by itself (/bossindex, /nextreforger), and when it may.
 *
 * Never while a screen is open, never within {@link CommandSpacing#QUIET_MS} of any other command
 * or chat message (the server kicks for two in one tick), and never on an Expedition, where any
 * command opens a "Quit Expedition?" menu, nor for a while after one or after changing worlds,
 * while the sidebar may still be catching up.
 */
public final class AutoCommands {

    /** After the Expedition rows were last seen: a sidebar redrawn for a moment is not leaving. */
    private static final long EXPEDITION_GRACE_MS = 15_000L;
    /** After a world change, so the new world's sidebar is in before anything is sent. */
    private static final long NEW_WORLD_MS = 5_000L;

    private static final CommandSpacing SPACING = new CommandSpacing();
    private static long expeditionSeenAt;
    private static Object level;
    private static long levelSince;

    private AutoCommands() {
    }

    /** A new connection. */
    static synchronized void onJoin() {
        SPACING.reset();
        expeditionSeenAt = 0L;
        level = null;
        levelSince = 0L;
    }

    /** A command or chat message is leaving, whoever sent it. From the packet mixin. */
    public static synchronized void noteSent() {
        SPACING.sent(System.currentTimeMillis());
    }

    /** Every tick: notices a world change and the Expedition sidebar. */
    static synchronized void tick(Minecraft client, long now) {
        if (client == null || client.player == null) {
            return;
        }
        look(client, now);
    }

    private static void look(Minecraft client, long now) {
        if (client.level != level) {
            level = client.level;
            levelSince = now;
        }
        if (Expedition.named(SidebarReader.sidebarLines(client))) {
            expeditionSeenAt = now;
        }
    }

    /** Whether one of ours may go now. */
    static synchronized boolean maySend(Minecraft client, long now) {
        if (client == null || client.player == null || Compat.screen(client) != null || !SPACING.quiet(now)) {
            return false;
        }
        // The sidebar as it is right now, not as of the last tick.
        look(client, now);
        return Math.abs(now - levelSince) >= NEW_WORLD_MS
                && (expeditionSeenAt == 0L || Math.abs(now - expeditionSeenAt) >= EXPEDITION_GRACE_MS);
    }

    /** Sends {@code command} if {@link #maySend} allows. True if it went. */
    static synchronized boolean send(Minecraft client, String command, long now) {
        if (!maySend(client, now) || !Compat.sendCommand(client, command)) {
            return false;
        }
        // The packet mixin notes it too; this covers a Minecraft version where it does not apply.
        SPACING.sent(now);
        return true;
    }

    /** For the client game test: a command just went, so ours are held. */
    public static synchronized boolean heldByRecentCommand() {
        return !SPACING.quiet(System.currentTimeMillis());
    }

    /** For /minerefine debug. */
    static synchronized String describe(long now) {
        if (expeditionSeenAt > 0L && Math.abs(now - expeditionSeenAt) < EXPEDITION_GRACE_MS) {
            return "held: on an Expedition";
        }
        if (Math.abs(now - levelSince) < NEW_WORLD_MS) {
            return "held: world just changed";
        }
        return SPACING.quiet(now) ? "free" : "held: another command just went";
    }
}
