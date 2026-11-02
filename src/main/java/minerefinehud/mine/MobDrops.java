package minerefinehud.mine;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The resource a mining mob drops, which is the mine it was found in. A mob turns up every half
 * minute or so while mining, and its rewards come as chat lines:
 *
 * <pre>
 * + ⟡ 1,400 Hunting EXP
 * + 16 Tokens
 * + ⬢ Ultra Compressed Ichor x33
 * + ⬟ Super Compressed Ichor x18
 * </pre>
 *
 * In the Sift, where every block of a mine can be another mine's too, that is the one thing that
 * names the mine for certain. Only lines right after "Hunting EXP" count: a cleared dungeon also
 * hands out "+ ⬢ Mega Compressed Timber x1", from a mine the player is nowhere near.
 *
 * No Minecraft types.
 */
public final class MobDrops {

    /** "+ ⟡ 1,400 Hunting EXP", the first line of a mob's rewards. */
    private static final Pattern HUNTING = Pattern.compile("^\\+\\s*\\S*\\s*[\\d,.]+\\s+Hunting EXP$",
            Pattern.CASE_INSENSITIVE);

    /** "+ ⬢ Ultra Compressed Ichor x33": the name and the count. */
    private static final Pattern DROP = Pattern.compile("^\\+\\s*(.*\\bcompressed\\s+.+?)\\s+x[\\d,]+$",
            Pattern.CASE_INSENSITIVE);

    /** The reward lines arrive together; one still coming this long after is something else. */
    static final long WINDOW_MS = 2_000L;

    private long huntingAt = Long.MIN_VALUE;

    /**
     * Reads one chat line. Returns the mine whose resource a mob just dropped, spelt as the price
     * data has it, or empty for any other line.
     */
    public Optional<String> onChat(String line, long now, MineCatalog catalog) {
        if (line == null) {
            return Optional.empty();
        }
        String text = line.strip();
        if (HUNTING.matcher(text).matches()) {
            huntingAt = now;
            return Optional.empty();
        }
        if (huntingAt == Long.MIN_VALUE || now - huntingAt > WINDOW_MS || now < huntingAt) {
            return Optional.empty();
        }
        var m = DROP.matcher(text);
        if (!m.matches()) {
            return Optional.empty();
        }
        return ResourcePickups.resourceName(m.group(1), List.of(), catalog);
    }

    /** Forget the last reward, after a relog or a server switch. */
    public void reset() {
        huntingAt = Long.MIN_VALUE;
    }
}
