package minerefinehud.client;

import minerefinehud.shop.ShopItemParser;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;

/**
 * Runs /outpost now and then without the player seeing it, to read when each outpost opens. By
 * Arreba. How the menu is kept off the screen is in {@link MenuPoller}.
 */
final class OutpostPoller extends MenuPoller {

    static final long MIN_INTERVAL_MS = 60_000L;

    private final ModConfig config;
    private final Runnable save;
    private final BiFunction<List<ShopItemParser.ItemView>, Long, Boolean> reader;

    OutpostPoller(ModConfig config, Runnable save, BooleanSupplier onMineRefine,
                  BiFunction<List<ShopItemParser.ItemView>, Long, Boolean> reader) {
        super(onMineRefine);
        this.config = config;
        this.save = save;
        this.reader = reader;
    }

    @Override
    String command() {
        return "outpost";
    }

    @Override
    boolean auto() {
        return config.showOutposts && config.outpostAuto;
    }

    @Override
    long intervalMs() {
        return Math.max(MIN_INTERVAL_MS, config.outpostEveryMs);
    }

    @Override
    boolean isMenuTitle(String title) {
        return plain(title).contains("outpost");
    }

    @Override
    boolean read(List<ShopItemParser.ItemView> items, long now) {
        return Boolean.TRUE.equals(reader.apply(items, now));
    }

    @Override
    void giveUp(Minecraft client, int misses) {
        config.outpostAuto = false;
        save.run();
        if (client != null) {
            Compat.chat(client, Component.literal("[MineRefine] /outpost could not be read " + misses
                    + " times in a row, so the automatic check is off. Open /outpost once yourself, then "
                    + "turn it back on in Settings, Timers."));
        }
    }
}
