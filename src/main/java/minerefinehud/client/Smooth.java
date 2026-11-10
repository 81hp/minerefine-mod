package minerefinehud.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

/**
 * Smooth rounded shapes for the settings screen, by Arreba.
 *
 * Minecraft's GUI only fills whole pixels, which is why rounded corners come out jagged. Each shape
 * here is drawn at the screen's real pixel size instead: the pose is scaled down by the GUI scale,
 * every row is a run of whole pixels with its two ends faded by how much of them the shape covers.
 * Colours are 0xAARRGGBB; sizes are in GUI units, so a shape sits where the plain widgets do.
 *
 * VERSION SENSITIVE for the pose stack and the GUI scale only.
 */
final class Smooth {

    private Smooth() {
    }

    private static float unit() {
        return (float) Minecraft.getInstance().getWindow().getGuiScale();
    }

    static int alpha(int argb, float a) {
        float k = a < 0f ? 0f : Math.min(a, 1f);
        int al = Math.round(((argb >>> 24) & 0xFF) * k);
        return (al << 24) | (argb & 0xFFFFFF);
    }

    static int mix(int a, int b, float t) {
        float k = t < 0f ? 0f : Math.min(t, 1f);
        int al = Math.round(((a >>> 24) & 0xFF) * (1f - k) + ((b >>> 24) & 0xFF) * k);
        int r = Math.round(((a >> 16) & 0xFF) * (1f - k) + ((b >> 16) & 0xFF) * k);
        int gr = Math.round(((a >> 8) & 0xFF) * (1f - k) + ((b >> 8) & 0xFF) * k);
        int bl = Math.round((a & 0xFF) * (1f - k) + (b & 0xFF) * k);
        return (al << 24) | (r << 16) | (gr << 8) | bl;
    }

    /** Dark ink on a light colour, white on a dark one. */
    static int contrast(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return 0.299 * r + 0.587 * g + 0.114 * b > 140 ? 0xFF101018 : 0xFFFFFFFF;
    }

    /** How far in from the straight edge a row {@code d} pixels from the top sits, for corner radius {@code r}. */
    private static float cornerInset(float d, float r) {
        if (r <= 0f || d >= r) {
            return 0f;
        }
        float dy = r - d;
        return r - (float) Math.sqrt(Math.max(0f, r * r - dy * dy));
    }

    private static int rowColor(float yc, float h, int top, int bot) {
        return top == bot ? top : mix(top, bot, yc / h);
    }

    private static void px(GuiGraphicsExtractor context, int x, int y, int color, float cov) {
        context.fill(x, y, x + 1, y + 1, alpha(color, cov));
    }

    /** One row of the shape, from x0 to x1 in real pixels, the ends faded by their coverage. */
    private static void span(GuiGraphicsExtractor context, int y, float x0, float x1, int color) {
        if (x1 <= x0) {
            return;
        }
        int a = (int) Math.floor(x0);
        int b = (int) Math.ceil(x1);
        if (b - a <= 1) {
            px(context, a, y, color, x1 - x0);
            return;
        }
        float lc = (a + 1) - x0;
        float rc = x1 - (b - 1);
        int fa = a;
        int fb = b;
        if (lc < 0.999f) {
            px(context, a, y, color, lc);
            fa = a + 1;
        }
        if (rc < 0.999f) {
            px(context, b - 1, y, color, rc);
            fb = b - 1;
        }
        if (fb > fa) {
            context.fill(fa, y, fb, y + 1, color);
        }
    }

    private static void shape(GuiGraphicsExtractor context, float x, float y, float w, float h, float rt, float rb,
                              int top, int bot) {
        float u = unit();
        int px = Math.round(x * u);
        int py = Math.round(y * u);
        int pw = Math.round(w * u);
        int ph = Math.round(h * u);
        if (pw <= 0 || ph <= 0) {
            return;
        }
        float lim = Math.min(pw, ph) / 2f;
        float rT = Math.min(rt * u, lim);
        float rB = Math.min(rb * u, lim);
        if (rT + rB > ph) {
            float k = ph / (rT + rB);
            rT *= k;
            rB *= k;
        }
        int ti = (int) Math.ceil(rT);
        int bi = (int) Math.ceil(rB);

        Matrix3x2fStack pose = context.pose();
        pose.pushMatrix();
        try {
            pose.scale(1f / u, 1f / u);
            for (int j = 0; j < ti; j++) {
                float yc = j + 0.5f;
                float in = cornerInset(yc, rT);
                span(context, py + j, px + in, px + pw - in, rowColor(yc, ph, top, bot));
            }
            for (int j = 0; j < bi; j++) {
                float d = j + 0.5f;
                float in = cornerInset(d, rB);
                int row = ph - 1 - j;
                span(context, py + row, px + in, px + pw - in, rowColor(ph - d, ph, top, bot));
            }
            int y0 = py + ti;
            int y1 = py + ph - bi;
            if (y1 > y0) {
                if (top == bot) {
                    context.fill(px, y0, px + pw, y1, top);
                } else {
                    context.fillGradient(px, y0, px + pw, y1, rowColor(ti, ph, top, bot), rowColor(ph - bi, ph, top, bot));
                }
            }
        } finally {
            pose.popMatrix();
        }
    }

