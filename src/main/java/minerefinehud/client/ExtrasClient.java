package minerefinehud.client;

import minerefinehud.drops.RareDrops;
import minerefinehud.hud.ExtrasHud;
import minerefinehud.hud.HudModel;
import minerefinehud.mine.MiningRate;
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

    private final MiningRate miningRate = new MiningRate();
    private final RareDrops.Counter drops;
    private final StatsPoller statsPoller;

    ExtrasClient(ModConfig config, Runnable save, BooleanSupplier onMineRefine, BooleanSupplier seenMineRefine) {
        this.config = config;
        this.save = save;
        this.seenMineRefine = seenMineRefine;
        this.drops = new RareDrops.Counter(config.dropData);
        this.statsPoller = new StatsPoller(config, save, onMineRefine, this::readStats);
    }

    void register() {
        statsPoller.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            long now = System.currentTimeMillis();
            miningRate.tick(now);
            if (client != null && client.player != null) {
                statsPoller.tick(client, now);
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
    }

    void onChat(String text, long now) {
        if (!seenMineRefine.getAsBoolean()) {
            return;
        }
        RareDrops.parse(text).ifPresent(drop -> {
            drops.add(drop, now);
            config.dropData = drops.saved();
            save.run();
        });
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

    /** A /stats menu the player opened: read it, and know its title from now on. */
    void onVisibleMenu(String title, List<ShopItemParser.ItemView> items, long now) {
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
        long window = config.mineRateMinutes * 60_000L;
        double perHour = miningRate.perHour(mine, window);
        return ExtrasHud.miningPanel(new ExtrasHud.Mining(mine, balance, perHour, miningRate.session(mine),
                miningRate.miningMs(), ExtrasHud.etas(progress, mine, perHour)));
    }

    /** The mine being measured, to look its balance up. */
    String miningMine() {
        return miningRate.mine();
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
                + Math.round(miningRate.perHour(mine, config.mineRateMinutes * 60_000L)) + "/h, mining "
                + (miningRate.mining(now) ? "now" : "stopped") + ", "
                + ExtrasHud.eta(miningRate.miningMs()) + " measured"));
        out.add("stats: " + config.statsData.stats.size() + " read "
                + (config.statsData.readAt > 0 ? ExtrasHud.ago(now - config.statsData.readAt) : "never")
                + ", auto " + statsPoller.describe() + ", title \"" + config.statsTitle + "\"");
        out.add("rare drops: " + config.dropData.items.size() + " items, " + config.dropData.recent.size() + " recent");
        return out;
    }
}
