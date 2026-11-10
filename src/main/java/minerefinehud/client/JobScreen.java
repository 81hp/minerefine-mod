package minerefinehud.client;

import minerefinehud.hud.Theme;
import minerefinehud.job.JobCalc;
import minerefinehud.mine.Mine;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalLong;

/**
 * The job calculator, by Arreba. Type the rate you charge, pick a dimension, click mines to add
 * them to the job, and read off the mats and credits for all of them together. The mats for a mine
 * are what its whole gear costs, from the same prices the Mine panel shows. The job and the rate
 * are saved in the config.
 *
 * VERSION SENSITIVE for the Screen and widget API only; the arithmetic is {@link JobCalc}.
 */
public final class JobScreen extends Screen {

    private static final int ROW = 20;
    private static final int GAP = 4;

    private final Screen parent;
    private final ModConfig config;
    private final Runnable onChange;

    /** Kept across re-inits so a window resize does not lose them. */
    private String world = "";
    private int page;

    public JobScreen(Screen parent, ModConfig config, Runnable onChange) {
        super(Component.literal("Job Calculator"));
        this.parent = parent;
        this.config = config;
        this.onChange = onChange;
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

    private OptionalDouble rate() {
        return JobCalc.parseRate(config.jobRate);
    }

    /** Where the lists start: under the rate box and the dimension buttons. */
    private int listTop;

    @Override
    protected void init() {
        int l = left() + 10;
        int w = winW() - 20;
        int y = 30;

        EditBox rate = new EditBox(this.font, l + w - 110, y, 110, 18, Component.empty());
        rate.setMaxLength(24);
        rate.setValue(config.jobRate == null ? "" : config.jobRate);
        Compat.filter(rate, s -> s.matches("[0-9A-Za-z.,/ ]*"), s -> {
            config.jobRate = s;
            onChange.run();
        });
        addRenderableWidget(rate);

        List<String> worlds = JobSource.worlds();
        if (world.isBlank() && !worlds.isEmpty()) {
            world = worlds.get(0);
        }
        y += 26;
        int per = Math.max(1, (w + GAP) / (74 + GAP));
        int bw = (w - (per - 1) * GAP) / per;
        for (int i = 0; i < worlds.size(); i++) {
            String name = worlds.get(i);
            String shown = name.equals(world) ? "> " + name : name;
            addRenderableWidget(Button.builder(Component.literal(shown), b -> {
                        world = name;
                        page = 0;
                        rebuildWidgets();
                    })
                    .bounds(l + (i % per) * (bw + GAP), y + (i / per) * (ROW + GAP), bw, ROW).build());
        }
        int rows = (worlds.size() + per - 1) / per;
        listTop = y + rows * (ROW + GAP) + 14;

        int bottom = this.height - 34;
        int fit = Math.max(1, (bottom - listTop) / (ROW + 2));
        List<Mine> mines = JobSource.mines(world);
        int pages = Math.max(1, (mines.size() + fit - 1) / fit);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < fit; i++) {
            int at = page * fit + i;
            if (at >= mines.size()) {
                break;
            }
            Mine m = mines.get(at);
            OptionalLong mats = JobSource.view(m).total();
            String text = m.name() + "  " + (mats.isPresent() ? JobCalc.mats(mats.getAsLong()) : "?");
            addRenderableWidget(Button.builder(Component.literal(text), b -> {
                        config.jobMines.add(m.name());
                        onChange.run();
                        rebuildWidgets();
                    })
                    .bounds(l, listTop + i * (ROW + 2), colW(), ROW).build());
        }

        Map<String, Integer> counts = JobCalc.counts(config.jobMines);
        int shown = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (shown >= fit) {
                break;
            }
            String mine = e.getKey();
            OptionalLong each = JobSource.mine(mine).map(m -> JobSource.view(m).total()).orElse(OptionalLong.empty());
            String text = (e.getValue() > 1 ? e.getValue() + "x " : "") + mine + "  "
                    + (each.isPresent() ? JobCalc.mats((double) each.getAsLong() * e.getValue()) : "?");
            addRenderableWidget(Button.builder(Component.literal(text), b -> {
                        config.jobMines = new ArrayList<>(JobCalc.removeOne(config.jobMines, mine));
                        onChange.run();
                        rebuildWidgets();
                    })
                    .bounds(l + colW() + 10, listTop + shown * (ROW + 2), colW(), ROW).build());
            shown++;
        }

        int by = this.height - 28;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                    page--;
                    rebuildWidgets();
                }).bounds(l, by, 20, ROW).build()).active = page > 0;
        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                    page++;
                    rebuildWidgets();
                }).bounds(l + 24, by, 20, ROW).build()).active = page < pages - 1;
        addRenderableWidget(Button.builder(Component.literal("Clear job"), b -> {
                    config.jobMines = new ArrayList<>();
                    onChange.run();
                    rebuildWidgets();
                }).bounds(l + colW() + 10, by, 70, ROW).build());
        addRenderableWidget(Button.builder(Component.literal("Price lookup..."), b -> {
                    if (this.minecraft != null) {
                        Compat.setScreen(this.minecraft, new PriceScreen(this, config));
                    }
                }).bounds(l + colW() + 84, by, 90, ROW).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(l + w - 50, by, 50, ROW).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int l = left();
        Smooth.rect(context, l, 8, winW(), this.height - 16, 6f, 0xF0182221);
        Smooth.ring(context, l, 8, winW(), this.height - 16, 6f, 1f, Smooth.alpha(accent(), 0.8f));
        super.extractRenderState(context, mouseX, mouseY, delta);

        int x = l + 10;
        context.text(this.font, this.title.getString(), x, 15, accent(), false);
        context.text(this.font, "Rate (mats per credit)", x, 35, 0xFFCCCCCC, false);
        OptionalDouble r = rate();
        String parsed = r.isPresent() ? "= " + JobCalc.mats(r.getAsDouble()) + " per credit" : "type e.g. 1B or 1.25B/1c";
        context.text(this.font, parsed, x + 110, 35, r.isPresent() ? 0xFF66CC66 : 0xFFE08080, false);

        context.text(this.font, world.isBlank() ? "No mines found" : world + " - click a mine to add it",
                x, listTop - 11, 0xFFAAAAAA, false);
        context.text(this.font, "Your job - click to remove one", x + colW() + 10, listTop - 11, 0xFFAAAAAA, false);

        List<JobCalc.Entry> entries = new ArrayList<>();
        for (String name : config.jobMines) {
            entries.add(new JobCalc.Entry(name,
                    JobSource.mine(name).map(m -> JobSource.view(m).total()).orElse(OptionalLong.empty())));
        }
        JobCalc.Total t = JobCalc.total(entries, r);
        String sum = config.jobMines.isEmpty() ? "Nothing added yet"
                : "Total " + JobCalc.mats(t.mats()) + " mats = " + JobCalc.credits(t.credits())
                + (t.unknown() > 0 ? "  (" + t.unknown() + " unpriced)" : "");
        context.text(this.font, sum, x + colW() + 10, this.height - 42, 0xFFFFFFFF, false);
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
