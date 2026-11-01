package minerefinehud.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A list of names to tick on and off, one button each, a page at a time: which stats the stats
 * panel shows, which rarities the drops panel shows. Laid out like {@link BossPickScreen}.
 *
 * VERSION SENSITIVE for the Screen and Button API only, like {@link SettingsScreen}.
 */
final class PickScreen extends Screen {

    private static final int BUTTON_WIDTH = 150;
    private static final int ROW_HEIGHT = 22;
    private static final int TOP = 40;

    private final Screen parent;
    private final String hint;
    private final String emptyHint;
    private final List<String> names;
    private final Predicate<String> ticked;
    private final Consumer<String> toggle;
    private final String clearLabel;
    private final Runnable clear;
    private final Runnable onChange;
    private int page;

    PickScreen(Screen parent, String title, String hint, String emptyHint, List<String> names,
               Predicate<String> ticked, Consumer<String> toggle, String clearLabel, Runnable clear,
               Runnable onChange) {
        super(Component.literal(title));
        this.parent = parent;
        this.hint = hint;
        this.emptyHint = emptyHint;
        this.names = List.copyOf(names);
        this.ticked = ticked;
        this.toggle = toggle;
        this.clearLabel = clearLabel;
        this.clear = clear;
        this.onChange = onChange;
    }

    private int rows() {
        return Math.max(1, (this.height - 64 - TOP) / ROW_HEIGHT);
    }

    private int perPage() {
        return rows() * 2;
    }

    private int pages() {
        return Math.max(1, (names.size() + perPage() - 1) / perPage());
    }

    @Override
    protected void init() {
        page = Math.max(0, Math.min(page, pages() - 1));
        int left = this.width / 2 - BUTTON_WIDTH - 5;
        int from = page * perPage();
        for (int i = from; i < Math.min(names.size(), from + perPage()); i++) {
            String name = names.get(i);
            int slot = i - from;
            int x = slot % 2 == 0 ? left : this.width / 2 + 5;
            int y = TOP + (slot / 2) * ROW_HEIGHT;
            addRenderableWidget(Button.builder(label(name), b -> {
                        toggle.accept(name);
                        b.setMessage(label(name));
                        onChange.run();
                    })
                    .bounds(x, y, BUTTON_WIDTH, 20).build());
        }

        int bottom = this.height - 52;
        if (pages() > 1) {
            Button back = addRenderableWidget(Button.builder(Component.literal("<"), b -> turn(-1))
                    .bounds(this.width / 2 - 100, bottom, 40, 20).build());
            back.active = page > 0;
            Button on = addRenderableWidget(Button.builder(Component.literal(">"), b -> turn(1))
                    .bounds(this.width / 2 + 60, bottom, 40, 20).build());
            on.active = page < pages() - 1;
        }
        addRenderableWidget(Button.builder(Component.literal(clearLabel), b -> {
                    clear.run();
                    onChange.run();
                    rebuildWidgets();
                })
                .bounds(this.width / 2 - 104, this.height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(this.width / 2 + 4, this.height - 28, 100, 20).build());
    }

    private void turn(int by) {
        page = Math.max(0, Math.min(pages() - 1, page + by));
        rebuildWidgets();
    }

    private Component label(String name) {
        return Component.literal((ticked.test(name) ? "✔ " : "") + name);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // Background is drawn by renderWithTooltip; see HudPositionScreen for why not here.
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
        String text = names.isEmpty() && !emptyHint.isEmpty() ? emptyHint : hint;
        context.centeredText(this.font, Component.literal(text), this.width / 2, 24, 0xFFAAAAAA);
        if (pages() > 1) {
            context.centeredText(this.font, Component.literal((page + 1) + " / " + pages()),
                    this.width / 2, this.height - 46, 0xFFCCCCCC);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            // Back to the settings, rebuilt so the counts on its buttons are current.
            Compat.setScreen(this.minecraft, parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
