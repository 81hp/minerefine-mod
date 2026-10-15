package minerefinehud.halloween;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the Halloween event's numbers out of the sidebar, the action bar and chat.
 *
 * Observed in game, as plain text (icons are sprites, which plain text shows as "[item/...]" or
 * drops; neither is relied on):
 *
 *   sidebar   "HALLOWEEN", "Carrots 45,395", "Beetroots 86,824", "Nether Wart 13,535", "Candy 21.5"
 *   chat      "+1.1  Candy from the Pest!", "+1 Halloween Charm from the Pest! (right-click it to redeem)"
 *             "Will-o'-Wisps is recharging (2.31s)", "Will-o'-Wisps has recharged! [DROP]"
 *             "Farming ability set to Jack o'Lantern."
 *             "Your hoe has been refined to [I]."
 *
 * Candy has decimals, so everything here is a double. No Minecraft types.
 */
public final class HalloweenText {

    public static final String CANDY = "Candy";

    /** Every crop the event might use. Only names here are read, so "Resurrection 1" never counts. */
    public static final List<String> CROPS = List.of(
            "Carrots", "Beetroots", "Nether Wart", "Fire Coral", "Potatoes", "Wheat", "Pumpkins", "Melons");

    private static final Pattern FORMATTING = Pattern.compile("[§&][0-9A-Fa-fK-Ok-oRr]");

    /** A number the server prints: "45,395", "21.5", "2.5M", "250k". */
    private static final String NUMBER = "[0-9][0-9,]*(?:\\.[0-9]+)?[kKmMbB]?";

    private static final Pattern SIDEBAR_ROW = Pattern.compile(
            "(?<![\\p{L}])(?<name>" + String.join("|", names()) + ")\\s+(?<num>" + NUMBER + ")$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TRAILING_INVISIBLE = Pattern.compile("[\\s\\p{Cf}\\p{Co}]+$");

    private HalloweenText() {
    }

    private static List<String> names() {
        java.util.ArrayList<String> out = new java.util.ArrayList<>(CROPS);
        out.add(CANDY);
        return out;
    }

    /**
     * The server writes headings in small capitals, "ᴍɪɴᴇʀᴇғɪɴᴇ", some of them borrowed from other
     * alphabets (the "ғ" there is Cyrillic). These become plain letters, so "ᴍɪɴᴇʀᴇғɪɴᴇ" reads as
     * "minerefine".
     */
    private static final String SMALL_CAPS = "ᴀʙᴄᴅᴇꜰғɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱѕᴛᴜᴠᴡʏᴢ";
    private static final String SMALL_CAPS_PLAIN = "abcdeffghijklmnopqrsstuvwyz";

    public static String plainLetters(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int at = SMALL_CAPS.indexOf(c);
            out.append(at >= 0 ? SMALL_CAPS_PLAIN.charAt(at) : c);
        }
        return out.toString();
    }

    public static String strip(String raw) {
        if (raw == null) {
            return "";
        }
        String s = FORMATTING.matcher(raw).replaceAll("");
        return TRAILING_INVISIBLE.matcher(s).replaceAll("").trim();
    }

    /** "2.5M" is 2,500,000, "21.5" is 21.5, "45,395" is 45395. Empty for anything else. */
    public static OptionalDouble number(String raw) {
        if (raw == null) {
            return OptionalDouble.empty();
        }
        String s = raw.trim().replace(",", "");
        if (s.isEmpty()) {
            return OptionalDouble.empty();
        }
        double multiplier = switch (Character.toUpperCase(s.charAt(s.length() - 1))) {
            case 'K' -> 1e3;
            case 'M' -> 1e6;
            case 'B' -> 1e9;
            default -> 1.0;
        };
        if (multiplier != 1.0) {
            s = s.substring(0, s.length() - 1);
        }
        try {
            double v = Double.parseDouble(s) * multiplier;
            return v >= 0 && Double.isFinite(v) ? OptionalDouble.of(v) : OptionalDouble.empty();
        } catch (NumberFormatException e) {
            return OptionalDouble.empty();
        }
    }

    /** The canonical spelling of a currency name, whatever case the server used. */
    static String canonical(String name) {
        for (String n : names()) {
            if (n.equalsIgnoreCase(name.trim())) {
                return n;
            }
        }
        return name.trim();
    }

    // ---------------------------------------------------------------- sidebar

    /** What the sidebar says while in the Halloween dimension. */
    public record Sidebar(boolean halloween, java.util.Map<String, Double> balances) {}

    /**
     * Reads the sidebar. It is the Halloween one when it has the "HALLOWEEN" heading, or failing
     * that a Candy row next to a crop row. Order is not relied on: the scoreboard hands its rows
     * over unsorted.
     */
    public static Sidebar sidebar(List<String> lines) {
        java.util.Map<String, Double> balances = new java.util.LinkedHashMap<>();
        boolean heading = false;
        if (lines != null) {
            for (String raw : lines) {
                String line = plainLetters(strip(raw));
                String letters = line.replaceAll("[^\\p{L}]", "").toUpperCase(Locale.ROOT);
                if (letters.equals("HALLOWEEN")) {
                    heading = true;
                }
                Matcher m = SIDEBAR_ROW.matcher(line);
                if (m.find()) {
                    number(m.group("num")).ifPresent(v -> balances.put(canonical(m.group("name")), v));
                }
            }
        }
        boolean crops = balances.keySet().stream().anyMatch(CROPS::contains);
        boolean halloween = heading || (balances.containsKey(CANDY) && crops);
        return new Sidebar(halloween, halloween ? balances : java.util.Map.of());
    }

