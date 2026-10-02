package minerefinehud.turret;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a turret's size and merge count off its item, so the calculator can be filled in with a
 * click instead of typed.
 *
 * Written against the reforger tooltip, the one shape seen so far:
 *
 *   Sponge Turret
 *   | Size: 281 -> 312 (+31)
 *   | Size Cap: 2000 -> 1900 (-100)
 *   ⚒ 0 -> 1x MERGED • SIZE CAP: 2000 -> 1900
 *
 * so it is deliberately forgiving: the first number after "Size:" is the size now, the first
 * number after "Size Cap:" the cap now, and an arrow only ever points at what a merge would do.
 * The merge count comes from the cap where there is one (every merge takes 100 off 2000) and from
 * the "MERGED" count otherwise. The server writes some lore in small capitals ("ᴍᴇʀɢᴇᴅ"), which
 * are folded back to plain letters first.
 *
 * No Minecraft types.
 */
public final class TurretItem {

    /** @param merges merges already done to this turret */
    public record Turret(String name, double size, int merges) {

        /** "Sponge 281", short enough for a button. */
        public String label() {
            String shortName = name.replaceFirst("(?i)\\s*turret$", "").trim();
            return shortName + " " + String.format(Locale.ROOT, "%,d", Math.round(size));
        }
    }

    private static final Pattern FORMATTING = Pattern.compile("[§&][0-9A-Fa-fK-Ok-oRr]");
    private static final Pattern NUMBER = Pattern.compile("[0-9][0-9,]*(?:\\.[0-9]+)?");
    private static final Pattern SIZE = Pattern.compile("(?<!cap)\\bsize\\s*:\\s*(" + NUMBER.pattern() + ")");
    private static final Pattern SIZE_CAP = Pattern.compile("size\\s*cap\\s*:\\s*(" + NUMBER.pattern() + ")");
    private static final Pattern MERGED = Pattern.compile("(" + NUMBER.pattern() + ")\\s*x?\\s*(?:(?:→|->)\\s*[0-9,]+\\s*x?\\s*)?merged");

    private static final String SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";
    private static final String PLAIN = "abcdefghijklmnopqrstuvwxyz";

    private TurretItem() {
    }

    public static Optional<Turret> parse(String title, List<String> lore) {
        if (title == null) {
            return Optional.empty();
        }
        String name = FORMATTING.matcher(title).replaceAll("").replaceAll("[^\\p{L}\\p{N}' \\-]", " ")
                .replaceAll("\\s+", " ").trim();
        if (!fold(name).endsWith("turret") || lore == null) {
            return Optional.empty();
        }

        Double size = null;
        Double cap = null;
        Integer mergedCount = null;
        for (String raw : lore) {
            String line = fold(FORMATTING.matcher(raw == null ? "" : raw).replaceAll(""));
            Matcher c = SIZE_CAP.matcher(line);
            if (cap == null && c.find()) {
                cap = number(c.group(1));
            }
            Matcher s = SIZE.matcher(line);
            if (size == null && s.find()) {
                size = number(s.group(1));
            }
            Matcher m = MERGED.matcher(line);
            if (mergedCount == null && m.find()) {
                mergedCount = (int) Math.round(number(m.group(1)));
            }
        }
        if (size == null) {
            return Optional.empty();
        }

        int merges;
        if (cap != null && cap <= TurretCalculator.BASE_CAP) {
            merges = (int) Math.round((TurretCalculator.BASE_CAP - cap) / TurretCalculator.CAP_STEP);
        } else {
            merges = mergedCount == null ? 0 : mergedCount;
        }
        return Optional.of(new Turret(name, size, Math.max(0, merges)));
    }

    /** Every turret among these items, in order, skipping anything that is not one. */
    public static List<Turret> all(List<String> titles, List<List<String>> lores) {
        List<Turret> out = new ArrayList<>();
        for (int i = 0; i < titles.size() && i < lores.size(); i++) {
            parse(titles.get(i), lores.get(i)).ifPresent(out::add);
        }
        return out;
    }

    /** Lower case, small capitals made plain, arrows and spacing tidied. */
    static String fold(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) {
            int i = SMALL_CAPS.indexOf(ch);
            out.append(i >= 0 ? PLAIN.charAt(i) : Character.toLowerCase(ch));
        }
        return out.toString().replace('‌', ' ').replaceAll("\\s+", " ").trim();
    }

    private static double number(String raw) {
        return Double.parseDouble(raw.replace(",", ""));
    }
}
