package minerefinehud.client;

import minerefinehud.halloween.Abilities;
import minerefinehud.halloween.HalloweenHud;
import minerefinehud.halloween.HalloweenText;
import minerefinehud.halloween.PumpkinSet;
import minerefinehud.halloween.Rates;
import minerefinehud.halloween.RecurringEvent;
import minerefinehud.hud.HudModel;
import minerefinehud.shop.ShopItemParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The Halloween event and the Reforger, wired to the game. The thinking is in the
 * minerefinehud.halloween package; this only gathers what is on screen and in chat.
 *
 * Halloween mode is on while the sidebar is the Halloween one. In it the Halloween panels take the
 * place of the mine and progress panels, and the mining action bar is left alone, since a crop
 * total is not a mine's balance.
 */
final class HalloweenClient {

    private final ModConfig config;
    private final Runnable save;
    /** Plays the reminder sound, at the boss reminder's volume. */
    private final Runnable sound;
    /** Plays the ability-ready sound, which is not the ping, so the two are told apart. */
    private final Runnable abilitySound;

    private final Rates rates = new Rates();
    private final Abilities abilities = new Abilities();
    private final RecurringEvent caravan = RecurringEvent.caravan();
    private final RecurringEvent reforger = RecurringEvent.reforger();

    /** Sidebar balances, and the action bar's crop with when it was read. */
    private final Map<String, Double> sidebarBalances = new LinkedHashMap<>();
    private final Map<String, Double> actionBarCrops = new LinkedHashMap<>();
    private final Map<String, Long> actionBarAt = new LinkedHashMap<>();

    private boolean inHalloween;
    private long lastHalloweenSidebarAt;
    /** The world the Halloween sidebar was last seen in, to stay in Halloween while escorting the caravan. */
    private String halloweenDimension = "";
    private long lastSidebarCheck;
    private boolean onMineRefine;
    /** MineRefine's sidebar was seen since joining, so this is that server and not another. */
    private boolean seenMineRefine;

    private double pestCandy;
    private int pestCharms;

    /** Pumpkin pieces on the player right now, checked every couple of seconds. */
    private Set<String> carriedPieces = Set.of();
    private long lastCarriedCheck;

    // Reforger query: /nextreforger, sent quietly, its answer hidden.
    private boolean wantQuery;
    private long queryNotBefore;
    private long mineRefineSince;
    private long lastQueryAt;
    private int hideReplies;
    private long hideUntil;
    private boolean sendingOurs;

    /** Alerts already rung, so each sounds once. */
    private final Set<String> rung = new HashSet<>();
    /** The last caravan warning, to show "leaves in" for its minute. */
    private long caravanWarnedAt;

    private static final long ACTION_BAR_FRESH_MS = 5_000L;
    /** The Halloween sidebar this recently, then a caravan run, keeps the Halloween panels. */
    private static final long ESCORT_GRACE_MS = 15L * 60_000L;
    private static final long ALERT_SHOW_MS = 10_000L;
    private static final Pattern HOE = Pattern.compile("\\bhoe\\b", Pattern.CASE_INSENSITIVE);

    HalloweenClient(ModConfig config, Runnable save, Runnable sound, Runnable abilitySound) {
        this.config = config;
        this.save = save;
        this.sound = sound;
        this.abilitySound = abilitySound;
        abilities.importClasses(config.hwAbilityClasses, config.hwAbilityClass);
        caravan.importFrom(config.hwCaravanState);
        reforger.importFrom(config.reforgerState);
    }

    void register() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        DropKeyHook.listen(this::onDrop);

        // Before the message reaches chat, so the answer to the quiet /nextreforger can be hidden.
        ClientReceiveMessageEvents.ALLOW_GAME.register((Component message, boolean overlay) -> {
            if (overlay) {
                return true;
            }
            long countdown = RecurringEvent.reforgerCountdown(message.getString());
            if (countdown < 0L) {
                return true;
            }
            long now = System.currentTimeMillis();
            reforger.startsIn(countdown, now);
            saveTimers();
            if (hideReplies > 0 && now <= hideUntil) {
                hideReplies--;
                return false;
            }
            return true;
        });

        ClientReceiveMessageEvents.GAME.register((Component message, boolean overlay) -> {
            if (!overlay) {
                onChat(message.getString());
            }
        });

