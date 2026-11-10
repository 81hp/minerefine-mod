package minerefinehud.client;

import minerefinehud.hud.Theme;
import minerefinehud.job.JobCalc;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The job calculator, by Arreba. Type the rate you charge, pick a dimension, click mines to add
 * them to the job, click a mine in the job to switch single pieces (the charm, say) off, and read
 * the mats and credits for everything together. The mats are what each piece costs, from the same
 * prices the Mine panel shows. The rate, the job and the left-out pieces are saved in the config.
 *
 * VERSION SENSITIVE for the Screen and widget API only; the arithmetic is {@link JobCalc}.
 */
public final class JobScreen extends Screen {

    private static final int ROW = 16;
    private static final int STEP = 18;
    /** Room for the label in front of the rate box. */
    private static final int RATE_LABEL_W = 40;

    private final Screen parent;
    private final ModConfig config;
    private final Runnable onChange;
    private final Chips chips = new Chips(this::addRenderableWidget);

    private List<JobSource.Item> items = List.of();

    /** Kept across re-inits so a window resize does not lose them. */
    private String world = "";
    private String selected = "";
    private int page;
    private int listTop;
    private int pieceTop;

    public JobScreen(Screen parent, ModConfig config, Runnable onChange) {
        super(Component.literal("Job Calculator"));
        this.parent = parent;
        this.config = config;
        this.onChange = onChange;
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

    private OptionalDouble rate() {
        return JobCalc.parseRate(config.jobRate);
    }

    private void change() {
        onChange.run();
        rebuildWidgets();
    }

    @Override
    protected void init() {
        chips.clear();
        items = JobSource.items();
        int l = left() + 12;
        int w = winW() - 24;
        int rx = l + colW() + 12;

        // Rate, the one thing typed.
        EditBox rate = new EditBox(this.font, l + RATE_LABEL_W, 31, 90, 16, Component.empty());
        rate.setMaxLength(24);
        rate.setValue(config.jobRate == null ? "" : config.jobRate);
        Compat.filter(rate, s -> s.matches("[0-9A-Za-z.,/ ]*"), s -> {
            config.jobRate = s;
            onChange.run();
        });
        addRenderableWidget(rate);

        chips.add(l + w - 86, 12, 86, 16, "Price lookup", "", false, false, () -> {
            if (this.minecraft != null) {
                Compat.setScreen(this.minecraft, new PriceScreen(this, config));
            }
        });

        // Dimensions, as chips that wrap.
        List<String> worlds = JobSource.worlds(items);
        if ((world.isBlank() || !worlds.contains(world)) && !worlds.isEmpty()) {
            world = worlds.get(0);
        }
        int x = l;
        int y = 58;
        for (String name : worlds) {
            int cw = this.font.width(name) + 16;
            if (x + cw > l + w) {
                x = l;
                y += ROW + 3;
            }
            chips.add(x, y, cw, ROW, name, "", name.equals(world), false, () -> {
                world = name;
                page = 0;
                rebuildWidgets();
            });
            x += cw + 4;
        }
        listTop = y + ROW + 20;

        int bottom = this.height - 58;

        // Mines of this dimension.
        List<JobSource.Item> mines = JobSource.in(items, world);
        int fit = Math.max(1, (bottom - listTop) / STEP);
        int pages = Math.max(1, (mines.size() + fit - 1) / fit);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < fit; i++) {
            int at = page * fit + i;
            if (at >= mines.size()) {
                break;
            }
            JobSource.Item m = mines.get(at);
            chips.add(l, listTop + i * STEP, colW(), ROW, m.name(),
                    m.total().isPresent() ? JobCalc.mats(m.total().getAsLong()) : "?", false, false, () -> {
                        config.jobMines.add(m.name());
                        selected = m.name();
                        change();
                    });
        }

        // The job, and the pieces of the mine picked in it.
        Map<String, Integer> counts = JobCalc.counts(config.jobMines);
        if (!counts.containsKey(selected)) {
            selected = counts.isEmpty() ? "" : counts.keySet().iterator().next();
        }
        Optional<JobSource.Item> pick = JobSource.find(items, selected);
        int pieceRowsNeeded = pick.map(p -> (p.parts().size() + 1) / 2).orElse(0);
        int jobRows = Math.max(1, (bottom - listTop - (pieceRowsNeeded > 0 ? pieceRowsNeeded * STEP + 16 : 0)) / STEP);
        int shown = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (shown >= jobRows) {
                break;
            }
            String name = e.getKey();
            Optional<JobSource.Item> item = JobSource.find(items, name);
            JobCalc.Entry entry = item.map(it -> JobCalc.entry(name, it.parts(), config.jobSkips, e.getValue()))
                    .orElse(new JobCalc.Entry(name, 0L, e.getValue(), 1));
            String label = (e.getValue() > 1 ? e.getValue() + "x " : "") + name;
            chips.add(rx, listTop + shown * STEP, colW() - 22, ROW, label,
                    JobCalc.mats((double) entry.mats() * entry.times()) + (entry.missing() > 0 ? "+" : ""),
                    name.equalsIgnoreCase(selected), false, () -> {
                        selected = name;
                        rebuildWidgets();
                    });
            chips.add(rx + colW() - 18, listTop + shown * STEP, 18, ROW, "-", "", false, false, () -> {
                config.jobMines = new ArrayList<>(JobCalc.removeOne(config.jobMines, name));
                change();
            });
            shown++;
        }
        pieceTop = listTop + Math.max(shown, 1) * STEP + 14;
        if (pick.isPresent() && !counts.isEmpty()) {
            List<JobCalc.Part> parts = pick.get().parts();
            for (int i = 0; i < parts.size(); i++) {
                JobCalc.Part p = parts.get(i);
                String key = JobCalc.skipKey(selected, p.gear());
                boolean off = config.jobSkips.contains(key);
                int cw = (colW() - 4) / 2;
                String name = p.gear().isEmpty() ? p.gear()
                        : Character.toUpperCase(p.gear().charAt(0)) + p.gear().substring(1);
                chips.add(rx + (i % 2) * (cw + 4), pieceTop + (i / 2) * STEP, cw, ROW, name,
                        p.cost().isPresent() ? JobCalc.mats(p.cost().getAsLong()) : "?", false, off, () -> {
                            List<String> skips = new ArrayList<>(config.jobSkips);
                            if (!skips.remove(key)) {
                                skips.add(key);
                            }
                            config.jobSkips = skips;
                            change();
                        });
            }
        }

