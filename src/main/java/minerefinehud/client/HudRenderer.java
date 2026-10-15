package minerefinehud.client;

import org.joml.Matrix3x2fStack;
import minerefinehud.hud.HudLayout;
import minerefinehud.hud.HudModel;
import minerefinehud.hud.HudStyle;
import minerefinehud.hud.PanelPainter;
import minerefinehud.hud.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a painted panel into pixels.
 *
 * VERSION SENSITIVE, but only barely: what to show lives in HudModel, how it is laid out and
 * coloured in PanelPainter, where it goes in HudLayout, all plain Java and unit tested. This class
 * only replays the painter's rectangles, text and icons. Beyond drawing, it leans on the matrix
 * stack for scaling, which since 1.21.6 is a 2D JOML Matrix3x2fStack.
 */
public final class HudRenderer {

    private HudRenderer() {
    }

    /** One panel's look: what the painter needs on top of the lines. */
    public record Look(HudStyle style, HudStyle.Background background, int opacity, Theme theme) {

        public static Look of(ModConfig config, ModConfig.Look look) {
            return new Look(config.style(), look.backgroundStyle(), look.opacity, config.theme());
        }
    }

    public static PanelPainter.Painted paint(Font font, List<HudModel.Line> lines, Look look) {
        return PanelPainter.paint(lines, look.style(), look.background(), look.opacity(), look.theme(), font::width);
    }

    /** Where a panel lands on screen at this scale. Also what the position screen hit-tests. */
    public static HudLayout.Rect rect(PanelPainter.Painted painted, HudLayout.Placement placement, double scale,
                                      int screenWidth, int screenHeight) {
        return HudLayout.place(placement, HudLayout.scaled(painted.width(), scale),
                HudLayout.scaled(painted.height(), scale), screenWidth, screenHeight);
    }

    /** Draws the panel with its top left corner at (x, y), at this scale. */
    public static void render(GuiGraphicsExtractor context, PanelPainter.Painted painted, float x, float y,
                              double scale) {
        if (painted.isEmpty()) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        float s = (float) scale;

        Matrix3x2fStack matrices = context.pose();
        matrices.pushMatrix();
        try {
            matrices.translate(x, y);
            matrices.scale(s, s);
            for (PanelPainter.Op op : painted.ops()) {
                switch (op) {
                    case PanelPainter.Fill f -> context.fill(f.x1(), f.y1(), f.x2(), f.y2(), f.color());
                    case PanelPainter.Gradient g -> context.fillGradient(g.x1(), g.y1(), g.x2(), g.y2(), g.top(), g.bottom());
                    case PanelPainter.Text t -> context.text(font, t.text(), t.x(), t.y(), t.color(), t.shadow());
                    case PanelPainter.Icon i -> icon(context, i);
                }
            }
        } finally {
            matrices.popMatrix();
        }
    }

    /** Draws the panel into {@code rect}, which is already scaled and placed. */
    public static void render(GuiGraphicsExtractor context, PanelPainter.Painted painted, HudLayout.Rect rect,
                              double scale) {
        render(context, painted, rect.x(), rect.y(), HudLayout.clampScale(scale));
    }

    // ------------------------------------------------------------------ icons

    /** Stand-ins for the server's own item textures, which the client cannot ask for by name. */
    private static final Map<String, Item> ICON_ITEMS = Map.ofEntries(
            Map.entry("sword", Items.DIAMOND_SWORD),
            Map.entry("pickaxe", Items.DIAMOND_PICKAXE),
            Map.entry("axe", Items.DIAMOND_AXE),
            Map.entry("shovel", Items.DIAMOND_SHOVEL),
            Map.entry("tool", Items.DIAMOND_PICKAXE),
            Map.entry("helmet", Items.DIAMOND_HELMET),
            Map.entry("chestplate", Items.DIAMOND_CHESTPLATE),
            Map.entry("leggings", Items.DIAMOND_LEGGINGS),
            Map.entry("boots", Items.DIAMOND_BOOTS),
            Map.entry("armor", Items.DIAMOND_CHESTPLATE),
            Map.entry("armor set", Items.DIAMOND_CHESTPLATE),
            Map.entry("charm", Items.AMETHYST_SHARD),
            Map.entry("total", Items.GOLD_INGOT));

    private static final Map<String, ItemStack> STACKS = new HashMap<>();

    /** An item is 16 pixels; drawn at half size it fits one line of text. */
    private static void icon(GuiGraphicsExtractor context, PanelPainter.Icon icon) {
        Item item = ICON_ITEMS.get(icon.key());
        if (item == null) {
            return;
        }
        ItemStack stack = STACKS.computeIfAbsent(icon.key(), k -> new ItemStack(item));
        Matrix3x2fStack matrices = context.pose();
        matrices.pushMatrix();
        try {
            matrices.translate(icon.x(), icon.y());
            float half = PanelPainter.ICON / 16f;
            matrices.scale(half, half);
            context.item(stack, 0, 0);
        } finally {
            matrices.popMatrix();
        }
    }
}