        // Typed by the player: they want to see the answer, so nothing is hidden.
        ClientSendMessageEvents.COMMAND.register(command -> {
            if (!sendingOurs && command != null
                    && command.trim().toLowerCase(Locale.ROOT).startsWith("next")) {
                hideReplies = 0;
            }
        });
    }

    void onJoin() {
        inHalloween = false;
        lastHalloweenSidebarAt = 0L;
        halloweenDimension = "";
        onMineRefine = false;
        seenMineRefine = false;
        mineRefineSince = 0L;
        rates.forgetBaselines();
        sidebarBalances.clear();
        actionBarCrops.clear();
        actionBarAt.clear();
        wantQuery = true;
        queryNotBefore = 0L;
        hideReplies = 0;
    }

    /** In the Halloween dimension with the Halloween panels switched on. */
    boolean active() {
        return config.halloweenEnabled && inHalloween;
    }

    boolean inHalloween() {
        return inHalloween;
    }

    // ------------------------------------------------------------------ tick

    private void tick(Minecraft client) {
        if (client == null || client.player == null) {
            return;
        }
        long now = System.currentTimeMillis();

        if (now - lastSidebarCheck >= 500L) {
            lastSidebarCheck = now;
            readSidebar(client, now);
            rates.tick(now, inHalloween);
            maybeQueryReforger(client, now);
        }

        if (now - lastCarriedCheck >= 2_000L) {
            lastCarriedCheck = now;
            carriedPieces = carriedPieces(client);
        }

        if (!abilities.newlyReady(now).isEmpty() && inHalloween && config.halloweenEnabled
                && config.hwAbilities && config.hwAbilitySound) {
            abilitySound.run();
        }

        // Marked rung even with the sound off, so switching it on later does not ring old alerts.
        if (!alerts(now, true).isEmpty() && config.bossReminderSound) {
            sound.run();
        }
    }

    /**
     * Drop pressed. With a hoe in hand in Halloween it fires the ability, and the server says
     * nothing until it has recharged, so this is where the cooldown starts.
     */
    private void onDrop() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || !inHalloween || !holdingHoe(client)) {
            return;
        }
        boolean shift;
        try {
            shift = client.player.isShiftKeyDown() || client.options.keyShift.isDown();
        } catch (Exception | NoSuchMethodError | NoSuchFieldError ignored) {
            shift = false;
        }
        abilities.pressed(shift ? Abilities.Key.SHIFT_DROP : Abilities.Key.DROP, System.currentTimeMillis());
    }

    private void readSidebar(Minecraft client, long now) {
        List<String> lines = SidebarReader.sidebarLines(client);
        onMineRefine = HalloweenText.isMineRefine(lines);
        seenMineRefine |= onMineRefine;
        HalloweenText.Sidebar sidebar = HalloweenText.sidebar(lines);
        String dimension = dimension(client);
        if (sidebar.halloween()) {
            lastHalloweenSidebarAt = now;
            halloweenDimension = dimension;
        }
        // A moment's grace, so a sidebar that redraws for a tick does not flip the panels.
        boolean nowIn = lastHalloweenSidebarAt > 0L && now - lastHalloweenSidebarAt < 3_000L;
        // Escorting the caravan swaps the sidebar out. The player is still in Halloween while the
        // caravan runs and they stay in the world they farmed in. This only keeps the panels on,
        // never switches them on, in case the mines share that world.
        nowIn |= inHalloween && escorting(now, dimension);
        if (nowIn != inHalloween) {
            inHalloween = nowIn;
            rates.forgetBaselines();   // nothing changed while away counts as farmed here
            if (!nowIn) {
                actionBarCrops.clear();
                actionBarAt.clear();
            }
        }
        if (sidebar.halloween()) {
            sidebarBalances.putAll(sidebar.balances());
            sidebar.balances().forEach(rates::observe);
        }
    }

    /**
     * A caravan run is on, or its one-minute warning has sounded, and the player has not left the
     * world where they last saw the Halloween sidebar.
     */
    private boolean escorting(long now, String dimension) {
        if (lastHalloweenSidebarAt <= 0L || now - lastHalloweenSidebarAt >= ESCORT_GRACE_MS
                || dimension.isEmpty() || !dimension.equals(halloweenDimension)) {
            return false;
        }
        RecurringEvent.View v = caravan.view(now);
        return v.state() == RecurringEvent.State.ACTIVE
                || (v.state() == RecurringEvent.State.WAITING && v.exact() && v.ms() <= 65_000L);
    }

    /**
     * The action bar, while in Halloween. True when it was taken, so mining code leaves it alone.
     * While escorting, with no Halloween sidebar, only a crop total is taken.
     */
    boolean onActionBar(String text) {
        if (!inHalloween) {
            return false;
        }
        Optional<HalloweenText.CropReading> crop = HalloweenText.actionBar(text);
        crop.ifPresent(r -> {
            actionBarCrops.put(r.crop(), r.amount());
            actionBarAt.put(r.crop(), System.currentTimeMillis());
        });
        return crop.isPresent() || System.currentTimeMillis() - lastHalloweenSidebarAt < 3_000L;
    }

    /** Sidebar balances, with the action bar's newer crop total where it has one. */
    Map<String, Double> balances() {
        long now = System.currentTimeMillis();
        Map<String, Double> out = new LinkedHashMap<>(sidebarBalances);
        actionBarCrops.forEach((crop, amount) -> {
            if (now - actionBarAt.getOrDefault(crop, 0L) <= ACTION_BAR_FRESH_MS) {
                out.put(crop, amount);
            }
        });
        return out;
    }

    // ------------------------------------------------------------------ chat

    private void onChat(String text) {
        long now = System.currentTimeMillis();

        var candy = HalloweenText.pestCandy(text);
        if (candy.isPresent()) {
            pestCandy += candy.getAsDouble();
            return;
        }
        int charms = HalloweenText.pestCharms(text);
        if (charms > 0) {
            pestCharms += charms;
            return;
        }

        Optional<HalloweenText.Recharge> recharge = HalloweenText.recharge(text);
        if (recharge.isPresent()) {
            HalloweenText.Recharge r = recharge.get();
            boolean learned = r.secondsLeft().isPresent()
                    ? abilities.recharging(r.ability(), r.secondsLeft().getAsDouble(), now)
                    : abilities.recharged(r.ability(), now);
            if (learned) {
                saveAbilities();
            }
            return;
        }

        Optional<String> picked = HalloweenText.abilitySet(text);
        if (picked.isPresent()) {
            if (abilities.select(picked.get())) {
                saveAbilities();
            }
            return;
        }

        int refined = HalloweenText.refinedTo(text);
        if (refined > 0) {
            config.hwRefineLevel = refined;
            save.run();
            return;
        }

        if (RecurringEvent.caravanWarning(text)) {
            caravan.startsIn(60_000L, now);
            caravanWarnedAt = now;
            saveTimers();
        } else if (RecurringEvent.caravanStarted(text)) {
            caravan.started(now);
            saveTimers();
        } else if (RecurringEvent.caravanEnded(text)) {
            caravan.ended(now);
            saveTimers();
        } else if (RecurringEvent.reforgerArrived(text)) {
            reforger.started(now);
            saveTimers();
        } else if (RecurringEvent.reforgerDeparted(text)) {
            reforger.ended(now);
            saveTimers();
            // Ask for the next one once it has gone, so the countdown is exact again.
            wantQuery = true;
            queryNotBefore = now + 30_000L;
        }
    }

    // -------------------------------------------------------------- reforger

    /**
     * Sends /nextreforger once after joining MineRefine, after the Reforger leaves, and when the
     * countdown is only an estimate, at most every half hour. Only on MineRefine itself (its name
     * is the sidebar title), so the hub never sees the command.
     */
    private void maybeQueryReforger(Minecraft client, long now) {
        if (!config.reforgerTimer || !config.reforgerAutoCheck) {
            return;
        }
        if (!onMineRefine) {
            mineRefineSince = 0L;
            return;
        }
        if (mineRefineSince == 0L) {
            mineRefineSince = now;
        }
        RecurringEvent.View v = reforger.view(now);
        boolean guessing = v.state() == RecurringEvent.State.UNKNOWN
                || (v.state() == RecurringEvent.State.WAITING && !v.exact());
        if (!wantQuery && guessing && now - lastQueryAt >= 30L * 60_000L) {
            wantQuery = true;
        }
        if (!wantQuery || now < queryNotBefore || now - mineRefineSince < 5_000L
                || now - lastQueryAt < 60_000L || Compat.screen(client) != null) {
            return;
        }
        wantQuery = false;
        lastQueryAt = now;
        sendingOurs = true;
        try {
            if (Compat.sendCommand(client, "nextreforger")) {
                hideReplies = 1;
                hideUntil = now + 10_000L;
            }
        } finally {
            sendingOurs = false;
        }
    }

    // ---------------------------------------------------------------- alerts

    /**
     * The alert lines to draw now. With {@code ring}, only those not rung yet, marking them rung,
     * for the sound; without, everything due, for the panel.
     */
    List<String> alerts(long now, boolean ring) {
        List<String> out = new ArrayList<>();
        if (config.reforgerTimer && config.reforgerAlert && seenMineRefine) {
            RecurringEvent.View v = reforger.view(now);
            long lead = config.bossReminderSeconds * 1000L;
            if (v.state() == RecurringEvent.State.WAITING && v.exact() && v.ms() > 0L && v.ms() <= lead) {
                add(out, ring, "reforger-soon:" + (now + v.ms()) / 10_000L, "The Reforger is about to arrive!");
            }
            long arrived = config.reforgerState.lastStart;
            if (v.state() == RecurringEvent.State.ACTIVE && arrived > 0L && now - arrived <= ALERT_SHOW_MS) {
                add(out, ring, "reforger-here:" + arrived, "The Reforger has arrived!");
            }
        }
        // Only in the dimension: elsewhere the caravan is out of reach, and an estimate ringing
        // every 45 minutes while mining would only be noise.
        if (config.halloweenEnabled && config.hwCaravanReminder && inHalloween) {
            RecurringEvent.View v = caravan.view(now);
            if (v.state() == RecurringEvent.State.WAITING && v.ms() > 0L && v.ms() <= 60_000L
                    && (v.exact() || now - caravanWarnedAt > 120_000L)) {
                add(out, ring, "caravan:" + (now + v.ms()) / 10_000L,
                        "Caravan leaves in " + minerefinehud.hud.Formatting.duration(v.ms()) + "!");
            }
        }
        return out;
    }

    private void add(List<String> out, boolean ring, String key, String text) {
        if (!ring) {
            out.add(text);
        } else if (rung.add(key)) {
            out.add(text);
            if (rung.size() > 200) {
                rung.clear();
                rung.add(key);
            }
        }
    }

    // ----------------------------------------------------------------- shops

    /**
     * Reads Halloween menus while open: pumpkin prices and stock, the Farming Abilities classes,
     * and the refiner's candy price. Called with whatever container is on screen.
     */
    void scanScreen(String screenTitle, List<ShopItemParser.ItemView> items) {
        boolean changed = false;
        boolean refiner = screenTitle != null && screenTitle.toLowerCase(Locale.ROOT).contains("refin");
        for (ShopItemParser.ItemView item : items) {
            if (abilities.learnFromMenu(item.title(), item.lore())) {
                saveAbilities();
            }
            if (PumpkinSet.isEventItem(item.title(), item.lore())) {
                String piece = PumpkinSet.piece(item.title()).orElseThrow();
                Map<String, Double> cost = PumpkinSet.costs(item.lore());
                if (!cost.isEmpty() && !cost.equals(config.hwPumpkinPrices.get(piece))) {
                    config.hwPumpkinPrices.put(piece, new LinkedHashMap<>(cost));
                    changed = true;
                }
                Optional<Boolean> soldOut = PumpkinSet.soldOut(item.lore());
                if (soldOut.isPresent()) {
                    boolean had = config.hwPumpkinOwned.contains(piece);
                    if (soldOut.get() && !had) {
                        config.hwPumpkinOwned.add(piece);
                        changed = true;
                    } else if (!soldOut.get() && had) {
                        config.hwPumpkinOwned.remove(piece);
                        changed = true;
                    }
                }
                continue;
            }
            if (refiner) {
                Double candy = PumpkinSet.costs(item.lore()).get(HalloweenText.CANDY);
                if (candy != null && candy > 0 && candy != config.hwRefineCost) {
                    config.hwRefineCost = candy;
                    changed = true;
                }
            }
        }
        if (changed) {
            save.run();
        }
    }

    /** Halloween shop items, which the mine shop parser must not report as unreadable gear. */
    static boolean isHalloweenItem(ShopItemParser.ItemView item) {
        return PumpkinSet.isEventItem(item.title(), item.lore())
                || PumpkinSet.costs(item.lore()).containsKey(HalloweenText.CANDY);
    }

    // ---------------------------------------------------------------- panels

    /** The stats panel, rebuilt a few times a second rather than every frame: it sums an hour of gains. */
    private List<HudModel.Line> statsCache = List.of();
    private long statsCachedAt;

    List<HudModel.Line> statsPanel(long now) {
        if (now - statsCachedAt >= 250L || now < statsCachedAt) {
            statsCachedAt = now;
            statsCache = buildStats(now);
        }
        return statsCache;
    }

    private List<HudModel.Line> buildStats(long now) {
        return HalloweenHud.statsPanel(new HalloweenHud.Stats(balances(), rates, config.hwRateMinutes * 60_000L,
                pestCandy, pestCharms, caravan.view(now), config.hwRefineCost, config.hwRefineLevel),
                config.hwStatsLines());
    }

    List<HudModel.Line> pumpkinPanel() {
        return HalloweenHud.pumpkinPanel(PumpkinSet.plan(config.hwPumpkinBars, owned(), prices(), balances()),
                config.hwPumpkinDetailed);
    }

    List<HudModel.Line> abilityPanel(long now) {
        return config.hwAbilities ? HalloweenHud.abilityPanel(abilities.timers(now)) : List.of();
    }

    /**
     * The Reforger row for the boss panel: only on MineRefine, and only once its time is known, so
     * other servers and singleplayer never show a stray "unknown".
     */
    List<HudModel.Line> reforgerRows(long now) {
        RecurringEvent.View v = reforger.view(now);
        if (!config.reforgerTimer || !seenMineRefine || v.state() == RecurringEvent.State.UNKNOWN) {
            return List.of();
        }
        return List.of(HalloweenHud.reforgerLine(v));
    }

    Map<String, Map<String, Double>> prices() {
        Map<String, Map<String, Double>> out = PumpkinSet.bundledPrices();
        out.putAll(config.hwPumpkinPrices);
        return out;
    }

    Set<String> owned() {
        Set<String> out = new HashSet<>(config.hwPumpkinOwned);
        out.addAll(carriedPieces);
        return out;
    }

    void resetRates() {
        statsCachedAt = 0L;
        rates.reset();
        pestCandy = 0.0;
        pestCharms = 0;
    }

    List<String> debug(long now) {
        List<String> out = new ArrayList<>();
        out.add(" halloween: " + (inHalloween ? "in the dimension" : "not in it")
                + (config.halloweenEnabled ? "" : " (panels off)") + ", balances " + balances());
        out.add(" halloween ability class: " + (abilities.selectedName().isEmpty() ? "not known yet"
                : abilities.selectedName()) + ", pumpkin owned: " + owned());
        out.add(" reforger: " + reforger.view(now) + ", caravan: " + caravan.view(now));
        return out;
    }

    // --------------------------------------------------------------- helpers

    private void saveAbilities() {
        config.hwAbilityClasses = new LinkedHashMap<>(abilities.exportClasses());
        config.hwAbilityClass = abilities.selectedName();
        save.run();
    }

    private void saveTimers() {
        config.hwCaravanState = caravan.export();
        config.reforgerState = reforger.export();
        save.run();
    }

    private static String dimension(Minecraft client) {
        try {
            return client.level == null ? "" : client.level.dimension().identifier().toString();
        } catch (Exception | NoSuchMethodError | NoClassDefFoundError ignored) {
            return "";
        }
    }

    private static boolean holdingHoe(Minecraft client) {
        try {
            return HOE.matcher(client.player.getMainHandItem().getHoverName().getString()).find();
        } catch (Exception | NoSuchMethodError ignored) {
            return false;
        }
    }

    /** The Halloween pumpkin pieces in the inventory, worn, or in the off hand. */
    private static Set<String> carriedPieces(Minecraft client) {
        Set<String> out = new HashSet<>();
        try {
            List<ItemStack> stacks = new ArrayList<>(client.player.getInventory().getNonEquipmentItems());
            for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND)) {
                stacks.add(client.player.getItemBySlot(slot));
            }
            for (ItemStack stack : stacks) {
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                String title = stack.getHoverName().getString();
                if (PumpkinSet.isEventItem(title, ShopScanner.lore(stack))) {
                    PumpkinSet.piece(title).ifPresent(out::add);
                }
            }
        } catch (Exception | NoSuchMethodError | NoClassDefFoundError ignored) {
            // A mapping mismatch costs the owned check; the shop's stock line still works.
        }
        return out;
    }
}
