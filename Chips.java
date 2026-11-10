package minerefinehud.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Flat rounded rows and chips for the job screens, by Arreba: real but invisible buttons take the
 * clicks and these are drawn by hand, so they match the settings menu. Rebuilt on every init.
 *
 * VERSION SENSITIVE for Button only.
 */
final class Chips {

    static final int INK = 0xFFE3ECE8;
    static final int MUTED = 0xFF8FA49E;
    static final int OFF = 0xFF5A6967;
    private static final int RAISED = 0xFF1B2726;
    private static final int HOVER = 0xFF263534;
    private static final int RADIUS = 3;

    private record Cell(int x, int y, int w, int h, String left, String right, boolean selected, boolean off) {}

    private final Consumer<Button> register;
    private final List<Cell> cells = new ArrayList<>();

    Chips(Consumer<Button> register) {
        this.register = register;
    }

    void clear() {
        cells.clear();
    }

    /**
     * @param right   text at the right end, such as the mats
     * @param off     drawn dim and struck through: a piece left out of the job
     * @param pressed highlighted as the current choice
     */
    void add(int x, int y, int w, int h, String left, String right, boolean pressed, boolean off, Runnable click) {
        Button b = Button.builder(Component.empty(), btn -> click.run()).bounds(x, y, w, h).build();
        b.setAlpha(0f);
        register.accept(b);
        cells.add(new Cell(x, y, w, h, left, right == null ? "" : right, pressed, off));
    }

    void paint(GuiGraphicsExtractor context, Font font, int mouseX, int mouseY, int accent) {
        for (Cell c : cells) {
            boolean over = mouseX >= c.x() && mouseX < c.x() + c.w() && mouseY >= c.y() && mouseY < c.y() + c.h();
            int fill = c.selected() ? Smooth.alpha(accent, 0.28f) : over ? HOVER : RAISED;
            Smooth.rect(context, c.x(), c.y(), c.w(), c.h(), RADIUS, fill);
            if (c.selected()) {
                Smooth.ring(context, c.x(), c.y(), c.w(), c.h(), RADIUS, 1f, accent);
            }
            int ink = c.off() ? OFF : c.selected() ? 0xFFFFFFFF : INK;
            int ty = c.y() + (c.h() - 8) / 2;
            int rightW = c.right().isEmpty() ? 0 : font.width(c.right()) + 6;
            String left = fit(font, c.left(), c.w() - 12 - rightW);
            context.text(font, left, c.x() + 6, ty, ink, false);
            if (c.off()) {
                context.fill(c.x() + 6, ty + 4, c.x() + 6 + font.width(left), ty + 5, OFF);
            }
            if (!c.right().isEmpty()) {
                context.text(font, c.right(), c.x() + c.w() - 6 - font.width(c.right()), ty,
                        c.off() ? OFF : c.selected() ? 0xFFFFFFFF : MUTED, false);
            }
        }
    }

    static String fit(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        String t = text;
        while (t.length() > 1 && font.width(t + "…") > width) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "…";
    }
}
