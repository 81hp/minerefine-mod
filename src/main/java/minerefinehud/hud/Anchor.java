package minerefinehud.hud;

import java.util.Locale;
import java.util.Optional;

/**
 * Which corner or edge the overlay is pinned to.
 *
 * Raw pixel coordinates alone are not enough. A panel placed at x=1850 sits neatly on the right
 * of a 1920 wide window and disappears entirely when the window is resized or the GUI scale
 * changes. Anchoring to a corner and storing a small offset from it survives both.
 */
public enum Anchor {

    TOP_LEFT(Horizontal.LEFT, Vertical.TOP),
    TOP_CENTER(Horizontal.CENTER, Vertical.TOP),
    TOP_RIGHT(Horizontal.RIGHT, Vertical.TOP),
    MIDDLE_LEFT(Horizontal.LEFT, Vertical.MIDDLE),
    CENTER(Horizontal.CENTER, Vertical.MIDDLE),
    MIDDLE_RIGHT(Horizontal.RIGHT, Vertical.MIDDLE),
    BOTTOM_LEFT(Horizontal.LEFT, Vertical.BOTTOM),
    BOTTOM_CENTER(Horizontal.CENTER, Vertical.BOTTOM),
    BOTTOM_RIGHT(Horizontal.RIGHT, Vertical.BOTTOM);

    public enum Horizontal { LEFT, CENTER, RIGHT }

    public enum Vertical { TOP, MIDDLE, BOTTOM }

    private final Horizontal horizontal;
    private final Vertical vertical;

    Anchor(Horizontal horizontal, Vertical vertical) {
        this.horizontal = horizontal;
        this.vertical = vertical;
    }

    public Horizontal horizontal() {
        return horizontal;
    }

    public Vertical vertical() {
        return vertical;
    }

    public static Anchor of(Horizontal h, Vertical v) {
        for (Anchor a : values()) {
            if (a.horizontal == h && a.vertical == v) {
                return a;
            }
        }
        return TOP_LEFT;
    }

    /** Tolerant lookup, so a hand-edited config with odd casing still works. */
    public static Optional<Anchor> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String key = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (Anchor a : values()) {
            if (a.name().equals(key)) {
                return Optional.of(a);
            }
        }
        return Optional.empty();
    }

    /** Human label for the position screen. */
    public String label() {
        return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
