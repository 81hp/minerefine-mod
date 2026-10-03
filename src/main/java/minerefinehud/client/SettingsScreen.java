package minerefinehud.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import minerefinehud.hud.Theme;
import minerefinehud.progress.ProgressPlanner;
import minerefinehud.progress.ProgressSlot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Every setting that is not a position, in one screen, applied the moment it changes.
 *
 * VERSION SENSITIVE for the Screen, ButtonWidget and TextFieldWidget API only. Built from vanilla
 * widgets on purpose: no Cloth Config or Mod Menu dependency to keep in step with Lunar. The
 * screen only reads and writes {@link ModConfig}; nothing here decides anything.
 */
public final class SettingsScreen extends Screen {

    private enum Tab {
        GENERAL("General"), MINE("Mine"), BOSSES("Bosses"), PROGRESS("Progress"),
        PANELS("Panels"), COLOURS("Colours");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private static final int COLUMN_WIDTH = 150;
    private static final int ROW_HEIGHT = 22;
    private static final int TOP = 56;

    private final Screen parent;
    private final ModConfig config;
    /** Saves and nudges the HUD to recompute, called after every change. */
    private final Runnable onChange;
    private final Runnable openPositions;

    private Tab tab = Tab.GENERAL;

    private record Label(String text, int x, int y) {}

    private record Swatch(Supplier<String> hex, int fallback, int x, int y) {}

    private final List<Label> labels = new ArrayList<>();
    private final List<Swatch> swatches = new ArrayList<>();

    public SettingsScreen(Screen parent, ModConfig config, Runnable onChange, Runnable openPositions) {
        super(Text.literal("MineRefine HUD settings"));
        this.parent = parent;
        this.config = config;
        this.onChange = onChange;
        this.openPositions = openPositions;
    }

    private int left() {
        return this.width / 2 - COLUMN_WIDTH - 5;
    }

    private int right() {
        return this.width / 2 + 5;
    }

    private int x(int column) {
        return column == 0 ? left() : right();
    }

    private static int y(int row) {
        return TOP + row * ROW_HEIGHT;
    }

    @Override
    protected void init() {
        labels.clear();
        swatches.clear();

        Tab[] tabs = Tab.values();
        int tabWidth = 64;
        int tabLeft = this.width / 2 - (tabs.length * (tabWidth + 2)) / 2;
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            ButtonWidget b = ButtonWidget.builder(Text.literal(t.label), w -> {
                        tab = t;
                        clearAndInit();
                    })
                    .dimensions(tabLeft + i * (tabWidth + 2), 28, tabWidth, 20).build();
            b.active = t != tab;
            addDrawableChild(b);
        }

        switch (tab) {
            case GENERAL -> general();
            case MINE -> mine();
            case BOSSES -> bosses();
            case PROGRESS -> progress();
            case PANELS -> panels();
            case COLOURS -> colours();
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(this.width / 2 - 100, this.height - 28, 200, 20).build());
    }

    // ------------------------------------------------------------------ tabs

