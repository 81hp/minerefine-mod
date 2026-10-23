package minerefinehud.client;

import minerefinehud.hud.HudLayout;
import minerefinehud.hud.HudModel;
import minerefinehud.hud.HudStyle;
import minerefinehud.hud.PanelPainter;
import minerefinehud.hud.Theme;
import minerefinehud.progress.ProgressPlanner;
import minerefinehud.progress.ProgressSlot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Every setting that is not a position, in one screen, applied the moment it changes.
 *
 * VERSION SENSITIVE for the Screen, Button and EditBox API only. Built from vanilla widgets on
 * purpose: no Cloth Config or Mod Menu dependency to keep in step with Lunar. The look tabs
 * (Style, Colours, Panels) draw their own buttons, sliders and colour picker from plain
 * rectangles and text, which every supported version draws the same way, and show the panels
 * changing as each setting changes. The screen only reads and writes {@link ModConfig}; nothing
 * here decides what the HUD says.
 */
public final class SettingsScreen extends Screen {

    private enum Tab {
        GENERAL("General"), STYLE("Style"), COLOURS("Colours"), PANELS("Panels"), MINE("Mine"),
        BOSSES("Bosses"), PROGRESS("Progress"), HALLOWEEN("Halloween");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    /** Example panels to preview the looks on, from {@code SamplePanels}. */
    public record Previews(List<HudModel.Line> mine, List<HudModel.Line> bosses, List<HudModel.Line> progress,
                           List<HudModel.Line> alert, List<HudModel.Line> halloween,
                           List<HudModel.Line> pumpkin, List<HudModel.Line> abilities) {}

    private static final int COLUMN_WIDTH = 150;
    private static final int ROW_HEIGHT = 22;
    private static final int TOP = 56;

    // The look tabs' own controls, in the design page's colours.
    private static final int UI_CARD = 0xD0141D1C;
    private static final int UI_RAISED = 0xFF1B2726;
    private static final int UI_LINE = 0xFF283836;
    private static final int UI_FG = 0xFFE3ECE8;
    private static final int UI_MUTED = 0xFF8FA49E;
    private static final int UI_OFF = 0xFF4A5856;
    private static final int UI_ACCENT = 0xFFFFD24A;
    private static final int UI_ACCENT_INK = 0xFF231A00;

    /** Quick picks, the design page's. */
    private static final int[] ACCENTS = { 0xFFFFD24A, 0xFF7FE3FF, 0xFFFF8AD8, 0xFF9BFF6B, 0xFFFF9F43, 0xFFFFFFFF };
    private static final int[] FRAMES = { 0xFFFFD24A, 0xFF7A3CFF, 0xFF4FC3D9, 0xFFFF5C7A, 0xFF5CE65C, 0xFFFFFFFF };
    private static final int[] PALETTE = { 0xFFFFD24A, 0xFFFF9F43, 0xFFFF6B6B, 0xFFFF8AD8, 0xFFC86BFF,
            0xFF7A3CFF, 0xFF4FC3D9, 0xFF7FE3FF, 0xFF5CE65C, 0xFF9BFF6B,
            0xFFFFFFFF, 0xFFBFBFBF, 0xFF8A8A8A, 0xFF3A3A3A, 0xFF000000 };

    private final Screen parent;
    private final ModConfig config;
    /** Saves and nudges the HUD to recompute, called after every change. */
    private final Runnable onChange;
    private final Runnable openPositions;
    private final Runnable openHalloweenPositions;
    private final Runnable resetHalloweenRates;
    /** Pumpkin pieces the player has, which get no bar. */
    private final Supplier<java.util.Set<String>> pumpkinOwned;
    private final Supplier<Previews> previews;
    /** Every boss that can be picked to watch. */
    private final Supplier<List<String>> bossNames;

    private Tab tab = Tab.GENERAL;

    private record Label(String text, int x, int y) {}

    private final List<Label> labels = new ArrayList<>();

    /** The look tabs' own controls, drawn and clicked by this screen. */
    private final List<Control> controls = new ArrayList<>();
    /** The slider or picker being dragged, saved when it is let go. */
    private Control dragging;

    /** The Style tab's list scrolls when it does not fit; this is how far, in pixels. */
    private int scroll;
    private int listTop;
    private int listBottom;
    private int listHeight;
    private int listX;
    private int listWidth;
    private boolean draggingScrollbar;

    /** Which preview the Style tab shows: 0 the main panels, 1 mine, 2 bosses, 3 bars. */
    private int previewChoice;
    /** Where this tab's preview goes; width 0 for none. */
    private int stageX;
    private int stageY;
    private int stageW;
    private int stageH;

    /** The Colours tab's colour being edited, and the picker's position for it. */
    private int colourIndex;
    private double pickHue;
    private double pickSat;
    private double pickVal;
    private EditBox hexBox;
    private boolean settingHex;

    /** The Panels tab's panel being edited. */
    private int panelIndex;

    public SettingsScreen(Screen parent, ModConfig config, Runnable onChange, Runnable openPositions,
                          Runnable openHalloweenPositions, Runnable resetHalloweenRates,
                          Supplier<java.util.Set<String>> pumpkinOwned, Supplier<Previews> previews,
                          Supplier<List<String>> bossNames) {
        super(Component.literal("MineRefine HUD settings"));
        this.parent = parent;
        this.config = config;
        this.onChange = onChange;
        this.openPositions = openPositions;
        this.openHalloweenPositions = openHalloweenPositions;
        this.resetHalloweenRates = resetHalloweenRates;
        this.pumpkinOwned = pumpkinOwned;
        this.previews = previews;
        this.bossNames = bossNames;
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

    /** Bottom of the room a tab can use, clear of the Done button. */
    private int contentBottom() {
        return this.height - 34;
    }

    private boolean lookTab() {
        return tab == Tab.STYLE || tab == Tab.COLOURS || tab == Tab.PANELS;
    }

    @Override
    protected void init() {
        labels.clear();
        controls.clear();
        dragging = null;
        draggingScrollbar = false;
        hexBox = null;
        stageW = 0;
        listHeight = 0;

        // Each tab as wide as its name needs plus an even share of what is left, so every name
        // stays readable at GUI scale 4; on the narrowest screen the margins round the names shrink.
        Tab[] tabs = Tab.values();
        int room = Math.min(this.width - 8, tabs.length * 66);
        int names = 0;
        for (Tab t : tabs) {
            names += this.font.width(t.label);
        }
        int gaps = 2 * (tabs.length - 1);
        int margin = Math.max(2, Math.min(10, (room - names - gaps) / tabs.length));
        int[] widths = new int[tabs.length];
        int natural = gaps;
        for (int i = 0; i < tabs.length; i++) {
            widths[i] = this.font.width(tabs[i].label) + margin;
            natural += widths[i];
        }
        int share = Math.max(0, (room - natural) / tabs.length);
        int total = natural + share * tabs.length;
        int tabX = this.width / 2 - total / 2;
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            int tw = widths[i] + share;
            Button b = Button.builder(Component.literal(t.label), w -> openTab(t))
                    .bounds(tabX, 28, tw, 20).build();
            b.active = t != tab;
            addRenderableWidget(b);
            tabX += tw + 2;
        }

        switch (tab) {
            case GENERAL -> general();
            case STYLE -> style();
            case COLOURS -> colours();
            case PANELS -> panels();
            case MINE -> mine();
            case BOSSES -> bosses();
            case PROGRESS -> progress();
            case HALLOWEEN -> halloween();
        }

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
    }

    private void openTab(Tab t) {
        tab = t;
        scroll = 0;
        rebuildWidgets();
    }

    // ------------------------------------------------------------------ tabs

