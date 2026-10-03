package minerefinehud.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import minerefinehud.boss.BossAlerts;
import minerefinehud.boss.BossMessageParser;
import minerefinehud.boss.BossStore;
import minerefinehud.boss.BossTracker;
import minerefinehud.boss.BossWorlds;
import minerefinehud.data.ProgressionData;
import minerefinehud.hud.HudLayout;
import minerefinehud.hud.HudModel;
import minerefinehud.hud.Theme;
import minerefinehud.mine.Mine;
import minerefinehud.mine.MineDetector;
import minerefinehud.mine.MiningBlocks;
import minerefinehud.mine.ResourcePickups;
import minerefinehud.progress.ActionBarReader;
import minerefinehud.progress.MineCosts;
import minerefinehud.progress.ProgressPlanner;
import minerefinehud.progress.ProgressSlot;
import minerefinehud.progress.ProgressionLinks;
import minerefinehud.progress.ResourceBalances;
import minerefinehud.shop.PriceLedger;
import minerefinehud.shop.PriceStore;
import minerefinehud.shop.ShopItemParser;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Entry point. Wires the tested core up to the game and does as little thinking as possible.
 */
public final class MinerefineHudClient implements ClientModInitializer {

    public static final String MOD_ID = "minerefine-hud";

    private static final Identifier HUD_LAYER = Identifier.of(MOD_ID, "overlay");

    private final BossTracker bossTracker = new BossTracker();
    private final PriceLedger prices = new PriceLedger();

    /** Mine order read from shop prerequisites, for dimensions newer than the bundled data. */
    private final ProgressionLinks links = new ProgressionLinks();

    private ModConfig config;
    private ProgressionData catalogSource;
    private BossStore bossStore;
    private PriceStore priceStore;
    private Path configFile;

    /** Shop items with a cost the parser could not read, written here for a parser fix. */
    private Path unrecognisedFile;
    private final java.util.Set<String> unrecognisedSeen = new java.util.HashSet<>();
    private boolean toldAboutUnrecognised;

    private KeyBinding keyPosition;
    private KeyBinding keyToggle;
    private KeyBinding keySettings;
    private KeyBinding keyTurret;

    /** Set by /mrhud turret, opened next tick for the same reason as the settings. */
    private boolean openTurretNextTick;

    /** Set by /mrhud config. Opened next tick, after the chat screen the command came from closes. */
    private boolean openSettingsNextTick;

    /** Which world each boss belongs to, for showing only this dimension's timers. */
    private final BossWorlds bossWorlds = new BossWorlds();
    private final BossAlerts bossAlerts = new BossAlerts();

    /** The dimension the player is in, from what they are mining. Empty until something is mined. */
    private Optional<String> currentWorld = Optional.empty();

    /** The gear slot the player is currently holding, if it is a MineRefine item. */
    private Optional<ShopItemParser.GearRef> heldGear = Optional.empty();
    private long lastShopScan;

    /** The last few distinct action bar lines, for /mrhud debug. */
    private final java.util.Deque<String> recentActionBars = new java.util.ArrayDeque<>();
    private Optional<ActionBarReader.Reading> lastActionBarReading = Optional.empty();
    private Optional<String> lastActionBarCurrency = Optional.empty();

    /** Which block belongs to which mine. Learned mappings are saved in config.json. */
    private final MiningBlocks miningBlocks = new MiningBlocks();

    /** Resource items gained, which name the mine being mined outright. */
    private final ResourcePickups resourcePickups = new ResourcePickups();
    private Optional<String> lastPickup = Optional.empty();
    private long lastPickupAt;
    /** The block icon on screen when the last resource was picked up. */
    private Optional<String> lastPickupSprite = Optional.empty();
    /** What the last mining total replaced, so it can be put back if a pickup proves it misfiled. */
    private Optional<ResourceBalances.Reading> balanceBeforeLastReading = Optional.empty();
    private long lastActionBarAt;

    /**
     * The mine the player was last seen mining, from a resource pickup or a known block, whichever
     * happened most recently. The main detection source: this server's sidebar never names it.
     */
    private Optional<String> lastMinedMine = Optional.empty();

    /** When a block nobody has placed was last mined, or 0 while mining known blocks. */
    private long lastUnknownBlockAt;

    /** A pickup and an action bar line this close together came from the same block. */
    private static final long SAME_BLOCK_MS = 3_000L;

    private final ResourceBalances balances = new ResourceBalances();

    /** Recomputed twice a second from the inventory, so they follow purchases by themselves. */
    private List<ProgressPlanner.ProgressView> progressViews = List.of();
    private long lastProgressCheck;

    /** Mine named by the shop most recently opened. A fallback when nothing else names one. */
    private Optional<String> lastShopMine = Optional.empty();
    private long lastShopAt;

    /** Recomputed on a timer rather than every frame: the sidebar barely changes. */
    private Optional<Mine> currentMine = Optional.empty();
    private MineDetector.Source currentMineSource = MineDetector.Source.NONE;
    private long lastMineCheck;

    /** The mine panel's figures, recomputed with the mine rather than every frame. */
    private Optional<MineCosts.View> mineCosts = Optional.empty();

