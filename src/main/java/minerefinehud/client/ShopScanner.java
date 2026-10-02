package minerefinehud.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import minerefinehud.shop.ShopItemParser;

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
    public static List<ShopItemParser.ItemView> visibleItems(MinecraftClient client) {
        List<ShopItemParser.ItemView> out = new ArrayList<>();
        try {
            if (client == null) {
                return out;
            }
            Screen screen = client.currentScreen;
            if (!(screen instanceof HandledScreen<?> handled)) {
                return out;
            }

            for (var slot : handled.getScreenHandler().slots) {
                ItemStack stack = slot.getStack();
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
        Text name = stack.getName();
        return name == null ? "" : name.getString();
    }

    /** Package-visible so /mrhud debug can show where a held item keeps its tier. */
    static List<String> lore(ItemStack stack) {
        List<String> lines = new ArrayList<>();
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore != null) {
            for (Text line : lore.lines()) {
                lines.add(line.getString());
            }
        }
        return lines;
    }
}
