package minerefinehud.client;

import minerefinehud.beacon.BeaconTimer;
import minerefinehud.drops.RareDrops;
import minerefinehud.hud.ExtrasHud;
import minerefinehud.hud.HudModel;
import minerefinehud.mine.MiningRate;
import minerefinehud.outpost.OutpostTimers;
import minerefinehud.progress.ProgressPlanner;
import minerefinehud.shop.ShopItemParser;
import minerefinehud.stats.PlayerStats;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.function.BooleanSupplier;

/**
 * Mining per hour, the player's stats and rare drops, wired to the game. The thinking is in
 * {@link MiningRate}, {@link PlayerStats}, {@link RareDrops} and {@link ExtrasHud}; this only
 * feeds them what the action bar, chat and the /stats menu say.
 */
final class ExtrasClient {

    private final ModConfig config;
    private final Runnable save;
    /** Drops count once MineRefine was seen since joining, so a sidebar swapped out never loses one. */
    private final BooleanSupplier seenMineRefine;

    private final BeaconTimer beacon = new BeaconTimer();
    private final Runnable ping;
    private final OutpostPoller outpostPoller;
    private final OutpostTimers.Pings outpostPings = new OutpostTimers.Pings();
    private long lastRecheckAt;
    private long lastOutpostSave;

    private final MiningRate miningRate = new MiningRate();
    private final minerefinehud.mine.XpRate xpRate = new minerefinehud.mine.XpRate();
    private final RareDrops.Counter drops;
    private final StatsPoller statsPoller;

    ExtrasClient(ModConfig config, Runnable save, BooleanSupplier onMineRefine, BooleanSupplier seenMineRefine,
                 Runnable ping) {
        this.config = config;
        this.ping = ping;
        this.save = save;
        this.seenMineRefine = seenMineRefine;
        this.drops = new RareDrops.Counter(config.dropData);
        this.statsPoller = new StatsPoller(config, save, onMineRefine, this::readStats);
        this.outpostPoller = new OutpostPoller(config, save, onMineRefine, this::readOutposts);
    }

