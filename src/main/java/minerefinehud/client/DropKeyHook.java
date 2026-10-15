package minerefinehud.client;

/**
 * Tells the mod the player pressed drop, from the mixin on the player's drop.
 *
 * Watching the drop key from a tick handler misses a quick tap: pressed and released between two
 * ticks, the key never reads as down. Every press goes through the player's drop, so the mixin
 * sees each one. Like {@link ActionBarHook}, the listener lives here because a mixin package may
 * hold only mixins, and anything it throws is swallowed.
 */
public final class DropKeyHook {

    private static volatile Runnable listener = () -> { };

    /** Drops seen, so the client game test can prove the mixin is applied. */
    private static volatile int count;

    private DropKeyHook() {
    }

    public static void listen(Runnable l) {
        listener = l == null ? () -> { } : l;
    }

    public static int count() {
        return count;
    }

    public static void fire() {
        count++;
        try {
            listener.run();
        } catch (Exception ignored) {
        }
    }
}
