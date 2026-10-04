package minerefinehud.client;

import minerefinehud.shop.ShopItemParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the contents of whatever container menu the player has open.
 *
 * VERSION SENSITIVE. Item lore moved to the component system in 1.20.5, so this reads
 * DataComponentTypes.LORE rather than building a tooltip. If it does not resolve, that is the
 * line to change.
 *
 * This is strictly passive. It never opens a menu, never clicks, never hovers and never sends a
 * packet. It reads item stacks the client already has in memory because the player opened the
 * screen themselves, which is no different from the player reading the same tooltip with their
 * eyes. Automating the opening of shop menus would be a different thing entirely and is
 * deliberately not done here.
 */
public final class ShopScanner {

    private ShopScanner() {
    }

    /** Returns an empty list when no container menu is open. */
    public static List<ShopItemParser.ItemView> visibleItems(Minecraft client) {
        List<ShopItemParser.ItemView> out = new ArrayList<>();
        try {
            if (client == null) {
                return out;
            }
            Screen screen = Compat.screen(client);
            if (!(screen instanceof AbstractContainerScreen<?> handled)) {
                return out;
            }

            for (var slot : handled.getMenu().slots) {
                ItemStack stack = slot.getItem();
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                out.add(new ShopItemParser.ItemView(title(stack), lore(stack)));
            }
        } catch (Exception | NoSuchMethodError | NoClassDefFoundError ignored) {
            // A mapping mismatch should cost price learning, not the client.
        }
        return out;
    }

    private static String title(ItemStack stack) {
        Component name = stack.getHoverName();
        return name == null ? "" : name.getString();
    }

    /** Package-visible so /mrhud debug can show where a held item keeps its tier. */
    static List<String> lore(ItemStack stack) {
        List<String> lines = new ArrayList<>();
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null) {
            for (Component line : lore.lines()) {
                lines.add(line.getString());
            }
        }
        return lines;
    }
}
