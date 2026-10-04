package minerefinehud.client;

import org.joml.Matrix3x2fStack;
import minerefinehud.turret.TurretCalculator;
import minerefinehud.turret.TurretItem;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
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

    private EditBox turretA;

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
        super(Component.literal("Turret Size Calculator"));
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
            addRenderableWidget(Button.builder(Component.literal(t.label()), w -> fill(t))
                    .bounds(fillLeft + i * (fillWidth + 4), this.height - 78, fillWidth, 20).build());
        }

        addRenderableWidget(Button.builder(modeLabel(), w -> {
                    sizeNeeded = !sizeNeeded;
                    w.setMessage(modeLabel());
                })
                .bounds(mid - 80, 32, 160, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Clear"), w -> {
                    a = b = m = "";
                    rebuildWidgets();
                    setFocused(turretA);
                })
                .bounds(mid - 104, this.height - 52, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), w -> onClose())
                .bounds(mid + 4, this.height - 52, 100, 20).build());

        setInitialFocus(turretA);
    }

    private EditBox number(int x, int y, String text, java.util.function.Consumer<String> onChange, int max) {
        EditBox f = new EditBox(this.font, x, y, FIELD_WIDTH, 20, Component.empty());
        f.setMaxLength(max);
        f.setValue(text);
        Compat.filter(f, s -> s.matches("[0-9]*\\.?[0-9]*"), onChange);
        addRenderableWidget(f);
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
        rebuildWidgets();
    }

    private static String plain(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    /** Every turret in the main inventory, hotbar and off hand, read off the items. */
    private List<TurretItem.Turret> turretsCarried() {
        List<TurretItem.Turret> out = new ArrayList<>();
        try {
            if (this.minecraft == null || this.minecraft.player == null) {
                return out;
            }
            List<net.minecraft.world.item.ItemStack> stacks = new ArrayList<>(this.minecraft.player.getInventory().getNonEquipmentItems());
            stacks.add(this.minecraft.player.getOffhandItem());
            for (net.minecraft.world.item.ItemStack stack : stacks) {
                if (stack != null && !stack.isEmpty()) {
                    TurretItem.parse(stack.getHoverName().getString(), ShopScanner.lore(stack)).ifPresent(out::add);
                }
            }
        } catch (Exception | NoSuchMethodError | NoClassDefFoundError ignored) {
            // No fill buttons is a fine fallback; typing still works.
        }
        return out;
    }

    private Component modeLabel() {
        return Component.literal(sizeNeeded ? "Mode: size needed" : "Mode: merge result");
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
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // Background is drawn by renderWithTooltip; see HudPositionScreen for why not here.
        super.extractRenderState(context, mouseX, mouseY, delta);

        int mid = this.width / 2;
        int top = TOP;
        int white = 0xFFFFFFFF;
        int muted = 0xFFAAAAAA;

        context.centeredText(this.font, this.title, mid, 14, white);
        context.centeredText(this.font,
                Component.literal(sizeNeeded ? "YOUR TURRET" : "TURRET A"), mid - 110, top - 12, white);
        context.centeredText(this.font,
                Component.literal(sizeNeeded ? "TARGET SIZE" : "TURRET B"), mid + 110, top - 12, white);
        context.centeredText(this.font, Component.literal(sizeNeeded ? "->" : "+"), mid, top + 6, white);
        context.centeredText(this.font,
                Component.literal(sizeNeeded ? "YOUR TURRET'S MERGES (OPTIONAL)" : "MERGES (OPTIONAL)"), mid, top + 27, muted);

        context.centeredText(this.font,
                Component.literal(sizeNeeded ? "MERGER SIZE NEEDED" : "COMBINED SIZE"), mid, top + 72, white);

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
        Matrix3x2fStack matrices = context.pose();
        matrices.pushMatrix();
        try {
            matrices.translate(mid, top + 86);
            matrices.scale(3.0f, 3.0f);
            context.centeredText(this.font, Component.literal(big), 0, 0, white);
        } finally {
            matrices.popMatrix();
        }

        context.centeredText(this.font, Component.literal(detail), mid, top + 122,
                ok ? 0xFF66CC66 : muted);

        if (!turrets.isEmpty()) {
            context.centeredText(this.font,
                    Component.literal(sizeNeeded ? "Click a turret to fill in yours:" : "Click turrets to fill in A, then B:"),
                    mid, this.height - 90, muted);
        }

        // Explanations only where they fit, and not when the turret buttons use that space.
        if (turrets.isEmpty() && this.height - 92 > top + 134) {
            context.centeredText(this.font,
                    Component.literal("Smaller value is the efficiency turret (4/9 + 500/9x, 50-100%)."), mid, this.height - 92, muted);
            context.centeredText(this.font,
                    Component.literal("Each merge lowers the max cap by 100, starting at 2000."), mid, this.height - 82, muted);
        }
        if (turrets.isEmpty() && this.height - 68 > top + 134) {
            context.centeredText(this.font,
                    Component.literal("Formula from garfieldthelord21's Turret Calculator"), mid, this.height - 68, 0xFF777777);
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