    /**
     * The server's name, so commands only go to this server. It is the sidebar title, "ᴍɪɴᴇʀᴇғɪɴᴇ",
     * and the address at the bottom, "ᴍɪɴᴇʀᴇꜰɪɴᴇ.ᴍɪɴᴇʜᴜᴛ.ɢɢ"; any sidebar line naming it will do.
     */
    public static boolean isMineRefine(List<String> sidebarLines) {
        if (sidebarLines != null) {
            for (String line : sidebarLines) {
                if (isMineRefineTitle(line)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isMineRefineTitle(String title) {
        return plainLetters(strip(title)).replaceAll("[^\\p{L}]", "").toUpperCase(Locale.ROOT).contains("MINEREFINE");
    }

    // ------------------------------------------------------------- action bar

    private static final Pattern CROP_SPRITE = Pattern.compile(
            "\\[item/(?<sprite>carrot|beetroot|nether_wart|fire_coral(?:_block|_fan)?|potato|wheat|pumpkin|melon_slice|melon)(?:@[a-z0-9_:./\\-]+)?\\]",
            Pattern.CASE_INSENSITIVE);

    private static String cropOfSprite(String sprite) {
        return switch (sprite.toLowerCase(Locale.ROOT)) {
            case "carrot" -> "Carrots";
            case "beetroot" -> "Beetroots";
            case "nether_wart" -> "Nether Wart";
            case "fire_coral", "fire_coral_block", "fire_coral_fan" -> "Fire Coral";
            case "potato" -> "Potatoes";
            case "wheat" -> "Wheat";
            case "pumpkin" -> "Pumpkins";
            default -> "Melons";
        };
    }

    public record CropReading(String crop, double amount) {}

    /**
     * The crop total on the action bar while farming: the crop's icon with its total, "[item/
     * beetroot] 34,347". The number after the icon is preferred, since the hex stat ("4.94 ☽")
     * can sit just before it.
     */
    public static Optional<CropReading> actionBar(String raw) {
        String text = FORMATTING.matcher(raw == null ? "" : raw).replaceAll("");
        Matcher m = CROP_SPRITE.matcher(text);
        CropReading found = null;
        while (m.find()) {
            String crop = cropOfSprite(m.group("sprite"));
            Matcher after = Pattern.compile("^[^\\p{L}\\p{N}\\[\\]+\\-]{0,4}(" + NUMBER + ")(?![\\p{N}/])")
                    .matcher(text.substring(m.end()));
            if (after.find()) {
                OptionalDouble v = number(after.group(1));
                if (v.isPresent()) {
                    found = new CropReading(crop, v.getAsDouble());
                    continue;
                }
            }
            Matcher before = Pattern.compile("(?<![\\p{N}.,+\\-/])(" + NUMBER + ")[^\\p{L}\\p{N}\\[\\]|+\\-]{0,4}$")
                    .matcher(text.substring(0, m.start()));
            if (before.find()) {
                OptionalDouble v = number(before.group(1));
                if (v.isPresent()) {
                    found = new CropReading(crop, v.getAsDouble());
                }
            }
        }
        return Optional.ofNullable(found);
    }

    // ------------------------------------------------------------------- chat

    private static final Pattern PEST_CANDY = Pattern.compile(
            "^\\+\\s*(" + NUMBER + ")\\s+.*?Candy from the Pest!", Pattern.CASE_INSENSITIVE);
    private static final Pattern PEST_CHARM = Pattern.compile(
            "^\\+\\s*([0-9]+)\\s+.*?Charm from the Pest!", Pattern.CASE_INSENSITIVE);
    private static final Pattern RECHARGING = Pattern.compile(
            "^(?<name>.+?) is recharging \\((?<secs>[0-9]+(?:\\.[0-9]+)?)s\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RECHARGED = Pattern.compile(
            "^(?<name>.+?) has recharged!", Pattern.CASE_INSENSITIVE);
    private static final Pattern ABILITY_SET = Pattern.compile(
            "^Farming ability set to (?<name>.+?)\\.?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern REFINED = Pattern.compile(
            "hoe has been refined to \\[?(?<level>[IVXLC]+|[0-9]+)\\]?", Pattern.CASE_INSENSITIVE);

    public static OptionalDouble pestCandy(String line) {
        Matcher m = PEST_CANDY.matcher(strip(line));
        return m.find() ? number(m.group(1)) : OptionalDouble.empty();
    }

    public static int pestCharms(String line) {
        Matcher m = PEST_CHARM.matcher(strip(line));
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    public record Recharge(String ability, OptionalDouble secondsLeft) {}

    /** "X is recharging (2.31s)" with the seconds, or "X has recharged!" without. */
    public static Optional<Recharge> recharge(String line) {
        String s = strip(line);
        Matcher m = RECHARGING.matcher(s);
        if (m.find()) {
            return Optional.of(new Recharge(m.group("name").trim(),
                    OptionalDouble.of(Double.parseDouble(m.group("secs")))));
        }
        m = RECHARGED.matcher(s);
        if (m.find()) {
            return Optional.of(new Recharge(m.group("name").trim(), OptionalDouble.empty()));
        }
        return Optional.empty();
    }

    /** The class in "Farming ability set to Jack o'Lantern.", sent when one is picked at the NPC. */
    public static Optional<String> abilitySet(String line) {
        Matcher m = ABILITY_SET.matcher(strip(line));
        return m.find() ? Optional.of(m.group("name").trim()) : Optional.empty();
    }

    /** The refine level from "Your hoe has been refined to [I].", or 0. */
    public static int refinedTo(String line) {
        Matcher m = REFINED.matcher(strip(line));
        if (!m.find()) {
            return 0;
        }
        String level = m.group("level");
        if (level.chars().allMatch(Character::isDigit)) {
            return Integer.parseInt(level);
        }
        return minerefinehud.shop.RomanNumerals.parse(level).orElse(0);
    }
}
