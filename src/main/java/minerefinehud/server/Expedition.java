package minerefinehud.server;

import java.util.Locale;
import minerefinehud.halloween.HalloweenText;

/**
 * Whether the player is on an Expedition, from its two sidebar rows: "Expedition Level 141" and
 * "Time Elapsed: 0s". In an Expedition any command opens a "Quit Expedition?" menu, so the mod
 * sends none of its own there.
 *
 * Either row is enough. No Minecraft types.
 */
public final class Expedition {

    private Expedition() {
    }

    /** One sidebar row is an Expedition's. */
    public static boolean isExpeditionRow(String raw) {
        String s = HalloweenText.plainLetters(HalloweenText.strip(raw)).toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}]+", "");
        return s.contains("expeditionlevel") || s.contains("timeelapsed");
    }

    /** Any of these sidebar rows is an Expedition's. */
    public static boolean named(Iterable<String> sidebarLines) {
        if (sidebarLines != null) {
            for (String line : sidebarLines) {
                if (isExpeditionRow(line)) {
                    return true;
                }
            }
        }
        return false;
    }
}
