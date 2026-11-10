package minerefinehud.client;

import minerefinehud.hud.Formatting;
import minerefinehud.hud.Theme;
import minerefinehud.job.JobCalc;
import minerefinehud.progress.MineCosts;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The price lookup, by Arreba: pick a dimension, pick a mine, read its gear prices the way the
 * Mine panel shows them. A "*" means the figure was read from the shop; without it, from the
 * bundled data. Credits use the rate typed in the job calculator.
 *
 * VERSION SENSITIVE for the Screen and widget API only.
 */
public final class PriceScreen extends Screen {

    private static final int ROW = 16;
    private static final int STEP = 18;

    private final Screen parent;
    private final ModConfig config;
    private final Chips chips = new Chips(this::addRenderableWidget);

    private List<JobSource.Item> items = List.of();
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
        return Math.min(this.width - 16, 480);
    }

    private int left() {
        return (this.width - winW()) / 2;
    }

    private int colW() {
        return (winW() - 36) / 2;
    }

    private int accent() {
        return Theme.parseOr(config.menuAccent, 0xFFFFD24A);
    }

    @Override
    protected void init() {
        chips.clear();
        items = JobSource.items();
        int l = left() + 12;
        int w = winW() - 24;

        List<String> worlds = JobSource.worlds(items);
        if ((world.isBlank() || !worlds.contains(world)) && !worlds.isEmpty()) {
            world = worlds.get(0);
        }
        int x = l;
        int y = 36;
        for (String name : worlds) {
            int cw = this.font.width(name) + 16;
            if (x + cw > l + w) {
                x = l;
                y += ROW + 3;
            }
            chips.add(x, y, cw, ROW, name, "", name.equals(world), false, () -> {
                world = name;
                mine = "";
                page = 0;
                rebuildWidgets();
            });
            x += cw + 4;
        }
        listTop = y + ROW + 20;

        int by = this.height - 30;
        int fit = Math.max(1, (by - 4 - listTop) / STEP);
        List<JobSource.Item> mines = JobSource.in(items, world);
        int pages = Math.max(1, (mines.size() + fit - 1) / fit);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < fit; i++) {
            int at = page * fit + i;
            if (at >= mines.size()) {
                break;
            }
            String name = mines.get(at).name();
            chips.add(l, listTop + i * STEP, colW(), ROW, name, "", name.equals(mine), false, () -> {
                mine = name;
                rebuildWidgets();
            });
        }
        chips.add(l, by, 22, ROW, "<", "", false, page == 0, () -> {
            if (page > 0) {
                page--;
                rebuildWidgets();
            }
        });
        chips.add(l + 26, by, 22, ROW, ">", "", false, page >= pages - 1, () -> {
            if (page < pages - 1) {
                page++;
                rebuildWidgets();
            }
        });
        chips.add(l + w - 60, by, 60, ROW, "Back", "", false, false, this::onClose);
    }

    private static String label(String gear) {
        return gear.isEmpty() ? gear : Character.toUpperCase(gear.charAt(0)) + gear.substring(1);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int l = left();
        int a = accent();
        Smooth.rect(context, l, 6, winW(), this.height - 12, 6f, 0xF0182221);
        Smooth.ring(context, l, 6, winW(), this.height - 12, 6f, 1f, Smooth.alpha(a, 0.7f));
        chips.paint(context, this.font, mouseX, mouseY, a);
        super.extractRenderState(context, mouseX, mouseY, delta);

        int x = l + 12;
        context.text(this.font, this.title.getString(), x, 16, a, false);
        context.text(this.font, world.isBlank() ? "NO MINES FOUND" : world.toUpperCase(Locale.ROOT) + " - PICK A MINE",
                x, listTop - 12, Chips.MUTED, false);

        int rx = x + colW() + 12;
        int rw = colW();
        int y = listTop;
        Optional<JobSource.Item> found = JobSource.find(items, mine);
        if (found.isEmpty()) {
            context.text(this.font, "Pick a mine to see its prices", rx, y + 4, Chips.OFF, false);
            return;
        }
        MineCosts.View v = found.get().view();
        Smooth.rect(context, rx, y - 4, rw, this.height - 40 - y + 4, 4f, 0x80101818);
        context.text(this.font, found.get().name().toUpperCase(Locale.ROOT), rx + 8, y + 2, a, false);
        y += 18;
        for (MineCosts.Piece p : v.pieces()) {
            String value = p.shown() ? Formatting.blocks(p.cost().getAsLong())
                    + (p.source() == MineCosts.Source.SHOP ? "*" : "")
                    + (p.source() == MineCosts.Source.PARTIAL ? " (" + p.tiers() + ")" : "") : "?";
            context.text(this.font, label(p.gear()), rx + 8, y, Chips.INK, false);
            context.text(this.font, value, rx + rw - 8 - this.font.width(value), y, 0xFFFFFFFF, false);
            y += 12;
        }
        y += 2;
        context.fill(rx + 8, y, rx + rw - 8, y + 1, 0xFF283836);
        y += 6;
        String total = v.total().isPresent() ? Formatting.blocks(v.total().getAsLong()) : "?";
        context.text(this.font, "Total", rx + 8, y, a, false);
        context.text(this.font, total, rx + rw - 8 - this.font.width(total), y, a, false);
        y += 12;
        OptionalDouble rate = JobCalc.parseRate(config.jobRate);
        if (v.total().isPresent() && rate.isPresent()) {
            String c = JobCalc.credits(v.total().getAsLong() / rate.getAsDouble());
            context.text(this.font, "At your rate", rx + 8, y, Chips.MUTED, false);
            context.text(this.font, c, rx + rw - 8 - this.font.width(c), y, Chips.MUTED, false);
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
