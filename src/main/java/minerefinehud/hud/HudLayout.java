package minerefinehud.hud;

/**
 * Works out where the overlay actually draws, and handles dragging it around.
 *
 * All of this is plain arithmetic with no Minecraft types, which means the fiddly parts (staying
 * on screen, picking a sensible anchor when dropped, keeping the panel under the cursor while
 * dragging) are unit tested rather than discovered by eye in game.
 */
public final class HudLayout {

    /** Keeps at least this much of the panel on screen, so it can always be grabbed again. */
    private static final int MIN_VISIBLE = 8;

    public record Rect(int x, int y, int width, int height) {

        public boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }

        public int centerX() {
            return x + width / 2;
        }

        public int centerY() {
            return y + height / 2;
        }
    }

    /** Where the overlay sits: an anchor plus a nudge away from it. */
    public record Placement(Anchor anchor, int offsetX, int offsetY) {

        public Placement withOffset(int x, int y) {
            return new Placement(anchor, x, y);
        }

        public Placement withAnchor(Anchor a) {
            return new Placement(a, offsetX, offsetY);
        }
    }

    private HudLayout() {
    }

    /** The on-screen rectangle for a panel of this size, clamped so it can never be lost. */
    public static Rect place(Placement placement, int panelWidth, int panelHeight,
                             int screenWidth, int screenHeight) {

        int baseX = switch (placement.anchor().horizontal()) {
            case LEFT -> 0;
            case CENTER -> (screenWidth - panelWidth) / 2;
            case RIGHT -> screenWidth - panelWidth;
        };

        int baseY = switch (placement.anchor().vertical()) {
            case TOP -> 0;
            case MIDDLE -> (screenHeight - panelHeight) / 2;
            case BOTTOM -> screenHeight - panelHeight;
        };

        int x = clamp(baseX + placement.offsetX(), panelWidth, screenWidth);
        int y = clamp(baseY + placement.offsetY(), panelHeight, screenHeight);

        return new Rect(x, y, panelWidth, panelHeight);
    }

    private static int clamp(int value, int size, int limit) {
        int min = MIN_VISIBLE - size;
        int max = limit - MIN_VISIBLE;
        return Math.max(min, Math.min(max, value));
    }

    /**
     * New placement after a drag.
     *
     * The anchor is re-chosen from where the panel's centre lands, so dragging into the bottom
     * right corner actually pins it there rather than leaving it anchored top left with a large
     * offset that breaks on the next resolution change. The offset is then recomputed against
     * the new anchor, which keeps the panel exactly where the cursor dropped it.
     */
    public static Placement drag(Placement current, int panelWidth, int panelHeight,
                                 int screenWidth, int screenHeight,
                                 double deltaX, double deltaY) {

        Rect now = place(current, panelWidth, panelHeight, screenWidth, screenHeight);
        int wantX = now.x() + (int) Math.round(deltaX);
        int wantY = now.y() + (int) Math.round(deltaY);

        Anchor anchor = anchorFor(wantX + panelWidth / 2, wantY + panelHeight / 2,
                screenWidth, screenHeight);

        return offsetFor(anchor, wantX, wantY, panelWidth, panelHeight, screenWidth, screenHeight);
    }

    /**
     * Placement that puts the panel's top-left at (targetX, targetY), re-anchored by where its
     * centre lands.
     *
     * This is what dragging should use. {@link #drag} adds up per-event deltas, and each delta is
     * rounded to a whole GUI pixel: at GUI scale 3 a one-pixel mouse move is a third of a GUI
     * pixel, rounds to zero and is lost, so a slow drag sticks and then jumps, and the panel
     * creeps away from the cursor. Positioning absolutely from the cursor and the point where the
     * panel was grabbed has no error to accumulate, and a clamped edge cannot leave it behind.
     */
    public static Placement dragTo(int panelWidth, int panelHeight,
                                   int screenWidth, int screenHeight,
                                   double targetX, double targetY) {
        int x = (int) Math.round(targetX);
        int y = (int) Math.round(targetY);
        Anchor anchor = anchorFor(x + panelWidth / 2, y + panelHeight / 2, screenWidth, screenHeight);
        return offsetFor(anchor, x, y, panelWidth, panelHeight, screenWidth, screenHeight);
    }

    /** Which third of the screen a point falls in. */
    public static Anchor anchorFor(int x, int y, int screenWidth, int screenHeight) {
        Anchor.Horizontal h;
        if (x < screenWidth / 3) {
            h = Anchor.Horizontal.LEFT;
        } else if (x < 2 * screenWidth / 3) {
            h = Anchor.Horizontal.CENTER;
        } else {
            h = Anchor.Horizontal.RIGHT;
        }

        Anchor.Vertical v;
        if (y < screenHeight / 3) {
            v = Anchor.Vertical.TOP;
        } else if (y < 2 * screenHeight / 3) {
            v = Anchor.Vertical.MIDDLE;
        } else {
            v = Anchor.Vertical.BOTTOM;
        }

        return Anchor.of(h, v);
    }

    /** The offset that puts a panel's top-left at exactly (targetX, targetY) for this anchor. */
    public static Placement offsetFor(Anchor anchor, int targetX, int targetY,
                                      int panelWidth, int panelHeight,
                                      int screenWidth, int screenHeight) {

        Rect zero = place(new Placement(anchor, 0, 0), panelWidth, panelHeight,
                screenWidth, screenHeight);

        return new Placement(anchor, targetX - zero.x(), targetY - zero.y());
    }

    // ----------------------------------------------------------------- scale

    public static final double MIN_SCALE = 0.5;
    public static final double MAX_SCALE = 4.0;
    public static final double SCALE_STEP = 0.25;

    /** Any stored scale made safe: within range, and a corrupt or zero value means normal size. */
    public static double clampScale(double scale) {
        if (Double.isNaN(scale) || scale <= 0.0) {
            return 1.0;
        }
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
    }

    /** One step bigger or smaller, snapped to the step so repeated presses land on round sizes. */
    public static double stepScale(double scale, int steps) {
        double snapped = Math.round(clampScale(scale) / SCALE_STEP) * SCALE_STEP;
        return clampScale(snapped + steps * SCALE_STEP);
    }

    /** On-screen size of something drawn at this scale, rounded up so nothing is clipped. */
    public static int scaled(int size, double scale) {
        return (int) Math.ceil(size * clampScale(scale));
    }

    /** Drops the panel exactly on its anchor, which is the obvious "tidy this up" action. */
    public static Placement snapToAnchor(Placement current) {
        return current.withOffset(0, 0);
    }
}
