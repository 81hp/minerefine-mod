package minerefinehud.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * Harvests every bit of on-screen server text that might name the current mine.
 *
 * VERSION SENSITIVE. Scoreboard class and method names move between Minecraft versions more than
 * anything else in this mod. If the project fails to compile, this file is the first place to
 * look, and it is deliberately the only place that touches the scoreboard.
 *
 * The strategy is to collect candidate strings rather than to understand the sidebar's layout.
 * MineCatalog then scans them for any known mine name, so the server can reformat its sidebar
 * freely without breaking detection.
 */
public final class SidebarReader {

    private SidebarReader() {
    }

    /** Sidebar title and rows, as plain strings. Returns empty rather than throwing. */
    public static List<String> sidebarLines(Minecraft client) {
        List<String> lines = new ArrayList<>();
        try {
            if (client == null || client.level == null) {
                return lines;
            }

            Scoreboard scoreboard = client.level.getScoreboard();
            Objective objective =
                    scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
            if (objective == null) {
                return lines;
            }

            lines.add(objective.getDisplayName().getString());

            for (PlayerScoreEntry entry : scoreboard.listPlayerScores(objective)) {
                PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
                Component decorated = PlayerTeam.formatNameForTeam(team, entry.ownerName());
                lines.add(decorated.getString());
            }
        } catch (Exception | NoSuchMethodError | NoClassDefFoundError ignored) {
            // A mapping mismatch should degrade mine detection, never crash the client.
        }
        return lines;
    }
}
