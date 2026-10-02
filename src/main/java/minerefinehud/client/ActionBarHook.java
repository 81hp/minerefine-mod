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

    private ActionBarHook() {
    }

    public static void listen(Consumer<String> l) {
        listener = l == null ? text -> { } : l;
    }

    public static void fire(String text) {
        try {
            listener.accept(text);
        } catch (Exception ignored) {
        }
    }
}
