package minerefinehud.client;

import java.util.Locale;
import minerefinehud.turret.SalvageGuard;
import minerefinehud.turret.TurretItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The salvage guard's eyes and voice: reads the clicked turret off the open menu, asks
 * {@link SalvageGuard} whether the click may go ahead, and plays the warning.
 *
 * Runs only when the player clicks in a menu, never per tick. It stops clicks and sends nothing:
 * a stopped click simply never reaches the server. It does not ask {@link ModConfig} whether the
 * mod is limited to MineRefine; SalvageGuard says why.
 *
 * VERSION SENSITIVE for the menu, slot and sound calls; the decision itself is tested in
 * SalvageGuard.
 */
final class SalvageGuardClient {

    /** Longest title or item name /mrhud debug prints. */
    private static final int DEBUG_TEXT = 60;

    private final ModConfig config;
    private final SalvageGuard guard = new SalvageGuard();

    /** For /mrhud debug: the last menu and salvage-menu item clicked, and what the guard did since joining. */
    private String lastTitle = "";
    private String lastItem = "";
    private int warned;
    private int confirmed;

    SalvageGuardClient(ModConfig config) {
        this.config = config;
    }

    void register() {
        ContainerClickHook.listen(this::stop);
    }

    void onJoin() {
        guard.reset();
        lastTitle = "";
        lastItem = "";
        warned = 0;
        confirmed = 0;
    }

    private boolean stop(int containerId, int slotId, int button, String input) {
        if (!config.salvageGuard) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || !client.isSameThread()) {
            return false;
        }
        if (!(Compat.screen(client) instanceof AbstractContainerScreen<?> screen)) {
            return false;
        }
        AbstractContainerMenu menu = screen.getMenu();
        if (menu == null || menu.containerId != containerId) {
            return false;
        }
        lastTitle = screen.getTitle().getString();
        if (!SalvageGuard.isSalvageMenu(lastTitle)) {
            return false;
        }

        // Whose click this is, and whether the player did something else since the last one. A
        // key is the player's whatever the mouse is doing, and uses no press up.
        boolean key = "SWAP".equals(input) || "THROW".equals(input);
        SalvageGuard.Presses presses = ContainerClickHook.pressLog();
        SalvageGuard.Origin origin = key ? SalvageGuard.Origin.OWN : presses.origin(input);
        if (!key && presses.skippedAPress()) {
            guard.forget();
        }

        // Each item is read only where SalvageGuard looks at it.
        Inventory inventory = client.player.getInventory();
        boolean inInventory = false;
        TurretItem.Turret inSlot = null;
        if (slotId >= 0 && slotId < menu.slots.size()) {
            Slot slot = menu.getSlot(slotId);
            inInventory = slot.container == inventory;
            if (inInventory) {
                inSlot = turret(slot.getItem());
            }
        }
        // The same slots the game lets a key swap with: the hotbar and the off hand.
        boolean swapKey = "SWAP".equals(input) && !inInventory
                && (button >= 0 && button < 9 || button == Inventory.SLOT_OFFHAND);
        TurretItem.Turret onKey = swapKey ? turret(inventory.getItem(button)) : null;
        TurretItem.Turret carried = "PICKUP_ALL".equals(input) ? turret(menu.getCarried()) : null;

        SalvageGuard.Result result = guard.check(
                new SalvageGuard.Click(containerId, slotId, button, input, origin, inInventory,
                        inSlot, onKey, carried),
                config.salvageGuardSize, System.currentTimeMillis());
        switch (result.verdict()) {
            case WARN, WARN_KEY -> {
                warned++;
                warn(client, result.turret(), result.verdict() == SalvageGuard.Verdict.WARN_KEY);
            }
            case CONFIRMED -> confirmed++;
            default -> {
            }
        }
        return result.verdict().stops();
    }

    private TurretItem.Turret turret(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        String name = stack.getHoverName().getString();
        TurretItem.Turret turret = SalvageGuard.read(name, ShopScanner.lore(stack));
        lastItem = "'" + shortened(name) + "' read as "
                + (turret == null ? "no turret"
                : turret.size() == SalvageGuard.UNREADABLE ? "a turret of unreadable size"
                : "size " + String.format(Locale.ROOT, "%,.2f", turret.size()));
        return turret;
    }

    private static String shortened(String text) {
        return text.length() > DEBUG_TEXT ? text.substring(0, DEBUG_TEXT) + "..." : text;
    }

    /** Chat and sound apart, so one failing still leaves the other. The click is stopped either way. */
    private void warn(Minecraft client, TurretItem.Turret turret, boolean byKey) {
        try {
            Compat.chat(client, Component.literal(SalvageGuard.warning(turret, config.salvageGuardSize, byKey))
                    .withStyle(ChatFormatting.RED));
        } catch (Exception | LinkageError ignored) {
        }
        try {
            // A UI sound, so it is as loud wherever the player stands. A low bass note, unlike the
            // mod's pings and chimes, which mean something good is ready.
            client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6f, 1.0f));
        } catch (Exception | LinkageError ignored) {
        }
    }

    String debugLine() {
        String menu = lastTitle.isEmpty() ? "none" : "'" + shortened(lastTitle) + "'"
                + (SalvageGuard.isSalvageMenu(lastTitle) ? " (the salvage menu)" : " (not the salvage menu)");
        // Zero clicks or presses after clicking in a menu means that mixin is not applied.
        return "salvage guard: " + (config.salvageGuard
                ? "on, above size " + String.format(Locale.ROOT, "%,d", config.salvageGuardSize) : "off")
                + ", last menu clicked in " + menu
                + ", last item read in the salvage menu " + (lastItem.isEmpty() ? "none" : lastItem)
                + ", warned " + warned + ", clicked through " + confirmed
                + ", this session " + ContainerClickHook.count() + " clicks and "
                + ContainerClickHook.presses() + " mouse presses seen, clicks "
                + (ContainerClickHook.pressLog().handlingSeen() ? "told apart by the press they come from"
                : "told apart by time only (none seen during a press yet)")
                + (ContainerClickHook.failures() > 0 ? ", " + ContainerClickHook.failures()
                + " clicks failed to check (" + shortened(ContainerClickHook.lastFailure()) + ")" : "");
    }
}