    void register() {
        statsPoller.register();
        outpostPoller.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            long now = System.currentTimeMillis();
            miningRate.tick(now);
            xpRate.tick(now);
            // Items taken out of a vault or chest raise the total too; they are not mined.
            if (client != null && Compat.screen(client)
                    instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) {
                miningRate.interrupt();
            }
            if (client != null && client.player != null) {
                statsPoller.tick(client, now);
                outpostPoller.tick(client, now);
                if (config.showBeacon && beacon.warnNow(now, config.beaconWarnSeconds * 1000L)) {
                    ping.run();
                }
                if (config.showOutposts) {
                    outpostTick(now);
                }
            }
        });
        // Drops are the server's own lines. SecondChat moves them to its window after this event,
        // so they are counted whichever chat shows them.
        ClientReceiveMessageEvents.GAME.register((Component message, boolean overlay) -> {
            if (!overlay && message != null) {
                onChat(message.getString(), System.currentTimeMillis());
            }
        });
    }

    void onJoin() {
        statsPoller.onJoin();
        outpostPoller.onJoin();
        outpostPings.clear();
        beacon.clear();
    }

    void onChat(String text, long now) {
        if (!seenMineRefine.getAsBoolean()) {
            return;
        }
        if (BeaconTimer.isReset(text)) {
            beacon.onReset(now);
        }
        RareDrops.parse(text).ifPresent(drop -> {
            drops.add(drop, now);
            config.dropData = drops.saved();
            save.run();
        });
    }

    /** A mining line from the action bar, with the EXP it shows and the balance beside it. */
    void onXp(String line, long balance, long now) {
        minerefinehud.mine.XpRate.parse(line).ifPresent(gain -> xpRate.observe(gain, balance, now));
    }

    /** The action bar's balance of the mine being mined. */
    void onBalance(String mine, long amount, long now) {
        miningRate.observe(mine, amount, now);
    }

    // ------------------------------------------------------------ stats

    /** A menu's items, from the hidden check or a screen the player opened. True if it held stats. */
    boolean readStats(List<ShopItemParser.ItemView> items, long now) {
        List<String> titles = new ArrayList<>();
        List<List<String>> lores = new ArrayList<>();
        for (ShopItemParser.ItemView item : items) {
            titles.add(item.title());
            lores.add(item.lore());
        }
        List<PlayerStats.Stat> stats = PlayerStats.readAll(titles, lores);
        if (stats.isEmpty()) {
            return false;
        }
        // An open /stats screen is read twice a second: the same figures again only refresh the
        // time, so the arrows from the last change stay and the config is not written each time.
        if (PlayerStats.same(config.statsData, stats)) {
            long before = config.statsData.readAt;
            config.statsData.readAt = now;
            if (now - before >= 60_000L) {
                save.run();
            }
            return true;
        }
        config.statsData = PlayerStats.update(config.statsData, stats, now);
        save.run();
        return true;
    }

    // --------------------------------------------------------- outposts

    /** Every tick while the panel is on: ping before an outpost opens, and read the menu again after a battle. */
    private void outpostTick(long now) {
        List<OutpostTimers.View> views = OutpostTimers.views(config.outpostData, now);
        if (outpostPings.due(views, config.outpostWarnSeconds * 1000L)) {
            ping.run();
        }
        // The countdown after a battle is only a guess until the menu says: ask, but not every tick.
        if (OutpostTimers.needsRecheck(config.outpostData, now) && now - lastRecheckAt >= RECHECK_EVERY_MS) {
            lastRecheckAt = now;
            outpostPoller.requestNow();
        }
    }

    private static final long RECHECK_EVERY_MS = 60_000L;
    private static final long OUTPOST_SAVE_EVERY_MS = 10_000L;

    /** A menu's items, from the hidden check or a screen the player opened. True if it held outposts. */
    boolean readOutposts(List<ShopItemParser.ItemView> items, long now) {
        List<String> titles = new ArrayList<>();
        List<List<String>> lores = new ArrayList<>();
        for (ShopItemParser.ItemView item : items) {
            titles.add(item.title());
            lores.add(item.lore());
        }
        List<OutpostTimers.Reading> readings = OutpostTimers.readAll(titles, lores);
        if (readings.isEmpty()) {
            return false;
        }
        config.outpostData = OutpostTimers.update(config.outpostData, readings, now);
        // An open /outpost screen is read twice a second: the config is not written each time.
        if (now - lastOutpostSave >= OUTPOST_SAVE_EVERY_MS) {
            lastOutpostSave = now;
            save.run();
        }
        return true;
    }

    List<HudModel.Line> outpostPanel(long now) {
        if (!config.showOutposts) {
            return List.of();
        }
        return ExtrasHud.outpostPanel(OutpostTimers.views(config.outpostData, now), config.outpostWarnSeconds * 1000L);
    }

    /** The title text for the alert panel: an outpost about to open, or just open. */
    List<String> outpostAlerts(long now) {
        if (!config.showOutposts) {
            return List.of();
        }
        return OutpostTimers.alerts(OutpostTimers.views(config.outpostData, now), config.outpostWarnSeconds * 1000L);
    }

    /** For the settings button: reads /outpost as soon as it may. */
    void checkOutpostsNow() {
        if (config.showOutposts) {
            outpostPoller.requestNow();
        }
    }

    /** A /stats menu the player opened: read it, and know its title from now on. */
    void onVisibleMenu(String title, List<ShopItemParser.ItemView> items, long now) {
        if (config.showOutposts) {
            readOutposts(items, now);
        }
        if (readStats(items, now)) {
            statsPoller.learnTitle(title);
        }
    }

    // ----------------------------------------------------------- panels

    List<HudModel.Line> miningPanel(List<ProgressPlanner.ProgressView> progress, OptionalLong balance) {
        if (!config.showMiningRate) {
            return List.of();
        }
        String mine = miningRate.mine();
        double perHour = minePerHour(mine);
        ExtrasHud.Xp xp = config.showXp && xpRate.any()
                ? new ExtrasHud.Xp(xpPerHour(), xpRate.session()) : null;
        return ExtrasHud.miningPanel(new ExtrasHud.Mining(mine, balance, perHour, miningRate.session(mine),
                miningRate.miningMs(), ExtrasHud.etas(progress, mine, perHour)), xp);
    }

    /** The figures shown are held for a few seconds, so the panel is calm; the rates behind them are live. */
    private final minerefinehud.mine.HeldRate heldMine = new minerefinehud.mine.HeldRate();
    private final minerefinehud.mine.HeldRate heldXp = new minerefinehud.mine.HeldRate();

    private double minePerHour(String mine) {
        long now = System.currentTimeMillis();
        return heldMine.get(now, mine + "|" + config.mineRateSeconds + "|" + config.mineRateBlocks,
                () -> config.mineRateSeconds > 0
                        ? miningRate.perHourOver(mine, config.mineRateSeconds * 1000L, now)
                        : miningRate.perHour(mine, config.mineRateBlocks));
    }

    private double xpPerHour() {
        long now = System.currentTimeMillis();
        return heldXp.get(now, config.mineRateSeconds + "|" + config.mineRateBlocks,
                () -> config.mineRateSeconds > 0
                        ? xpRate.perHourOver(config.mineRateSeconds * 1000L, now)
                        : xpRate.perHour(config.mineRateBlocks));
    }

    /** The mine being measured, to look its balance up. */
    String miningMine() {
        return miningRate.mine();
    }

    List<HudModel.Line> beaconPanel(long now) {
        if (!config.showBeacon) {
            return List.of();
        }
        long rem = beacon.remaining(now);
        long warn = config.beaconWarnSeconds * 1000L;
        return ExtrasHud.beaconPanel(rem, warn > 0L && rem >= 0L && rem <= warn);
    }

    /** The title text for the alert panel: the mine just reset, or is about to. */
    List<String> beaconAlerts(long now) {
        if (!config.showBeacon) {
            return List.of();
        }
        return ExtrasHud.beaconAlerts(beacon.remaining(now), beacon.justReset(now), config.beaconWarnSeconds * 1000L);
    }

    List<HudModel.Line> statsPanel() {
        return config.showStats ? ExtrasHud.statsPanel(config.statsData, config.statsHiddenSet()) : List.of();
    }

    List<HudModel.Line> dropsPanel(long now) {
        if (!config.showDrops) {
            return List.of();
        }
        java.util.Set<String> hidden = config.dropsHiddenSet();
        return ExtrasHud.dropsPanel(drops.rows(hidden, config.dropsRows), drops.recent(hidden, config.dropsRecent), now);
    }

    // ---------------------------------------------------------- settings

    /** Every stat read so far, for the settings picker. */
    List<String> statNames() {
        List<String> out = new ArrayList<>();
        for (PlayerStats.SavedStat s : config.statsData.stats) {
            out.add(s.name);
        }
        return out;
    }

    List<String> raritiesSeen() {
        return drops.raritiesSeen();
    }

    void resetMiningRate() {
        miningRate.reset();
        xpRate.reset();
        heldMine.reset();
        heldXp.reset();
    }

    void resetDropSession() {
        drops.resetSession();
    }

    void resetAllDrops() {
        drops.resetAll();
        config.dropData = drops.saved();
        save.run();
    }

    /** For /mrhud debug. */
    List<String> debugLines(long now) {
        List<String> out = new ArrayList<>();
        String mine = miningRate.mine();
        out.add("mining rate: " + (mine.isEmpty() ? "nothing read yet" : mine + " "
                + Math.round(minePerHour(mine)) + "/h, mining "
                + (miningRate.mining(now) ? "now" : "stopped") + ", "
                + ExtrasHud.eta(miningRate.miningMs()) + " measured"));
        out.add("exp rate: " + (xpRate.any() ? Math.round(xpPerHour()) + "/h, +"
                + Math.round(xpRate.session()) + " this session, mining "
                + (xpRate.mining(now) ? "now" : "stopped") : "no EXP line read yet"));
        out.add("stats: " + config.statsData.stats.size() + " read "
                + (config.statsData.readAt > 0 ? ExtrasHud.ago(now - config.statsData.readAt) : "never")
                + ", auto " + statsPoller.describe() + ", title \"" + config.statsTitle + "\"");
        out.add("beacon mine: " + (beacon.known()
                ? ExtrasHud.eta(beacon.remaining(now)) + " to the next reset" : "no reset seen yet")
                + (config.showBeacon ? "" : " (panel off)"));
        out.add("outposts: " + config.outpostData.outposts.size() + " known, auto " + outpostPoller.describe()
                + (config.showOutposts ? "" : " (panel off)"));
        for (OutpostTimers.View v : OutpostTimers.views(config.outpostData, now)) {
            out.add("  " + v.name() + ": " + v.phase().name().toLowerCase(java.util.Locale.ROOT) + " "
                    + OutpostTimers.clock(v.ms()) + (v.estimated() ? " (counted forward)" : ""));
        }
        out.add("rare drops: " + config.dropData.items.size() + " items, " + config.dropData.recent.size() + " recent");
        return out;
    }
}
