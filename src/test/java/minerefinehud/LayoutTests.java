package minerefinehud;

import minerefinehud.hud.Anchor;
import minerefinehud.hud.HudLayout;
import minerefinehud.hud.HudModel;
import minerefinehud.hud.Theme;

/** Tests for overlay placement and dragging. */
public final class LayoutTests {

    private static int passed = 0;
    private static int failed = 0;

    private static final int W = 1920;
    private static final int H = 1080;
    private static final int PW = 200;
    private static final int PH = 120;

    public static void main(String[] args) {
        anchorsPlaceCorrectly();
        offsetsNudgeFromTheAnchor();
        panelCanNeverBeLostOffScreen();
        anchorSurvivesResize();
        draggingFollowsTheCursor();
        draggingRepinsTheAnchor();
        slowDragIsNotLost();
        dragToKeepsGrabPoint();
        anchorForPicksThirds();
        snapTidiesUp();
        parsingIsForgiving();
        scaleStaysSane();
        scaledPanelsStayOnScreen();
        coloursParse();

        System.out.println();
        System.out.println("passed: " + passed + "   failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void anchorsPlaceCorrectly() {
        eq("top left x", 0, at(Anchor.TOP_LEFT).x());
        eq("top left y", 0, at(Anchor.TOP_LEFT).y());

        eq("bottom right x", W - PW, at(Anchor.BOTTOM_RIGHT).x());
        eq("bottom right y", H - PH, at(Anchor.BOTTOM_RIGHT).y());

        eq("centre x", (W - PW) / 2, at(Anchor.CENTER).x());
        eq("centre y", (H - PH) / 2, at(Anchor.CENTER).y());

        eq("top centre x", (W - PW) / 2, at(Anchor.TOP_CENTER).x());
        eq("top centre y", 0, at(Anchor.TOP_CENTER).y());
    }

    private static void offsetsNudgeFromTheAnchor() {
        var r = HudLayout.place(new HudLayout.Placement(Anchor.TOP_LEFT, 4, 4), PW, PH, W, H);
        eq("offset from top left x", 4, r.x());
        eq("offset from top left y", 4, r.y());

        // A negative offset from the right edge moves it inward, which is the intuitive direction.
        var br = HudLayout.place(new HudLayout.Placement(Anchor.BOTTOM_RIGHT, -10, -10), PW, PH, W, H);
        eq("inset from bottom right x", W - PW - 10, br.x());
        eq("inset from bottom right y", H - PH - 10, br.y());
    }

    private static void panelCanNeverBeLostOffScreen() {
        // A wildly wrong hand-edited config must still leave something grabbable.
        var far = HudLayout.place(new HudLayout.Placement(Anchor.TOP_LEFT, 99999, 99999), PW, PH, W, H);
        yes("clamped on the right", far.x() < W);
        yes("clamped at the bottom", far.y() < H);
        yes("still partly visible horizontally", far.x() + PW > 0);
        yes("still partly visible vertically", far.y() + PH > 0);

        var negative = HudLayout.place(new HudLayout.Placement(Anchor.TOP_LEFT, -99999, -99999), PW, PH, W, H);
        yes("clamped on the left", negative.x() + PW > 0);
        yes("clamped at the top", negative.y() + PH > 0);
    }

    private static void anchorSurvivesResize() {
        // The point of anchoring: a bottom-right panel stays bottom-right on a smaller window.
        var placement = new HudLayout.Placement(Anchor.BOTTOM_RIGHT, -10, -10);
        var big = HudLayout.place(placement, PW, PH, 1920, 1080);
        var small = HudLayout.place(placement, PW, PH, 1280, 720);

        eq("inset from right is unchanged", 1920 - big.x() - PW, 1280 - small.x() - PW);
        eq("inset from bottom is unchanged", 1080 - big.y() - PH, 720 - small.y() - PH);
        yes("actually moved with the window", big.x() != small.x());
    }

    private static void draggingFollowsTheCursor() {
        var start = new HudLayout.Placement(Anchor.TOP_LEFT, 0, 0);
        var moved = HudLayout.drag(start, PW, PH, W, H, 150, 90);
        var rect = HudLayout.place(moved, PW, PH, W, H);
        eq("panel moved by the drag delta in x", 150, rect.x());
        eq("panel moved by the drag delta in y", 90, rect.y());
    }

    private static void draggingRepinsTheAnchor() {
        // Drag from the top left corner all the way to the bottom right.
        var start = new HudLayout.Placement(Anchor.TOP_LEFT, 0, 0);
        var dropped = HudLayout.drag(start, PW, PH, W, H, W - PW, H - PH);

        eq("anchor repinned to bottom right", Anchor.BOTTOM_RIGHT, dropped.anchor());

        // And it must not have jumped: the panel stays exactly where it was dropped.
        var rect = HudLayout.place(dropped, PW, PH, W, H);
        eq("no jump on drop, x", W - PW, rect.x());
        eq("no jump on drop, y", H - PH, rect.y());

        // Now the real payoff: it is still bottom right at another resolution.
        var smaller = HudLayout.place(dropped, PW, PH, 1280, 720);
        eq("still pinned bottom right after resize, x", 1280 - PW, smaller.x());
        eq("still pinned bottom right after resize, y", 720 - PH, smaller.y());
    }

    private static void slowDragIsNotLost() {
        // GUI scale 3: a one-pixel mouse move arrives as a third of a GUI pixel. Thirty of them
        // should move the panel ten pixels.
        var start = new HudLayout.Placement(Anchor.TOP_LEFT, 100, 100);
        var summed = start;
        for (int i = 0; i < 30; i++) {
            summed = HudLayout.drag(summed, PW, PH, W, H, 1.0 / 3.0, 0);
        }
        eq("old delta drag loses slow movement", 100, HudLayout.place(summed, PW, PH, W, H).x());

        double grabX = 10;
        double mouseX = 110;
        for (int i = 0; i < 30; i++) {
            mouseX += 1.0 / 3.0;
        }
        var absolute = HudLayout.dragTo(PW, PH, W, H, mouseX - grabX, 100);
        eq("absolute drag keeps slow movement", 110, HudLayout.place(absolute, PW, PH, W, H).x());
    }

    private static void dragToKeepsGrabPoint() {
        // Dropped in the bottom right third: repinned there, and exactly where it was put.
        var p = HudLayout.dragTo(PW, PH, W, H, W - PW - 30, H - PH - 20);
        eq("dragTo repins", Anchor.BOTTOM_RIGHT, p.anchor());
        var r = HudLayout.place(p, PW, PH, W, H);
        eq("dragTo exact x", W - PW - 30, r.x());
        eq("dragTo exact y", H - PH - 20, r.y());

        // Pulled past the edge and back: the panel returns to the cursor rather than lagging.
        HudLayout.dragTo(PW, PH, W, H, -5000, 50);
        var back = HudLayout.place(HudLayout.dragTo(PW, PH, W, H, 40, 50), PW, PH, W, H);
        eq("no lag after hitting an edge", 40, back.x());
    }

    private static void anchorForPicksThirds() {
        eq("top left third", Anchor.TOP_LEFT, HudLayout.anchorFor(10, 10, W, H));
        eq("dead centre", Anchor.CENTER, HudLayout.anchorFor(W / 2, H / 2, W, H));
        eq("bottom right third", Anchor.BOTTOM_RIGHT, HudLayout.anchorFor(W - 10, H - 10, W, H));
        eq("top centre", Anchor.TOP_CENTER, HudLayout.anchorFor(W / 2, 10, W, H));
        eq("middle left", Anchor.MIDDLE_LEFT, HudLayout.anchorFor(10, H / 2, W, H));
    }

    private static void snapTidiesUp() {
        var messy = new HudLayout.Placement(Anchor.BOTTOM_RIGHT, -137, 42);
        var tidy = HudLayout.snapToAnchor(messy);
        eq("offset cleared", 0, tidy.offsetX());
        eq("anchor kept", Anchor.BOTTOM_RIGHT, tidy.anchor());
        var rect = HudLayout.place(tidy, PW, PH, W, H);
        eq("sits flush in the corner", W - PW, rect.x());
    }

    private static void parsingIsForgiving() {
        eq("exact", Anchor.BOTTOM_RIGHT, Anchor.parse("BOTTOM_RIGHT").orElse(null));
        eq("lowercase", Anchor.BOTTOM_RIGHT, Anchor.parse("bottom_right").orElse(null));
        eq("hyphenated", Anchor.BOTTOM_RIGHT, Anchor.parse("bottom-right").orElse(null));
        eq("spaced", Anchor.BOTTOM_RIGHT, Anchor.parse("Bottom Right").orElse(null));
        yes("nonsense rejected", Anchor.parse("somewhere").isEmpty());
        yes("null rejected", Anchor.parse(null).isEmpty());
        eq("label", "Bottom right", Anchor.BOTTOM_RIGHT.label());
    }

    private static HudLayout.Rect at(Anchor anchor) {
        return HudLayout.place(new HudLayout.Placement(anchor, 0, 0), PW, PH, W, H);
    }

    private static void scaleStaysSane() {
        eq("normal size", 1.0, HudLayout.clampScale(1.0));
        eq("zero from a hand edit is normal size", 1.0, HudLayout.clampScale(0.0));
        eq("NaN is normal size", 1.0, HudLayout.clampScale(Double.NaN));
        eq("capped large", HudLayout.MAX_SCALE, HudLayout.clampScale(50.0));
        eq("capped small", HudLayout.MIN_SCALE, HudLayout.clampScale(0.1));
        eq("one step up", 1.25, HudLayout.stepScale(1.0, 1));
        eq("odd value snaps to a step", 1.25, HudLayout.stepScale(1.1, 1));
        eq("cannot step past the top", HudLayout.MAX_SCALE, HudLayout.stepScale(HudLayout.MAX_SCALE, 1));
        eq("scaled size rounds up", 151, HudLayout.scaled(101, 1.49));
    }

    private static void scaledPanelsStayOnScreen() {
        // A double-size panel anchored bottom right sits flush in the corner, not half off it.
        int w = HudLayout.scaled(PW, 2.0);
        int h = HudLayout.scaled(PH, 2.0);
        HudLayout.Rect r = HudLayout.place(new HudLayout.Placement(Anchor.BOTTOM_RIGHT, 0, 0), w, h, W, H);
        eq("scaled right edge", W, r.x() + r.width());
        eq("scaled bottom edge", H, r.y() + r.height());
    }

    private static void coloursParse() {
        eq("hash form", 0xFFFFD24A, Theme.parse("#FFD24A").orElse(0));
        eq("bare form, lower case", 0xFFFFD24A, Theme.parse("ffd24a").orElse(0));
        eq("short form", 0xFFFF0000, Theme.parse("#f00").orElse(0));
        yes("half-typed is rejected", Theme.parse("#FFD2").isEmpty());
        yes("not hex is rejected", Theme.parse("#GGGGGG").isEmpty());
        eq("bad entry falls back", 0xFF123456, Theme.parseOr("oops", 0xFF123456));
        eq("formats back", "#FFD24A", Theme.format(0xFFFFD24A));
        eq("round trip for every default", Theme.defaults().barTrack(),
                Theme.parse(Theme.format(Theme.defaults().barTrack())).orElse(0));
        eq("style lookup", Theme.defaults().warn(), Theme.defaults().color(HudModel.Style.WARN));
        eq("background off", 0, Theme.background(0));
        eq("background half", 0x80000000, Theme.background(50));
        eq("background clamped", 0xFF000000, Theme.background(400));
    }

    private static void eq(String what, Object expected, Object actual) {
        if (expected.equals(actual)) pass(what); else fail(what, expected, actual);
    }

    private static void yes(String what, boolean c) {
        if (c) pass(what); else fail(what, "true", "false");
    }

    private static void pass(String what) {
        passed++;
        System.out.println("  ok   " + what);
    }

    private static void fail(String what, Object e, Object a) {
        failed++;
        System.out.println("  FAIL " + what + "  expected <" + e + "> got <" + a + ">");
    }
}
