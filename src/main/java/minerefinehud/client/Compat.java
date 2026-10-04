package minerefinehud.client;

import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Every Minecraft call whose shape differs between the supported versions, in one place.
 *
 * The rest of the mod calls these instead of Minecraft directly, so a new Minecraft version that
 * moves one of them is fixed here once. The `//?` comments are Stonecutter guards: each version's
 * jar is compiled with only its own branch. Plain renames (GuiGraphics and friends) are swapped in
 * stonecutter.gradle.kts instead and need no guard.
 */
public final class Compat {

    private Compat() {
    }

    /** The open screen, or null in game. 26.2 moved screen handling from Minecraft to Gui. */
    public static Screen screen(Minecraft client) {
        //? if >=26.2 {
        return client.gui.screen();
        //?} else {
        /*return client.screen;
        *///?}
    }

    public static void setScreen(Minecraft client, Screen screen) {
        //? if >=26.2 {
        client.gui.setScreen(screen);
        //?} else {
        /*client.setScreen(screen);
        *///?}
    }

    /** True while the player has the HUD hidden with F1. */
    public static boolean hudHidden(Minecraft client) {
        //? if >=26.2 {
        return client.gui.hud.isHidden();
        //?} else {
        /*return client.options.hideGui;
        *///?}
    }

    /** A line in the player's own chat, never sent to the server. */
    public static void chat(Minecraft client, Component message) {
        if (client.player == null) {
            return;
        }
        //? if >=26.1 {
        client.player.sendSystemMessage(message);
        //?} else {
        /*client.player.displayClientMessage(message, false);
        *///?}
    }

    /**
     * Only lets text through that passes {@code allowed}, and reports accepted changes to
     * {@code changed}. 26.1 dropped EditBox's filter, so there a rejected edit is undone right
     * after it lands, before anything else sees it.
     */
    public static void filter(EditBox box, Predicate<String> allowed, Consumer<String> changed) {
        //? if >=26.1 {
        String[] lastGood = { box.getValue() };
        boolean[] reverting = { false };
        box.setResponder(text -> {
            if (reverting[0]) {
                return;
            }
            if (!allowed.test(text)) {
                // Put the cursor back where it was before the refused characters went in, rather
                // than at the end where setValue leaves it.
                int cursor = box.getCursorPosition() - (text.length() - lastGood[0].length());
                reverting[0] = true;
                try {
                    box.setValue(lastGood[0]);
                    // moveCursorTo(.., false) moves the selection end too; setCursorPosition alone
                    // would leave the rest of the text selected for the next key to overwrite.
                    box.moveCursorTo(Math.max(0, Math.min(cursor, lastGood[0].length())), false);
                } finally {
                    reverting[0] = false;
                }
                return;
            }
            lastGood[0] = text;
            changed.accept(text);
        });
        //?} else {
        /*box.setFilter(allowed);
        box.setResponder(changed);
        *///?}
    }
}
