package minerefinehud.client;

import minerefinehud.turret.SalvageGuard;

/**
 * Shows the mod every click in a menu before the game acts on it, from the mixin on the game's
 * own click handler, and lets it stop one. Also keeps count of the mouse button going down and up
 * on a menu screen, from the second mixin, so a click can be told from the ones the game adds.
 *
 * Every way of moving an item ends in that handler: a mouse click, a shift-click, a hotbar key,
 * the drop key, another mod's inventory shortcuts. Like {@link DropKeyHook}, the listener lives
 * here because a mixin package may hold only mixins. Anything it throws lets the click through:
 * a broken guard must never lock the player out of their inventory.
 *
 * All of it happens on the game thread; the counts are volatile for the game test, which reads
 * them from its own.
 */
public final class ContainerClickHook {

    public interface Listener {
        /**
         * @param input the game's name for the kind of click: PICKUP, QUICK_MOVE, SWAP, THROW...
         * @return true to stop the click: nothing moves and nothing is sent to the server
         */
        boolean stop(int containerId, int slot, int button, String input);
    }

    private static final Listener NONE = (containerId, slot, button, input) -> false;

    private static volatile Listener listener = NONE;

    /** Clicks seen, so the client game test can prove the mixin is applied. */
    private static volatile int count;

    /** Clicks the listener threw on, for /mrhud debug: each was let through unguarded. */
    private static volatile int failures;
    private static volatile String lastFailure = "";

    private static final SalvageGuard.Presses PRESSES = new SalvageGuard.Presses();
    private static volatile int presses;
    private static volatile int releases;

    private ContainerClickHook() {
    }

    public static void listen(Listener l) {
        listener = l == null ? NONE : l;
    }

    public static int count() {
        return count;
    }

    public static int failures() {
        return failures;
    }

    public static String lastFailure() {
        return lastFailure;
    }

    /** Presses of a mouse button on a menu screen so far. */
    public static int presses() {
        return presses;
    }

    static SalvageGuard.Presses pressLog() {
        return PRESSES;
    }

    /** Mouse buttons let go on a menu screen so far. */
    public static int releases() {
        return releases;
    }

    public static void pressed(boolean doubleClick) {
        PRESSES.pressed(doubleClick);
        presses = PRESSES.count();
    }

    public static void pressDone() {
        PRESSES.pressDone();
    }

    public static void released() {
        PRESSES.released();
        releases++;
    }

    public static void releaseDone() {
        PRESSES.releaseDone();
    }

    public static boolean fire(int containerId, int slot, int button, String input) {
        count++;
        PRESSES.click();
        try {
            return listener.stop(containerId, slot, button, input);
        } catch (Exception | LinkageError e) {
            // LinkageError: a Minecraft version that moved a field or method the listener reads.
            failures++;
            lastFailure = String.valueOf(e);
            return false;
        }
    }
}
