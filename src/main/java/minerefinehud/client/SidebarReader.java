package minerefinehud.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

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
    public static List<String> sidebarLines(MinecraftClient client) {
        List<String> lines = new ArrayList<>();
        try {
            if (client == null || client.world == null) {
                return lines;
            }

            Scoreboard scoreboard = client.world.getScoreboard();
            ScoreboardObjective objective =
                    scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
            if (objective == null) {
                return lines;
            }

            lines.add(objective.getDisplayName().getString());

            for (ScoreboardEntry entry : scoreboard.getScoreboardEntries(objective)) {
                Team team = scoreboard.getScoreHolderTeam(entry.owner());
                Text decorated = Team.decorateName(team, entry.name());
                lines.add(decorated.getString());
            }
        } catch (Exception | NoSuchMethodError | NoClassDefFoundError ignored) {
            // A mapping mismatch should degrade mine detection, never crash the client.
        }
        return lines;
    }
}
