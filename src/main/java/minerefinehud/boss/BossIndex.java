package minerefinehud.boss;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the /boss index menu: every boss with whether it is up or when it is back.
 *
 * Each boss is an item like this (plain text):
 *
 *   « Zeus »                      « Endor »
 *   Health: 1,250,000❤            Health: 750,000❤
 *   Total Loot                    Total Loot
 *   ★ Threshold at 5% ...         ★ Threshold at 10% ...
 *   SPAWNED! (43.86% HP)          ⌚ 9m 40s
 *
 * An item counts only with "Total Loot" or "Threshold at" in its lore, so nothing else in a menu
 * is taken for a boss. No Minecraft types.
 */
public final class BossIndex {

    /** @param respawnMs time until it is back, or -1 when it is up */
    public record Entry(String bossName, long respawnMs) {

        public boolean alive() {
            return respawnMs < 0L;
        }
    }

    private static final Pattern FORMATTING = Pattern.compile("[§&][0-9A-Fa-fK-Ok-oRr]");
    private static final Pattern TIMER = Pattern.compile(
            "^[^\\p{L}\\p{N}]*((?:\\d+\\s*h\\s*)?(?:\\d+\\s*m\\s*)?(?:\\d+\\s*s)?)\\s*$", Pattern.CASE_INSENSITIVE);

    private BossIndex() {
    }

    private static String clean(String raw) {
        return FORMATTING.matcher(raw == null ? "" : raw).replaceAll("")
                .replaceAll("[\\s\\p{Cf}\\p{Co}]+", " ").trim();
    }

    /** "« Zeus »" is Zeus. */
    static String name(String title) {
        return clean(title).replaceAll("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}.!']+$", "").trim();
    }

    public static List<Entry> read(List<String> titles, List<List<String>> lores) {
        List<Entry> out = new ArrayList<>();
        for (int i = 0; i < titles.size() && i < lores.size(); i++) {
            entry(titles.get(i), lores.get(i)).ifPresent(out::add);
        }
        return out;
    }

    public static java.util.Optional<Entry> entry(String title, List<String> lore) {
        if (lore == null) {
            return java.util.Optional.empty();
        }
        boolean isBoss = false;
        long respawn = Long.MIN_VALUE;
        for (String raw : lore) {
            String line = clean(raw);
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.startsWith("total loot") || lower.contains("threshold at")) {
                isBoss = true;
            }
            if (lower.startsWith("spawned!")) {
                respawn = -1L;
                continue;
            }
            Matcher m = TIMER.matcher(line);
            if (m.matches() && !m.group(1).isBlank()) {
                long ms = minerefinehud.halloween.RecurringEvent.duration(m.group(1));
                if (ms >= 0L) {
                    respawn = ms;
                }
            }
        }
        String name = name(title);
        if (!isBoss || respawn == Long.MIN_VALUE || name.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new Entry(name, respawn));
    }
}
