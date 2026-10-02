package minerefinehud.progress;

import minerefinehud.shop.Amounts;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the resource balance out of the mining action bar.
 *
 * Observed in game while mining, as plain text:
 *
 *   +140,732 EXP   +34.20 Coins   +0.135 Tokens  |  1.55B[block/cobblestone]
 *
 * and when idle, a different line entirely:
 *
 *   267.1/267.1 ❤   24,290 ⛨   1,885 ⛏
 *
 * The icon after the mining total is a sprite of the block being mined, which plain text renders
 * as "[block/cobblestone]". That sprite is required: the idle line's "1,885 ⛏" is a stat, not a
 * balance, and reading it as one replaced a 1.57B balance with 1,885 the moment mining stopped.
 * The sprite also says which block is being mined, which is how the mine is recognised. Some
 * mines show an item instead, "881.71M[item/iron_ingot@items]" at Zircon; those were once
 * ignored, which froze the progress bar and mine detection at every such mine.
 */
public final class ActionBarReader {

    /** @param sprite the block sprite after the total, e.g. "block/cobblestone" */
    public record Reading(long amount, String sprite) {}

    private static final Pattern FORMATTING = Pattern.compile("[§&][0-9A-Fa-fK-Ok-oRr]");

    /**
     * An unsigned amount followed by a sprite, "1.55B[block/cobblestone]".
     *
     * Up to four non-printing characters may sit between them. The server lays the bar out with
     * resource-pack spacing glyphs, non-breaking spaces and the like, which plain text keeps but
     * chat shows as nothing, so "773.16M[block/cobblestone]" on screen is not necessarily those
     * characters back to back. Letters, digits, brackets, signs and the "|" separator are still
     * excluded, so a gain can never be joined to the sprite.
     */
    private static final Pattern TOTAL = Pattern.compile(
            "(?<![\\p{N}.,+\\-])(?<num>[0-9][0-9,]*(?:\\.[0-9]+)?)(?<suffix>[KMBTkmbt])?"
            + "[^\\p{L}\\p{N}\\[\\]|+\\-]{0,4}"
            + "\\[(?<sprite>(?:block|item)/[a-z0-9_./\\-]+)(?:@[a-z0-9_:./\\-]+)?\\]",
            Pattern.CASE_INSENSITIVE);

    /** Spacing glyphs the server pads the bar with, which plain text keeps but shows as nothing. */
    private static final Pattern INVISIBLE_TAIL = Pattern.compile("[\\s\\p{Cf}\\p{Co}]+$");

    private ActionBarReader() {
    }

    public static Optional<Reading> read(String line) {
        if (line == null || line.isBlank()) {
            return Optional.empty();
        }
        String text = FORMATTING.matcher(line).replaceAll("");

        // The total sits at the end of the line, so the last match is the one wanted.
        Reading last = null;
        Matcher m = TOTAL.matcher(text);
        while (m.find()) {
            String sprite = m.group("sprite").toLowerCase(Locale.ROOT);
            // Some mines show an item as their icon: Zircon's total is "881.71M[item/iron_ingot]".
            // A special tool's name can end in a number before an item sprite too, "Khan's Spoil
            // 3[item/book]", so for items the number must open the line or follow the "|".
            if (sprite.startsWith("item/") && !opensSegment(text, m.start("num"))) {
                continue;
            }
            String raw = m.group("num") + (m.group("suffix") == null ? "" : m.group("suffix"));
            OptionalLong amount = Amounts.parse(raw);
            if (amount.isPresent()) {
                last = new Reading(amount.getAsLong(), sprite);
            }
        }
        return Optional.ofNullable(last);
    }

    /** Nothing but padding between the start of the line, or a "|", and this position. */
    private static boolean opensSegment(String text, int at) {
        String before = INVISIBLE_TAIL.matcher(text.substring(0, at)).replaceAll("");
        return before.isEmpty() || before.endsWith("|");
    }

    /**
     * Whose resource the total is: the mine the block belongs to, or the resource picked up with
     * this very block. Nothing else.
     *
     * The tool in hand and the detected mine used to fill in for an unknown block. Both are stale
     * exactly when the block is unknown, at a mine just walked into: mining Wind with a Lodestone
     * pickaxe filed Wind's total as the Lodestone balance, and the Lodestone bar showed a fill it
     * never had. No balance is better than a wrong one; the block is learned within a few blocks
     * from a pickup, or at once from a shop visit.
     */
    public static Optional<String> currencyFor(Optional<String> blockMine, Optional<String> pickupNow) {
        if (blockMine.isPresent() && !blockMine.get().isBlank()) {
            return blockMine;
        }
        return pickupNow.filter(m -> !m.isBlank());
    }
}
