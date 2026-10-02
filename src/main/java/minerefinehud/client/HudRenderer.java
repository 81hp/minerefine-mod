package minerefinehud.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;
import minerefinehud.hud.HudLayout;
import minerefinehud.hud.HudModel;
import minerefinehud.hud.Theme;

import java.util.List;

/**
 * Turns the model's lines into pixels.
 *
 * VERSION SENSITIVE, but only barely: every decision about what to show lives in HudModel and
 * every decision about where to show it lives in HudLayout, both of which are plain Java and
 * unit tested. This class only draws. The one API it leans on beyond drawing text is the
 * matrix stack for scaling, which since 1.21.6 is a 2D JOML Matrix3x2fStack.
 */
public final class HudRenderer {

    public static final int LINE_HEIGHT = 10;
    public static final int PADDING = 3;

    private HudRenderer() {
    }

    /**
     * Pixel size of the panel these lines would occupy, before scaling.
     *
     * Needed before drawing, because anchoring to the right or bottom edge cannot be worked out
     * without knowing how wide and tall the panel is.
     */
    public static int[] measure(TextRenderer font, List<HudModel.Line> lines) {
        if (lines.isEmpty()) {
            return new int[] { 0, 0 };
        }
        int width = 0;
        for (HudModel.Line line : lines) {
            int w = line.style() == HudModel.Style.BAR ? BAR_MIN_WIDTH : font.getWidth(line.text());
            width = Math.max(width, w);
        }
        return new int[] { width + PADDING * 2, lines.size() * LINE_HEIGHT + PADDING * 2 };
    }

    /** Where a panel lands on screen at this scale. Also what the position screen hit-tests. */
    public static HudLayout.Rect rect(TextRenderer font, List<HudModel.Line> lines,
                                      HudLayout.Placement placement, double scale,
                                      int screenWidth, int screenHeight) {
        int[] size = measure(font, lines);
        return HudLayout.place(placement, HudLayout.scaled(size[0], scale),
                HudLayout.scaled(size[1], scale), screenWidth, screenHeight);
    }

    /** A bar stretches to the panel's text width, but never so short it reads as a dash. */
    private static final int BAR_MIN_WIDTH = 100;
    private static final int BAR_HEIGHT = 5;

    /**
     * Draws the panel into {@code rect}, which is already scaled and placed.
     *
     * @param background ARGB fill behind the text, or 0 for none
     */
    public static void render(DrawContext context, List<HudModel.Line> lines, HudLayout.Rect rect,
                              double scale, int background, Theme theme) {
        if (lines.isEmpty()) {
            return;
        }
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        int[] size = measure(font, lines);
        float s = (float) HudLayout.clampScale(scale);

        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        try {
            matrices.translate(rect.x(), rect.y());
            matrices.scale(s, s);

            if ((background >>> 24) != 0) {
                context.fill(0, 0, size[0], size[1], background);
            }

            int row = PADDING;
            for (HudModel.Line line : lines) {
                if (line.style() == HudModel.Style.BAR) {
                    drawBar(context, PADDING, row + 2, size[0] - PADDING * 2, line.progress(), theme);
                } else if (!line.text().isEmpty()) {
                    context.drawTextWithShadow(font, line.text(), PADDING, row, theme.color(line.style()));
                }
                row += LINE_HEIGHT;
            }
        } finally {
            matrices.popMatrix();
        }
    }

    private static void drawBar(DrawContext context, int x, int y, int width, double progress, Theme theme) {
        context.fill(x, y, x + width, y + BAR_HEIGHT, theme.barTrack());
        int filled = (int) Math.round(width * progress);
        if (filled > 0) {
            // Its own colour once complete, so "finished" reads from the bar alone at a glance.
            context.fill(x, y, x + filled, y + BAR_HEIGHT, progress >= 1.0 ? theme.barDone() : theme.barFill());
        }
    }
}
