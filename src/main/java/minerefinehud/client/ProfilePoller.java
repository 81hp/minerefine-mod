package minerefinehud.client;

import minerefinehud.mine.ExpTotal;
import minerefinehud.shop.ShopItemParser;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;

/**
 * Runs /profile every 30 seconds while the player mines, without them seeing it, to read the
 * total EXP on their head. By Arreba. Only while EXP per hour is set to come from the profile:
 * nobody needs it read otherwise. How the menu is kept off the screen is in {@link MenuPoller}.
 */
final class ProfilePoller extends MenuPoller {

    private final ModConfig config;
    private final Runnable save;
    private final BooleanSupplier mining;
    private final BiFunction<List<ShopItemParser.ItemView>, Long, Boolean> reader;

    ProfilePoller(ModConfig config, Runnable save, BooleanSupplier onMineRefine, BooleanSupplier mining,
                  BiFunction<List<ShopItemParser.ItemView>, Long, Boolean> reader) {
        super(onMineRefine);
        this.config = config;
        this.save = save;
        this.mining = mining;
        this.reader = reader;
    }

    @Override
    String command() {
        return "profile";
    }

    /** On while EXP comes from the profile, and only while the player is mining: nothing to measure otherwise. */
    @Override
    boolean auto() {
        return config.showMiningRate && config.showXp && config.xpFromProfile && mining.getAsBoolean();
    }

    @Override
    long intervalMs() {
        return ExpTotal.EVERY_MS;
    }

    @Override
    boolean isMenuTitle(String title) {
        return plain(title).contains("profile");
    }

    @Override
    boolean read(List<ShopItemParser.ItemView> items, long now) {
        return Boolean.TRUE.equals(reader.apply(items, now));
    }

    @Override
    void giveUp(Minecraft client, int misses) {
        config.xpFromProfile = false;
        save.run();
        if (client != null) {
            Compat.chat(client, Component.literal("[MineRefine] /profile could not be read " + misses
                    + " times in a row, so EXP per hour is back to the action bar. Open /profile once "
                    + "yourself to check it shows TOTAL EXP, then pick it again in Settings, Extras."));
        }
    }
}
