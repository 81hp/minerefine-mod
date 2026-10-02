package minerefinehud.client;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import minerefinehud.hud.HudLayout;
import minerefinehud.hud.HudModel;
import minerefinehud.hud.Theme;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Drag each overlay panel where you want it, and size it.
 *
 * VERSION SENSITIVE for the Screen and ButtonWidget API only. Every placement decision is made by
 * HudLayout, which is plain Java and covered by tests, so this class just forwards the cursor
 * and draws.
 */
public final class HudPositionScreen extends Screen {

    /** One movable panel: what it shows, where it is and how big, and what Reset puts back. */
    public record Panel(String label, List<HudModel.Line> preview,
                        HudLayout.Placement placement, HudLayout.Placement defaults,
                        double scale, double defaultScale) {}

    /** What the screen hands back on close, in the same order as the panels it was given. */
    public record Result(List<HudLayout.Placement> placements, List<Double> scales) {}

    private static final int KEY_TAB = 258;
    private static final int KEY_RIGHT = 262;
    private static final int KEY_LEFT = 263;
    private static final int KEY_DOWN = 264;
    private static final int KEY_UP = 265;

    /** Previews always get a backdrop, so a panel can be found and grabbed whatever its settings. */
    private static final int PREVIEW_BACKGROUND = 0x80000000;

    private final Screen parent;
    private final List<Panel> panels;
    private final List<HudLayout.Placement> placements;
    private final List<Double> scales;
    private final Theme theme;
    private final Consumer<Result> onSave;
    private final Runnable openSettings;

    /** The panel that Snap, Reset, size and the arrow keys act on. Last one clicked. */
    private int selected;

    private int dragging = -1;

    /**
     * Where inside the panel it was grabbed. Dragging places the panel at cursor minus this, so
     * the same point stays under the cursor for the whole drag rather than drifting.
     */
    private double grabX;
    private double grabY;

    /** @param openSettings called after saving, when the Settings button is pressed */
    public HudPositionScreen(Screen parent, List<Panel> panels, Theme theme,
                             Consumer<Result> onSave, Runnable openSettings) {
        super(Text.literal("MineRefine HUD position"));
        this.parent = parent;
        this.panels = List.copyOf(panels);
        this.placements = new ArrayList<>(panels.stream().map(Panel::placement).toList());
        this.scales = new ArrayList<>(panels.stream().map(p -> HudLayout.clampScale(p.scale())).toList());
        this.theme = theme;
        this.onSave = onSave;
        this.openSettings = openSettings;
    }

    @Override
    protected void init() {
        int y = this.height - 28;
        int left = this.width / 2 - 154;

        addDrawableChild(ButtonWidget.builder(Text.literal("Smaller"), b -> resize(-1))
                .dimensions(left, y - 24, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Bigger"), b -> resize(1))
                .dimensions(left + 104, y - 24, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Settings..."), b -> {
                    save();
                    openSettings.run();
                })
                .dimensions(left + 208, y - 24, 100, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Snap to corner"),
                        b -> placements.set(selected, HudLayout.snapToAnchor(placements.get(selected))))
                .dimensions(left, y, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Reset"), b -> {
                    placements.set(selected, panels.get(selected).defaults());
                    scales.set(selected, panels.get(selected).defaultScale());
                })
                .dimensions(left + 104, y, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(left + 208, y, 100, 20).build());
    }

    private void resize(int steps) {
        scales.set(selected, HudLayout.stepScale(scales.get(selected), steps));
    }

    private HudLayout.Rect rectOf(int i) {
        return HudRenderer.rect(this.textRenderer, panels.get(i).preview(), placements.get(i),
                scales.get(i), this.width, this.height);
    }

