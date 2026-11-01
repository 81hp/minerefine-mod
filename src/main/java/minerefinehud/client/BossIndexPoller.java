package minerefinehud.client;

import minerefinehud.shop.ShopItemParser;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;

/**
 * Opens /bossindex now and then without the player ever seeing it, so every boss timer stays
 * right in every world. How the menu is kept off the screen is in {@link MenuPoller}.
 */
public final class BossIndexPoller extends MenuPoller {

    /** Never more often than this, whatever is typed: each check is a command to the server. */
    public static final long MIN_INTERVAL_MS = 30_000L;

    private static volatile BossIndexPoller instance;

    private final ModConfig config;
    private final Runnable save;
    /** Reads the boss items out of the menu, at the time given; true if there were any. */
    private final BiFunction<List<ShopItemParser.ItemView>, Long, Boolean> reader;

    BossIndexPoller(ModConfig config, Runnable save, BooleanSupplier onMineRefine,
                    BiFunction<List<ShopItemParser.ItemView>, Long, Boolean> reader) {
        super(onMineRefine);
        this.config = config;
        this.save = save;
        this.reader = reader;
        instance = this;
    }

    public static int readCount() {
        BossIndexPoller p = instance;
        return p == null ? 0 : p.menusRead();
    }

    /** For the client game test: take the next menu as the index's, as if the command had gone. */
    public static void expectForTest() {
        BossIndexPoller p = instance;
        if (p != null) {
            p.expectNow();
        }
    }

    @Override
    String command() {
        return "bossindex";
    }

    @Override
    boolean auto() {
        return config.bossIndexAuto;
    }

    @Override
    long intervalMs() {
        return Math.max(MIN_INTERVAL_MS, config.bossIndexEveryMs);
    }

    /**
     * The index's title as last seen open with bosses in it, or before that any title with "boss"
     * in it. A different title shows the menu once, is learned from its items, and is hidden from
     * then on.
     */
    @Override
    boolean isMenuTitle(String title) {
        String plain = plain(title);
        if (config.bossIndexTitle != null && !config.bossIndexTitle.isBlank()) {
            return plain.equals(plain(config.bossIndexTitle));
        }
        return plain.contains("boss");
    }

    @Override
    boolean read(List<ShopItemParser.ItemView> items, long now) {
        return Boolean.TRUE.equals(reader.apply(items, now));
    }

    /** A screen the player has open held bosses: remember its title as the index's. */
    void learnTitle(String title) {
        if (title != null && !title.isBlank() && !plain(title).equals(plain(config.bossIndexTitle))) {
            config.bossIndexTitle = title;
            save.run();
        }
    }

    @Override
    void giveUp(Minecraft client, int misses) {
        config.bossIndexAuto = false;
        save.run();
        if (client != null) {
            Compat.chat(client, Component.literal("[MineRefine] /bossindex brought no boss timers "
                    + misses + " times in a row, so the automatic check is off. Turn it back on in "
                    + "Settings, Bosses."));
        }
    }
}