    private void general() {
        toggle(0, 0, "Overlay", () -> config.enabled, v -> config.enabled = v);
        toggle(1, 0, "Mine panel", () -> config.showMine, v -> config.showMine = v);
        toggle(0, 1, "Boss panel", () -> config.showBosses, v -> config.showBosses = v);
        toggle(1, 1, "Costs in credits", () -> config.showCredits, v -> config.showCredits = v);

        labels.add(new Label("Block rate (millions per credit)", x(0), y(2) + 6));
        TextFieldWidget rate = field(x(1), y(2), 60, String.valueOf(config.blocksPerCreditMillions), 10,
                s -> s.matches("[0-9]*\\.?[0-9]*"),
                s -> {
                    try {
                        double v = Double.parseDouble(s);
                        if (v > 0.0) {
                            config.blocksPerCreditMillions = v;
                            onChange.run();
                        }
                    } catch (NumberFormatException ignored) {
                        // Half-typed, e.g. "2." or empty. Keep the last good value.
                    }
                });
        addDrawableChild(rate);

        addDrawableChild(ButtonWidget.builder(Text.literal("Move and resize panels..."), b -> {
                    onChange.run();
                    openPositions.run();
                })
                .dimensions(x(0), y(4), COLUMN_WIDTH * 2 + 10, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Turret size calculator..."), b -> {
                    onChange.run();
                    if (this.client != null) {
                        this.client.setScreen(new TurretScreen(this));
                    }
                })
                .dimensions(x(0), y(5), COLUMN_WIDTH * 2 + 10, 20).build());
    }

    private void mine() {
        toggle(0, 0, "Sword", () -> config.showSword, v -> config.showSword = v);
        toggle(1, 0, "Tool", () -> config.showTool, v -> config.showTool = v);
        toggle(0, 1, "Armour", () -> config.showArmor, v -> config.showArmor = v);
        toggle(1, 1, "Armour as pieces", () -> config.showArmorPieces, v -> config.showArmorPieces = v);
        toggle(0, 2, "Charm", () -> config.showCharm, v -> config.showCharm = v);
        toggle(1, 2, "Mine total", () -> config.showMineTotal, v -> config.showMineTotal = v);
        toggle(0, 3, "Item in hand", () -> config.showHeldUpgrade, v -> config.showHeldUpgrade = v);
    }

    private void bosses() {
        toggle(0, 0, "Only this dimension", () -> !config.showAllBossWorlds, v -> config.showAllBossWorlds = !v);
        cycle(1, 0, "Max shown", () -> config.maxBosses, v -> config.maxBosses = v,
                new int[] { 1, 2, 3, 4, 5, 6, 8, 10, 15 }, String::valueOf);
        toggle(0, 1, "Alive timers", () -> config.showBossUpTimers, v -> config.showBossUpTimers = v);
        toggle(1, 1, "Still learning", () -> config.showBossLearning, v -> config.showBossLearning = v);

        toggle(0, 3, "Respawn reminder", () -> config.bossReminder, v -> config.bossReminder = v);
        cycle(1, 3, "Remind", () -> config.bossReminderSeconds, v -> config.bossReminderSeconds = v,
                new int[] { 5, 10, 15, 20, 30, 45, 60 }, v -> v + "s before");
        toggle(0, 4, "Reminder sound", () -> config.bossReminderSound, v -> config.bossReminderSound = v);
        cycle(1, 4, "Volume", () -> config.bossReminderVolume, v -> config.bossReminderVolume = v,
                new int[] { 25, 50, 75, 100 }, v -> v + "%");

        labels.add(new Label("Reminders wait until a timer is measured (no ?).", x(0), y(6)));
    }

    private void progress() {
        List<ModConfig.Bar> bars = config.progressBars;
        ProgressSlot[] slots = ProgressSlot.values();

        for (int i = 0; i < bars.size(); i++) {
            ModConfig.Bar bar = bars.get(i);
            int row = y(i);

            addDrawableChild(ButtonWidget.builder(Text.literal("Item: " + niceSlot(bar.slot)), b -> {
                        int at = bar.choice().map(Enum::ordinal).orElse(-1);
                        bar.slot = slots[(at + 1) % slots.length].name();
                        onChange.run();
                        clearAndInit();   // the Amount box comes and goes with Total
                    })
                    .dimensions(x(0), row, COLUMN_WIDTH, 20).build());

            // A whole mine is bought once, so a Total bar has no amount.
            if (!bar.choice().map(ProgressSlot::isTotal).orElse(false)) {
                labels.add(new Label("Amount", x(1), row + 6));
                addDrawableChild(field(x(1) + 40, row, 40, String.valueOf(bar.quantity), 3,
                        s -> s.matches("[0-9]{0,3}"),
                        s -> {
                            if (!s.isEmpty()) {
                                bar.quantity = Math.max(1, Math.min(ProgressPlanner.ProgressView.MAX_QUANTITY,
                                        Integer.parseInt(s)));
                                onChange.run();
                            }
                        }));
            }

            addDrawableChild(ButtonWidget.builder(Text.literal("Remove"), b -> {
                        bars.remove(bar);
                        onChange.run();
                        clearAndInit();
                    })
                    .dimensions(x(1) + 86, row, 64, 20).build());
        }

        int below = y(bars.size());
        if (bars.isEmpty()) {
            labels.add(new Label("No progress bars yet.", x(0), below + 6));
        }
        ButtonWidget add = ButtonWidget.builder(Text.literal("Add bar"), b -> {
                    bars.add(new ModConfig.Bar(ProgressSlot.SWORD.name(), 1));
                    onChange.run();
                    clearAndInit();
                })
                .dimensions(x(1), below, COLUMN_WIDTH, 20).build();
        add.active = bars.size() < ModConfig.MAX_BARS;
        addDrawableChild(add);

        // Three to a row, so six bars plus these still clear the Done button at GUI scale 4.
        int after = below + ROW_HEIGHT;
        int third = (COLUMN_WIDTH * 2 + 10 - 8) / 3;
        toggleAt(x(0), after, third, "Text", () -> config.showProgressText, v -> config.showProgressText = v);
        toggleAt(x(0) + third + 4, after, third, "Bar", () -> config.showProgressBar, v -> config.showProgressBar = v);
        addDrawableChild(ButtonWidget.builder(goalLabel(), b -> {
                    config.progressToMax = !config.progressToMax;
                    onChange.run();
                    clearAndInit();   // the explanation under it changes too
                })
                .dimensions(x(0) + 2 * (third + 4), after, third, 20).build());
        int explain = after + ROW_HEIGHT + 6;
        labels.add(new Label(config.progressToMax
                ? "Each bar counts every tier left to max the piece at its mine."
                : "Each bar counts only the next tier.", x(0), explain));
        // Only with room above the Done button, which six bars at GUI scale 4 do not leave.
        if (explain + 12 + 10 < this.height - 28
                && bars.stream().anyMatch(b -> b.choice().map(ProgressSlot::isTotal).orElse(false))) {
            labels.add(new Label(config.progressToMax
                    ? "Total: the whole mine at full price, as in the mine panel."
                    : "Total: what is still to buy at the mine, gear owned left out.", x(0), explain + 12));
        }
    }

    private void panels() {
        background(0, "Mine", config.mineLook);
        background(1, "Bosses", config.bossLook);
        background(2, "Progress", config.progressLook);
        background(3, "Reminder", config.alertLook);
        labels.add(new Label("Size is set in the move screen: scroll over a panel.", x(0), y(5)));
    }

    private void background(int row, String name, ModConfig.Look look) {
        cycle(0, row, name + " background", () -> look.background ? look.opacity : 0, v -> {
                    look.background = v > 0;
                    if (v > 0) {
                        look.opacity = v;
                    }
                },
                new int[] { 0, 25, 50, 75, 100 }, v -> v == 0 ? "off" : v + "%");
    }

    private void colours() {
        colour(0, 0, "Headers", () -> config.colorHeader, v -> config.colorHeader = v, Theme.defaults().header());
        colour(1, 0, "Labels", () -> config.colorLabel, v -> config.colorLabel = v, Theme.defaults().label());
        colour(0, 1, "Values", () -> config.colorValue, v -> config.colorValue = v, Theme.defaults().value());
        colour(1, 1, "Good", () -> config.colorGood, v -> config.colorGood = v, Theme.defaults().good());
        colour(0, 2, "Warnings", () -> config.colorWarn, v -> config.colorWarn = v, Theme.defaults().warn());
        colour(1, 2, "Dim text", () -> config.colorDim, v -> config.colorDim = v, Theme.defaults().dim());
        colour(0, 3, "Bar", () -> config.colorBar, v -> config.colorBar = v, Theme.defaults().barFill());
        colour(1, 3, "Bar finished", () -> config.colorBarDone, v -> config.colorBarDone = v, Theme.defaults().barDone());
        colour(0, 4, "Bar track", () -> config.colorBarTrack, v -> config.colorBarTrack = v, Theme.defaults().barTrack());

        addDrawableChild(ButtonWidget.builder(Text.literal("Reset colours"), b -> {
                    config.resetColors();
                    onChange.run();
                    clearAndInit();
                })
                .dimensions(x(1), y(4), COLUMN_WIDTH, 20).build());
        labels.add(new Label("Type a colour as #RRGGBB.", x(0), y(6)));
    }

    private void colour(int column, int row, String name, Supplier<String> get, Consumer<String> set, int fallback) {
        int x = x(column);
        labels.add(new Label(name, x, y(row) + 6));
        addDrawableChild(field(x + 70, y(row), 62, get.get(), 7,
                s -> s.matches("#?[0-9A-Fa-f]{0,6}"),
                s -> {
                    if (Theme.parse(s).isPresent()) {
                        set.accept(s.startsWith("#") ? s.toUpperCase(Locale.ROOT) : "#" + s.toUpperCase(Locale.ROOT));
                        onChange.run();
                    }
                }));
        swatches.add(new Swatch(get, fallback, x + 136, y(row) + 3));
    }

    // --------------------------------------------------------------- widgets

    private static Text onOff(String label, boolean on) {
        return Text.literal(label + ": " + (on ? "ON" : "OFF"));
    }

    private void toggle(int column, int row, String label, BooleanSupplier get, Consumer<Boolean> set) {
        toggleAt(x(column), y(row), label, get, set);
    }

    private void toggleAt(int x, int y, String label, BooleanSupplier get, Consumer<Boolean> set) {
        toggleAt(x, y, COLUMN_WIDTH, label, get, set);
    }

    private void toggleAt(int x, int y, int width, String label, BooleanSupplier get, Consumer<Boolean> set) {
        addDrawableChild(ButtonWidget.builder(onOff(label, get.getAsBoolean()), b -> {
                    set.accept(!get.getAsBoolean());
                    b.setMessage(onOff(label, get.getAsBoolean()));
                    onChange.run();
                })
                .dimensions(x, y, width, 20).build());
    }

    private void cycle(int column, int row, String label, IntSupplier get, IntConsumer set,
                       int[] values, IntFunction<String> show) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label + ": " + show.apply(get.getAsInt())), b -> {
                    set.accept(next(values, get.getAsInt()));
                    b.setMessage(Text.literal(label + ": " + show.apply(get.getAsInt())));
                    onChange.run();
                })
                .dimensions(x(column), y(row), COLUMN_WIDTH, 20).build());
    }

    /** The next value up, wrapping round. A hand-edited value between steps lands on the next step. */
    static int next(int[] values, int current) {
        for (int v : values) {
            if (v > current) {
                return v;
            }
        }
        return values[0];
    }

    private TextFieldWidget field(int x, int y, int width, String text, int maxLength,
                                  Predicate<String> allowed, Consumer<String> changed) {
        TextFieldWidget f = new TextFieldWidget(this.textRenderer, x, y, width, 20, Text.empty());
        f.setMaxLength(maxLength);
        f.setText(text);
        f.setTextPredicate(allowed);
        f.setChangedListener(changed);
        return f;
    }

    private Text goalLabel() {
        return Text.literal("Track: " + (config.progressToMax ? "to max" : "next tier"));
    }

    private static String niceSlot(String slot) {
        return ProgressSlot.parse(slot).map(ProgressSlot::label).orElse(slot);
    }

    // --------------------------------------------------------------- drawing

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Background is drawn by renderWithTooltip; see HudPositionScreen for why not here.
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xFFFFFFFF);
        for (Label l : labels) {
            context.drawTextWithShadow(this.textRenderer, l.text(), l.x(), l.y(), 0xFFCCCCCC);
        }
        for (Swatch s : swatches) {
            int color = Theme.parseOr(s.hex().get(), s.fallback());
            context.fill(s.x() - 1, s.y() - 1, s.x() + 15, s.y() + 15, 0xFF000000);
            context.fill(s.x(), s.y(), s.x() + 14, s.y() + 14, color);
        }
    }

    @Override
    public void close() {
        onChange.run();
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
