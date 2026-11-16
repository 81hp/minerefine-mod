package minerefinehud.client;

/**
 * Hands the tab list's header and footer from the mixin to the mod.
 *
 * A mixin package may hold only mixins, so the text lives here. The game clears both without
 * going through the setters, on disconnect and when a proxy moves the player to another server,
 * and the mixin clears these at that same moment.
 */
public final class TabListHook {

    private static volatile String header = "";
    private static volatile String footer = "";

    private TabListHook() {
    }

    public static void header(String text) {
        header = text == null ? "" : text;
    }

    public static void footer(String text) {
        footer = text == null ? "" : text;
    }

    public static String header() {
        return header;
    }

    public static String footer() {
        return footer;
    }

    public static void clear() {
        header = "";
        footer = "";
    }
}