    private void general() {
        toggle(0, 0, "Overlay", () -> config.enabled, v -> config.enabled = v);
        toggle(1, 0, "Mine panel", () -> config.showMine, v -> config.showMine = v);
        toggle(0, 1, "Boss panel", () -> config.showBosses, v -> config.showBosses = v);
        toggle(1, 1, "Costs in credits", () -> config.showCredits, v -> config.showCredits = v);

        labels.add(new Label("Block rate (millions per credit)", x(0), y(2) + 6));
        EditBox rate = field(x(1), y(2), 60, String.valueOf(config.blocksPerCreditMillions), 10,
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
        addRenderableWidget(rate);

        addRenderableWidget(Button.builder(Component.literal("Move and resize panels..."), b -> {
                    onChange.run();
                    openPositions.run();
                })
                .bounds(x(0), y(4), COLUMN_WIDTH * 2 + 10, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Change how the HUD looks..."), b -> openTab(Tab.STYLE))
                .bounds(x(0), y(5), COLUMN_WIDTH * 2 + 10, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Turret size calculator..."), b -> {
                    onChange.run();
                    if (this.minecraft != null) {
                        Compat.setScreen(this.minecraft, new TurretScreen(this));
                    }
                })
                .bounds(x(0), y(6), COLUMN_WIDTH * 2 + 10, 20).build());
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
        Button dims = toggleAt(x(0), y(0), "Only this dimension", () -> !config.showAllBossWorlds,
                v -> config.showAllBossWorlds = !v);
        dims.setTooltip(Tooltip.create(Component.literal("With bosses picked to watch, those show from every "
                + "dimension instead.")));
        cycle(1, 0, "Max shown", () -> config.maxBosses, v -> config.maxBosses = v,
                new int[] { 1, 2, 3, 4, 5, 6, 8, 10, 15 }, String::valueOf);
        toggle(0, 1, "Alive timers", () -> config.showBossUpTimers, v -> config.showBossUpTimers = v);
        toggle(1, 1, "Still learning", () -> config.showBossLearning, v -> config.showBossLearning = v);

        Button reminder = toggleAt(x(0), y(3), "Respawn reminder", () -> config.bossReminder,
                v -> config.bossReminder = v);
        reminder.setTooltip(Tooltip.create(Component.literal("Reminders wait until a timer is measured (no ?). "
                + "Opening /bossindex corrects every timer at once.")));
        cycle(1, 3, "Remind", () -> config.bossReminderSeconds, v -> config.bossReminderSeconds = v,
                new int[] { 5, 10, 15, 20, 30, 45, 60 }, v -> v + "s before");
        toggle(0, 4, "Reminder sound", () -> config.bossReminderSound, v -> config.bossReminderSound = v);
        cycle(1, 4, "Volume", () -> config.bossReminderVolume, v -> config.bossReminderVolume = v,
                new int[] { 25, 50, 75, 100 }, v -> v + "%");

        toggle(0, 2, "Reforger timer", () -> config.reforgerTimer, v -> config.reforgerTimer = v);
        Button check = toggleAt(x(1), y(2), "Check on join", () -> config.reforgerAutoCheck,
                v -> config.reforgerAutoCheck = v);
        check.setTooltip(Tooltip.create(Component.literal("Runs /nextreforger by itself after you join and "
                + "after the Reforger leaves, and hides that answer. Typed by you, it shows as usual.")));
        toggle(0, 5, "Reforger alert", () -> config.reforgerAlert, v -> config.reforgerAlert = v);
        addRenderableWidget(Button.builder(Component.literal(watchLabel()), b -> {
                    onChange.run();
                    if (this.minecraft != null) {
                        Compat.setScreen(this.minecraft, new BossPickScreen(this, config, bossNames.get(), onChange));
                    }
                })
                .bounds(x(1), y(5), COLUMN_WIDTH, 20).build());

        Button auto = toggleAt(x(0), y(6), "Auto /bossindex", () -> config.bossIndexAuto,
                v -> config.bossIndexAuto = v);
        auto.setTooltip(Tooltip.create(Component.literal("Runs /bossindex by itself every so often and reads "
                + "it without opening it on your screen, so every boss timer stays right in every dimension. "
                + "It is a real command sent as you; it waits while you have a menu or chat open.")));
        labels.add(new Label("Every", x(1), y(6) + 6));
        int labelWidth = this.font.width("Every") + 6;
        EditBox every = field(x(1) + labelWidth, y(6), COLUMN_WIDTH - labelWidth,
                minerefinehud.boss.Interval.format(config.bossIndexEveryMs), 24, s -> true, s ->
                        minerefinehud.boss.Interval.parse(s).ifPresent(ms -> {
                            config.bossIndexEveryMs = Math.max(BossIndexPoller.MIN_INTERVAL_MS, ms);
                            onChange.run();
                        }));
        every.setTooltip(Tooltip.create(Component.literal("How often: 30s, 1 minute, 5m, 1h 30m, 2 hours... "
                + "Shortest 30 seconds. Something it cannot read keeps the last good time.")));
        addRenderableWidget(every);
    }

    private String watchLabel() {
        int n = config.watchedBosses.size();
        return n == 0 ? "Choose bosses..." : "Watching " + n + (n == 1 ? " boss..." : " bosses...");
    }

    /**
     * Seven rows, some split in four, so it all clears the Done button at GUI scale 4 on a 1080p
     * screen.
     */
    private void halloween() {
        // Two buttons to a column, so the small buttons line up with the full-width ones above.
        int quarter = (COLUMN_WIDTH - QUARTER_GAP) / 2;

        toggleAt(quarterX(0, quarter), y(0), quarter, "Panels", () -> config.halloweenEnabled,
                v -> config.halloweenEnabled = v);
        Button spooky = toggleAt(quarterX(1, quarter), y(0), quarter, "Spooky", () -> config.hwSpooky,
                v -> config.hwSpooky = v);
        spooky.setTooltip(Tooltip.create(Component.literal("The Halloween panels in their own orange and "
                + "purple look, with crop icons. Off, they follow the Style and Colours tabs.")));
        addRenderableWidget(Button.builder(Component.literal("Move Halloween panels..."), b -> {
                    onChange.run();
                    openHalloweenPositions.run();
                })
                .bounds(x(1), y(0), COLUMN_WIDTH, 20).build());

        toggle(0, 1, "Candy per hour", () -> config.hwCandyRate, v -> config.hwCandyRate = v);
        cycle(1, 1, "Rates over", () -> config.hwRateMinutes, v -> config.hwRateMinutes = v,
                new int[] { 5, 10, 15, 30, 60 }, v -> v + " min");
        toggle(0, 2, "Crops per hour", () -> config.hwCropRates, v -> config.hwCropRates = v);
        toggleAt(quarterX(2, quarter), y(2), quarter, "Each crop", () -> config.hwPerCrop,
                v -> config.hwPerCrop = v);
        Button resurrect = toggleAt(quarterX(3, quarter), y(2), quarter, "Resurrect", () -> config.hwResurrect,
                v -> config.hwResurrect = v);
        resurrect.setTooltip(Tooltip.create(Component.literal("A bar toward your next resurrection: the crops "
                + "you hold against its cost, and the hoe it needs. Open the Resurrection menu once to read "
                + "the cost. Hidden after the last one.")));

        toggleAt(quarterX(0, quarter), y(3), quarter, "Pests", () -> config.hwPests, v -> config.hwPests = v);
        toggleAt(quarterX(1, quarter), y(3), quarter, "Caravan", () -> config.hwCaravan, v -> config.hwCaravan = v);
        Button alert = toggleAt(quarterX(2, quarter), y(3), quarter, "Alert", () -> config.hwCaravanReminder,
                v -> config.hwCaravanReminder = v);
        alert.setTooltip(Tooltip.create(Component.literal("A reminder, with the boss reminder's sound, "
                + "in the minute before the caravan leaves.")));
        addRenderableWidget(Button.builder(Component.literal("Reset rates"), b -> resetHalloweenRates.run())
                .bounds(quarterX(3, quarter), y(3), quarter, 20).build());

        toggleAt(quarterX(0, quarter), y(4), quarter, "Abilities", () -> config.hwAbilities,
                v -> config.hwAbilities = v);
        Button readySound = toggleAt(quarterX(1, quarter), y(4), quarter, "Sound", () -> config.hwAbilitySound,
                v -> config.hwAbilitySound = v);
        readySound.setTooltip(Tooltip.create(Component.literal("A chime when a farming ability is ready again.")));
        Button buildingBars = toggleAt(quarterX(2, quarter), y(4), quarter, "Buildings", () -> config.hwBuildings,
                v -> config.hwBuildings = v);
        buildingBars.setTooltip(Tooltip.create(Component.literal("A bar for each building's next level, "
                + "read when you open its menu, and a countdown while it upgrades. Maxed buildings are hidden.")));
        Button bell = toggleAt(quarterX(3, quarter), y(4), quarter, "Bell", () -> config.hwBuildingSound,
                v -> config.hwBuildingSound = v);
        bell.setTooltip(Tooltip.create(Component.literal("A bell when a building upgrade finishes.")));

        // Pumpkin set bars; a piece already owned gets no bar.
        java.util.Set<String> owned = pumpkinOwned.get();
        List<String> pieces = minerefinehud.halloween.PumpkinSet.PIECES;
        for (int i = 0; i < pieces.size(); i++) {
            String piece = pieces.get(i);
            String shortName = switch (piece) {
                case "Chestplate" -> "Chest";
                case "Leggings" -> "Legs";
                default -> piece;
            };
            if (owned.contains(piece)) {
                Button b = Button.builder(Component.literal(shortName + ": owned"), w -> { })
                        .bounds(quarterX(i, quarter), y(5), quarter, 20).build();
                b.active = false;
                addRenderableWidget(b);
                continue;
            }
            pumpkinToggle(quarterX(i, quarter), y(5), quarter, piece, shortName);
        }
        Button set = pumpkinToggle(quarterX(0, quarter), y(6), quarter, minerefinehud.halloween.PumpkinSet.SET, "Set");
        set.setTooltip(Tooltip.create(Component.literal("One bar for every pumpkin piece you do not have yet.")));
        addRenderableWidget(Button.builder(Component.literal(barsLabel()), b -> {
                    config.hwPumpkinDetailed = !config.hwPumpkinDetailed;
                    b.setMessage(Component.literal(barsLabel()));
                    onChange.run();
                })
                .bounds(quarterX(1, quarter), y(6), quarter, 20).build());

        labels.add(new Label("Refine cost", x(1), y(6) + 6));
        EditBox refine = field(x(1) + 66, y(6), COLUMN_WIDTH - 66,
                config.hwRefineCost > 0 ? minerefinehud.halloween.HalloweenHud.amount(config.hwRefineCost).replace(",", "") : "",
                8, s -> s.matches("[0-9]*\\.?[0-9]*"),
                s -> {
                    try {
                        config.hwRefineCost = s.isEmpty() ? 0.0 : Math.max(0.0, Double.parseDouble(s));
                        onChange.run();
                    } catch (NumberFormatException ignored) {
                        // Half-typed; keep the last good value.
                    }
                });
        refine.setTooltip(Tooltip.create(Component.literal("Candy your next hoe refine costs. Read by itself "
                + "when the refiner shows a candy price; empty hides the refine bar.")));
        addRenderableWidget(refine);
    }

    private String barsLabel() {
        return "Bars: " + (config.hwPumpkinDetailed ? "full" : "short");
    }

    private static final int QUARTER_GAP = 4;

    /** Left edge of one of four equal buttons across both columns, two in each. */
    private int quarterX(int i, int quarter) {
        return x(i / 2) + (i % 2) * (quarter + QUARTER_GAP);
    }

    private Button pumpkinToggle(int x, int y, int width, String bar, String label) {
        java.util.function.BooleanSupplier on = () -> config.hwPumpkinBars.contains(bar);
        return toggleAt(x, y, width, label, on, v -> {
            config.hwPumpkinBars.remove(bar);
            if (v) {
                config.hwPumpkinBars.add(bar);
            }
        });
    }

    private void progress() {
        List<ModConfig.Bar> bars = config.progressBars;
        ProgressSlot[] slots = ProgressSlot.values();

        for (int i = 0; i < bars.size(); i++) {
            ModConfig.Bar bar = bars.get(i);
            int row = y(i);

            // Bosses sell swords, pickaxes, armour and charms, never an axe, a shovel or a Total.
            boolean bossable = bar.choice().map(SettingsScreen::bossesSell).orElse(false);
            int itemWidth = bossable ? COLUMN_WIDTH - 46 : COLUMN_WIDTH;
            addRenderableWidget(Button.builder(Component.literal("Item: " + niceSlot(bar.slot)), b -> {
                        int at = bar.choice().map(Enum::ordinal).orElse(-1);
                        do {
                            at = (at + 1) % slots.length;
                        } while (bar.boss && !bossesSell(slots[at]));
                        bar.slot = slots[at].name();
                        onChange.run();
                        rebuildWidgets();   // the Amount box comes and goes with Total
                    })
                    .bounds(x(0), row, itemWidth, 20).build());
            if (bossable) {
                addRenderableWidget(Button.builder(Component.literal(bar.boss ? "Boss" : "Mine"), b -> {
                            bar.boss = !bar.boss;
                            onChange.run();
                            rebuildWidgets();
                        })
                        .bounds(x(0) + COLUMN_WIDTH - 42, row, 42, 20).build());
            }

            // A whole mine is bought once, so a Total bar has no amount. It has, in that spot,
            // whether gear already bought comes off it.
            if (bar.choice().map(ProgressSlot::isTotal).orElse(false)) {
                addRenderableWidget(Button.builder(Component.literal(bar.minusOwned ? "Minus bought" : "Full price"),
                                b -> {
                                    bar.minusOwned = !bar.minusOwned;
                                    onChange.run();
                                    rebuildWidgets();   // the explanation under the bars changes too
                                })
                        .bounds(x(1), row, 82, 20).build());
            } else {
                labels.add(new Label("Amount", x(1), row + 6));
                addRenderableWidget(field(x(1) + 40, row, 40, String.valueOf(bar.quantity), 3,
                        s -> s.matches("[0-9]{0,3}"),
                        s -> {
                            if (!s.isEmpty()) {
                                bar.quantity = Math.max(1, Math.min(ProgressPlanner.ProgressView.MAX_QUANTITY,
                                        Integer.parseInt(s)));
                                onChange.run();
                            }
                        }));
            }

            addRenderableWidget(Button.builder(Component.literal("Remove"), b -> {
                        bars.remove(bar);
                        onChange.run();
                        rebuildWidgets();
                    })
                    .bounds(x(1) + 86, row, 64, 20).build());
        }

        int below = y(bars.size());
        if (bars.isEmpty()) {
            labels.add(new Label("No progress bars yet.", x(0), below + 6));
        }
        Button add = Button.builder(Component.literal("Add bar"), b -> {
                    bars.add(new ModConfig.Bar(ProgressSlot.SWORD.name(), 1));
                    onChange.run();
                    rebuildWidgets();
                })
                .bounds(x(1), below, COLUMN_WIDTH, 20).build();
        add.active = bars.size() < ModConfig.MAX_BARS;
        addRenderableWidget(add);
        // Beside Add bar, where nothing else goes once there is a bar, so it never needs a row of
        // its own that six bars at GUI scale 4 would push into the Done button.
        if (bars.stream().anyMatch(b -> !b.boss && b.choice().map(s -> s == ProgressSlot.PICKAXE).orElse(false))) {
            Button swap = Button.builder(onOff("Axe/shovel mines", config.pickaxeSwapsAtToolMines), b -> {
                        config.pickaxeSwapsAtToolMines = !config.pickaxeSwapsAtToolMines;
                        b.setMessage(onOff("Axe/shovel mines", config.pickaxeSwapsAtToolMines));
                        onChange.run();
                    })
                    .bounds(x(0), below, COLUMN_WIDTH, 20).build();
            swap.setTooltip(Tooltip.create(Component.literal("ON: the Pickaxe bar shows the shovel or axe of a mine on the way "
                    + "to the next pickaxe mine, then carries on to the pickaxe. OFF: it skips those mines.")));
            addRenderableWidget(swap);
        }

        // Three to a row, so six bars plus these still clear the Done button at GUI scale 4. The
        // bar itself is switched off with the Style tab's "Progress bar: None".
        int after = below + ROW_HEIGHT;
        int third = (COLUMN_WIDTH * 2 + 10 - 8) / 3;
        toggleAt(x(0), after, third, "Text", () -> config.showProgressText, v -> config.showProgressText = v);
        Button needed = toggleAt(x(0) + third + 4, after, third, "Needed", () -> config.showNeeded,
                v -> config.showNeeded = v);
        needed.setTooltip(Tooltip.create(Component.literal("A line under each bar with what is still needed, "
                + "\"180m still needed\". How the bar looks is in the Style tab.")));
        addRenderableWidget(Button.builder(goalLabel(), b -> {
                    config.progressToMax = !config.progressToMax;
                    onChange.run();
                    rebuildWidgets();   // the explanation under it changes too
                })
                .bounds(x(0) + 2 * (third + 4), after, third, 20).build());
        int explain = after + ROW_HEIGHT + 6;
        labels.add(new Label(config.progressToMax
                ? "Each bar counts every tier left to max the piece at its mine."
                : "Each bar counts only the next tier.", x(0), explain));
        // Only with room above the Done button, which six bars at GUI scale 4 do not leave.
        if (explain + 12 + 10 < this.height - 28) {
            List<ModConfig.Bar> totals = bars.stream()
                    .filter(b -> b.choice().map(ProgressSlot::isTotal).orElse(false)).toList();
            if (!totals.isEmpty()) {
                labels.add(new Label(totals.get(0).minusOwned
                        ? "Total: Minus bought takes gear already bought off it."
                        : "Total: Full price is the whole mine, as in the mine panel.", x(0), explain + 12));
            }
        }
    }

    // ----------------------------------------------------------------- style

    /**
     * Every look setting from the design page, in a list that scrolls on a small screen, with the
     * panels drawn beside it so each change shows straight away.
     */
    private void style() {
        listTop = TOP - 4;
        listBottom = contentBottom();
        listX = 8;
        listWidth = Math.max(180, Math.min(250, (this.width - 24) * 45 / 100));
        int w = listWidth - 14;   // room for the scrollbar
        int[] y = { listTop + 4 };

        HudStyle.Preset[] presets = HudStyle.Preset.values();
        addListed(y, new Segments("Preset", names(presets, p -> p.label),
                () -> config.matchingPreset().map(Enum::ordinal).orElse(-1),
                i -> config.applyPreset(presets[i]), i -> true,
                i -> presets[i].description + " Sets everything below, colours too.",
                () -> config.matchingPreset().isPresent() ? "" : "custom"), w);

        HudStyle.Background[] backgrounds = HudStyle.Background.values();
        addListed(y, new Segments("Panel background", names(backgrounds, b -> b.label),
                () -> sameBackground() == null ? -1 : sameBackground().ordinal(),
                i -> setBackground(config.allLooks(), backgrounds[i]), i -> true,
                i -> backgroundTip(backgrounds[i]) + " Sets every panel; the Panels tab sets them one at a time.",
                () -> sameBackground() == null ? "differs per panel" : ""), w);

        addListed(y, new Slider("Background opacity", 0, 100, 5,
                () -> config.mineLook.opacity,
                v -> config.allLooks().forEach(l -> l.opacity = (int) Math.round(v)),
                v -> sameOpacity() ? Math.round(v) + "%" : "mixed",
                () -> sameBackground() != HudStyle.Background.NONE), w);

        addListed(y, new Slider("HUD size", ModConfig.MIN_HUD_SCALE, ModConfig.MAX_HUD_SCALE, 0.05,
                () -> config.hudScale, v -> config.hudScale = v,
                v -> String.format(Locale.ROOT, "%.2fx", v), () -> true)
                .tip("Grows or shrinks every panel at once. Each panel also has a size of its own, "
                        + "in the Panels tab or by scrolling over it in the move screen."), w);

        addListed(y, styleChoice("Titles", HudStyle.Titles.values(), t -> t.label,
                () -> config.style().titles(), v -> config.setStyle(config.style().withTitles(v)),
                v -> switch (v) {
                    case PLAIN -> "Just the title, in the accent colour.";
                    case UNDERLINE -> "A line in the frame colour under each title.";
                    case BAND -> "A tinted band in the frame colour behind each title.";
                }), w);
        addListed(y, styleChoice("Numbers", HudStyle.Numbers.values(), n -> n.label,
                () -> config.style().numbers(), v -> config.setStyle(config.style().withNumbers(v)),
                v -> v == HudStyle.Numbers.INLINE ? "\"Sword: 20.24b\": each figure right after its label."
                        : "Labels on the left, figures in right-aligned columns, credits in a quieter column."), w);
        addListed(y, styleChoice("Gear labels", HudStyle.GearLabels.values(), g -> g.label,
                () -> config.style().gearLabels(), v -> config.setStyle(config.style().withGearLabels(v)),
                v -> switch (v) {
                    case WORDS -> "Gear names: Sword, Pickaxe, Armor.";
                    case ICONS -> "Item icons instead of gear names, so the mine panel is narrower.";
                    case BOTH -> "An item icon in front of each gear name and progress bar.";
                }), w);
        addListed(y, styleChoice("Boss status", HudStyle.BossStatus.values(), b -> b.label,
                () -> config.style().bossStatus(), v -> config.setStyle(config.style().withBossStatus(v)),
                v -> switch (v) {
                    case TEXT -> "The whole row in the status colour.";
                    case DOTS -> "A coloured dot in front of each boss.";
                    case CHIPS -> "UP and DUE NOW on a coloured chip.";
                }), w);
        addListed(y, styleChoice("Progress bar", HudStyle.BarStyle.values(), b -> b.label,
                () -> config.style().bar(), v -> {
                    HudStyle s = config.style().withBar(v);
                    // Keep the percentage somewhere this bar can show it.
                    if (!HudStyle.percentFits(s.percent(), v)) {
                        s = s.withPercent(v == HudStyle.BarStyle.NONE ? HudStyle.Percent.TEXT : HudStyle.Percent.AFTER);
                    }
                    config.setStyle(s);
                },
                v -> switch (v) {
                    case THIN -> "A slim bar.";
                    case THICK -> "A tall bar that can hold the percentage.";
                    case SEGMENTED -> "Ten blocks that fill one by one.";
                    case NONE -> "No bar, only the text.";
                }), w);
        HudStyle.Percent[] percents = HudStyle.Percent.values();
        addListed(y, new Segments("Percentage", names(percents, p -> p.label),
                () -> config.style().percent().ordinal(),
                i -> config.setStyle(config.style().withPercent(percents[i])),
                i -> HudStyle.percentFits(percents[i], config.style().bar()),
                i -> {
                    if (!HudStyle.percentFits(percents[i], config.style().bar())) {
                        return percents[i] == HudStyle.Percent.INSIDE ? "Needs the thick bar." : "Needs a bar.";
                    }
                    String where = switch (percents[i]) {
                        case OFF -> "No percentage.";
                        case TEXT -> "After the amounts: \"1.66b/1.84b  90%\".";
                        case AFTER -> "To the right of the bar.";
                        case INSIDE -> "Inside the thick bar.";
                    };
                    return where + " Rounded down, so it only says 100% once you can afford it.";
                }, () -> ""), w);
        addListed(y, styleChoice("Bar colour", HudStyle.BarColor.values(), b -> b.label,
                () -> config.style().barColor(), v -> config.setStyle(config.style().withBarColor(v)),
                v -> v == HudStyle.BarColor.ACCENT ? "Every bar in the bar colour (Colours tab)."
                        : "Red when empty, through yellow, to green. A finished bar always uses the finished colour."), w);
        addListed(y, styleChoice("Spacing", HudStyle.Spacing.values(), s -> s.label,
                () -> config.style().spacing(), v -> config.setStyle(config.style().withSpacing(v)),
                v -> "Room around and between the rows."), w);

        addListed(y, new Heading("Show"), w);
        addListed(y, new Check("Credits column", () -> config.showCredits, v -> config.showCredits = v)
                .tip("Mine costs in credits too, at the block rate set in General."), w);
        addListed(y, new Check("Text shadow", () -> config.textShadow,
                v -> config.setStyle(config.style().withShadow(v)))
                .tip("Minecraft's dark shadow under the text. Keeps it readable without a background."), w);
        addListed(y, new Check("Amount still needed under bars", () -> config.showNeeded, v -> config.showNeeded = v)
                .tip("\"180m still needed\" under each progress bar that is not finished yet."), w);

        addListed(y, new SwatchRow("Accent (titles, totals, bars)", ACCENTS,
                () -> config.theme().header(), c -> {
                    config.colorHeader = Theme.format(c);
                    config.colorBar = Theme.format(c);
                }, 0), w);
        addListed(y, new SwatchRow("Frame (borders, bands)", FRAMES,
                () -> config.theme().frame(), c -> config.colorFrame = Theme.format(c), 2), w);
        addListed(y, new Action("Every colour, with a colour picker...", () -> openTab(Tab.COLOURS)).quiet(), w);

        listHeight = y[0] - listTop;
        clampScroll();

        String[] views = { "Main panels", "Mine", "Bosses", "Bars" };
        Segments pick = new Segments("", views, () -> previewChoice, i -> previewChoice = i, i -> true,
                i -> i == 0 ? "Preview the mine, boss and bar panels together."
                        : "Preview this panel alone, at its real size if it fits.", () -> "");
        int sx = listX + listWidth + 10;
        pick.place(sx, listTop, Math.min(this.width - 8 - sx, 220));
        pick.saves = false;
        controls.add(pick);
        stage(sx, listTop + pick.h + 4, this.width - 8, listBottom);
    }

    private <E extends Enum<E>> Segments styleChoice(String title, E[] values, Function<E, String> label,
                                                     Supplier<E> get, Consumer<E> set, Function<E, String> tip) {
        return new Segments(title, names(values, label), () -> get.get().ordinal(), i -> set.accept(values[i]),
                i -> true, i -> tip.apply(values[i]), () -> "");
    }

    private static <T> String[] names(T[] values, Function<T, String> label) {
        String[] out = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = label.apply(values[i]);
        }
        return out;
    }

