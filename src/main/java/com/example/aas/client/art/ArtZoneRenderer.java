package com.example.aas.client.art;


import com.example.aas.art.ArtGeometry;
import com.example.aas.art.ArtRequest;
import com.example.aas.art.ArtStrike;
import com.example.aas.client.ClientData;
import com.example.aas.client.uav.UavZoneRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/**
 * Draws the artillery on the map: request circles (blue, in the style of UavZoneRenderer.drawZone) and active strikes
 * (red). Called from AASMapRenderer.render inside the map scissor, so it works on the normal map, the minimap and
 * the death screen alike. What is drawn is exactly what the server sent to this player.
 */
public final class ArtZoneRenderer {
    private ArtZoneRenderer() {}

    public static final int REQ_FILL = 0x404F8CFF;       // translucent blue
    public static final int REQ_OUTLINE = 0xFF2F66E8;    // blue
    public static final int SELECTED_OUTLINE = 0xFFFFFFFF;
    public static final int STRIKE_FILL = 0x40FF4040;    // translucent red
    public static final int STRIKE_OUTLINE = 0xFFD01818; // red
    public static final int CROSS_RED = 0xFFFF2020;

    public static void renderAll(GuiGraphics gui, int mapX, int mapY, int mapSize, double cx, double cz, double bpp) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ArtMapView view = new ArtMapView(mapX, mapY, mapSize, cx, cz, bpp);

        float reqRadius = (float) (ClientData.artRequestRadius() / bpp);
        for (ArtRequest r : ArtClientState.requests) {
            float sx = view.screenX(r.centerX()), sy = view.screenY(r.centerZ());
            boolean selected = r.id.equals(ArtClientState.selectedRequest);
            UavZoneRenderer.drawZone(gui, sx, sy, reqRadius, REQ_FILL, selected ? SELECTED_OUTLINE : REQ_OUTLINE);
            ArtIcons.draw(gui, ArtIcons.REQUEST, Math.round(sx) - 8, Math.round(sy) - 8, 16, 16, 1f);
        }

        for (ArtStrike s : ArtClientState.strikes) {
            drawStrike(gui, view, s.type, s.ax, s.az, s.bx, s.bz, s.size, STRIKE_FILL, STRIKE_OUTLINE);
        }
    }

    /** One strike shape (also used for the CMD's selection preview). The gold icon marks the centre / the start. */
    public static void drawStrike(GuiGraphics gui, ArtMapView view, ArtStrike.Type type,
                                  double ax, double az, double bx, double bz, double size, int fill, int outline) {
        if (type == ArtStrike.Type.CIRCLE) {
            float sx = view.screenX(ax), sy = view.screenY(az);
            UavZoneRenderer.drawZone(gui, sx, sy, (float) (size / view.bpp), fill, outline);
            ArtIcons.drawRotatedTinted(gui, ArtIcons.STRIKE, sx, sy, 16, 0f, ArtIcons.COLOR_GOLD, 1f);
        } else {
            double[] c = ArtGeometry.rectCorners(ax, az, bx, bz, size);
            float[] xs = new float[4], ys = new float[4];
            for (int i = 0; i < 4; i++) { xs[i] = view.screenX(c[i * 2]); ys[i] = view.screenY(c[i * 2 + 1]); }
            drawPolygon(gui, xs, ys, fill, outline);
            // the icon looks where the strike ends: the texture points right, screen x = world x, screen y = world z
            float angle = (float) Math.atan2(bz - az, bx - ax);
            ArtIcons.drawRotatedTinted(gui, ArtIcons.STRIKE, view.screenX(ax), view.screenY(az), 16, angle, ArtIcons.COLOR_GOLD, 1f);
        }
    }

    public static void drawCross(GuiGraphics gui, float sx, float sy, int argb, int half) {
        int x = Math.round(sx), y = Math.round(sy);
        gui.fill(x - half, y, x + half + 1, y + 1, argb);
        gui.fill(x, y - half, x + 1, y + half + 1, argb);
    }

    /** Convex polygon: translucent fill (triangle fan) + 2 px outline. */
    public static void drawPolygon(GuiGraphics gui, float[] xs, float[] ys, int fill, int outline) {
        int n = xs.length;
        if (n < 3) return;
        Matrix4f m = gui.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder bb = tess.getBuilder();

        float hx = 0, hy = 0;
        for (int i = 0; i < n; i++) { hx += xs[i]; hy += ys[i]; }
        hx /= n; hy /= n;
        float fa = (fill >>> 24 & 255) / 255f, fr = (fill >> 16 & 255) / 255f, fg = (fill >> 8 & 255) / 255f, fb = (fill & 255) / 255f;
        bb.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        bb.vertex(m, hx, hy, 0f).color(fr, fg, fb, fa).endVertex();
        for (int i = 0; i <= n; i++) bb.vertex(m, xs[i % n], ys[i % n], 0f).color(fr, fg, fb, fa).endVertex();
        tess.end();

        float oa = (outline >>> 24 & 255) / 255f, or = (outline >> 16 & 255) / 255f, og = (outline >> 8 & 255) / 255f, ob = (outline & 255) / 255f;
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float dx = xs[j] - xs[i], dy = ys[j] - ys[i];
            float len = (float) Math.hypot(dx, dy);
            if (len < 1e-3f) continue;
            float nx = -dy / len, ny = dx / len;     // 1 px to each side = 2 px line
            bb.vertex(m, xs[i] + nx, ys[i] + ny, 0f).color(or, og, ob, oa).endVertex();
            bb.vertex(m, xs[j] + nx, ys[j] + ny, 0f).color(or, og, ob, oa).endVertex();
            bb.vertex(m, xs[j] - nx, ys[j] - ny, 0f).color(or, og, ob, oa).endVertex();
            bb.vertex(m, xs[i] - nx, ys[i] - ny, 0f).color(or, og, ob, oa).endVertex();
        }
        tess.end();

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}