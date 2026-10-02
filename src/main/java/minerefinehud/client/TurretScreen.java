package minerefinehud.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.joml.Matrix3x2fStack;
import minerefinehud.turret.TurretCalculator;
import minerefinehud.turret.TurretItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The community turret size calculator, in game.
 *
 * VERSION SENSITIVE for the Screen, ButtonWidget and TextFieldWidget API only. The arithmetic is
 * {@link TurretCalculator}, a port of garfieldthelord21's calculator, and is tested there. The
 * result updates as you type, so there is no Calculate button.
 */
public final class TurretScreen extends Screen {

    private static final int FIELD_WIDTH = 80;
    /** Below the title and the mode button. */
    private static final int TOP = 70;

    private final Screen parent;

    private TextFieldWidget turretA;

    /** Kept across re-inits (a window resize rebuilds the widgets), so typed values survive. */
    private String a = "";
    private String b = "";
    private String m = "";

    /**
     * Which question: what two turrets merge into, or what size merger takes a turret to a
     * target size. The two boxes are reused, so switching keeps what was typed.
     */
    private boolean sizeNeeded;

    /** Turrets in the inventory, offered as one-click fills. Read each time the screen opens. */
    private List<TurretItem.Turret> turrets = List.of();

    /** Room for this many fill buttons in a row. */
    private static final int MAX_FILL_BUTTONS = 4;

    public TurretScreen(Screen parent) {
        super(Text.literal("Turret Size Calculator"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int mid = this.width / 2;
        int top = TOP;

        turretA = number(mid - 110 - FIELD_WIDTH / 2, top, a, s -> a = s, 7);
        number(mid + 110 - FIELD_WIDTH / 2, top, b, s -> b = s, 7);
        number(mid - 25, top + 38, m, s -> m = s, 3).setWidth(50);

        turrets = turretsCarried();
        int shown = Math.min(MAX_FILL_BUTTONS, turrets.size());
        int fillWidth = 96;
        int fillLeft = mid - (shown * (fillWidth + 4) - 4) / 2;
        for (int i = 0; i < shown; i++) {
            TurretItem.Turret t = turrets.get(i);
            addDrawableChild(ButtonWidget.builder(Text.literal(t.label()), w -> fill(t))
                    .dimensions(fillLeft + i * (fillWidth + 4), this.height - 78, fillWidth, 20).build());
        }

        addDrawableChild(ButtonWidget.builder(modeLabel(), w -> {
                    sizeNeeded = !sizeNeeded;
                    w.setMessage(modeLabel());
                })
                .dimensions(mid - 80, 32, 160, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Clear"), w -> {
                    a = b = m = "";
                    clearAndInit();
                    setFocused(turretA);
                })
                .dimensions(mid - 104, this.height - 52, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), w -> close())
                .dimensions(mid + 4, this.height - 52, 100, 20).build());

        setInitialFocus(turretA);
    }

    private TextFieldWidget number(int x, int y, String text, java.util.function.Consumer<String> onChange, int max) {
        TextFieldWidget f = new TextFieldWidget(this.textRenderer, x, y, FIELD_WIDTH, 20, Text.empty());
        f.setMaxLength(max);
        f.setText(text);
        f.setTextPredicate(s -> s.matches("[0-9]*\\.?[0-9]*"));
        f.setChangedListener(onChange);
        addDrawableChild(f);
        return f;
    }

    /**
     * Puts a turret's size and merges in the boxes. Size needed: it is your turret. Merge
     * result: it goes in A first, then B, and A's merges are the ones that count.
     */
    private void fill(TurretItem.Turret t) {
        String size = plain(t.size());
        if (sizeNeeded || a.isBlank()) {
            a = size;
            m = String.valueOf(t.merges());
        } else {
            b = size;
        }
        clearAndInit();
    }