    private static String backgroundTip(HudStyle.Background b) {
        return switch (b) {
            case NONE -> "No panel, only the text.";
            case SHADE -> "A dark see-through shade.";
            case TOOLTIP -> "Like Minecraft's item tooltips: notched corners and a fading edge in the frame colour.";
            case FRAMED -> "A dark shade with a thin border in the frame colour.";
        };
    }

    /** A background picked from None gets an opacity, or it would look like nothing happened. */
    private static void setBackground(List<ModConfig.Look> looks, HudStyle.Background b) {
        for (ModConfig.Look look : looks) {
            look.setBackground(b);
            if (b != HudStyle.Background.NONE && look.opacity == 0) {
                look.opacity = 60;
            }
        }
    }

    /** The background every panel shares, or null when they differ. */
    private HudStyle.Background sameBackground() {
        HudStyle.Background first = config.mineLook.backgroundStyle();
        return config.allLooks().stream().allMatch(l -> l.backgroundStyle() == first) ? first : null;
    }

    private boolean sameOpacity() {
        return config.allLooks().stream().allMatch(l -> l.opacity == config.mineLook.opacity);
    }

    /** Adds a control to the scrolling list, under the one before. */
    private void addListed(int[] y, Control c, int width) {
        c.place(listX + 6, y[0], width);
        c.scrolls = true;
        controls.add(c);
        y[0] += c.h + 7;
    }