        int by = this.height - 26;
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
        chips.add(rx, by, 70, ROW, "Clear job", "", false, false, () -> {
            config.jobMines = new ArrayList<>();
            config.jobSkips = new ArrayList<>();
            selected = "";
            change();
        });
        chips.add(l + w - 60, by, 60, ROW, "Done", "", false, false, this::onClose);
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
        int rx = x + colW() + 12;
        context.text(this.font, this.title.getString(), x, 16, a, false);
        context.text(this.font, "Rate", x, 35, Chips.MUTED, false);
        OptionalDouble r = rate();
        String parsed = r.isPresent() ? "= " + JobCalc.mats(r.getAsDouble()) + " mats per credit" : "try 1B or 1.25B/1c";
        context.text(this.font, parsed, x + RATE_LABEL_W + 98, 35, r.isPresent() ? 0xFF66CC66 : 0xFFE08080, false);

        context.text(this.font, world.isBlank() ? "NO MINES FOUND" : world.toUpperCase(Locale.ROOT) + " - CLICK TO ADD",
                x, listTop - 12, Chips.MUTED, false);
        context.text(this.font, "YOUR JOB", rx, listTop - 12, Chips.MUTED, false);
        if (!selected.isBlank() && !config.jobMines.isEmpty()) {
            context.text(this.font, selected.toUpperCase(Locale.ROOT) + " - CLICK A PIECE TO LEAVE IT OUT", rx,
                    pieceTop - 12, Chips.MUTED, false);
        } else if (config.jobMines.isEmpty()) {
            context.text(this.font, "Nothing added yet", rx, listTop + 4, Chips.OFF, false);
        }

        List<JobCalc.Entry> entries = new ArrayList<>();
        for (Map.Entry<String, Integer> e : JobCalc.counts(config.jobMines).entrySet()) {
            String name = e.getKey();
            entries.add(JobSource.find(items, name).map(it -> JobCalc.entry(name, it.parts(), config.jobSkips, e.getValue()))
                    .orElse(new JobCalc.Entry(name, 0L, e.getValue(), 1)));
        }
        JobCalc.Total t = JobCalc.total(entries, r);
        if (!config.jobMines.isEmpty()) {
            String sum = JobCalc.mats(t.mats()) + " mats = " + JobCalc.credits(t.credits());
            int sw = this.font.width(sum);
            context.text(this.font, sum, rx + colW() - sw, this.height - 42, 0xFFFFFFFF, false);
            if (t.unknown() > 0) {
                context.text(this.font, Chips.fit(this.font, t.unknown() + " unpriced - open the shop", colW()), rx,
                        this.height - 54, 0xFFE0B060, false);
            }
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
