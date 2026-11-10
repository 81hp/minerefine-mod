package minerefinehud.client;

import minerefinehud.hud.Theme;
import minerefinehud.job.JobCalc;
import minerefinehud.job.JobWorlds;
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

    /** Mine and job rows, the dimension chips and the piece chips. */
    private static final int ROW = 16;
    private static final int STEP = 18;
    private static final int CHIP = 14;
    private static final int CHIP_STEP = 16;
    /** Room for the label in front of the rate box. */
    private static final int RATE_LABEL_W = 30;

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

        // Title, then the rate: the one thing typed, on the same line.
        EditBox rate = new EditBox(this.font, l + 100 + RATE_LABEL_W, 10, 84, 16, Component.empty());
        rate.setMaxLength(24);
        rate.setValue(config.jobRate == null ? "" : config.jobRate);
        Compat.filter(rate, s -> s.matches("[0-9A-Za-z.,/ ]*"), s -> {
            config.jobRate = s;
            onChange.run();
        });
        addRenderableWidget(rate);

        // Dimensions, as small chips that wrap.
        List<String> worlds = JobSource.worlds(items);
        if ((world.isBlank() || !worlds.contains(world)) && !worlds.isEmpty()) {
            world = worlds.get(0);
        }
        int x = l;
        int y = 34;
        for (String name : worlds) {
            int cw = this.font.width(name) + 14;
            if (x + cw > l + w) {
                x = l;
                y += CHIP_STEP;
            }
            chips.add(x, y, cw, CHIP, name, "", name.equals(world), false, () -> {
                world = name;
                page = 0;
                rebuildWidgets();
            });
            x += cw + 3;
        }
        listTop = y + CHIP + 14;

        // The totals and the buttons sit under the lists.
        int bottom = this.height - 52;

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
        String pickedKey = JobWorlds.canonical(selected);
        if (counts.keySet().stream().noneMatch(n -> JobWorlds.canonical(n).equals(pickedKey))) {
            selected = counts.isEmpty() ? "" : counts.keySet().iterator().next();
        }
        Optional<JobSource.Item> pick = counts.isEmpty() ? Optional.empty() : JobSource.find(items, selected);
        int parts = pick.map(p -> p.parts().size()).orElse(0);
        // Two columns of pieces with their prices when there is room, else three with names only.
        int region = bottom - listTop;
        int pieceCols = 2;
        if (parts > 0 && region - (((parts + 1) / 2) * CHIP_STEP + 14) < 2 * STEP) {
            pieceCols = 3;
        }
        int pieceH = parts == 0 ? 0 : ((parts + pieceCols - 1) / pieceCols) * CHIP_STEP + 14;
        int jobRows = Math.max(1, (region - pieceH) / STEP);
        int shown = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (shown >= jobRows) {
                break;
            }
            String name = e.getKey();
            Optional<JobSource.Item> item = JobSource.find(items, name);
            JobCalc.Entry entry = item.map(it -> JobCalc.entry(name, it.parts(), config.jobSkips, e.getValue()))
                    .orElse(new JobCalc.Entry(name, 0L, e.getValue(), 1));
            String label = (e.getValue() > 1 ? e.getValue() + "x " : "") + item.map(JobSource.Item::name).orElse(name);
            chips.add(rx, listTop + shown * STEP, colW() - 22, ROW, label,
                    JobCalc.mats((double) entry.mats() * entry.times()) + (entry.missing() > 0 ? "+" : ""),
                    JobWorlds.canonical(name).equals(JobWorlds.canonical(selected)), false, () -> {
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
        if (pick.isPresent()) {
            List<JobCalc.Part> list = pick.get().parts();
            int cw = (colW() - (pieceCols - 1) * 3) / pieceCols;
            for (int i = 0; i < list.size(); i++) {
                JobCalc.Part p = list.get(i);
                boolean off = JobCalc.isSkipped(config.jobSkips, selected, p.gear());
                String name = p.gear().isEmpty() ? p.gear()
                        : Character.toUpperCase(p.gear().charAt(0)) + p.gear().substring(1);
                String price = pieceCols == 3 ? ""
                        : p.cost().isPresent() ? JobCalc.mats(p.cost().getAsLong()) + (p.partial() ? "+" : "") : "?";
                chips.add(rx + (i % pieceCols) * (cw + 3), pieceTop + (i / pieceCols) * CHIP_STEP, cw, CHIP, name,
                        price, false, off, () -> {
                            config.jobSkips = JobCalc.toggleSkip(config.jobSkips, selected, p.gear());
                            change();
                        });
            }
        }

        // Bottom bar: pages on the left, the job's buttons on the right.
        int by = this.height - 26;
        chips.add(l, by, 22, CHIP, "<", "", false, page == 0, () -> {
            if (page > 0) {
                page--;
                rebuildWidgets();
            }
        });
        chips.add(l + 26, by, 22, CHIP, ">", "", false, page >= pages - 1, () -> {
            if (page < pages - 1) {
                page++;
                rebuildWidgets();
            }
        });
        chips.add(rx, by, 46, CHIP, "Clear", "", false, false, () -> {
            config.jobMines = new ArrayList<>();
            config.jobSkips = new ArrayList<>();
            selected = "";
            change();
        });
        chips.add(rx + 50, by, 52, CHIP, "Prices", "", false, false, () -> {
            if (this.minecraft != null) {
                Compat.setScreen(this.minecraft, new PriceScreen(this, config));
            }
        });
        chips.add(l + w - 46, by, 46, CHIP, "Done", "", false, false, this::onClose);
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
        context.text(this.font, this.title.getString(), x, 15, a, false);
        context.text(this.font, "Rate", x + 100, 15, Chips.MUTED, false);
        OptionalDouble r = rate();
        String parsed = r.isPresent() ? "= " + JobCalc.mats(r.getAsDouble()) + " / credit" : "try 1B or 1.25B/1c";
        context.text(this.font, Chips.fit(this.font, parsed, winW() - 24 - (RATE_LABEL_W + 100 + 84 + 6)),
                x + RATE_LABEL_W + 100 + 84 + 6, 15, r.isPresent() ? 0xFF66CC66 : 0xFFE08080, false);

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
            context.text(this.font, sum, rx + colW() - sw, this.height - 36, 0xFFFFFFFF, false);
            if (t.unknown() > 0) {
                context.text(this.font, Chips.fit(this.font, "+ = a price is not complete yet", colW()), rx,
                        this.height - 46, 0xFFE0B060, false);
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