    private void clampScroll() {
        int max = Math.max(0, listHeight - (listBottom - listTop));
        scroll = Math.max(0, Math.min(max, scroll));
    }

    private boolean listScrolls() {
        return tab == Tab.STYLE && listHeight > listBottom - listTop;
    }

    private void stage(int x1, int y1, int x2, int y2) {
        stageX = x1;
        stageY = y1;
        stageW = Math.max(0, x2 - x1);
        stageH = Math.max(0, y2 - y1);
    }

    // --------------------------------------------------------------- colours

    private record ColourEntry(String name, String help, Supplier<String> get, Consumer<String> set, int fallback) {}

    private List<ColourEntry> colourEntries() {
        Theme d = Theme.defaults();
        return List.of(
                new ColourEntry("Accent", "Panel titles and the mine total.", () -> config.colorHeader,
                        v -> config.colorHeader = v, d.header()),
                new ColourEntry("Bar", "Progress bars while they fill, when the bar colour is \"One colour\".",
                        () -> config.colorBar, v -> config.colorBar = v, d.barFill()),
                new ColourEntry("Frame", "Panel borders, title bands and underlines.", () -> config.colorFrame,
                        v -> config.colorFrame = v, d.frame()),
                new ColourEntry("Bar finished", "A bar you can afford.", () -> config.colorBarDone,
                        v -> config.colorBarDone = v, d.barDone()),
                new ColourEntry("Bar track", "The empty part of a bar.", () -> config.colorBarTrack,
                        v -> config.colorBarTrack = v, d.barTrack()),
                new ColourEntry("Labels", "Gear names when the numbers are lined up.", () -> config.colorLabel,
                        v -> config.colorLabel = v, d.label()),
                new ColourEntry("Values", "Costs, timers and other figures.", () -> config.colorValue,
                        v -> config.colorValue = v, d.value()),
                new ColourEntry("Good", "Bosses that are up, finished bars.", () -> config.colorGood,
                        v -> config.colorGood = v, d.good()),
                new ColourEntry("Warnings", "Bosses due now, the respawn reminder.", () -> config.colorWarn,
                        v -> config.colorWarn = v, d.warn()),
                new ColourEntry("Dim text", "Hints, credits, bosses still being learned.", () -> config.colorDim,
                        v -> config.colorDim = v, d.dim()));
    }

    private int entryColour(ColourEntry e) {
        return Theme.parseOr(e.get().get(), e.fallback());
    }

    private int pickedColour() {
        return entryColour(colourEntries().get(colourIndex));
    }

