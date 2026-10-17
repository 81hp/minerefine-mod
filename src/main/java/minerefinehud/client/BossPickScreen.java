package minerefinehud.client;

import minerefinehud.boss.BossWorlds;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

/**
 * Which bosses the boss panel watches, one button each, a page at a time. With none picked the
 * panel shows the bosses of the dimension the player is in, as before; with some, exactly those,
 * from every world.
 *
 * VERSION SENSITIVE for the Screen and Button API only, like {@link SettingsScreen}.
 */
public final class BossPickScreen extends Screen {

    private static final int BUTTON_WIDTH = 150;
    private static final int ROW_HEIGHT = 22;
    private static final int TOP = 40;

    private final Screen parent;
    private final ModConfig config;
    private final List<String> names;
    private final Runnable onChange;
    private int page;

    public BossPickScreen(Screen parent, ModConfig config, List<String> names, Runnable onChange) {
        super(Component.literal("Bosses to watch"));
        this.parent = parent;
        this.config = config;
        this.names = names;
        this.onChange = onChange;
    }

    /** Rows that fit above the bottom buttons, at least one. */
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
                        toggle(name);
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
        addRenderableWidget(Button.builder(Component.literal("Clear picks"), b -> {
                    config.watchedBosses.clear();
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

    private boolean watched(String name) {
        String key = BossWorlds.key(name);
        return config.watchedBosses.stream().anyMatch(n -> BossWorlds.key(n).equals(key));
    }

    private void toggle(String name) {
        String key = BossWorlds.key(name);
        if (!config.watchedBosses.removeIf(n -> BossWorlds.key(n).equals(key))) {
            config.watchedBosses.add(name);
        }
    }

    private Component label(String name) {
        return Component.literal((watched(name) ? "✔ " : "") + name);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // Background is drawn by renderWithTooltip; see HudPositionScreen for why not here.
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
        String hint = names.isEmpty() ? "No bosses known yet: they appear here once heard or read from /bossindex."
                : config.watchedBosses.isEmpty() ? "None picked: the panel shows this dimension's bosses."
                : "Picked bosses show from every world.";
        context.centeredText(this.font, Component.literal(hint), this.width / 2, 24, 0xFFAAAAAA);
        if (pages() > 1) {
            context.centeredText(this.font, Component.literal((page + 1) + " / " + pages()),
                    this.width / 2, this.height - 46, 0xFFCCCCCC);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            Compat.setScreen(this.minecraft, parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
