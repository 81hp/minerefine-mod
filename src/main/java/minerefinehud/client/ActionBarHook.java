package minerefinehud.client;

import java.util.function.Consumer;

/**
 * Hands action bar text from the mixin to the mod.
 *
 * A mixin package may hold only mixins, so the listener lives here. The listener runs on the
 * render thread, where setOverlayMessage is called, and any exception it throws is swallowed so
 * a parsing bug can never take the HUD down with it.
 */
public final class ActionBarHook {

    private static volatile Consumer<String> listener = text -> { };

    /** The last action bar seen, so the client game test can prove the mixin is applied. */
    private static volatile String last = "";

    private ActionBarHook() {
    }

    public static void listen(Consumer<String> l) {
        listener = l == null ? text -> { } : l;
    }

    public static String last() {
        return last;
    }

    public static void fire(String text) {
        last = text;
        try {
            listener.accept(text);
        } catch (Exception ignored) {
        }
    }
}