    /**
     * The colours on the left; a picker for the chosen one in the middle (a square for strength
     * and brightness, a hue slider, a hex box and quick swatches); the panels on the right.
     */
    private void colours() {
        List<ColourEntry> entries = colourEntries();
        colourIndex = Math.max(0, Math.min(entries.size() - 1, colourIndex));
        loadPicker(pickedColour());

        int top = TOP - 4;
        int listW = 96;
        int rowH = Math.max(13, Math.min(16, (contentBottom() - top - 18) / entries.size()));
        for (int i = 0; i < entries.size(); i++) {
            int index = i;
            Control row = new ColourRow(entries.get(i), () -> colourIndex == index, () -> {
                colourIndex = index;
                rebuildWidgets();
            });
            row.place(8, top + i * rowH, listW);
            row.h = rowH - 1;
            controls.add(row);
        }
        Action resetAll = new Action("Reset all", () -> {
            config.resetColors();
            rebuildWidgets();
        });
        resetAll.place(8, top + entries.size() * rowH + 3, listW);
        resetAll.tip("Every colour back to the default. The Style tab's presets set the main ones too.");
        controls.add(resetAll);

        int px = 8 + listW + 12;
        // Square, hue strip, hex row and three rows of swatches all have to fit above Done.
        int size = Math.max(60, Math.min(110, contentBottom() - top - 102));
        Picker picker = new Picker();
        picker.place(px, top, size);
        controls.add(picker);

        int hexY = top + size + 4 + 8 + 6;
        hexBox = new EditBox(this.font, px + 20, hexY, size - 20, 16, Component.empty());
        hexBox.setMaxLength(7);
        hexBox.setValue(Theme.format(pickedColour()));
        Compat.filter(hexBox, s -> s.matches("#?[0-9A-Fa-f]{0,6}"), s -> {
            if (settingHex) {
                return;
            }
            Theme.parse(s).ifPresent(c -> {
                colourEntries().get(colourIndex).set().accept(Theme.format(c));
                loadPicker(c);
                onChange.run();
            });
        });
        hexBox.setTooltip(Tooltip.create(Component.literal("Type a colour as #RRGGBB.")));
        addRenderableWidget(hexBox);

        int sw = Math.max(8, Math.min(14, (size - 4 * 2) / 5));
        int swatchTop = hexY + 20;
        for (int i = 0; i < PALETTE.length; i++) {
            int colour = PALETTE[i];
            Control s = new SwatchButton(colour, () -> (pickedColour() & 0xFFFFFF) == (colour & 0xFFFFFF), () -> {
                setPicked(colour);
                loadPicker(colour);
                onChange.run();
            });
            s.place(px + (i % 5) * (sw + 2), swatchTop + (i / 5) * (sw + 2), sw);
            s.h = sw;
            controls.add(s);
        }
        int resetTop = swatchTop + 3 * (sw + 2) + 2;
        Action reset = new Action("Default", () -> {
            ColourEntry e = colourEntries().get(colourIndex);
            setPicked(e.fallback());
            loadPicker(e.fallback());
        });
        reset.place(px, resetTop, size);
        reset.tip("This colour back to the default.");
        if (resetTop + reset.h <= contentBottom()) {
            controls.add(reset);
        }

        stage(px + size + 12, top, this.width - 8, contentBottom());
    }

    private void loadPicker(int argb) {
        double[] hsv = Theme.toHsv(argb);
        // A grey has no hue of its own; keep the slider where it was rather than jump to red.
        if (hsv[1] > 0 && hsv[2] > 0) {
            pickHue = hsv[0];
        }
        pickSat = hsv[1];
        pickVal = hsv[2];
    }

    /** Stores the picked colour in the chosen entry and shows it in the hex box. Not saved yet. */
    private void setPicked(int argb) {
        colourEntries().get(colourIndex).set().accept(Theme.format(argb));
        if (hexBox != null) {
            settingHex = true;
            try {
                hexBox.setValue(Theme.format(argb));
            } finally {
                settingHex = false;
            }
        }
    }

    // ---------------------------------------------------------------- panels

    private record PanelEntry(String name, ModConfig.Look look, double defaultScale,
                              Function<Previews, List<HudModel.Line>> lines) {}

    private List<PanelEntry> panelEntries() {
        return List.of(
                new PanelEntry("Mine", config.mineLook, 1.0, Previews::mine),
                new PanelEntry("Bosses", config.bossLook, 1.0, Previews::bosses),
                new PanelEntry("Progress", config.progressLook, 1.0, Previews::progress),
                new PanelEntry("Reminder", config.alertLook, 2.0, Previews::alert),
                new PanelEntry("Halloween", config.hwStatsLook, 1.0, Previews::halloween),
                new PanelEntry("Pumpkin set", config.hwPumpkinLook, 1.0, Previews::pumpkin),
                new PanelEntry("Abilities", config.hwAbilityLook, 1.0, Previews::abilities));
    }

    /** Each panel's own background and size, with that panel drawn as it will look. */
    private void panels() {
        List<PanelEntry> entries = panelEntries();
        panelIndex = Math.max(0, Math.min(entries.size() - 1, panelIndex));
        PanelEntry entry = entries.get(panelIndex);

        int top = TOP - 4;
        int listW = 80;
        int rowH = Math.max(13, Math.min(16, (contentBottom() - top - 24) / entries.size()));
        for (int i = 0; i < entries.size(); i++) {
            int index = i;
            Control row = new TextRow(entries.get(i).name(), () -> panelIndex == index, () -> {
                panelIndex = index;
                rebuildWidgets();
            });
            row.place(8, top + i * rowH, listW);
            row.h = rowH - 1;
            controls.add(row);
        }
        addRenderableWidget(Button.builder(Component.literal("Move..."), b -> {
                    onChange.run();
                    openPositions.run();
                })
                .bounds(8, Math.min(top + entries.size() * rowH + 3, contentBottom() - 20), listW, 20).build());

        int cx = 8 + listW + 12;
        int cw = Math.max(130, Math.min(200, (this.width - cx - 8) * 45 / 100));
        int[] y = { top };
        ModConfig.Look look = entry.look();

        HudStyle.Background[] backgrounds = HudStyle.Background.values();
        place(y, new Segments(entry.name() + " background", names(backgrounds, b -> b.label),
                () -> look.backgroundStyle().ordinal(), i -> setBackground(List.of(look), backgrounds[i]),
                i -> true, i -> backgroundTip(backgrounds[i]), () -> ""), cx, cw);
        place(y, new Slider("Opacity", 0, 100, 5, () -> look.opacity, v -> look.opacity = (int) Math.round(v),
                v -> Math.round(v) + "%", () -> look.backgroundStyle() != HudStyle.Background.NONE), cx, cw);
        place(y, new Slider("Size", HudLayout.MIN_SCALE, HudLayout.MAX_SCALE, HudLayout.SCALE_STEP,
                () -> look.scale, v -> look.scale = v, v -> String.format(Locale.ROOT, "%.2fx", v), () -> true)
                .tip("This panel's own size. The Style tab's HUD size multiplies it."), cx, cw);
        place(y, new Action("Copy to all panels", () -> {
            for (ModConfig.Look other : config.allLooks()) {
                other.setBackground(look.backgroundStyle());
                other.opacity = look.opacity;
            }
        }).tip("Copies this panel's background and opacity to all the others. Sizes stay as they are."), cx, cw);
        place(y, new Action("Default size", () -> look.scale = entry.defaultScale()), cx, cw);

        stage(cx + cw + 12, top, this.width - 8, contentBottom());
    }

    private void place(int[] y, Control c, int x, int width) {
        c.place(x, y[0], width);
        controls.add(c);
        y[0] += c.h + 7;
    }

    // --------------------------------------------------------------- widgets

    private static Component onOff(String label, boolean on) {
        return Component.literal(label + ": " + (on ? "ON" : "OFF"));
    }

    private void toggle(int column, int row, String label, BooleanSupplier get, Consumer<Boolean> set) {
        toggleAt(x(column), y(row), label, get, set);
    }

    private Button toggleAt(int x, int y, String label, BooleanSupplier get, Consumer<Boolean> set) {
        return toggleAt(x, y, COLUMN_WIDTH, label, get, set);
    }

    private Button toggleAt(int x, int y, int width, String label, BooleanSupplier get, Consumer<Boolean> set) {
        return addRenderableWidget(Button.builder(onOff(label, get.getAsBoolean()), b -> {
                    set.accept(!get.getAsBoolean());
                    b.setMessage(onOff(label, get.getAsBoolean()));
                    onChange.run();
                })
                .bounds(x, y, width, 20).build());
    }

