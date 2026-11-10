package minerefinehud.client;

import minerefinehud.hud.Formatting;
import minerefinehud.hud.Theme;
import minerefinehud.job.JobCalc;
import minerefinehud.mine.Mine;
import minerefinehud.progress.MineCosts;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.OptionalDouble;

/**
 * The price lookup, by Arreba: pick a dimension, pick a mine, read its gear prices the way the
 * Mine panel shows them. A "*" means the figure was read from the shop; without it, from the
 * bundled data. Credits use the rate typed in the job calculator.
 *
 * VERSION SENSITIVE for the Screen and widget API only.
 */
public final class PriceScreen extends Screen {

    private static final int ROW = 20;
    private static final int GAP = 4;

    private final Screen parent;
    private final ModConfig config;

    private String world = "";
    private String mine = "";
    private int page;
    private int listTop;

    public PriceScreen(Screen parent, ModConfig config) {
        super(Component.literal("Price Lookup"));
        this.parent = parent;
        this.config = config;
    }

    private int winW() {
        return Math.min(this.width - 16, 460);
    }

    private int left() {
        return (this.width - winW()) / 2;
    }

    private int colW() {
        return (winW() - 30) / 2;
    }

    private int accent() {
        return Theme.parseOr(config.menuAccent, 0xFFFFD24A);
    }

    @Override
    protected void init() {
        int l = left() + 10;
        int w = winW() - 20;
        int y = 30;

        List<String> worlds = JobSource.worlds();
        if (world.isBlank() && !worlds.isEmpty()) {
            world = worlds.get(0);
        }
        int per = Math.max(1, (w + GAP) / (74 + GAP));
        int bw = (w - (per - 1) * GAP) / per;
        for (int i = 0; i < worlds.size(); i++) {
            String name = worlds.get(i);
            addRenderableWidget(Button.builder(Component.literal(name.equals(world) ? "> " + name : name), b -> {
                        world = name;
                        mine = "";
                        page = 0;
                        rebuildWidgets();
                    })
                    .bounds(l + (i % per) * (bw + GAP), y + (i / per) * (ROW + GAP), bw, ROW).build());
        }
        listTop = y + ((worlds.size() + per - 1) / per) * (ROW + GAP) + 14;

        int by = this.height - 28;
        int fit = Math.max(1, (by - 4 - listTop) / (ROW + 2));
        List<Mine> mines = JobSource.mines(world);
        int pages = Math.max(1, (mines.size() + fit - 1) / fit);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < fit; i++) {
            int at = page * fit + i;
            if (at >= mines.size()) {
                break;
            }
            String name = mines.get(at).name();
            addRenderableWidget(Button.builder(Component.literal(name.equals(mine) ? "> " + name : name), b -> {
                        mine = name;
                        rebuildWidgets();
                    })
                    .bounds(l, listTop + i * (ROW + 2), colW(), ROW).build());
        }
        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                    page--;
                    rebuildWidgets();
                }).bounds(l, by, 20, ROW).build()).active = page > 0;
        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                    page++;
                    rebuildWidgets();
                }).bounds(l + 24, by, 20, ROW).build()).active = page < pages - 1;
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(l + w - 50, by, 50, ROW).build());
    }

    private static String label(String gear) {
        return gear.isEmpty() ? gear : Character.toUpperCase(gear.charAt(0)) + gear.substring(1);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int l = left();
        Smooth.rect(context, l, 8, winW(), this.height - 16, 6f, 0xF0182221);
        Smooth.ring(context, l, 8, winW(), this.height - 16, 6f, 1f, Smooth.alpha(accent(), 0.8f));
        super.extractRenderState(context, mouseX, mouseY, delta);

        int x = l + 10;
        context.text(this.font, this.title.getString(), x, 15, accent(), false);
        context.text(this.font, world.isBlank() ? "No mines found" : world, x, listTop - 11, 0xFFAAAAAA, false);

        int rx = l + colW() + 20;
        int rw = colW() - 10;
        int y = listTop;
        var found = JobSource.mine(mine);
        if (found.isEmpty()) {
            context.text(this.font, "Pick a mine", rx, y, 0xFFAAAAAA, false);
            return;
        }
        MineCosts.View v = JobSource.view(found.get());
        context.text(this.font, v.mine(), rx, y, accent(), false);
        y += 14;
        for (MineCosts.Piece p : v.pieces()) {
            String name = label(p.gear());
            String value = p.shown() ? Formatting.blocks(p.cost().getAsLong())
                    + (p.source() == MineCosts.Source.SHOP ? "*" : "")
                    + (p.source() == MineCosts.Source.PARTIAL ? " (" + p.tiers() + ")" : "") : "?";
            context.text(this.font, name, rx, y, 0xFFDDDDDD, false);
            context.text(this.font, value, rx + rw - this.font.width(value), y, 0xFFFFFFFF, false);
            y += 12;
        }
        y += 4;
        String total = v.total().isPresent() ? Formatting.blocks(v.total().getAsLong()) : "?";
        context.text(this.font, "Total", rx, y, accent(), false);
        context.text(this.font, total, rx + rw - this.font.width(total), y, accent(), false);
        y += 12;
        OptionalDouble rate = JobCalc.parseRate(config.jobRate);
        if (v.total().isPresent() && rate.isPresent()) {
            String c = JobCalc.credits(v.total().getAsLong() / rate.getAsDouble());
            context.text(this.font, "At your rate", rx, y, 0xFFAAAAAA, false);
            context.text(this.font, c, rx + rw - this.font.width(c), y, 0xFFAAAAAA, false);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            Compat.setScreen(this.minecraft, parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