    /** Topmost panel under the point, or -1. Later panels draw on top, so search backwards. */
    private int panelAt(double x, double y) {
        for (int i = panels.size() - 1; i >= 0; i--) {
            if (rectOf(i).contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // No renderBackground() here. Since 1.21.6 Screen.renderWithTooltip draws the background
        // itself before calling render(), and the menu blur may only run once per frame, so a
        // second call throws "Can only blur once per frame" and crashes the client.

        // Thirds, so it is obvious which corner a panel will pin itself to when dropped.
        int thirdW = this.width / 3;
        int thirdH = this.height / 3;
        int guide = 0x22FFFFFF;
        context.fill(thirdW, 0, thirdW + 1, this.height, guide);
        context.fill(thirdW * 2, 0, thirdW * 2 + 1, this.height, guide);
        context.fill(0, thirdH, this.width, thirdH + 1, guide);
        context.fill(0, thirdH * 2, this.width, thirdH * 2 + 1, guide);

        int hovered = dragging >= 0 ? dragging : panelAt(mouseX, mouseY);
        for (int i = 0; i < panels.size(); i++) {
            HudLayout.Rect rect = rectOf(i);
            HudRenderer.render(context, panels.get(i).preview(), rect, scales.get(i), PREVIEW_BACKGROUND, theme);

            int outline = i == selected ? 0xFFFFD24A : i == hovered ? 0xCCFFFFFF : 0x66FFFFFF;
            context.drawStrokedRectangle(rect.x(), rect.y(), rect.width(), rect.height(), outline);

            // Label outside the panel, above it unless that would be off screen.
            int labelY = rect.y() >= 11 ? rect.y() - 10 : rect.y() + rect.height() + 2;
            context.drawTextWithShadow(this.textRenderer, panels.get(i).label(),
                    rect.x(), labelY, i == selected ? 0xFFFFD24A : 0xFFAAAAAA);
        }

        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Drag to move, scroll to resize. Tab switches, arrows nudge."),
                this.width / 2, 12, 0xFFFFFFFF);

        HudLayout.Placement p = placements.get(selected);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(panels.get(selected).label() + ": " + p.anchor().label()
                        + "   offset " + p.offsetX() + ", " + p.offsetY()
                        + "   size " + String.format(Locale.ROOT, "%.2f", scales.get(selected)) + "x"),
                this.width / 2, 26, 0xFFAAAAAA);

        super.render(context, mouseX, mouseY, delta);
    }

    // Since 1.21.9 mouse and key events arrive as Click and KeyInput records instead of loose
    // coordinates and key codes, and the modifier state travels with the event rather than being
    // polled from a static helper.

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        // Buttons first, so a panel dragged over them cannot make them unclickable.
        if (super.mouseClicked(click, doubled)) {
            return true;
        }
        int hit = click.button() == 0 ? panelAt(click.x(), click.y()) : -1;
        if (hit >= 0) {
            HudLayout.Rect rect = rectOf(hit);
            selected = hit;
            dragging = hit;
            grabX = click.x() - rect.x();
            grabY = click.y() - rect.y();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (dragging >= 0) {
            HudLayout.Rect rect = rectOf(dragging);
            placements.set(dragging, HudLayout.dragTo(rect.width(), rect.height(), this.width, this.height,
                    click.x() - grabX, click.y() - grabY));
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        dragging = -1;
        return super.mouseReleased(click);
    }

    /** Scrolling over a panel resizes it, and selects it so the size shows in the info line. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int hit = panelAt(mouseX, mouseY);
        if (hit >= 0 && verticalAmount != 0.0) {
            selected = hit;
            resize(verticalAmount > 0.0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    /** Arrow keys nudge by one pixel, which a mouse cannot do reliably. Tab picks the panel. */
    @Override
    public boolean keyPressed(KeyInput input) {
        int step = input.hasShift() ? 10 : 1;
        HudLayout.Placement p = placements.get(selected);
        switch (input.key()) {
            case KEY_TAB -> { selected = (selected + 1) % panels.size(); return true; }
            case KEY_LEFT -> { placements.set(selected, p.withOffset(p.offsetX() - step, p.offsetY())); return true; }
            case KEY_RIGHT -> { placements.set(selected, p.withOffset(p.offsetX() + step, p.offsetY())); return true; }
            case KEY_UP -> { placements.set(selected, p.withOffset(p.offsetX(), p.offsetY() - step)); return true; }
            case KEY_DOWN -> { placements.set(selected, p.withOffset(p.offsetX(), p.offsetY() + step)); return true; }
            default -> { return super.keyPressed(input); }
        }
    }

    private void save() {
        onSave.accept(new Result(List.copyOf(placements), List.copyOf(scales)));
    }

    @Override
    public void close() {
        save();
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
