package minerefinehud.server;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Tells MineRefine's own text from any other server's, however the name is dressed up.
 *
 * The server writes its name in small capitals, "ᴍɪɴᴇʀᴇғɪɴᴇ", with letters borrowed from other
 * alphabets (that "ғ" is Cyrillic), and its address as "ᴍɪɴᴇʀᴇꜰɪɴᴇ.ᴍɪɴᴇʜᴜᴛ.ɢɢ". Other styles a
 * sidebar or tab list may use are read too: full-width "ＭＩＮＥＲＥＦＩＮＥ", mathematical bold or
 * script letters, circled letters, superscripts, accents, Greek and Cyrillic look-alikes, spaces or
 * dots between letters, and colour codes. All become plain lower-case letters before matching.
 *
 * No Minecraft types.
 */
public final class ServerName {

    private static final String NAME = "minerefine";

    /**
     * A name is in the first few characters of its line. The rest is not read: a server decides
     * how long a line is, and this runs on every sidebar and tab list line many times a second.
     */
    private static final int MAX_LENGTH = 512;

    private static final Pattern FORMATTING = Pattern.compile("[§&][0-9A-Fa-fK-Ok-oRrXx]");
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NOT_LETTER = Pattern.compile("[^\\p{L}]+");

    /**
     * Letters Unicode does not fold to plain Latin by itself: small capitals and look-alikes from
     * other alphabets. Each character in FROM reads as the one at the same place in TO, so the two
     * must stay the same length.
     */
    private static final String FROM =
            // Latin small capitals and modifier-style letters
            "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡʏᴢıȷ"
            // Cyrillic look-alikes, upper and lower case, including the ones used as small caps
            + "АВЕЅІЈКМНОРСТХУаеѕіјорсхуғҒмнкт"
            // Greek look-alikes
            + "ΑΒΕΖΗΙΚΜΝΟΡΤΥΧιο";
    private static final String TO =
            "abcdefghijklmnopqrstuvwyzij"
            + "abesijkmhopctxyaesijopcxyffmhkt"
            + "abezhikmnoptyxio";

    private ServerName() {
    }

    /** Plain lower-case letters only: "ᴍɪɴᴇʀᴇꜰɪɴᴇ.ᴍɪɴᴇʜᴜᴛ.ɢɢ" becomes "minerefineminehutgg". */
    public static String letters(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String s = raw.length() > MAX_LENGTH ? raw.substring(0, MAX_LENGTH) : raw;
        s = FORMATTING.matcher(s).replaceAll("");
        // Marks off first, and only after the colour codes, or "&" + mark + "b" would become one.
        // Left on, thousands of stacked marks take the normaliser seconds: it sorts them one by one.
        s = MARKS.matcher(s).replaceAll("");
        // Full-width, bold, script, circled and superscript letters fold to plain ones here.
        s = Normalizer.normalize(s, Normalizer.Form.NFKC);
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int at = FROM.indexOf(c);
            out.append(at >= 0 ? TO.charAt(at) : c);
        }
        // Accents last, so "é" and a small capital carrying a mark both end up plain.
        s = MARKS.matcher(Normalizer.normalize(out, Normalizer.Form.NFD)).replaceAll("");
        return NOT_LETTER.matcher(s).replaceAll("").toLowerCase(Locale.ROOT);
    }

    /** This text names MineRefine: a sidebar title, its address line, a tab list header. */
    public static boolean isMineRefine(String raw) {
        return letters(raw).contains(NAME);
    }

    /** Any of these lines names MineRefine. */
    public static boolean anyMineRefine(Iterable<String> lines) {
        if (lines != null) {
            for (String line : lines) {
                if (isMineRefine(line)) {
                    return true;
                }
            }
        }
        return false;
    }
}