    static void rect(GuiGraphicsExtractor context, float x, float y, float w, float h, float r, int color) {
        shape(context, x, y, w, h, r, r, color, color);
    }

    static void rect(GuiGraphicsExtractor context, float x, float y, float w, float h, float r, int top, int bottom) {
        shape(context, x, y, w, h, r, r, top, bottom);
    }

    /** Rounded on top only. */
    static void rectTop(GuiGraphicsExtractor context, float x, float y, float w, float h, float rt, int top, int bottom) {
        shape(context, x, y, w, h, rt, 0f, top, bottom);
    }

    private static void ringRow(GuiGraphicsExtractor context, int x, int y, int w, int h, float outer, float inner,
                                int t, int row, int color) {
        float yc = row + 0.5f;
        float o = cornerInset(Math.min(yc, h - yc), outer);
        if (row < t || row >= h - t) {
            span(context, y + row, x + o, x + w - o, color);
        } else {
            float yi = yc - t;
            float hi = h - 2f * t;
            float ii = t + cornerInset(Math.min(yi, hi - yi), inner);
            span(context, y + row, x + o, x + ii, color);
            span(context, y + row, x + w - ii, x + w - o, color);
        }
    }

    /** An outline of thickness {@code t} just inside the rectangle. */
    static void ring(GuiGraphicsExtractor context, float x, float y, float w, float h, float r, float t, int color) {
        float u = unit();
        int px = Math.round(x * u);
        int py = Math.round(y * u);
        int pw = Math.round(w * u);
        int ph = Math.round(h * u);
        if (pw <= 0 || ph <= 0) {
            return;
        }
        int th = Math.max(1, Math.round(t * u));
        float outer = Math.min(r * u, Math.min(pw, ph) / 2f);
        float inner = Math.max(0f, outer - th);
        int edge = Math.min((int) Math.ceil(Math.max(outer, (float) th)), (ph + 1) / 2);

        Matrix3x2fStack pose = context.pose();
        pose.pushMatrix();
        try {
            pose.scale(1f / u, 1f / u);
            for (int row = 0; row < edge; row++) {
                ringRow(context, px, py, pw, ph, outer, inner, th, row, color);
            }
            for (int row = Math.max(edge, ph - edge); row < ph; row++) {
                ringRow(context, px, py, pw, ph, outer, inner, th, row, color);
            }
            if (ph - edge > edge) {
                context.fill(px, py + edge, px + th, py + ph - edge, color);
                context.fill(px + pw - th, py + edge, px + pw, py + ph - edge, color);
            }
        } finally {
            pose.popMatrix();
        }
    }

    static void circle(GuiGraphicsExtractor context, float cx, float cy, float rad, int color) {
        round(context, cx, cy, rad, color, false);
    }

    static void diamond(GuiGraphicsExtractor context, float cx, float cy, float rad, int color) {
        round(context, cx, cy, rad, color, true);
    }

    private static void round(GuiGraphicsExtractor context, float cx, float cy, float rad, int color, boolean diamond) {
        float u = unit();
        float pcx = cx * u;
        float pcy = cy * u;
        float pr = rad * u;
        int y0 = (int) Math.floor(pcy - pr);
        int y1 = (int) Math.ceil(pcy + pr);
        Matrix3x2fStack pose = context.pose();
        pose.pushMatrix();
        try {
            pose.scale(1f / u, 1f / u);
            for (int row = y0; row < y1; row++) {
                float dy = row + 0.5f - pcy;
                float ad = Math.abs(dy);
                if (ad >= pr) {
                    continue;
                }
                float hw = diamond ? pr - ad : (float) Math.sqrt(pr * pr - dy * dy);
                span(context, row, pcx - hw, pcx + hw, color);
            }
        } finally {
            pose.popMatrix();
        }
    }

    /** A soft shadow under a rounded rectangle, built from a few fading rings. */
    static void shadow(GuiGraphicsExtractor context, float x, float y, float w, float h, float r, int steps, int color) {
        for (int k = steps; k >= 1; k--) {
            float a = (1f - (float) k / (steps + 1)) * 0.5f;
            shape(context, x - k, y - k + 1f, w + 2f * k, h + 2f * k, r + k, r + k, alpha(color, a), alpha(color, a));
        }
    }
}
