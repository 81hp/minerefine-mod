package minerefinehud.client;

import minerefinehud.hud.HudModel;
import minerefinehud.turret.TurretLevels;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Reads turret level-ups from server chat and builds the HUD lines for MineRefine HUD 1.6.7.
 *
 * Uses only that version's {@link ModConfig} and {@link HudModel}. Chat is a second GAME
 * listener, the same event 1.6.7 already uses for boss broadcasts.
 */
public final class TurretLevelsClient {

    private final ModConfig config;
    private final Runnable save;
    /** True once MineRefine was seen since joining, so another server's chat never fills the panel. */
    private final BooleanSupplier onMineRefine;
    private final TurretLevels.Log levels;

    public TurretLevelsClient(ModConfig config, Runnable save, BooleanSupplier onMineRefine) {
        this.config = config;
        this.save = save;
        this.onMineRefine = onMineRefine;
        this.levels = new TurretLevels.Log(config.turretLevels);
    }

    public void register() {
        ClientReceiveMessageEvents.GAME.register((Component message, boolean overlay) -> {
            if (!overlay && message != null) {
                onChat(message.getString(), System.currentTimeMillis());
            }
        });
    }

    public void onChat(String text, long now) {
        if (!onMineRefine.getAsBoolean()) {
            return;
        }
        TurretLevels.parse(text).ifPresent(up -> {
            if (levels.add(up, now)) {
                save.run();
            }
        });
    }

    /** Lines for the live HUD. Empty while the Turret levels toggle is off, so drawPanel skips it. */
    public List<HudModel.Line> panel() {
        if (!config.showTurretLevels) {
            return List.of();
        }
        return lines(levels.saved());
    }

    /** Move-screen copy. Still shows the header before any level-up has been seen. */
    public List<HudModel.Line> preview() {
        if (levels.saved().isEmpty()) {
            return lines(List.of());
        }
        return lines(levels.saved());
    }

    /** Example rows for the Panels tab, which draws from sample text rather than live data. */
    public static List<HudModel.Line> sample() {
        List<HudModel.Line> out = new ArrayList<>();
        out.add(HudModel.Line.of("Turret levels", HudModel.Style.HEADER));
        out.add(HudModel.Line.of("jade 117->118", HudModel.Style.VALUE));
        return out;
    }

    private static List<HudModel.Line> lines(List<TurretLevels.Saved> recent) {
        List<HudModel.Line> out = new ArrayList<>();
        out.add(HudModel.Line.of("Turret levels", HudModel.Style.HEADER));
        if (recent == null || recent.isEmpty()) {
            out.add(HudModel.Line.of("none yet", HudModel.Style.DIM));
            return out;
        }
        for (TurretLevels.Saved row : recent) {
            out.add(HudModel.Line.of(row.name + " " + row.from + "->" + row.to, HudModel.Style.VALUE));
        }
        return out;
    }
}