    private static String plain(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    /** Every turret in the main inventory, hotbar and off hand, read off the items. */
    private List<TurretItem.Turret> turretsCarried() {
        List<TurretItem.Turret> out = new ArrayList<>();
        try {
            if (this.client == null || this.client.player == null) {
                return out;
            }
            List<net.minecraft.item.ItemStack> stacks = new ArrayList<>(this.client.player.getInventory().getMainStacks());
            stacks.add(this.client.player.getOffHandStack());
            for (net.minecraft.item.ItemStack stack : stacks) {
                if (stack != null && !stack.isEmpty()) {
                    TurretItem.parse(stack.getName().getString(), ShopScanner.lore(stack)).ifPresent(out::add);
                }
            }
        } catch (Exception | NoSuchMethodError | NoClassDefFoundError ignored) {
            // No fill buttons is a fine fallback; typing still works.
        }
        return out;
    }

    private Text modeLabel() {
        return Text.literal(sizeNeeded ? "Mode: size needed" : "Mode: merge result");
    }

    /** Both boxes filled with numbers, with the merges, or empty while half-typed. */
    private Optional<double[]> inputs() {
        try {
            if (a.isBlank() || b.isBlank()) {
                return Optional.empty();
            }
            double previous = m.isBlank() ? 0 : Math.floor(Double.parseDouble(m));
            return Optional.of(new double[] { Double.parseDouble(a), Double.parseDouble(b), previous });
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private Optional<TurretCalculator.Needed> needed() {
        try {
            return inputs().map(in -> TurretCalculator.needed(in[0], in[1], (int) in[2]));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** The result, or empty while a turret box is blank or half-typed. */
    private Optional<TurretCalculator.Result> result() {
        try {
            if (a.isBlank() || b.isBlank()) {
                return Optional.empty();
            }
            int previous = m.isBlank() ? 0 : (int) Math.floor(Double.parseDouble(m));
            return Optional.of(TurretCalculator.combine(Double.parseDouble(a), Double.parseDouble(b), previous));
        } catch (IllegalArgumentException e) {
            // NumberFormatException included: "." on its own, for one.
            return Optional.empty();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Background is drawn by renderWithTooltip; see HudPositionScreen for why not here.
        super.render(context, mouseX, mouseY, delta);

        int mid = this.width / 2;
        int top = TOP;
        int white = 0xFFFFFFFF;
        int muted = 0xFFAAAAAA;

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, mid, 14, white);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(sizeNeeded ? "YOUR TURRET" : "TURRET A"), mid - 110, top - 12, white);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(sizeNeeded ? "TARGET SIZE" : "TURRET B"), mid + 110, top - 12, white);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(sizeNeeded ? "->" : "+"), mid, top + 6, white);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(sizeNeeded ? "YOUR TURRET'S MERGES (OPTIONAL)" : "MERGES (OPTIONAL)"), mid, top + 27, muted);

        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(sizeNeeded ? "MERGER SIZE NEEDED" : "COMBINED SIZE"), mid, top + 72, white);

        String big;
        String detail;
        boolean ok;
        if (sizeNeeded) {
            Optional<TurretCalculator.Needed> n = needed();
            boolean answer = n.map(x -> x.need() == TurretCalculator.Need.SMALLER_MERGER
                    || x.need() == TurretCalculator.Need.LARGER_MERGER).orElse(false);
            big = answer ? String.format(java.util.Locale.ROOT, "%,d", n.get().merger()) : "—";
            detail = n.map(TurretCalculator::explain).orElse("enter your turret and the size you want");
            ok = answer;
        } else {
            Optional<TurretCalculator.Result> r = result();
            big = r.map(TurretCalculator::size).orElse("—");
            detail = r.map(TurretCalculator::details).orElse("enter both turret sizes");
            ok = r.isPresent();
        }
        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        try {
            matrices.translate(mid, top + 86);
            matrices.scale(3.0f, 3.0f);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(big), 0, 0, white);
        } finally {
            matrices.popMatrix();
        }

        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(detail), mid, top + 122,
                ok ? 0xFF66CC66 : muted);

        if (!turrets.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal(sizeNeeded ? "Click a turret to fill in yours:" : "Click turrets to fill in A, then B:"),
                    mid, this.height - 90, muted);
        }

        // Explanations only where they fit, and not when the turret buttons use that space.
        if (turrets.isEmpty() && this.height - 92 > top + 134) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("Smaller value is the efficiency turret (4/9 + 500/9x, 50-100%)."), mid, this.height - 92, muted);
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("Each merge lowers the max cap by 100, starting at 2000."), mid, this.height - 82, muted);
        }
        if (turrets.isEmpty() && this.height - 68 > top + 134) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("Formula from garfieldthelord21's Turret Calculator"), mid, this.height - 68, 0xFF777777);
        }
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
