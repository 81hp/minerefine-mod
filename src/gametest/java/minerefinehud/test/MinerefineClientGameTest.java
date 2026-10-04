package minerefinehud.test;

import minerefinehud.client.ActionBarHook;
import minerefinehud.client.Compat;
import minerefinehud.client.HudPositionScreen;
import minerefinehud.client.SettingsScreen;
import minerefinehud.client.ShopScanner;
import minerefinehud.client.TurretScreen;
import minerefinehud.shop.ShopItemParser;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Starts the real game with the mod and walks through everything that touches Minecraft, so a
 * Minecraft version that moved something fails the build instead of the player's game.
 *
 * Runs on every supported version: ./gradlew :26.2:runClientGameTest, or testAll for all of them.
 * Screenshots land in versions/<version>/build/run/clientGameTest/screenshots.
 */
@SuppressWarnings("UnstableApiUsage")
public class MinerefineClientGameTest implements FabricClientGameTest {

    private static final String ACTION_BAR = "MineRefine HUD action bar check";
    private static final String SHOP_ITEM = "Test Shop Item";
    private static final String SHOP_LORE = "Cost: 5 Test Coins";

    @Override
    public void runTest(ClientGameTestContext context) {
        check(FabricLoader.getInstance().isModLoaded("minerefine-hud"), "the mod did not load");

        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null);
            context.waitTicks(20);

            // The action bar arrives as its own packet and reaches the mod only through the
            // mixin. If a Minecraft update moved the method it hooks, this is where it shows.
            world.getServer().runCommand("title @a actionbar \"" + ACTION_BAR + "\"");
            context.waitFor(client -> ActionBarHook.last().contains(ACTION_BAR), 100);

            // The HUD draws every frame from here on; a crash in it fails the test.
            context.runOnClient(client -> Compat.chat(client, Component.literal("[MineRefine] test message")));
            context.waitTicks(5);
            check(context.computeOnClient(client -> !Compat.hudHidden(client)), "HUD reported hidden");
            context.takeScreenshot("minerefine-hud-in-game");

            // The default key opens the move screen, which links on to the others.
            context.getInput().pressKey(GLFW.GLFW_KEY_F6);
            context.waitForScreen(HudPositionScreen.class);
            context.takeScreenshot("minerefine-hud-position-screen");

            context.clickScreenButton("Settings...");
            context.waitForScreen(SettingsScreen.class);
            context.takeScreenshot("minerefine-hud-settings-screen");

            context.clickScreenButton("Turret size calculator...");
            context.waitForScreen(TurretScreen.class);

            // The first field takes numbers only; the letter has to be refused.
            context.getInput().typeChars("12a.5");
            context.waitTick();
            String typed = context.computeOnClient(client ->
                    Compat.screen(client).getFocused() instanceof EditBox box ? box.getValue() : null);
            check("12.5".equals(typed), "turret field holds '" + typed + "', expected '12.5'");

            // A refused character must leave the cursor where it was: two left, x refused, 9 in.
            context.getInput().pressKey(GLFW.GLFW_KEY_LEFT);
            context.getInput().pressKey(GLFW.GLFW_KEY_LEFT);
            context.getInput().typeChars("x9");
            context.waitTick();
            typed = context.computeOnClient(client ->
                    Compat.screen(client).getFocused() instanceof EditBox box ? box.getValue() : null);
            check("129.5".equals(typed), "turret field holds '" + typed + "', expected '129.5'");
            context.takeScreenshot("minerefine-hud-turret-screen");

            // Escape walks back through each screen's onClose: turret, settings, game.
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitForScreen(SettingsScreen.class);
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitFor(client -> Compat.screen(client) == null);

            // Shop prices come from item names and lore in an open container. The scanner
            // swallows errors so a broken read never crashes the game, which is exactly why it is
            // checked here: an inventory is a container screen like any shop.
            world.getServer().runCommand(
                    "give @a minecraft:stone[minecraft:custom_name=\"" + SHOP_ITEM
                    + "\",minecraft:lore=[\"" + SHOP_LORE + "\"]]");
            context.waitTicks(5);
            context.getInput().pressKey(options -> options.keyInventory);
            context.waitFor(client -> Compat.screen(client) != null);
            List<ShopItemParser.ItemView> items = context.computeOnClient(ShopScanner::visibleItems);
            check(items.stream().anyMatch(item -> item.title().equals(SHOP_ITEM)
                            && item.lore().contains(SHOP_LORE)),
                    "the shop scanner did not read the item's name and lore: " + items);
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitFor(client -> Compat.screen(client) == null);
        }
    }

    private static void check(boolean ok, String failure) {
        if (!ok) {
            throw new AssertionError(failure);
        }
    }
}
