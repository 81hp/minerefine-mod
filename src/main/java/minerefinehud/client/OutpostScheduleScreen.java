package minerefinehud.client;

import minerefinehud.hud.ExtrasHud;
import minerefinehud.hud.Theme;
import minerefinehud.outpost.OutpostTimers;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.ZoneId;
import java.util.List;

/**
 * When each outpost opens over the next day, one row per outpost, in the player's own clock. The
 * times are the ones the outpost panel reads from /outpost, counted on by {@link OutpostTimers}:
 * open five minutes, closed four hours, again.
 *
 * VERSION SENSITIVE for the Screen and Button API only.
 */
public final class OutpostScheduleScreen extends Screen {

    /** Openings per outpost, about a day. Fewer when the window is too narrow. */
    static final int SHOWN = 6;

    private static final int TOP = 56;
    private static final int CELL_H = 20;
    /** A cell, the countdown under the first one, and a gap. */
    private static final int ROW_H = 36;
    private static final int GAP = 4;

    private static final int WHITE = 0xFFFFFFFF;
    private static final int MUTED = 0xFFAAAAAA;
    private static final int DARK = 0xFF101010;
    private static final int OPEN = 0xFF3FA34D;
    private static final int TODAY = 0x40FFFFFF;
    private static final int TOMORROW = 0x18FFFFFF;

    private final Screen parent;
    private final ModConfig config;
    private final Runnable onChange;
    private final Runnable checkNow;
    /** Asked once per opening of the screen; init runs again on every window resize. */
    private boolean asked;

    public OutpostScheduleScreen(Screen parent, ModConfig config, Runnable onChange, Runnable checkNow) {
        super(Component.literal("Outpost Schedule"));
        this.parent = parent;
        this.config = config;
        this.onChange = onChange;
        this.checkNow = checkNow;
    }

