package com.example.aas.client.gui;

import net.minecraft.client.gui.GuiGraphics;

/** Small helpers for the preparation screens (vote / versus): easing curves and a "fade-in veil". */
public final class DeckAnim {
    private DeckAnim() {}

    /** Colour of the dark backdrop the veil fades from (must match the screen backdrop). */
    public static final int BACKDROP_RGB = 0x101010;

    public static float clamp01(float v) { return v < 0f ? 0f : Math.min(v, 1f); }

    public static float easeOutCubic(float t) {
        t = clamp01(t);
        float u = 1f - t;
        return 1f - u * u * u;
    }

    /** Overshoots a bit and settles at 1 (used for the "VS" pop). */
    public static float easeOutBack(float t) {
        t = clamp01(t);
        final float c1 = 1.70158f, c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    public static int lerpColor(int a, int b, float t) {
        t = clamp01(t);
        int out = 0;
        for (int shift = 24; shift >= 0; shift -= 8) {
            int ca = (a >> shift) & 0xFF, cb = (b >> shift) & 0xFF;
            out |= ((int) (ca + (cb - ca) * t) & 0xFF) << shift;
        }
        return out;
    }

    /** Draws backdrop-coloured rect over an already drawn area: visible=0 hides it, visible=1 leaves it untouched (= cheap fade-in). */
    public static void veil(GuiGraphics g, int x, int y, int w, int h, float visible) {
        if (visible >= 0.999f) return;
        g.flush();   // text is batched and drawn late; without this it would stay above the veil and not fade
        int a = (int) ((1f - clamp01(visible)) * 255f);
        g.fill(x, y, x + w, y + h, (a << 24) | BACKDROP_RGB);
    }
}