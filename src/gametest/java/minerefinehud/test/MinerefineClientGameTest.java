package minerefinehud.test;

import minerefinehud.client.ActionBarHook;
import minerefinehud.client.BossIndexPoller;
import minerefinehud.client.BossPickScreen;
import minerefinehud.client.Compat;
import minerefinehud.client.ContainerClickHook;
import minerefinehud.client.DropKeyHook;
import minerefinehud.client.HudPositionScreen;
import minerefinehud.client.ServerCheck;
import minerefinehud.client.SettingsScreen;
import minerefinehud.client.ShopScanner;
import minerefinehud.client.TurretScreen;
import minerefinehud.shop.ShopItemParser;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
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
    /** The salvage menu's title on MineRefine, and two turrets either side of the default size. */
    private static final String SALVAGE_MENU = "Shift+Click Turrets Inside";
    private static final String BIG_TURRET = "Jade Turret";
    private static final String SMALL_TURRET = "Sponge Turret";

    @Override
    public void runTest(ClientGameTestContext context) {
        check(FabricLoader.getInstance().isModLoaded("minerefine-hud"), "the mod did not load");

        // CI machines draw the game in software, which at full frame rate takes the CPU the world
        // generator needs. A low frame cap and the smallest view keep world loading well inside the
        // test framework's one-minute limit.
        context.runOnClient(client -> {
            client.options.framerateLimit().set(10);
            client.options.enableVsync().set(false);
            client.options.renderDistance().set(2);
            client.options.simulationDistance().set(5);
            // The blur behind the "Connecting" screen is the slowest thing software rendering draws,
            // and the server only ticks as fast as the client does (see serverProperties).
            client.options.menuBackgroundBlurriness().set(0);
        });

        // A dedicated server rather than a singleplayer world: on GitHub's machines the built-in
        // server of 1.21.9 to 1.21.11 freezes at "Preparing spawn area: 16%" and the test times out.
        // It is also closer to the real thing, a client on a server.
        // `var`: the connection's type is named differently in each Fabric API version.
        for (int attempt = 1; ; attempt++) {
            boolean connected = false;
            // The dump after a failed join comes once the server has stopped; this one comes while
            // both sides are still stuck, with the login step each side's connection is at.
            java.util.concurrent.atomic.AtomicBoolean joined = new java.util.concurrent.atomic.AtomicBoolean();
            Thread watchdog = new Thread(() -> {
                try {
                    Thread.sleep(30_000L);
                } catch (InterruptedException e) {
                    return;
                }
                if (!joined.get()) {
                    dumpThreads();
                    dumpConnections();
                }
            }, "minerefine-hud-test join watchdog");
            watchdog.setDaemon(true);
            watchdog.start();
            try (TestDedicatedServerContext server = context.worldBuilder().createServer(serverProperties());
                 var connection = server.connect()) {
                joined.set(true);
                watchdog.interrupt();
                connected = true;
                context.waitFor(client -> client.player != null);
                context.waitTicks(20);

                // The action bar arrives as its own packet and reaches the mod only through the
                // mixin. If a Minecraft update moved the method it hooks, this is where it shows.
                server.runCommand("title @a actionbar \"" + ACTION_BAR + "\"");
                context.waitFor(client -> ActionBarHook.last().contains(ACTION_BAR), 100);

                // The test server is not MineRefine, so the mod stays off until its sidebar says so,
                // in the server's own small capitals. Everything below needs the mod on.
                context.waitTicks(20);
                check(!context.computeOnClient(client -> ServerCheck.onMineRefine()),
                        "a plain server was taken for MineRefine");
                server.runCommand("scoreboard objectives add mrhudtest dummy \"ᴍɪɴᴇʀᴇғɪɴᴇ\"");
                server.runCommand("scoreboard objectives setdisplay sidebar mrhudtest");
                context.waitFor(client -> ServerCheck.onMineRefine(), 200);

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

                // The Halloween tab, then back to General for the turret button.
                context.clickScreenButton("Halloween");
                context.waitTick();
                context.takeScreenshot("minerefine-hud-settings-halloween");

                // The Bosses tab and its boss picker, there and back.
                context.clickScreenButton("Bosses");
                context.waitTick();
                context.takeScreenshot("minerefine-hud-settings-bosses");
                context.clickScreenButton("Choose bosses...");
                context.waitForScreen(BossPickScreen.class);
                context.takeScreenshot("minerefine-hud-boss-picker");
                context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
                context.waitForScreen(SettingsScreen.class);

                // The look tabs draw their own controls and the panels as previews, item icons
                // included; every preset is clicked through the real mouse handling.
                context.clickScreenButton("Style");
                context.waitTick();
                for (String preset : List.of("Fork", "Tooltip", "Ledger", "Minimal", "Icons")) {
                    check(context.computeOnClient(client -> Compat.screen(client) instanceof SettingsScreen s
                            && s.clickOption("Preset", preset)), "could not pick the " + preset + " preset");
                    context.waitTick();
                    context.takeScreenshot("minerefine-hud-style-" + preset.toLowerCase(java.util.Locale.ROOT));
                }
                check(context.computeOnClient(client -> Compat.screen(client) instanceof SettingsScreen s
                        && s.clickOption("", "Mine")), "could not switch the preview");
                context.waitTick();
                context.takeScreenshot("minerefine-hud-style-mine-preview");
                context.clickScreenButton("Colours");
                context.waitTick();
                context.takeScreenshot("minerefine-hud-settings-colours");
                context.clickScreenButton("Panels");
                context.waitTick();
                context.takeScreenshot("minerefine-hud-settings-panels");
                // The Halloween panel in its own look: sections, crop icons, fading bars.
                for (String panel : List.of("Halloween", "Pumpkin set", "Abilities")) {
                    check(context.computeOnClient(client -> Compat.screen(client) instanceof SettingsScreen s
                            && s.selectPanel(panel)), "could not show the " + panel + " panel");
                    context.waitTick();
                    context.takeScreenshot("minerefine-hud-panel-" + panel.toLowerCase(java.util.Locale.ROOT)
                            .replace(' ', '-'));
                }
                context.clickScreenButton("General");
                context.waitTick();

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
                server.runCommand(
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

                // The Halloween hoe's ability starts its cooldown on drop, seen only through the
                // mixin on the player's drop. A quick press is the case a tick handler would miss.
                int drops = DropKeyHook.count();
                context.getInput().pressKey(options -> options.keyDrop);
                context.waitFor(client -> DropKeyHook.count() > drops, 100);

                // The automatic /bossindex check: the menu it asked for is read and never shown. A
                // test server has no such command, so the menu's packets are handed to the game
                // directly, through the same handlers (and mixin) the server's would go through.
                server.runCommand("give @a minecraft:dragon_head[minecraft:custom_name=\"« Zeus »\","
                        + "minecraft:lore=[\"Total Loot\",\"9m 40s\"]]");
                context.waitTicks(5);
                int menusRead = BossIndexPoller.readCount();
                context.runOnClient(client -> {
                    net.minecraft.world.item.ItemStack zeus = client.player.getInventory().getNonEquipmentItems()
                            .stream().filter(s -> s.getHoverName().getString().contains("Zeus")).findFirst()
                            .orElseThrow(() -> new AssertionError("the boss item was not given"));
                    BossIndexPoller.expectForTest();
                    client.getConnection().handleOpenScreen(new net.minecraft.network.protocol.game.ClientboundOpenScreenPacket(
                            77, net.minecraft.world.inventory.MenuType.GENERIC_9x3, Component.literal("Boss Index")));
                    java.util.List<net.minecraft.world.item.ItemStack> slots = new java.util.ArrayList<>();
                    for (int i = 0; i < 63; i++) {
                        slots.add(i == 4 ? zeus.copy() : net.minecraft.world.item.ItemStack.EMPTY);
                    }
                    client.getConnection().handleContainerContent(
                            new net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket(
                                    77, 1, slots, net.minecraft.world.item.ItemStack.EMPTY));
                });
                check(context.computeOnClient(client -> Compat.screen(client) == null), "the boss index menu was shown");
                context.waitFor(client -> BossIndexPoller.readCount() > menusRead, 100);
                check(context.computeOnClient(client -> Compat.screen(client) == null), "the boss index menu was shown");

                // Any command, however sent, holds the mod's own ones back (the server kicks for
                // two in one tick): proves the mixin on the outgoing packets is applied.
                context.waitFor(client -> !minerefinehud.client.AutoCommands.heldByRecentCommand(), 200);
                context.runOnClient(client -> client.getConnection().sendCommand("help"));
                check(context.computeOnClient(client -> minerefinehud.client.AutoCommands.heldByRecentCommand()),
                        "a command sent did not hold the mod's own commands back");

                // The salvage guard. A test server has no salvage menu, so one is opened the way
                // the server's would be: the same packet, the same title. Two identical big turrets
                // (items that do not stack) and a small one.
                server.runCommand("give @a minecraft:totem_of_undying[minecraft:custom_name=\"" + BIG_TURRET
                        + "\",minecraft:lore=[\"| Size: 1,800\",\"| Size Cap: 1900\"]] 2");
                server.runCommand("give @a minecraft:dropper[minecraft:custom_name=\"" + SMALL_TURRET
                        + "\",minecraft:lore=[\"| Size: 281\"]]");
                context.waitFor(client -> carried(client, BIG_TURRET) == 2 && carried(client, SMALL_TURRET) == 1, 100);
                context.runOnClient(client -> client.getConnection().handleOpenScreen(
                        new net.minecraft.network.protocol.game.ClientboundOpenScreenPacket(
                                78, net.minecraft.world.inventory.MenuType.GENERIC_9x6,
                                Component.literal(SALVAGE_MENU))));
                context.waitFor(client -> Compat.screen(client)
                        instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>, 100);
                int clicks = ContainerClickHook.count();
                int presses = ContainerClickHook.presses();
                int releases = ContainerClickHook.releases();
                // Real presses and releases of the mouse button, handed to the screen the way the
                // game's mouse handler does, so every click goes through the game's own handling and
                // both mixins: the one that counts presses and the one on the click itself. All in
                // one go with its own short waits: the second click only counts within three seconds
                // of the first, and a slow machine's ticks are not that reliable.
                String guardFailure = context.computeOnClient(client -> {
                    int first = slotOf(client, BIG_TURRET);
                    mouse(client, first, true, false, true);
                    mouse(client, first, true, false, false);
                    if (carried(client, BIG_TURRET) != 2) {
                        return "a big turret left the inventory on the first shift-click";
                    }
                    pause(150L);
                    // The second press of a double click. Held a while, then let go: the game now
                    // shift-clicks every stack like the turret by itself, its twin among them.
                    mouse(client, first, true, true, true);
                    if (carried(client, BIG_TURRET) != 1) {
                        return "a big turret did not move on the second shift-click";
                    }
                    pause(400L);
                    mouse(client, first, true, true, false);
                    if (carried(client, BIG_TURRET) != 1) {
                        return "the twin of a turret moved by itself when the double click was let go";
                    }
                    int twin = slotOf(client, BIG_TURRET);
                    mouse(client, twin, true, false, true);
                    mouse(client, twin, true, false, false);
                    if (carried(client, BIG_TURRET) != 1) {
                        return "the twin moved on one click: the game's own click counted as its first";
                    }
                    // A double click on the menu's frame clicks nothing, then is let go over the
                    // warned twin: the game shift-clicks it by itself again.
                    pause(150L);
                    mouse(client, FRAME, true, false, true);
                    mouse(client, FRAME, true, false, false);
                    mouse(client, FRAME, true, true, true);
                    mouse(client, twin, true, true, false);
                    if (carried(client, BIG_TURRET) != 1) {
                        return "a double click on the frame, let go over a warned turret, moved it";
                    }
                    // Those presses were the player doing something else, so the twin starts over.
                    pause(150L);
                    mouse(client, twin, true, false, true);
                    mouse(client, twin, true, false, false);
                    if (carried(client, BIG_TURRET) != 1) {
                        return "the twin moved on the first click after clicking elsewhere";
                    }
                    pause(150L);
                    mouse(client, twin, true, false, true);
                    mouse(client, twin, true, false, false);
                    if (carried(client, BIG_TURRET) != 0) {
                        return "the twin did not move on its second click";
                    }
                    int small = slotOf(client, SMALL_TURRET);
                    mouse(client, small, true, false, true);
                    mouse(client, small, true, false, false);
                    return carried(client, SMALL_TURRET) != 0 ? "a small turret did not move on one shift-click" : "";
                });
                // One of each has happened before the guard can first go wrong, so a guard that
                // misbehaves is reported as that, further down, and not as a missing mixin.
                check(ContainerClickHook.presses() > presses,
                        "mouse presses were not counted: the mixin on the menu screen's mouse press is not applied");
                check(ContainerClickHook.releases() > releases,
                        "mouse releases were not counted: the mixin on the menu screen's mouse release is not applied");
                check(ContainerClickHook.count() > clicks,
                        "the clicks were not seen: the mixin on the click handler is not applied");
                check(guardFailure.isEmpty(), guardFailure);
                context.waitTicks(5);
                context.takeScreenshot("minerefine-hud-salvage-guard");
                context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
                context.waitFor(client -> Compat.screen(client) == null);
            } catch (AssertionError e) {
                // Never got into the world. 1.21.11 on GitHub's machines sat on "Connecting" for good
                // until the server stopped pausing (October 2026, see serverProperties). The threads
                // say where, then a fresh server.
                if (connected || attempt >= CONNECT_ATTEMPTS) {
                    if (!connected) {
                        dumpThreads();
                    }
                    throw e;
                }
                dumpThreads();
                System.out.println("[minerefine-hud-test] could not join, attempt " + attempt + ", trying again");
            } finally {
                // A join that failed early must not have its watchdog fire during the next attempt.
                watchdog.interrupt();
            }
            if (connected) {
                return;
            }
        }
    }

    private static final int CONNECT_ATTEMPTS = 3;

    /** How many of the named item sit in the player's main inventory and hotbar. */
    private static int carried(net.minecraft.client.Minecraft client, String name) {
        return (int) client.player.getInventory().getNonEquipmentItems().stream()
                .filter(s -> s.getHoverName().getString().equals(name)).count();
    }

    /** The first of the player's own slots in the open menu that holds the named item. */
    private static int slotOf(net.minecraft.client.Minecraft client, String name) {
        for (net.minecraft.world.inventory.Slot slot : client.player.containerMenu.slots) {
            if (slot.container == client.player.getInventory()
                    && slot.getItem().getHoverName().getString().equals(name)) {
                return slot.index;
            }
        }
        throw new AssertionError("no " + name + " in the open menu");
    }

    /** Not a slot: a spot on the menu's frame, beside its title, where a press clicks nothing. */
    private static final int FRAME = -1;

    /**
     * The left mouse button going down or up over a slot of the open six-row chest, as the game's
     * mouse handler would tell the screen. A chest screen is 176 wide and 114 + 18 a row high,
     * centred, and a slot's x and y are its top left corner inside it.
     */
    private static void mouse(net.minecraft.client.Minecraft client, int slotIndex, boolean shift,
                              boolean doubleClick, boolean down) {
        net.minecraft.client.gui.screens.Screen screen = Compat.screen(client);
        int left = (screen.width - 176) / 2;
        int top = (screen.height - (114 + 6 * 18)) / 2;
        double x = left + 170;
        double y = top + 5;
        if (slotIndex != FRAME) {
            net.minecraft.world.inventory.Slot slot = client.player.containerMenu.getSlot(slotIndex);
            x = left + slot.x + 8;
            y = top + slot.y + 8;
        }
        net.minecraft.client.input.MouseButtonEvent event = new net.minecraft.client.input.MouseButtonEvent(x, y,
                new net.minecraft.client.input.MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT,
                        shift ? GLFW.GLFW_MOD_SHIFT : 0));
        if (down) {
            screen.mouseClicked(event, doubleClick);
        } else {
            screen.mouseReleased(event);
        }
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** The test's server, for the watchdog: the test thread is blocked inside connect() by then. */
    private static volatile net.minecraft.server.MinecraftServer liveServer;

    static {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(s -> liveServer = s);
    }

    /**
     * Which login step each side's connection is at, read from another thread while the join is
     * stuck: the packet listener's class names the step (handshake, login, configuration, play).
     */
    private static void dumpConnections() {
        StringBuilder out = new StringBuilder("[minerefine-hud-test] connections while stuck joining ("
                + Runtime.getRuntime().availableProcessors() + " processors):\n");
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            describeFields(out, "client", mc);
            Object screen = Compat.screen(mc);
            if (screen != null) {
                describeFields(out, "screen " + screen.getClass().getSimpleName(), screen);
            }
            net.minecraft.server.MinecraftServer s = liveServer;
            if (s == null) {
                out.append("  server: not started\n");
            } else {
                for (net.minecraft.network.Connection c : List.copyOf(s.getConnection().getConnections())) {
                    out.append("  server side: ").append(describe(c)).append('\n');
                    // The configuration step the server waits on, and the ones queued after it.
                    Object listener = c.getPacketListener();
                    for (Class<?> k = listener == null ? null : listener.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
                        for (java.lang.reflect.Field f : k.getDeclaredFields()) {
                            if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())
                                    && (f.getType().getName().contains("Task") || f.getName().toLowerCase(java.util.Locale.ROOT).contains("task"))) {
                                f.setAccessible(true);
                                out.append("    ").append(f.getName()).append(": ").append(f.get(listener)).append('\n');
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            out.append("  failed: ").append(t).append('\n');
        }
        System.out.println(out);
    }

    private static void describeFields(StringBuilder out, String owner, Object o) throws IllegalAccessException {
        for (Class<?> k = o.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
            for (java.lang.reflect.Field f : k.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())
                        && net.minecraft.network.Connection.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    out.append("  ").append(owner).append('.').append(f.getName()).append(": ")
                            .append(describe((net.minecraft.network.Connection) f.get(o))).append('\n');
                }
            }
        }
    }

    private static String describe(net.minecraft.network.Connection c) {
        if (c == null) {
            return "null";
        }
        Object listener = c.getPacketListener();
        return (listener == null ? "no listener" : listener.getClass().getName())
                + ", connected " + c.isConnected() + ", connecting " + c.isConnecting()
                + ", remote " + c.getRemoteAddress();
    }

    /** Every thread's stack, to see where a join that never finishes is stuck. */
    private static void dumpThreads() {
        StringBuilder out = new StringBuilder("[minerefine-hud-test] threads while stuck joining:\n");
        Thread.getAllStackTraces().forEach((thread, stack) -> {
            out.append("  \"").append(thread.getName()).append("\" ").append(thread.getState()).append('\n');
            for (int i = 0; i < Math.min(stack.length, 60); i++) {
                out.append("      at ").append(stack[i]).append('\n');
            }
        });
        System.out.println(out);
    }

    /**
     * A free port, so several versions tested at once each get their own server, and a server that
     * never pauses while the player joins.
     */
    private static java.util.Properties serverProperties() {
        java.util.Properties p = new java.util.Properties();
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            p.setProperty("server-port", String.valueOf(socket.getLocalPort()));
            // A joining player counts as nobody until the spawn chunks are loaded, so after 60
            // seconds the server paused itself and the join never finished. On GitHub's AMD EPYC
            // 7763 machines the test's lockstep ticking takes longer than that (October 2026).
            p.setProperty("pause-when-empty-seconds", "0");
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return p;
    }

    private static void check(boolean ok, String failure) {
        if (!ok) {
            throw new AssertionError(failure);
        }
    }
}