    @Override
    protected void init() {
        if (!asked && OutpostTimers.scheduleStale(config.outpostData, System.currentTimeMillis())) {
            asked = true;
            checkNow.run();
        }
        int mid = this.width / 2;
        addRenderableWidget(Button.builder(clockLabel(), w -> {
                    config.outpostTwelveHour = !config.outpostTwelveHour;
                    onChange.run();
                    w.setMessage(clockLabel());
                })
                .bounds(mid - 124, this.height - 28, 120, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), w -> onClose())
                .bounds(mid + 4, this.height - 28, 120, 20).build());
    }

    private Component clockLabel() {
        return Component.literal(config.outpostTwelveHour ? "Clock: 12-hour" : "Clock: 24-hour");
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // Background is drawn by renderWithTooltip; see HudPositionScreen for why not here.
        super.extractRenderState(context, mouseX, mouseY, delta);

        long now = System.currentTimeMillis();
        ZoneId zone = ZoneId.systemDefault();
        boolean twelve = config.outpostTwelveHour;
        int accent = Theme.parseOr(config.menuAccent, 0xFFFFD24A);
        int mid = this.width / 2;

        context.centeredText(this.font, this.title, mid, 14, WHITE);
        context.centeredText(this.font,
                Component.literal("Open 5 minutes, then closed 4 hours."), mid, 30, MUTED);

        int panel = (Theme.parseOr(config.menuBackground, 0xFF182221) & 0x00FFFFFF) | 0xE0000000;
        List<OutpostTimers.Schedule> rows = OutpostTimers.schedule(config.outpostData, now, SHOWN);
        if (rows.isEmpty()) {
            // No times always counts as stale, so init has asked for /outpost by now.
            String hint = "Read from /outpost once you are back in game.";
            int half = this.font.width(hint) / 2 + 12;
            context.fill(mid - half, TOP + 8, mid + half, TOP + 50, panel);
            context.fill(mid - half, TOP + 8, mid + half, TOP + 9, accent);
            context.centeredText(this.font, Component.literal("No outpost times yet."), mid, TOP + 18, WHITE);
            context.centeredText(this.font, Component.literal(hint), mid, TOP + 34, MUTED);
            return;
        }

        int nameW = this.font.width("Outpost");
        for (OutpostTimers.Schedule row : rows) {
            nameW = Math.max(nameW, this.font.width(row.name()));
        }
        int cellW = this.font.width(twelve ? "12:59 PM" : "23:59") + 14;
        int shown = SHOWN;
        while (shown > 1 && tableWidth(nameW, cellW, shown) > this.width - 24) {
            shown--;
        }
        int total = tableWidth(nameW, cellW, shown);
        int left = mid - total / 2;
        int bottom = TOP + rows.size() * ROW_H;

        context.fill(left - 10, TOP - 10, left + total + 10, bottom + 2, panel);
        context.fill(left - 10, TOP - 10, left + total + 10, TOP - 9, accent);

        for (int i = 0; i < rows.size(); i++) {
            OutpostTimers.Schedule row = rows.get(i);
            int y = TOP + i * ROW_H;
            context.text(this.font, row.name(), left, y + 6, WHITE, false);
            for (int j = 0; j < shown && j < row.opens().size(); j++) {
                long at = row.opens().get(j);
                int x = left + nameW + 12 + j * (cellW + GAP);
                boolean openNow = j == 0 && row.openNow();
                int box;
                int ink;
                if (openNow) {
                    box = OPEN;
                    ink = WHITE;
                } else if (j == 0) {
                    box = accent;
                    ink = DARK;
                } else if (OutpostTimers.daysAhead(at, now, zone) == 0L) {
                    box = TODAY;
                    ink = WHITE;
                } else {
                    box = TOMORROW;
                    ink = MUTED;
                }
                context.fill(x, y, x + cellW, y + CELL_H, box);
                String time = openNow ? "OPEN" : OutpostTimers.timeOfDay(at, zone, twelve);
                context.text(this.font, time, x + (cellW - this.font.width(time)) / 2, y + 6, ink, false);
                if (j == 0) {
                    String left0 = openNow
                            ? "ends in " + OutpostTimers.clock(at + OutpostTimers.BATTLE_MS - now)
                            : "in " + OutpostTimers.clock(at - now);
                    context.text(this.font, left0, x + (cellW - this.font.width(left0)) / 2, y + CELL_H + 3,
                            openNow ? OPEN : accent, false);
                }
            }
        }

        // What the colours mean and how fresh the times are, under the table, where they fit above
        // the buttons (a big GUI scale on a small window leaves little room).
        int ly = bottom + 10;
        if (ly + 25 > this.height - 32) {
            return;
        }
        String[] labels = { "open", "next", "today", "tomorrow" };
        int[] swatches = { OPEN, accent, TODAY, TOMORROW };
        int legendW = 0;
        for (String l : labels) {
            legendW += 12 + this.font.width(l) + 14;
        }
        int lx = mid - legendW / 2;
        for (int i = 0; i < labels.length; i++) {
            context.fill(lx, ly, lx + 8, ly + 8, swatches[i]);
            context.text(this.font, labels[i], lx + 12, ly, MUTED, false);
            lx += 12 + this.font.width(labels[i]) + 14;
        }

        // Right under the table, clear of the chat lines that show through at the bottom.
        context.centeredText(this.font, Component.literal(status(now)), mid, ly + 16, MUTED);
    }

    private static int tableWidth(int nameW, int cellW, int shown) {
        return nameW + 12 + shown * (cellW + GAP) - GAP;
    }

    /** How fresh the times are, and whether /outpost will be read again. */
    private String status(long now) {
        long read = OutpostTimers.oldestRead(config.outpostData);
        // Hidden commands wait while any screen is open, so the check goes once the player plays on.
        String ago = ExtrasHud.ago(now - read);
        String when = read <= 0L ? "Not read yet" : "Read " + ("now".equals(ago) ? "just now" : ago);
        if (asked && OutpostTimers.scheduleStale(config.outpostData, now)) {
            return when + ", reading /outpost again in game";
        }
        return when + ", in your PC's time";
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