    @Override
    public void onInitializeClient() {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
        configFile = dir.resolve("config.json");
        unrecognisedFile = dir.resolve("unrecognised-shop-items.txt");

        config = ModConfig.load(configFile);
        config.save(configFile);
        miningBlocks.importShared(config.sharedBlocks);
        miningBlocks.importLearned(config.minedBlocks);

        links.importAll(config.learnedProgression);
        bossWorlds.importAll(config.bossWorlds);

        catalogSource = new ProgressionData(dir.resolve("progression.json"));
        catalogSource.load();

        bossStore = new BossStore(dir.resolve("bosses.json"));
        bossStore.load(bossTracker);

        priceStore = new PriceStore(dir.resolve("prices.json"));
        priceStore.load(prices);

        registerKeyBindings();
        ActionBarHook.listen(this::onActionBar);
        registerChatHooks();
        registerConnectionHooks();
        registerShopLearning();
        registerProgressTracking();
        registerBossReminder();
        registerDebugCommand();
        registerHud();
    }

    // --------------------------------------------------------------- keybinds

    /**
     * F6 for the move screen; the toggle and settings keys ship unbound.
     *
     * F6 is free in vanilla and is a reasonable default for the screen people will actually want
     * to find, and it links to the settings. The others ship unbound on purpose: a mod that
     * silently claims more keys is the kind of thing that breaks someone's muscle memory without
     * explaining itself. All are rebindable in Options, Controls under "MineRefine HUD".
     */
    private void registerKeyBindings() {
        // VERSION SENSITIVE. Since 1.21.9 a category is an Identifier-backed object rather than a
        // translation key string. Its label is looked up as key.category.<namespace>.<path>,
        // which is why en_us.json uses key.category.minerefine-hud.main.
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));

        keyPosition = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.minerefine-hud.position",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F6,
                category));

        keyToggle = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.minerefine-hud.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));

        keySettings = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.minerefine-hud.settings",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));

        keyTurret = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.minerefine-hud.turret",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (keyTurret.wasPressed()) {
                client.setScreen(new TurretScreen(null));
            }
            if (openTurretNextTick && client.currentScreen == null) {
                openTurretNextTick = false;
                client.setScreen(new TurretScreen(null));
            }
            while (keyPosition.wasPressed()) {
                openPositionScreen(client);
            }
            while (keyToggle.wasPressed()) {
                config.enabled = !config.enabled;
                config.save(configFile);
            }
            while (keySettings.wasPressed()) {
                openSettings(client);
            }
            if (openSettingsNextTick && client.currentScreen == null) {
                openSettingsNextTick = false;
                openSettings(client);
            }
        });
    }

    private void openSettings(MinecraftClient client) {
        if (client == null) {
            return;
        }
        client.setScreen(new SettingsScreen(null, config, this::settingsChanged,
                () -> openPositionScreen(client)));
    }

    /** Settings apply live: the HUD reads the config every frame. This saves and re-plans now. */
    private void settingsChanged() {
        config.save(configFile);
        lastProgressCheck = 0L;
        lastMineCheck = 0L;
    }

    /**
     * Opens the drag screen with the live panel contents as the previews, so what gets
     * positioned is the real panel at its real size. An empty panel gets a stand-in, otherwise
     * the boss panel could not be placed until a boss had been seen.
     */
    private void openPositionScreen(MinecraftClient client) {
        if (client == null) {
            return;
        }
        long now = System.currentTimeMillis();
        HudModel.Options options = config.toOptions();

        List<HudModel.Line> minePreview = HudModel.costPanel(mineCosts, upgradeView(), options);
        if (minePreview.isEmpty()) {
            minePreview = List.of(HudModel.Line.of("Mine costs", HudModel.Style.HEADER),
                    HudModel.Line.of("nothing to show yet", HudModel.Style.DIM));
        }

        List<HudModel.Line> bossPreview = HudModel.bossPanel(visibleBosses(now), options);
        if (bossPreview.isEmpty()) {
            bossPreview = List.of(HudModel.Line.of("Bosses", HudModel.Style.HEADER),
                    HudModel.Line.of("no timers yet", HudModel.Style.DIM));
        }

        List<HudModel.Line> progressPreview = HudModel.progressPanel(planAllProgress(), options.progressLines());
        if (progressPreview.isEmpty()) {
            progressPreview = List.of(HudModel.Line.of("Progress bars", HudModel.Style.HEADER),
                    HudModel.Line.of("add one in Settings", HudModel.Style.DIM));
        }

        List<HudModel.Line> alertPreview = HudModel.alertPanel(List.of("Angry Archaeologist"));

        List<HudPositionScreen.Panel> panels = List.of(
                new HudPositionScreen.Panel("Mine", minePreview,
                        config.minePlacement(), ModConfig.DEFAULT_MINE_PLACEMENT,
                        config.mineLook.scale, 1.0),
                new HudPositionScreen.Panel("Bosses", bossPreview,
                        config.bossPlacement(), ModConfig.DEFAULT_BOSS_PLACEMENT,
                        config.bossLook.scale, 1.0),
                new HudPositionScreen.Panel("Progress", progressPreview,
                        config.progressPlacement(), ModConfig.DEFAULT_PROGRESS_PLACEMENT,
                        config.progressLook.scale, 1.0),
                new HudPositionScreen.Panel("Respawn reminder", alertPreview,
                        config.alertPlacement(), ModConfig.DEFAULT_ALERT_PLACEMENT,
                        config.alertLook.scale, 2.0));

        client.setScreen(new HudPositionScreen(null, panels, config.theme(),
                saved -> {
                    config.setMinePlacement(saved.placements().get(0));
                    config.setBossPlacement(saved.placements().get(1));
                    config.setProgressPlacement(saved.placements().get(2));
                    config.setAlertPlacement(saved.placements().get(3));
                    config.mineLook.scale = saved.scales().get(0);
                    config.bossLook.scale = saved.scales().get(1);
                    config.progressLook.scale = saved.scales().get(2);
                    config.alertLook.scale = saved.scales().get(3);
                    config.save(configFile);
                },
                () -> openSettings(client)));
    }

    // ------------------------------------------------------------------ hooks

    private void registerChatHooks() {
        // Boss broadcasts are server system messages, not player chat, so GAME is the right
        // event. Using the chat event instead would miss them entirely and would also let a
        // player spoof a boss timer by typing the message.
        ClientReceiveMessageEvents.GAME.register((Text message, boolean overlay) -> {
            String text = message.getString();

            if (overlay) {
                // Older servers still send the action bar as a flagged chat message. Most send
                // it as its own packet, which only the InGameHud mixin sees.
                onActionBar(text);
                return;
            }

            BossMessageParser.parse(text).ifPresent(event -> {
                long now = System.currentTimeMillis();
                switch (event.kind()) {
                    case SPAWNED -> bossTracker.onSpawned(event.bossName(), now);
                    case SLAIN -> bossTracker.onSlain(event.bossName(), now);
                }
                bossStore.save(bossTracker);

                // Broadcasts only reach players in that area, so this is the boss's world.
                if (bossWorlds.learn(event.bossName(), currentWorld, catalogSource.catalog())) {
                    config.bossWorlds = new java.util.LinkedHashMap<>(bossWorlds.export());
                    config.save(configFile);
                }
            });
        });
    }

    private void registerConnectionHooks() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            // Anything measured across a join is untrustworthy, so stop any pending measurement.
            bossTracker.onContinuityBreak();
            currentMine = Optional.empty();
            currentMineSource = MineDetector.Source.NONE;
            lastShopMine = Optional.empty();
            lastShopAt = 0L;
            lastMinedMine = Optional.empty();
            lastPickup = Optional.empty();
            lastPickupSprite = Optional.empty();
            currentWorld = Optional.empty();
            lastUnknownBlockAt = 0L;
            resourcePickups.reset();
            // Cheap, and lets a replaced progression.json in the config folder apply on rejoin.
            catalogSource.load();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            bossTracker.onContinuityBreak();
            bossStore.save(bossTracker);
            priceStore.save(prices);
        });
    }

    /**
     * Learns prices from any shop menu the player opens.
     *
     * Entirely passive. Nothing is opened, clicked or hovered: the client already holds the
     * contents of a screen the player opened, so this only reads what is on their screen anyway.
     * Throttled because a container's contents arrive over a few ticks after it opens, so one
     * scan on open would miss most of it.
     */
    private void registerShopLearning() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client == null || client.player == null) {
                return;
            }

            long now = System.currentTimeMillis();

            // Read the gear in hand, which gives the level the player already owns.
            try {
                String held = client.player.getMainHandStack().getName().getString();
                Optional<ShopItemParser.GearRef> ref = ShopItemParser.parseTitle(held);
                if (ref.isPresent()) {
                    heldGear = ref;
                }
            } catch (Exception ignored) {
                // Not worth a crash; the HUD simply omits the upgrade line.
            }

            if (client.currentScreen == null) {
                return;
            }

            if (now - lastShopScan < 500L) {
                return;
            }
            lastShopScan = now;

            List<ShopItemParser.ItemView> items = ShopScanner.visibleItems(client);
            List<ShopItemParser.Entry> entries = ShopItemParser.parseAll(items);
            recordUnrecognised(client, ShopItemParser.unrecognised(items));
            ShopItemParser.balances(items).forEach((currency, amount) -> {
                balances.update(currency, amount, now);
                if (miningBlocks.onShopBalance(currency, amount, now)) {
                    saveLearnedBlocks();
                }
            });
            boolean pricesChanged = false;
            for (ShopItemParser.Entry entry : entries) {
                if (prices.record(entry, now) != PriceLedger.Result.UNCHANGED) {
                    pricesChanged = true;
                }
            }
            // Saved the moment something new is read, not on close or disconnect: a crash with
            // the shop still open must not lose what was just learned.
            if (pricesChanged) {
                priceStore.save(prices);
                lastMineCheck = 0L;   // show the new figures in the mine panel straight away
            }

            boolean linksChanged = false;
            for (ShopItemParser.Link link : ShopItemParser.links(items)) {
                linksChanged |= links.learn(link.mine(), link.gear(), link.previousMine());
            }
            if (linksChanged) {
                config.learnedProgression = new java.util.LinkedHashMap<>(links.export());
                config.save(configFile);
            }

            // Only priced rows count: the player's own inventory is part of the same screen.
            MineDetector.mostCommon(entries.stream().map(ShopItemParser.Entry::mine).toList())
                    .ifPresent(mine -> {
                        lastShopMine = Optional.of(mine);
                        lastShopAt = now;
                        lastMineCheck = 0L;   // re-detect as soon as the HUD is visible again
                    });

            // A price that moved is worth saying out loud: it usually means a balance patch, and
            // silently swapping the number would leave the player trusting a figure they never
            // saw change.
            for (PriceLedger.PriceChange change : prices.drainChanges()) {
                client.player.sendMessage(Text.literal(
                        "[MineRefine] " + change.mine() + " " + change.gear() + " "
                        + minerefinehud.shop.RomanNumerals.toRoman(change.level())
                        + ": " + minerefinehud.shop.Amounts.format(change.from())
                        + " -> " + minerefinehud.shop.Amounts.format(change.to())), false);
            }
        });
    }

    /**
     * Appends shop items that carry a cost but could not be read, each once per session, and says
     * so in chat the first time. A shape the parser has never seen, charms for instance, would
     * otherwise be skipped without a trace and its prices never learned.
     */
    private void recordUnrecognised(MinecraftClient client, List<ShopItemParser.ItemView> unknown) {
        StringBuilder out = new StringBuilder();
        for (ShopItemParser.ItemView item : unknown) {
            if (!unrecognisedSeen.add(item.title())) {
                continue;
            }
            out.append(revealHidden(item.title())).append('\n');
            for (String line : item.lore()) {
                out.append("    | ").append(revealHidden(line == null ? "" : line)).append('\n');
            }
            out.append('\n');
        }
        if (out.isEmpty()) {
            return;
        }
        try {
            java.nio.file.Files.createDirectories(unrecognisedFile.getParent());
            java.nio.file.Files.writeString(unrecognisedFile, out.toString(), java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {
            return;
        }
        if (!toldAboutUnrecognised && client.player != null) {
            toldAboutUnrecognised = true;
            client.player.sendMessage(Text.literal("[MineRefine] Some shop items could not be read. Saved to "
                    + "config/minerefine-hud/unrecognised-shop-items.txt so the parser can be fixed."), false);
        }
    }

    /** Builds the upgrade panel from the gear in hand and whatever prices have been learned. */
    private Optional<HudModel.UpgradeView> upgradeView() {
        if (heldGear.isEmpty()) {
            return Optional.empty();
        }
        ShopItemParser.GearRef g = heldGear.get();
        // Tier counts vary by item; the spreadsheet total is how the ledger tells them apart.
        int max = Math.max(g.level(), prices.maxLevel(g.mine(), g.gear(),
                catalogSource.catalog().pieceTotal(g.mine(), g.gear())));

        var next = g.level() >= max ? Optional.<PriceLedger.Price>empty()
                : prices.nextUpgrade(g.mine(), g.gear(), g.level());
        return Optional.of(new HudModel.UpgradeView(
                g.mine(),
                g.gear(),
                g.level(),
                max,
                next.map(p -> java.util.OptionalLong.of(p.amount())).orElse(java.util.OptionalLong.empty()),
                prices.remainingCost(g.mine(), g.gear(), g.level(), max),
                next.map(p -> p.source() == PriceLedger.Source.OBSERVED).orElse(false)));
    }

    /**
     * VERSION SENSITIVE. Fabric API has moved HUD registration twice: HudRenderCallback, then
     * HudLayerRegistrationCallback, and on 1.21.11 a static HudElementRegistry. HudRenderCallback
     * still exists but is deprecated, so this uses the registry, which keeps the overlay anchored
     * to a named vanilla element instead of drawing at an arbitrary point in the frame.
     */
    private void registerHud() {
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.MISC_OVERLAYS,
                HUD_LAYER,
                (context, tickCounter) -> renderHud(context));
    }

    // ----------------------------------------------------------------- render

    private void renderHud(net.minecraft.client.gui.DrawContext context) {
        if (!config.enabled) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.options.hudHidden) {
            return;
        }
        if (client.currentScreen != null
                && !(client.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen)) {
            // The position screen draws its own copy of the panel, so suppress this one.
            return;
        }

        long now = System.currentTimeMillis();
        refreshCurrentMine(client, now);

        HudModel.Options options = config.toOptions();
        var theme = config.theme();
        List<BossTracker.BossView> bosses = visibleBosses(now);

        drawPanel(context, client, HudModel.costPanel(mineCosts, upgradeView(), options),
                config.minePlacement(), config.mineLook, theme);
        drawPanel(context, client, HudModel.bossPanel(bosses, options),
                config.bossPlacement(), config.bossLook, theme);
        drawPanel(context, client, HudModel.progressPanel(progressViews, options.progressLines()),
                config.progressPlacement(), config.progressLook, theme);

        if (config.bossReminder) {
            List<String> due = BossAlerts.active(bosses, reminderLeadMs()).stream()
                    .map(BossTracker.BossView::displayName).toList();
            drawPanel(context, client, HudModel.alertPanel(due),
                    config.alertPlacement(), config.alertLook, theme);
        }
    }

    private static void drawPanel(net.minecraft.client.gui.DrawContext context, MinecraftClient client,
                                  List<HudModel.Line> lines, HudLayout.Placement placement,
                                  ModConfig.Look look, Theme theme) {
        if (lines.isEmpty()) {
            return;
        }
        HudLayout.Rect rect = HudRenderer.rect(client.textRenderer, lines, placement, look.scale,
                context.getScaledWindowWidth(), context.getScaledWindowHeight());
        int background = look.background
                ? Theme.background(look.opacity) : 0;
        HudRenderer.render(context, lines, rect, look.scale, background, theme);
    }

    /** Timers for the dimension the player is in, or every dimension if they chose that. */
    private List<BossTracker.BossView> visibleBosses(long now) {
        return bossWorlds.filter(bossTracker.views(now), currentWorld, catalogSource.catalog(),
                config.showAllBossWorlds);
    }

    private long reminderLeadMs() {
        return config.bossReminderSeconds * 1000L;
    }

    /**
     * Plays the reminder sound once per respawn. A tick handler rather than the HUD, so it still
     * sounds with the inventory or a shop open, when the HUD is not drawn.
     */
    private void registerBossReminder() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client == null || client.player == null || !config.bossReminder) {
                return;
            }
            long now = System.currentTimeMillis();
            List<BossTracker.BossView> rung = bossAlerts.newlyDue(visibleBosses(now), now, reminderLeadMs());
            if (!rung.isEmpty() && config.bossReminderSound && config.bossReminderVolume > 0) {
                playReminderSound(client);
            }
        });
    }

    /**
     * VERSION SENSITIVE. A UI sound through the sound manager, so it plays at full volume wherever
     * the player stands and is not heard by anyone else, unlike a sound played at the player.
     */
    private void playReminderSound(MinecraftClient client) {
        try {
            float volume = config.bossReminderVolume / 100f;
            client.getSoundManager().play(PositionedSoundInstance.ui(
                    SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0f, volume));
        } catch (Exception | NoSuchMethodError | NoClassDefFoundError ignored) {
            // A missing sound must not cost the title, which is drawn separately.
        }
    }

    /**
     * Works out which mine the player is in. Throttled to twice a second: this is string
     * scanning, not something that belongs in a frame callback. MineDetector decides which
     * source wins; this only gathers them.
     *
     * Runs even before the price data has loaded, because the held item and the shop can still
     * name the mine, and a name with no prices is better than "unknown".
     */
    /** How long after the last unknown block the "not one of the known mines" rule holds. */
    private static final long UNKNOWN_BLOCK_MS = 15_000L;

    /**
     * The mine of the tool in hand right now, if it is a MineRefine pickaxe, axe or shovel. A
     * sword or a chestplate says nothing about where the player is, and the remembered heldGear
     * keeps the last gear ever held, which is no evidence at all.
     */
    private static Optional<String> toolInHand(MinecraftClient client) {
        try {
            if (client == null || client.player == null) {
                return Optional.empty();
            }
            return ShopItemParser.parseTitle(client.player.getMainHandStack().getName().getString())
                    .filter(g -> switch (g.gear()) {
                        case "pickaxe", "axe", "shovel" -> true;
                        default -> false;
                    })
                    .map(ShopItemParser.GearRef::mine);
        } catch (Exception | NoSuchMethodError | NoClassDefFoundError ignored) {
            return Optional.empty();
        }
    }

    private void refreshCurrentMine(MinecraftClient client, long now) {
        if (now - lastMineCheck < 500L) {
            return;
        }
        lastMineCheck = now;

        // Mining a block nobody has placed rules out every mine whose block is known.
        boolean miningUnknown = lastUnknownBlockAt > 0L && now - lastUnknownBlockAt <= UNKNOWN_BLOCK_MS;
        java.util.Set<String> ruledOut = miningUnknown
                ? new java.util.HashSet<>(miningBlocks.learned().values()) : java.util.Set.of();

        MineDetector.Detection found = MineDetector.detect(
                catalogSource.catalog(),
                MineDetector.stillMining(lastMinedMine, lastActionBarAt, lastShopAt),
                toolInHand(client),
                lastShopMine,
                ruledOut);
        if (found.found()) {
            currentMine = found.mine();
            currentMineSource = found.source();
        } else if (miningUnknown) {
            // Better to say "unknown" than to keep showing a mine the player has provably left.
            currentMine = Optional.empty();
            currentMineSource = MineDetector.Source.NONE;
        }
        // Otherwise no match keeps the previous mine rather than flickering on one bad frame.

        var catalog = catalogSource.catalog();
        mineCosts = currentMine.map(m -> MineCosts.of(catalog.serverName(m.name()), Optional.of(m),
                prices, config.showArmorPieces));
        currentWorld = BossWorlds.currentWorld(currentMine, resourcePickups.lastWorldTag());
    }

    // ------------------------------------------------------------- action bar

    /**
     * Every action bar line, from the mixin. Reads the resource total out of the mining line so
     * the progress bar moves while mining instead of only when a shop is opened, and re-plans
     * straight away rather than waiting for the next half-second check, because this is the
     * moment the number actually changed. The line is also kept as mine-detection text, in case
     * the server ever names the mine there.
     */
    private void onActionBar(String text) {
        if (text == null || text.isBlank()) {
            return;
        }

        if (!text.equals(recentActionBars.peekLast())) {
            recentActionBars.addLast(text);
            while (recentActionBars.size() > 5) {
                recentActionBars.removeFirst();
            }
        }

        Optional<ActionBarReader.Reading> reading = ActionBarReader.read(text);
        if (reading.isEmpty()) {
            return;
        }

        var catalog = catalogSource.catalog();
        long now = System.currentTimeMillis();
        ActionBarReader.Reading r = reading.get();

        if (miningBlocks.onMining(r.sprite(), r.amount(), now)) {
            saveLearnedBlocks();
        }
        lastActionBarAt = now;
        if (lastPickup.isPresent() && now - lastPickupAt <= SAME_BLOCK_MS
                && miningBlocks.teach(r.sprite(), lastPickup.get())) {
            saveLearnedBlocks();
        }

        Optional<MiningBlocks.Match> blockMine = miningBlocks.lookup(r.sprite(), catalog);
        boolean shared = miningBlocks.isShared(r.sprite());
        if (blockMine.isEmpty() && !shared) {
            // Mined resources are a balance here, not items, so pickups rarely teach a block.
            // A balance already known for this session usually can.
            Map<String, Long> known = new java.util.HashMap<>();
            balances.all().forEach((c, seen) -> known.put(c, seen.amount()));
            if (miningBlocks.inferFromBalances(r.sprite(), r.amount(), known)) {
                saveLearnedBlocks();
                blockMine = miningBlocks.lookup(r.sprite(), catalog);
            }
        }
        boolean pickupNow = lastPickup.isPresent() && now - lastPickupAt <= SAME_BLOCK_MS;
        // The resource picked up under this icon names the mine, over what the icon is known as.
        Optional<String> mined = MineDetector.minedMine(blockMine.map(MiningBlocks.Match::mine),
                pickupNow && lastPickupSprite.equals(Optional.of(r.sprite())) ? lastPickup : Optional.empty());
        // A shared icon is not unknown: it just cannot say which of its mines this is, so the
        // mine last named by a pickup stays rather than being dropped for the tool in hand.
        lastUnknownBlockAt = mined.isEmpty() && !shared ? now : 0L;
        lastMinedMine = MineDetector.afterMining(lastMinedMine, mined, pickupNow || shared);
        lastMineCheck = 0L;   // re-detect now: what is being mined just changed or was confirmed

        Optional<String> currency = ActionBarReader.currencyFor(mined, pickupNow ? lastPickup : Optional.empty());

        lastActionBarReading = reading;
        lastActionBarCurrency = currency;
        balanceBeforeLastReading = currency.flatMap(balances::get);
        currency.ifPresent(c -> {
            balances.update(c, r.amount(), now);
            lastProgressCheck = 0L;
        });
    }

    private void saveLearnedBlocks() {
        config.minedBlocks = new java.util.LinkedHashMap<>(miningBlocks.learned());
        config.sharedBlocks = new java.util.ArrayList<>(new java.util.TreeSet<>(miningBlocks.shared()));
        config.save(configFile);
    }

    // --------------------------------------------------------------- progress

    /**
     * Re-plans the progress bar twice a second from the inventory. Nothing is remembered between
     * runs, which is what makes buying a tier, maxing a piece and moving mine all just work.
     */
    private void registerProgressTracking() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client == null || client.player == null) {
                return;
            }
            long now = System.currentTimeMillis();
            if (now - lastProgressCheck < 500L) {
                return;
            }
            lastProgressCheck = now;
            scanPickups(client, now);
            progressViews = planAllProgress();
        });
    }

    /**
     * Checks which resource item just went up in the inventory. That names the mine being mined
     * directly, and if the action bar showed a block at the same moment, that block is taught to
     * MiningBlocks so the mine is known from the block alone next time, even with a full inventory.
     */
    private void scanPickups(MinecraftClient client, long now) {
        var catalog = catalogSource.catalog();
        Optional<String> gained = resourcePickups.observe(inventoryItems(client), catalog);
        // Still observed above, so the inventory baseline stays current, but a gain while not
        // mining (compressing, a backpack, a sale) says nothing about where the player is.
        if (gained.isEmpty() || !ResourcePickups.namesTheMine(now, lastActionBarAt, SAME_BLOCK_MS)) {
            return;
        }
        String resource = gained.get();
        lastPickup = gained;
        lastPickupAt = now;
        lastPickupSprite = lastActionBarReading.map(ActionBarReader.Reading::sprite);
        lastMinedMine = gained;
        lastMineCheck = 0L;

        if (lastActionBarReading.isEmpty()) {
            return;
        }
        ActionBarReader.Reading r = lastActionBarReading.get();
        if (miningBlocks.onPickup(r.sprite(), resource, catalog, now)) {
            saveLearnedBlocks();
        }
        // The total on screen is this resource's. If the icon filed it under another mine, put
        // that mine's balance back and file it here, or that mine's bar shows this one's total.
        boolean misfiled = lastActionBarCurrency
                .filter(c -> !catalog.serverName(c).equalsIgnoreCase(catalog.serverName(resource)))
                .isPresent();
        if (misfiled) {
            balances.restore(lastActionBarCurrency.get(), balanceBeforeLastReading);
        }
        if (misfiled || lastActionBarCurrency.isEmpty()) {
            balances.update(resource, r.amount(), lastActionBarAt);
            lastActionBarCurrency = gained;
            balanceBeforeLastReading = Optional.empty();
            lastProgressCheck = 0L;
        }
    }


    private static List<ResourcePickups.Item> inventoryItems(MinecraftClient client) {
        List<ResourcePickups.Item> out = new java.util.ArrayList<>();
        try {
            if (client == null || client.player == null) {
                return out;
            }
            for (ItemStack stack : client.player.getInventory().getMainStacks()) {
                if (stack != null && !stack.isEmpty()) {
                    out.add(new ResourcePickups.Item(stack.getName().getString(),
                            ShopScanner.lore(stack), stack.getCount()));
                }
            }
        } catch (Exception | NoSuchMethodError | NoClassDefFoundError ignored) {
            // A mapping mismatch should cost pickup detection, not the client.
        }
        return out;
    }

    /** One view per configured bar, each already multiplied by its quantity. */
    private List<ProgressPlanner.ProgressView> planAllProgress() {
        List<ProgressPlanner.ProgressView> out = new java.util.ArrayList<>();
        for (ModConfig.Bar bar : config.progressBars) {
            // A Total bar ignores any amount left over from when it tracked a single piece.
            bar.choice().ifPresent(slot -> out.add(planProgress(slot)
                    .withQuantity(slot.isTotal() ? 1 : bar.quantity)));
        }
        return out;
    }

    private ProgressPlanner.ProgressView planProgress(ProgressSlot slot) {
        return ProgressPlanner.plan(slot, ownedGear(MinecraftClient.getInstance()),
                catalogSource.catalog(), links, currentMine, prices, balances,
                config.progressToMax ? ProgressPlanner.Goal.TO_MAX : ProgressPlanner.Goal.NEXT_TIER);
    }

    /**
     * Every MineRefine item the player has on them: main inventory and hotbar, the four armour
     * slots and the off hand. Armour is read from the equipment slots because since 1.21.5 it is
     * no longer part of the inventory's main list.
     */
    private static List<ShopItemParser.GearRef> ownedGear(MinecraftClient client) {
        List<ShopItemParser.GearRef> out = new java.util.ArrayList<>();
        try {
            if (client == null || client.player == null) {
                return out;
            }
            var inventory = client.player.getInventory();
            List<ItemStack> stacks = new java.util.ArrayList<>(inventory.getMainStacks());
            for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND)) {
                stacks.add(client.player.getEquippedStack(slot));
            }
            for (ItemStack stack : stacks) {
                if (stack != null && !stack.isEmpty()) {
                    ShopItemParser.parseTitle(stack.getName().getString()).ifPresent(out::add);
                }
            }
        } catch (Exception | NoSuchMethodError | NoClassDefFoundError ignored) {
            // A mapping mismatch should cost the progress bar, not the client.
        }
        return out;
    }

    // ------------------------------------------------------------------ debug

    /**
     * /mrhud debug prints everything mine detection can see.
     *
     * Detection depends on what this particular server puts in its sidebar, which cannot be
     * known from here. When it says "unknown", this output is what is needed to fix it. Named
     * mrhud rather than minerefine because a client command shadows a server command of the
     * same name.
     */
    private void registerDebugCommand() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("mrhud")
                        .then(ClientCommandManager.literal("debug").executes(ctx -> {
                            for (String line : debugReport(MinecraftClient.getInstance())) {
                                ctx.getSource().sendFeedback(Text.literal(line));
                            }
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("config").executes(ctx -> {
                            openSettingsNextTick = true;
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("turret").executes(ctx -> {
                            openTurretNextTick = true;
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("forget").executes(ctx -> {
                            ctx.getSource().sendFeedback(Text.literal(forgetCurrentBlock()));
                            return 1;
                        }))));
    }

    /**
     * /mrhud forget: drops what the icon being mined right now was learned as, so a wrong
     * mapping can be cleared on the spot. It is relearned from the next shop visit or from a
     * balance already known, usually within a block or two.
     */
    private String forgetCurrentBlock() {
        if (lastActionBarReading.isEmpty()) {
            return "[MineRefine] Mine a block first, then run this again.";
        }
        String sprite = lastActionBarReading.get().sprite();
        String was = miningBlocks.isShared(sprite) ? "shared by several mines" : miningBlocks.learned().get(sprite);
        if (!miningBlocks.forget(sprite)) {
            return "[MineRefine] " + sprite + " was not learned yet, nothing to forget.";
        }
        saveLearnedBlocks();
        lastMinedMine = Optional.empty();
        lastMineCheck = 0L;
        return "[MineRefine] Forgot " + sprite + " (was " + was + "). Keep mining; it is relearned "
                + "from this mine's balance, or open its shop once.";
    }

    /**
     * Shows characters chat would draw as nothing, as <U+XXXX>. The server spaces its action bar
     * with such characters, and an invisible one between a number and its sprite is exactly the
     * kind of thing that breaks parsing while the screenshot looks perfect.
     */
    private static String revealHidden(String s) {
        StringBuilder out = new StringBuilder();
        s.codePoints().forEach(cp -> {
            int type = Character.getType(cp);
            boolean hidden = type == Character.FORMAT || type == Character.PRIVATE_USE
                    || type == Character.CONTROL || type == Character.UNASSIGNED
                    || (type == Character.SPACE_SEPARATOR && cp != ' ');
            if (hidden) {
                out.append(String.format("<U+%04X>", cp));
            } else {
                out.appendCodePoint(cp);
            }
        });
        return out.toString();
    }

    private List<String> debugReport(MinecraftClient client) {
        List<String> out = new java.util.ArrayList<>();
        out.add("[MineRefine] debug");

        int known = catalogSource.catalog().mines().size();
        out.add(" price data: " + (known > 0 ? known + " mines, " + catalogSource.source()
                : "not loaded") + catalogSource.lastError().map(e -> " (" + e + ")").orElse(""));
        out.add(" shop prices: " + prices.observedCount() + " tiers seen, "
                + links.size() + " mine links learned");

        out.add(" detected: " + currentMine.map(m -> m.name() + " via " + currentMineSource)
                .orElse("nothing yet"));

        List<String> sidebar = SidebarReader.sidebarLines(client);
        out.add(" sidebar: " + (sidebar.isEmpty() ? "empty or unreadable" : sidebar.size() + " lines"));
        for (String line : sidebar) {
            out.add("   | " + line);
        }

        out.add(" action bar, last " + recentActionBars.size() + ":");
        if (recentActionBars.isEmpty()) {
            out.add("   | nothing seen yet");
        }
        for (String line : recentActionBars) {
            out.add("   | " + revealHidden(line));
        }
        out.add(" action bar balance: " + lastActionBarReading
                .map(r -> minerefinehud.hud.Formatting.blocks(r.amount())
                        + " of " + lastActionBarCurrency.orElse("unknown resource"))
                .orElse("none read yet, mine a few blocks"));
        out.add(" mining: " + lastActionBarReading.map(r -> r.sprite() + " -> "
                + miningBlocks.lookup(r.sprite(), catalogSource.catalog())
                        .map(m -> m.mine() + (m.how() == MiningBlocks.How.LEARNED ? " (learned)" : " (by name)"))
                        .orElse(miningBlocks.isShared(r.sprite()) ? "shared by several mines, the pickup decides"
                                : "not known yet: open this mine's shop once while here"))
                .orElse("nothing mined yet"));
        out.add(" last pickup: " + lastPickup.map(p -> p + " ("
                + (System.currentTimeMillis() - lastPickupAt) / 1000L + "s ago, under "
                + lastPickupSprite.orElse("no icon") + ")").orElse("none yet"));
        out.add(" learned blocks: " + (miningBlocks.learned().isEmpty() ? "none"
                : miningBlocks.learned().toString()));
        out.add(" shared icons, the pickup decides: " + (miningBlocks.shared().isEmpty() ? "none"
                : String.join(", ", new java.util.TreeSet<>(miningBlocks.shared()))));

        String held = "";
        List<String> heldLore = List.of();
        if (client != null && client.player != null) {
            var stack = client.player.getMainHandStack();
            held = stack.getName().getString();
            heldLore = ShopScanner.lore(stack);
        }
        out.add(" in hand: " + held + "  ->  " + ShopItemParser.parseTitle(held)
                .map(g -> g.mine() + " " + g.gear() + " tier " + g.level())
                .orElse("not recognised as MineRefine gear"));
        // The first lore lines show where the tier lives if the name does not carry it.
        for (int i = 0; i < Math.min(4, heldLore.size()); i++) {
            out.add("   lore | " + heldLore.get(i));
        }
        if (held.toLowerCase(java.util.Locale.ROOT).contains("turret")) {
            out.add(" turret in hand: " + minerefinehud.turret.TurretItem.parse(held, heldLore)
                    .map(t -> t.label() + ", " + t.merges() + " merges")
                    .orElse("not read; its lore:"));
            if (minerefinehud.turret.TurretItem.parse(held, heldLore).isEmpty()) {
                for (String line : heldLore) {
                    out.add("   lore | " + revealHidden(line));
                }
            }
        }

        out.add(" last shop: " + lastShopMine.orElse("none opened yet"));

        out.add(" balances: " + (balances.all().isEmpty() ? "none seen yet"
                : String.join(", ", balances.all().entrySet().stream()
                        .map(e -> e.getKey() + " " + minerefinehud.hud.Formatting
                                .blocks(e.getValue().amount()))
                        .toList())));
        out.add(" world: " + currentWorld.orElse("not known yet, mine a block")
                + (config.showAllBossWorlds ? " (showing every world's bosses)" : ""));
        out.add(" boss worlds learned: " + (bossWorlds.learnedCount() == 0 ? "none"
                : bossWorlds.export().toString()));
        out.add(" progress: " + (progressViews.isEmpty() ? "no bars"
                : String.join(", ", progressViews.stream().map(v -> (v.quantity() > 1 ? v.quantity() + "x " : "")
                        + v.slot().label() + " -> " + v.mine() + " " + v.gear() + " " + v.targetLevel()
                        + " " + v.state()).toList())));
        return out;
    }
}
