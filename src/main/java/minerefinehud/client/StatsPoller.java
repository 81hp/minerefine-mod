package minerefinehud.client;

import minerefinehud.shop.ShopItemParser;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;

/**
 * Opens /stats now and then without the player seeing it, so the stats panel stays current.
 * Only while that panel is on: nobody needs their stats read for a panel they do not show.
 * How the menu is kept off the screen is in {@link MenuPoller}.
 */
final class StatsPoller extends MenuPoller {

    /** Stats change when gear does, not by the second: once a minute is plenty at most. */
    static final long MIN_INTERVAL_MS = 60_000L;

    private final ModConfig config;
    private final Runnable save;
    private final BiFunction<List<ShopItemParser.ItemView>, Long, Boolean> reader;

    StatsPoller(ModConfig config, Runnable save, BooleanSupplier onMineRefine,
                BiFunction<List<ShopItemParser.ItemView>, Long, Boolean> reader) {
        super(onMineRefine);
        this.config = config;
        this.save = save;
        this.reader = reader;
    }

    @Override
    String command() {
        return "stats";
    }

    @Override
    boolean auto() {
        return config.showStats && config.statsAuto;
    }

    @Override
    long intervalMs() {
        return Math.max(MIN_INTERVAL_MS, config.statsEveryMs);
    }

    /** The title learned from a /stats menu the player opened, or before that any with "stat" in it. */
    @Override
    boolean isMenuTitle(String title) {
        String plain = plain(title);
        if (config.statsTitle != null && !config.statsTitle.isBlank()) {
            return plain.equals(plain(config.statsTitle));
        }
        return plain.contains("stat");
    }

    @Override
    boolean read(List<ShopItemParser.ItemView> items, long now) {
        return Boolean.TRUE.equals(reader.apply(items, now));
    }

    void learnTitle(String title) {
        if (title != null && !title.isBlank() && !plain(title).equals(plain(config.statsTitle))) {
            config.statsTitle = title;
            save.run();
        }
    }

    @Override
    void giveUp(Minecraft client, int misses) {
        config.statsAuto = false;
        save.run();
        if (client != null) {
            Compat.chat(client, Component.literal("[MineRefine] /stats could not be read " + misses
                    + " times in a row, so the automatic check is off. Open /stats once yourself, then "
                    + "turn it back on in Settings, Extras."));
        }
    }
}