    private void cycle(int column, int row, String label, IntSupplier get, IntConsumer set,
                       int[] values, IntFunction<String> show) {
        addRenderableWidget(Button.builder(Component.literal(label + ": " + show.apply(get.getAsInt())), b -> {
                    set.accept(next(values, get.getAsInt()));
                    b.setMessage(Component.literal(label + ": " + show.apply(get.getAsInt())));
                    onChange.run();
                })
                .bounds(x(column), y(row), COLUMN_WIDTH, 20).build());
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

    private EditBox field(int x, int y, int width, String text, int maxLength,
                                  Predicate<String> allowed, Consumer<String> changed) {
        EditBox f = new EditBox(this.font, x, y, width, 20, Component.empty());
        f.setMaxLength(maxLength);
        f.setValue(text);
        Compat.filter(f, allowed, changed);
        return f;
    }

    private Component goalLabel() {
        return Component.literal("Track: " + (config.progressToMax ? "to max" : "next tier"));
    }

    private static boolean bossesSell(ProgressSlot slot) {
        return !slot.isTotal() && slot != ProgressSlot.AXE && slot != ProgressSlot.SHOVEL;
    }

    private static String niceSlot(String slot) {
        return ProgressSlot.parse(slot).map(ProgressSlot::label).orElse(slot);
    }

    // ------------------------------------------------------- custom controls

    /** One of the look tabs' controls. Positions are screen pixels, before the list scrolls. */
    private abstract class Control {
        int x;
        int y;
        int w;
        int h;
        /** In the Style tab's scrolling list. */
        boolean scrolls;
        /** Whether a change is saved; false for the preview picker, which is no setting. */
        boolean saves = true;
        String tooltip = "";

        void place(int x, int y, int w) {
            this.x = x;
            this.y = y;
            this.w = w;
            layout();
        }

        /** Works out the height for this width. */
        void layout() {
        }

        Control tip(String text) {
            tooltip = text;
            return this;
        }

        int top() {
            return scrolls ? y - scroll : y;
        }

        boolean visible() {
            return !scrolls || (top() + h > listTop && top() < listBottom);
        }

        boolean contains(double mx, double my) {
            if (scrolls && (my < listTop || my >= listBottom)) {
                return false;
            }
            return mx >= x && mx < x + w && my >= top() && my < top() + h;
        }

        abstract void draw(GuiGraphicsExtractor context, int mx, int my);

        /** @return true when the click was used */
        boolean click(double mx, double my) {
            return false;
        }

        void drag(double mx, double my) {
        }

        /** The tooltip for the point under the mouse, or empty. */
        String tipAt(double mx, double my) {
            return tooltip;
        }

        void changed() {
            if (saves) {
                onChange.run();
            }
        }
    }

    /** A title, then a row of buttons of which one is picked, wrapping onto more rows if needed. */
    private final class Segments extends Control {
        final String title;
        final String[] options;
        final IntSupplier current;
        final IntConsumer set;
        final IntPredicate enabled;
        final IntFunction<String> tips;
        final Supplier<String> note;
        /** x, y (from the control's corner) and width of each button. */
        int[][] boxes;

        Segments(String title, String[] options, IntSupplier current, IntConsumer set, IntPredicate enabled,
                 IntFunction<String> tips, Supplier<String> note) {
            this.title = title;
            this.options = options;
            this.current = current;
            this.set = set;
            this.enabled = enabled;
            this.tips = tips;
            this.note = note;
        }

        private int head() {
            return title.isEmpty() ? 0 : 11;
        }

        @Override
        void layout() {
            boxes = new int[options.length][];
            List<List<Integer>> rows = new ArrayList<>();
            List<Integer> row = new ArrayList<>();
            int used = 0;
            for (int i = 0; i < options.length; i++) {
                int need = font.width(options[i]) + 8;
                if (!row.isEmpty() && used + 2 + need > w) {
                    rows.add(row);
                    row = new ArrayList<>();
                    used = 0;
                }
                used += (row.isEmpty() ? 0 : 2) + need;
                row.add(i);
            }
            rows.add(row);
            int ry = head();
            for (List<Integer> r : rows) {
                int natural = -2;
                for (int i : r) {
                    natural += font.width(options[i]) + 8 + 2;
                }
                int extra = Math.max(0, w - natural);
                int bx = 0;
                for (int k = 0; k < r.size(); k++) {
                    int i = r.get(k);
                    // Spread the spare room evenly so each row fills the width exactly.
                    int share = extra / r.size() + (k < extra % r.size() ? 1 : 0);
                    int bw = font.width(options[i]) + 8 + share;
                    boxes[i] = new int[] { bx, ry, bw };
                    bx += bw + 2;
                }
                ry += 14 + 2;
            }
            h = ry - 2;
        }

        @Override
        void draw(GuiGraphicsExtractor context, int mx, int my) {
            int ty = top();
            if (!title.isEmpty()) {
                String n = note.get();
                int noteW = n.isEmpty() ? 0 : font.width(n) + 4;
                context.text(font, trim(title, w - noteW), x, ty + 1, UI_MUTED, false);
                if (!n.isEmpty()) {
                    context.text(font, n, x + w - font.width(n), ty + 1, UI_ACCENT, false);
                }
            }
            int picked = current.getAsInt();
            int hovered = contains(mx, my) ? at(mx, my) : -1;
            for (int i = 0; i < options.length; i++) {
                int[] b = boxes[i];
                int bx = x + b[0];
                int by = ty + b[1];
                boolean on = i == picked;
                boolean usable = enabled.test(i);
                int edge = on ? UI_ACCENT : usable && i == hovered ? UI_MUTED : UI_LINE;
                box(context, bx, by, b[2], 14, on ? UI_ACCENT : UI_RAISED, edge);
                int ink = on ? UI_ACCENT_INK : usable ? UI_FG : UI_OFF;
                context.text(font, options[i], bx + (b[2] - font.width(options[i])) / 2, by + 3, ink, false);
            }
        }

        private int at(double mx, double my) {
            for (int i = 0; i < options.length; i++) {
                int[] b = boxes[i];
                if (mx >= x + b[0] && mx < x + b[0] + b[2] && my >= top() + b[1] && my < top() + b[1] + 14) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        boolean click(double mx, double my) {
            int i = at(mx, my);
            if (i < 0 || !enabled.test(i)) {
                return i >= 0;
            }
            set.accept(i);
            changed();
            return true;
        }

        @Override
        String tipAt(double mx, double my) {
            int i = at(mx, my);
            return i >= 0 ? tips.apply(i) : "";
        }
    }

    /** A labelled slider that snaps to its step. Saved when let go, not on every pixel. */
    private final class Slider extends Control {
        final String title;
        final double min;
        final double max;
        final double step;
        final DoubleSupplier get;
        final DoubleConsumer set;
        final DoubleFunction<String> show;
        final BooleanSupplier active;

        Slider(String title, double min, double max, double step, DoubleSupplier get, DoubleConsumer set,
               DoubleFunction<String> show, BooleanSupplier active) {
            this.title = title;
            this.min = min;
            this.max = max;
            this.step = step;
            this.get = get;
            this.set = set;
            this.show = show;
            this.active = active;
        }

        @Override
        void layout() {
            h = 11 + 10;
        }

        @Override
        void draw(GuiGraphicsExtractor context, int mx, int my) {
            int ty = top();
            boolean on = active.getAsBoolean();
            String value = on ? show.apply(get.getAsDouble()) : "off";
            context.text(font, trim(title, w - font.width(value) - 4), x, ty + 1, UI_MUTED, false);
            context.text(font, value, x + w - font.width(value), ty + 1, on ? UI_FG : UI_OFF, false);
            int trackY = ty + 11 + 3;
            double f = (Math.max(min, Math.min(max, get.getAsDouble())) - min) / (max - min);
            int knob = x + 3 + (int) Math.round(f * (w - 6));
            context.fill(x, trackY, x + w, trackY + 4, UI_LINE);
            if (on) {
                context.fill(x, trackY, knob, trackY + 4, UI_ACCENT);
            }
            boolean hover = on && (dragging == this || contains(mx, my));
            box(context, knob - 3, ty + 11, 6, 10, on ? (hover ? UI_FG : 0xFFCCD6D2) : UI_OFF, UI_LINE);
        }

        @Override
        boolean click(double mx, double my) {
            if (!active.getAsBoolean()) {
                return true;
            }
            dragging = this;
            drag(mx, my);
            return true;
        }

        @Override
        void drag(double mx, double my) {
            double f = Math.max(0, Math.min(1, (mx - x - 3) / Math.max(1, w - 6)));
            // Rounded again after snapping, so the config holds 1.05 rather than 1.0500000000000003.
            double v = Math.round(Math.round((min + f * (max - min)) / step) * step * 1000) / 1000.0;
            set.accept(Math.max(min, Math.min(max, v)));
        }
    }

    /** A tick box and its label. */
    private final class Check extends Control {
        final String title;
        final BooleanSupplier get;
        final Consumer<Boolean> set;

        Check(String title, BooleanSupplier get, Consumer<Boolean> set) {
            this.title = title;
            this.get = get;
            this.set = set;
        }

        @Override
        void layout() {
            h = 11;
        }

        @Override
        void draw(GuiGraphicsExtractor context, int mx, int my) {
            int ty = top();
            boolean on = get.getAsBoolean();
            box(context, x, ty + 1, 9, 9, on ? UI_ACCENT : UI_RAISED, contains(mx, my) ? UI_MUTED : on ? UI_ACCENT : UI_LINE);
            if (on) {
                // A small tick, in pixels.
                context.fill(x + 2, ty + 5, x + 3, ty + 7, UI_ACCENT_INK);
                context.fill(x + 3, ty + 6, x + 4, ty + 8, UI_ACCENT_INK);
                context.fill(x + 4, ty + 5, x + 5, ty + 7, UI_ACCENT_INK);
                context.fill(x + 5, ty + 4, x + 6, ty + 6, UI_ACCENT_INK);
                context.fill(x + 6, ty + 3, x + 7, ty + 5, UI_ACCENT_INK);
            }
            context.text(font, trim(title, w - 14), x + 14, ty + 2, UI_FG, false);
        }

        @Override
        boolean click(double mx, double my) {
            set.accept(!get.getAsBoolean());
            changed();
            return true;
        }
    }

    /** A small heading inside the list. */
    private final class Heading extends Control {
        final String title;

        Heading(String title) {
            this.title = title;
        }

        @Override
        void layout() {
            h = 9;
        }

        @Override
        void draw(GuiGraphicsExtractor context, int mx, int my) {
            context.text(font, title, x, top() + 1, UI_MUTED, false);
        }
    }

    /** A plain button drawn like the rest of the look tabs. */
    private final class Action extends Control {
        final String title;
        final Runnable run;

        Action(String title, Runnable run) {
            this.title = title;
            this.run = run;
        }

        /** For one that only opens something, so it saves nothing. */
        Action quiet() {
            saves = false;
            return this;
        }

        @Override
        void layout() {
            h = 14;
        }

        @Override
        void draw(GuiGraphicsExtractor context, int mx, int my) {
            int ty = top();
            box(context, x, ty, w, h, UI_RAISED, contains(mx, my) ? UI_MUTED : UI_LINE);
            String t = trim(title, w - 8);
            context.text(font, t, x + (w - font.width(t)) / 2, ty + 3, UI_FG, false);
        }

        @Override
        boolean click(double mx, double my) {
            run.run();
            changed();
            return true;
        }
    }

    /** A title and a few colours to pick from, plus a button that opens the full picker. */
    private final class SwatchRow extends Control {
        final String title;
        final int[] colours;
        final IntSupplier get;
        final IntConsumer set;
        /** Which colour the Colours tab opens on. */
        final int entry;

        SwatchRow(String title, int[] colours, IntSupplier get, IntConsumer set, int entry) {
            this.title = title;
            this.colours = colours;
            this.get = get;
            this.set = set;
            this.entry = entry;
        }

        @Override
        void layout() {
            h = 11 + size();
        }

        private int size() {
            return Math.min(14, (w - 2 * (colours.length + 1)) / (colours.length + 2));
        }

        /** Whether the current colour is one of the quick picks. */
        private boolean known() {
            int current = get.getAsInt() & 0xFFFFFF;
            for (int c : colours) {
                if ((c & 0xFFFFFF) == current) {
                    return true;
                }
            }
            return false;
        }

        /** Which square is at this x: a quick pick, then the custom colour if any, then "+". */
        private int index(double mx) {
            return (int) Math.floor((mx - x) / (size() + 2));
        }

        @Override
        void draw(GuiGraphicsExtractor context, int mx, int my) {
            int ty = top();
            context.text(font, trim(title, w), x, ty + 1, UI_MUTED, false);
            int s = size();
            int current = get.getAsInt();
            boolean inside = contains(mx, my) && my >= ty + 11;
            int hover = inside ? index(mx) : -1;
            int at = x;
            int i = 0;
            for (int c : colours) {
                swatch(context, at, ty + 11, s, c, (c & 0xFFFFFF) == (current & 0xFFFFFF), hover == i);
                at += s + 2;
                i++;
            }
            if (!known()) {
                swatch(context, at, ty + 11, s, current, true, false);
                at += s + 2;
                i++;
            }
            box(context, at, ty + 11, s, s, UI_RAISED, hover == i ? UI_MUTED : UI_LINE);
            context.text(font, "+", at + (s - font.width("+")) / 2 + 1, ty + 11 + (s - 7) / 2, UI_FG, false);
        }

        @Override
        boolean click(double mx, double my) {
            int index = index(mx);
            if (my < top() + 11 || index < 0) {
                return false;
            }
            if (index < colours.length) {
                set.accept(colours[index]);
                changed();
                return true;
            }
            int plus = colours.length + (known() ? 0 : 1);
            if (index == plus) {
                colourIndex = entry;
                openTab(Tab.COLOURS);
            }
            return true;
        }

        @Override
        String tipAt(double mx, double my) {
            if (my < top() + 11) {
                return "";
            }
            int index = index(mx);
            if (index >= 0 && index < colours.length) {
                return Theme.format(colours[index]);
            }
            int plus = colours.length + (known() ? 0 : 1);
            return index == plus ? "Any colour: open the colour picker." : index >= 0 ? "Your own colour." : "";
        }
    }

    /** One colour in the Colours tab's list. */
    private final class ColourRow extends Control {
        final ColourEntry entry;
        final BooleanSupplier picked;
        final Runnable pick;

        ColourRow(ColourEntry entry, BooleanSupplier picked, Runnable pick) {
            this.entry = entry;
            this.picked = picked;
            this.pick = pick;
            this.tooltip = entry.help();
            this.saves = false;
        }

        @Override
        void draw(GuiGraphicsExtractor context, int mx, int my) {
            boolean on = picked.getAsBoolean();
            if (on || contains(mx, my)) {
                box(context, x, y, w, h, on ? UI_RAISED : 0x601B2726, on ? UI_ACCENT : UI_LINE);
            }
            int s = h - 4;
            context.fill(x + 2, y + 2, x + 2 + s, y + 2 + s, 0xFF000000);
            context.fill(x + 3, y + 3, x + 1 + s, y + 1 + s, entryColour(entry));
            context.text(font, trim(entry.name(), w - s - 10), x + s + 6, y + (h - 8) / 2 + 1, on ? UI_FG : UI_MUTED, false);
        }

        @Override
        boolean click(double mx, double my) {
            pick.run();
            return true;
        }
    }

    /** One name in the Panels tab's list. */
    private final class TextRow extends Control {
        final String name;
        final BooleanSupplier picked;
        final Runnable pick;

        TextRow(String name, BooleanSupplier picked, Runnable pick) {
            this.name = name;
            this.picked = picked;
            this.pick = pick;
            this.saves = false;
        }

        @Override
        void draw(GuiGraphicsExtractor context, int mx, int my) {
            boolean on = picked.getAsBoolean();
            if (on || contains(mx, my)) {
                box(context, x, y, w, h, on ? UI_RAISED : 0x601B2726, on ? UI_ACCENT : UI_LINE);
            }
            context.text(font, trim(name, w - 8), x + 5, y + (h - 8) / 2 + 1, on ? UI_FG : UI_MUTED, false);
        }

        @Override
        boolean click(double mx, double my) {
            pick.run();
            return true;
        }
    }

    /** A colour square to click. */
    private final class SwatchButton extends Control {
        final int colour;
        final BooleanSupplier picked;
        final Runnable pick;

        SwatchButton(int colour, BooleanSupplier picked, Runnable pick) {
            this.colour = colour;
            this.picked = picked;
            this.pick = pick;
            this.tooltip = Theme.format(colour);
            this.saves = false;
        }

        @Override
        void draw(GuiGraphicsExtractor context, int mx, int my) {
            swatch(context, x, y, w, colour, picked.getAsBoolean(), contains(mx, my));
        }

        @Override
        boolean click(double mx, double my) {
            pick.run();
            return true;
        }
    }

    /**
     * The colour picker: strength left to right and brightness top to bottom in the square, hue in
     * the strip under it. The square is thin columns each fading to black, which is exactly what
     * an HSV square is.
     */
    private final class Picker extends Control {
        /** 1 while dragging in the square, 2 in the hue strip. */
        int part;

        @Override
        void layout() {
            h = w + 4 + 8;
        }

        private int hueY() {
            return y + w + 4;
        }

        @Override
        void draw(GuiGraphicsExtractor context, int mx, int my) {
            int s = w;
            context.fill(x - 1, y - 1, x + s + 1, y + s + 1, UI_LINE);
            for (int i = 0; i < s; i += 2) {
                int top = Theme.hsv(pickHue, (i + 1) / (double) s, 1.0);
                context.fillGradient(x + i, y, Math.min(x + s, x + i + 2), y + s, top, 0xFF000000);
            }
            int kx = x + (int) Math.round(pickSat * (s - 1));
            int ky = y + (int) Math.round((1 - pickVal) * (s - 1));
            context.fill(kx - 3, ky - 3, kx + 4, ky + 4, 0xFF000000);
            context.fill(kx - 2, ky - 2, kx + 3, ky + 3, 0xFFFFFFFF);
            context.fill(kx - 1, ky - 1, kx + 2, ky + 2, Theme.hsv(pickHue, pickSat, pickVal));

            int hy = hueY();
            context.fill(x - 1, hy - 1, x + s + 1, hy + 9, UI_LINE);
            for (int i = 0; i < s; i++) {
                context.fill(x + i, hy, x + i + 1, hy + 8, Theme.hsv(i * 360.0 / s, 1, 1));
            }
            int hx = x + (int) Math.round(pickHue / 360.0 * (s - 1));
            context.fill(hx - 2, hy - 2, hx + 3, hy + 10, 0xFF000000);
            context.fill(hx - 1, hy - 1, hx + 2, hy + 9, 0xFFFFFFFF);

            // The colour as it is now, beside the hex box.
            int py = hy + 8 + 6;
            context.fill(x - 1, py - 1, x + 17, py + 17, UI_LINE);
            context.fill(x, py, x + 16, py + 16, pickedColour());
        }

        @Override
        boolean contains(double mx, double my) {
            return mx >= x - 2 && mx < x + w + 2 && my >= y - 2 && my < hueY() + 10;
        }

        @Override
        boolean click(double mx, double my) {
            part = my < hueY() - 1 ? 1 : 2;
            dragging = this;
            drag(mx, my);
            return true;
        }

        @Override
        void drag(double mx, double my) {
            double fx = Math.max(0, Math.min(1, (mx - x) / Math.max(1, w - 1)));
            if (part == 1) {
                pickSat = fx;
                pickVal = 1 - Math.max(0, Math.min(1, (my - y) / Math.max(1, w - 1)));
            } else {
                pickHue = Math.min(359.9, fx * 360.0);
            }
            setPicked(Theme.hsv(pickHue, pickSat, pickVal));
        }

        @Override
        String tipAt(double mx, double my) {
            return my < hueY() - 1 ? "Drag: left and right for how strong the colour is, up and down for how bright."
                    : "Drag to change the colour.";
        }
    }

    // --------------------------------------------------------------- drawing

    private static void box(GuiGraphicsExtractor context, int x, int y, int w, int h, int fill, int edge) {
        context.fill(x, y, x + w, y + h, edge);
        context.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    private static void swatch(GuiGraphicsExtractor context, int x, int y, int s, int colour, boolean picked, boolean hover) {
        context.fill(x, y, x + s, y + s, picked ? 0xFFFFFFFF : hover ? UI_MUTED : 0xFF000000);
        context.fill(x + 1, y + 1, x + s - 1, y + s - 1, picked ? 0xFF000000 : colour);
        if (picked) {
            context.fill(x + 2, y + 2, x + s - 2, y + s - 2, colour);
        }
    }

    private String trim(String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        String t = text;
        while (!t.isEmpty() && font.width(t + "...") > width) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "...";
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // Background is drawn by renderWithTooltip; see HudPositionScreen for why not here.
        boolean look = lookTab();
        if (look) {
            // A card under the controls, so they read over a busy world.
            if (tab == Tab.STYLE) {
                context.fill(listX, listTop, listX + listWidth, listBottom, UI_CARD);
            } else {
                context.fill(4, TOP - 8, stageX - 6, contentBottom() + 2, UI_CARD);
            }
        }

        super.extractRenderState(context, mouseX, mouseY, delta);

        context.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
        for (Label l : labels) {
            context.text(this.font, l.text(), l.x(), l.y(), 0xFFCCCCCC);
        }
        if (!look) {
            return;
        }

        if (stageW > 0 && stageH > 0) {
            drawPreview(context);
        }

        if (tab == Tab.STYLE) {
            context.enableScissor(listX, listTop, listX + listWidth, listBottom);
            try {
                for (Control c : controls) {
                    if (c.scrolls && c.visible()) {
                        c.draw(context, mouseX, mouseY);
                    }
                }
            } finally {
                context.disableScissor();
            }
        }
        for (Control c : controls) {
            if (!c.scrolls) {
                c.draw(context, mouseX, mouseY);
            }
        }
        if (listScrolls()) {
            int[] bar = scrollbar();
            context.fill(listX + listWidth - 6, listTop + 2, listX + listWidth - 3, listBottom - 2, UI_LINE);
            context.fill(listX + listWidth - 6, bar[0], listX + listWidth - 3, bar[1], UI_MUTED);
        }

        if (dragging == null && !draggingScrollbar) {
            for (Control c : controls) {
                if (c.visible() && c.contains(mouseX, mouseY)) {
                    String tip = c.tipAt(mouseX, mouseY);
                    if (!tip.isEmpty()) {
                        tooltip(context, tip, mouseX, mouseY);
                    }
                    break;
                }
            }
        }
    }

    /** Top and bottom of the scrollbar's thumb. */
    private int[] scrollbar() {
        int view = listBottom - listTop;
        int track = view - 4;
        int thumb = Math.max(16, track * view / Math.max(1, listHeight));
        int max = Math.max(1, listHeight - view);
        int start = listTop + 2 + (track - thumb) * scroll / max;
        return new int[] { start, start + thumb };
    }

    /** Wrapped text in a dark box by the mouse, kept on screen, over everything else. */
    private void tooltip(GuiGraphicsExtractor context, String text, int mx, int my) {
        int max = Math.max(60, Math.min(200, this.width - 24));
        List<String> lines = new ArrayList<>();
        String line = "";
        for (String word : text.split(" ")) {
            String longer = line.isEmpty() ? word : line + " " + word;
            if (font.width(longer) > max && !line.isEmpty()) {
                lines.add(line);
                line = word;
            } else {
                line = longer;
            }
        }
        lines.add(line);
        int w = 0;
        for (String l : lines) {
            w = Math.max(w, font.width(l));
        }
        int h = lines.size() * 10;
        int tx = Math.max(6, Math.min(mx + 10, this.width - w - 6));
        int ty = my + 12 + h + 6 > this.height ? Math.max(6, my - h - 8) : my + 12;
        // Its own layer, or text drawn earlier in the frame could show through the box.
        context.nextStratum();
        context.fill(tx - 4, ty - 4, tx + w + 4, ty + h + 2, 0xF0100010);
        context.fill(tx - 3, ty - 3, tx + w + 3, ty - 2, 0xFF5A3CA0);
        context.fill(tx - 3, ty + h, tx + w + 3, ty + h + 1, 0xFF2A1A50);
        for (int i = 0; i < lines.size(); i++) {
            context.text(font, lines.get(i), tx, ty + i * 10, UI_FG, false);
        }
    }

    // ---------------------------------------------------------------- preview

    private record Shown(PanelPainter.Painted painted, int dx, int dy) {}

    /**
     * The panels as they will look, over a stand-in for a block wall, so colours and backgrounds
     * are judged against something like the game rather than a black screen. At their real size
     * when that fits, smaller when not, and the caption says which.
     */
    private void drawPreview(GuiGraphicsExtractor context) {
        int x = stageX;
        int y = stageY;
        int w = stageW;
        int h = stageH;
        context.enableScissor(x, y, x + w, y + h);
        try {
            wall(context, x, y, w, h);
            Previews p = previews.get();
            List<Shown> shown = new ArrayList<>();
            double scale;
            if (tab == Tab.PANELS || (tab == Tab.STYLE && previewChoice > 0)) {
                ModConfig.Look look;
                List<HudModel.Line> lines;
                if (tab == Tab.PANELS) {
                    PanelEntry entry = panelEntries().get(panelIndex);
                    look = entry.look();
                    lines = entry.lines().apply(p);
                } else {
                    look = previewChoice == 1 ? config.mineLook : previewChoice == 2 ? config.bossLook : config.progressLook;
                    lines = previewChoice == 1 ? p.mine() : previewChoice == 2 ? p.bosses() : p.progress();
                }
                shown.add(new Shown(paint(lines, look), 0, 0));
                scale = config.effectiveScale(look);
            } else {
                // Mine on the left, bosses over bars on the right.
                PanelPainter.Painted mine = paint(p.mine(), config.mineLook);
                PanelPainter.Painted boss = paint(p.bosses(), config.bossLook);
                PanelPainter.Painted bars = paint(p.progress(), config.progressLook);
                int gap = 6;
                shown.add(new Shown(mine, 0, 0));
                shown.add(new Shown(boss, mine.width() + gap, 0));
                shown.add(new Shown(bars, mine.width() + gap, boss.height() + gap));
                scale = config.hudScale;
            }
            int totalW = 1;
            int totalH = 1;
            for (Shown s : shown) {
                totalW = Math.max(totalW, s.dx() + s.painted().width());
                totalH = Math.max(totalH, s.dy() + s.painted().height());
            }
            double fit = Math.min((w - 12) / (double) totalW, (h - 20) / (double) totalH);
            double used = Math.max(0.1, Math.min(scale, fit));
            for (Shown s : shown) {
                HudRenderer.render(context, s.painted(), (float) (x + 6 + s.dx() * used), (float) (y + 6 + s.dy() * used), used);
            }
            String caption = used >= scale - 1e-6 ? "Example data, real size"
                    : "Example data, " + Math.round(used / scale * 100) + "% size to fit";
            context.fill(x, y + h - 12, x + w, y + h, 0xA0000000);
            context.text(font, trim(caption, w - 6), x + 3, y + h - 10, UI_MUTED, false);
        } finally {
            context.disableScissor();
        }
    }

    private PanelPainter.Painted paint(List<HudModel.Line> lines, ModConfig.Look look) {
        return HudRenderer.paint(this.font, lines, HudRenderer.Look.of(config, look));
    }

    /** Blocks in a few greens, a fixed pattern so it does not flicker. */
    private static void wall(GuiGraphicsExtractor context, int x, int y, int w, int h) {
        int tile = 16;
        int[] shades = { 0xFF2F625C, 0xFF336A63, 0xFF2B5A54, 0xFF376E66, 0xFF2D5F59 };
        for (int ty = 0; ty < h; ty += tile) {
            for (int tx = 0; tx < w; tx += tile) {
                int hash = (tx / tile * 73856093) ^ (ty / tile * 19349663);
                int x1 = x + tx;
                int y1 = y + ty;
                int x2 = Math.min(x + w, x1 + tile);
                int y2 = Math.min(y + h, y1 + tile);
                context.fill(x1, y1, x2, y2, shades[Math.floorMod(hash, shades.length)]);
                context.fill(x1, y1, x2, y1 + 1, 0x30000000);
                context.fill(x1, y1 + 1, x1 + 1, y2, 0x30000000);
            }
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) {
            return true;
        }
        if (click.button() != 0 || !lookTab()) {
            return false;
        }
        double mx = click.x();
        double my = click.y();
        if (listScrolls() && mx >= listX + listWidth - 8 && mx < listX + listWidth && my >= listTop && my < listBottom) {
            draggingScrollbar = true;
            scrollTo(my);
            return true;
        }
        // A copy: a click can rebuild the controls (picking a tab, a colour, a panel).
        for (Control c : List.copyOf(controls)) {
            if (c.visible() && c.contains(mx, my)) {
                return c.click(mx, my);
            }
        }
        return false;
    }

    private void scrollTo(double my) {
        int[] bar = scrollbar();
        int thumb = bar[1] - bar[0];
        int track = listBottom - listTop - 4 - thumb;
        double f = (my - listTop - 2 - thumb / 2.0) / Math.max(1, track);
        scroll = (int) Math.round(Math.max(0, Math.min(1, f)) * (listHeight - (listBottom - listTop)));
        clampScroll();
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (draggingScrollbar) {
            scrollTo(click.y());
            return true;
        }
        if (dragging != null) {
            dragging.drag(click.x(), click.y());
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        if (dragging != null) {
            Control done = dragging;
            dragging = null;
            // Saved once on letting go rather than on every pixel of the drag.
            done.changed();
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (listScrolls() && mouseX >= listX && mouseX < listX + listWidth && mouseY >= listTop
                && mouseY < listBottom && verticalAmount != 0.0) {
            scroll -= (int) Math.round(verticalAmount * 24);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    /**
     * Clicks one option of a look-tab control, found by its title, through the same mouse handling
     * a player's click takes, scrolling the list to it first. For the in-game test.
     */
    /** The Panels tab's panel by name, as clicking it in the list does. For the game test. */
    public boolean selectPanel(String name) {
        List<PanelEntry> entries = panelEntries();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).name().equals(name)) {
                panelIndex = i;
                rebuildWidgets();
                return true;
            }
        }
        return false;
    }

    public boolean clickOption(String title, String option) {
        for (Control c : List.copyOf(controls)) {
            if (!(c instanceof Segments s) || !s.title.equals(title)) {
                continue;
            }
            for (int i = 0; i < s.options.length; i++) {
                if (!s.options[i].equals(option)) {
                    continue;
                }
                if (s.scrolls) {
                    scroll = s.y - listTop - 4;
                    clampScroll();
                }
                int[] b = s.boxes[i];
                MouseButtonEvent click = new MouseButtonEvent(s.x + b[0] + b[2] / 2.0, s.top() + b[1] + 7,
                        new MouseButtonInfo(0, 0));
                boolean used = mouseClicked(click, false);
                mouseReleased(click);
                return used;
            }
        }
        return false;
    }

    @Override
    public void onClose() {
        onChange.run();
        if (this.minecraft != null) {
            Compat.setScreen(this.minecraft, parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
