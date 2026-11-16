package minerefinehud.client;

import java.util.ArrayList;
import java.util.List;
import minerefinehud.server.ServerGate;
import minerefinehud.server.ServerName;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

/**
 * Whether this is MineRefine, from its name in the sidebar or the tab list. With "Only on
 * MineRefine" on, the panels, sounds and learning all stay off anywhere else, so another server's
 * shops and chat never teach the mod wrong prices or boss timers.
 *
 * The settings, the keys and /mrhud keep working everywhere, so the setting can always be found
 * and switched off. The salvage guard does not ask here either; SalvageGuard says why.
 *
 * VERSION SENSITIVE, lightly: the tab list's entries come from the connection's player list.
 */
public final class ServerCheck {

    /** Sooner than this after joining, the name is looked for every tick: chat is not read until it is found. */
    private static final long EAGER_MS = 10_000L;
    /** Tab list entries read at most. A server that names itself there does so on every entry or on none. */
    private static final int MAX_ENTRIES = 300;
    /** Lines of a tab list header or footer read apart; the rest stay one line, of which the start is read. */
    private static final int MAX_HEADER_LINES = 32;

    private static final ServerGate GATE = new ServerGate();
    private static long lastCheck;
    private static long joinedAt;

    private ServerCheck() {
    }

    /**
     * A new connection: off until the name shows up on it. The tab list's header and footer are
     * not cleared here but when the game clears its own (see TabListMixin): the new server's may
     * have arrived just before this.
     */
    public static void onJoin() {
        GATE.reset();
        lastCheck = 0L;
        joinedAt = System.currentTimeMillis();
    }

    /** Left the server: not MineRefine until the next one says so. */
    public static void onDisconnect() {
        GATE.reset();
        TabListHook.clear();
    }

    /**
     * Looks for the name until it is found: every tick for the first seconds after joining, then
     * twice a second, which on another server goes on for as long as the player stays. After it
     * is found there is nothing more to learn on this connection.
     */
    public static void tick(Minecraft client, long now) {
        if (client == null || client.player == null || GATE.onMineRefine()) {
            return;
        }
        boolean eager = now >= joinedAt && now - joinedAt < EAGER_MS;
        // A clock set back must not stop the looking until it has caught up.
        if (!eager && now >= lastCheck && now - lastCheck < 500L) {
            return;
        }
        lastCheck = now;
        GATE.observe(ServerName.anyMineRefine(lines(client)));
    }

    /** This connection is to MineRefine: its name was seen since joining. */
    public static boolean onMineRefine() {
        return GATE.onMineRefine();
    }

    /** Whether the mod should be at work: on MineRefine, or anywhere if the player chose that. */
    public static boolean active(ModConfig config) {
        return !config.onlyOnMineRefine || GATE.onMineRefine();
    }

    /**
     * The sidebar, the tab list's header and footer, and the names in the tab list. Not the
     * address the player connected to: joined as minerefine.minehut.gg, it stays that address on
     * every other Minehut server the proxy then moves them to.
     */
    // UNVERIFIED: what MineRefine's tab list shows is not known. The sidebar names the server
    // twice (title and address line); the tab list is there for a player whose sidebar does not.
    static List<String> lines(Minecraft client) {
        List<String> out = new ArrayList<>(SidebarReader.sidebarLines(client));
        // Line by line: only the start of a line is read for the name, and a header is several.
        out.addAll(List.of(TabListHook.header().split("\n", MAX_HEADER_LINES)));
        out.addAll(List.of(TabListHook.footer().split("\n", MAX_HEADER_LINES)));
        try {
            ClientPacketListener connection = client.getConnection();
            if (connection != null) {
                int entries = 0;
                for (PlayerInfo info : connection.getListedOnlinePlayers()) {
                    if (++entries > MAX_ENTRIES) {
                        break;
                    }
                    Component name = info.getTabListDisplayName();
                    if (name != null) {
                        // Without the player's own name, so a player called after the server
                        // does not turn the mod on in a lobby they share.
                        String player = info.getProfile().name();
                        String shown = name.getString();
                        out.add(player == null || player.isEmpty() ? shown : shown.replace(player, ""));
                    }
                }
            }
        } catch (Exception | LinkageError ignored) {
            // A mapping mismatch loses the tab names; the sidebar and header still tell.
        }
        return out;
    }
}
