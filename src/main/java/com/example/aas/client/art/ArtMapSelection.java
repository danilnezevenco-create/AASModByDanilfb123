package com.example.aas.client.art;

import com.example.aas.art.ArtRequest;
import com.example.aas.art.ArtStrike;
import com.example.aas.client.ClientData;
import com.example.aas.network.PacketArtLaunch;
import com.example.aas.network.PacketHandler;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * "Place the strike" mode of the map (CMD).
 *
 * The request circle is ONLY the zone where the anchor point may be put (circle centre / rectangle start).
 * The CMD does not choose a circle beforehand: the first RMB must simply land inside a confirmed request circle,
 * that circle becomes the request of the strike. Radius / length / width are free (config limits only) and may
 * reach outside of the circle.
 *
 * Circle:    RMB inside a request circle = centre -> mouse = radius -> RMB fires.
 * Rectangle: RMB inside a request circle = start -> mouse = direction and length, RMB -> mouse = width, RMB fires.
 * Esc cancels. The client only previews and clamps; the server clamps again.
 */
public class ArtMapSelection {
    private enum Stage { IDLE, CENTER, RADIUS, START, END, WIDTH }

    private Stage stage = Stage.IDLE;
    private boolean rect;
    private double ax, az, bx, bz;     // circle: (ax, az) centre; rectangle: start (a) and end (b)

    public boolean isActive() { return stage != Stage.IDLE; }
    public void cancel() { stage = Stage.IDLE; }

    public void start(boolean rectangle) {
        rect = rectangle;
        stage = rect ? Stage.START : Stage.CENTER;
    }

    /** The request the strike is anchored to (chosen by the first RMB). */
    private ArtRequest request() {
        return ArtClientState.selectedRequest == null ? null : ArtClientState.confirmedRequest(ArtClientState.selectedRequest);
    }

    private static boolean anchorStage(Stage s) { return s == Stage.CENTER || s == Stage.START; }

    // ------------------------------------------------------------------ limits (synced from the server config)
    private static double reqRadius() { return ClientData.artRequestRadius(); }

    /** The confirmed request circle that contains the world point (the nearest centre wins), or null. */
    private static ArtRequest requestContaining(double wx, double wz) {
        ArtRequest best = null;
        double bestD = Double.MAX_VALUE;
        for (ArtRequest r : ArtClientState.requests) {
            if (r.status != ArtRequest.CONFIRMED) continue;
            double d = Math.hypot(wx - r.centerX(), wz - r.centerZ());
            if (d <= reqRadius() && d < bestD) { best = r; bestD = d; }
        }
        return best;
    }

    private double radiusAt(double mx, double my, ArtMapView v) {
        double blocks = Math.hypot(v.worldX(mx) - ax, v.worldZ(my) - az);
        int min = ClientData.artCircleMin();
        int max = Math.max(min, ClientData.artCircleMax());
        return Mth.clamp(blocks, min, max);
    }

    /** End point for the mouse: only the length is limited, to [min, max]. */
    private double[] endAt(double mx, double my, ArtMapView v) {
        double dx = v.worldX(mx) - ax, dz = v.worldZ(my) - az, len = Math.hypot(dx, dz);
        if (len < 1e-6) { dx = 1; dz = 0; len = 1; }
        double nl = Mth.clamp(len, ClientData.artRectMinLen(), Math.max(ClientData.artRectMinLen(), ClientData.artRectMaxLen()));
        return new double[]{ax + dx / len * nl, az + dz / len * nl};
    }

    private double widthAt(double mx, double my, ArtMapView v) {
        double dx = bx - ax, dz = bz - az, len = Math.hypot(dx, dz);
        double px = v.worldX(mx) - ax, pz = v.worldZ(my) - az;
        double perp = len < 1e-9 ? 0 : Math.abs(px * (-dz / len) + pz * (dx / len)) * 2;
        int min = ClientData.artRectMinWidth();
        int max = Math.max(min, ClientData.artRectMaxWidth());
        return Mth.clamp(perp, min, max);
    }

    // ------------------------------------------------------------------ input
    /** @return true if the click was consumed (the mode is active and the click was on the map) */
    public boolean onRightClick(double mx, double my, ArtMapView v) {
        if (stage == Stage.IDLE || !v.inside(mx, my)) return false;

        if (anchorStage(stage)) {
            double wx = v.worldX(mx), wz = v.worldZ(my);
            ArtRequest hit = requestContaining(wx, wz);
            if (hit == null) return true;                          // outside every request circle: ignore the click
            ArtClientState.selectedRequest = hit.id;
            ax = wx; az = wz;
            stage = stage == Stage.CENTER ? Stage.RADIUS : Stage.END;
            return true;
        }

        ArtRequest r = request();
        if (r == null) { cancel(); return true; }

        switch (stage) {
            case RADIUS -> {
                double rad = radiusAt(mx, my, v);
                PacketHandler.INSTANCE.sendToServer(new PacketArtLaunch(r.id, 0, ax, az, 0, 0, rad));
                finish();
            }
            case END -> {
                double[] e = endAt(mx, my, v);
                bx = e[0]; bz = e[1];
                stage = Stage.WIDTH;
            }
            case WIDTH -> {
                double w = widthAt(mx, my, v);
                PacketHandler.INSTANCE.sendToServer(new PacketArtLaunch(r.id, 1, ax, az, bx, bz, w));
                finish();
            }
            default -> { }
        }
        return true;
    }

    private void finish() {
        stage = Stage.IDLE;
        ArtClientState.selectedRequest = null;      // the server deletes the request; the circle disappears
    }

    // ------------------------------------------------------------------ render
    public void render(GuiGraphics gui, Font font, int mx, int my, ArtMapView v) {
        if (stage == Stage.IDLE) return;
        ArtRequest r = request();
        if (!anchorStage(stage) && r == null) { stage = Stage.IDLE; return; }

        Component hint;
        gui.enableScissor(v.mapX, v.mapY, v.mapX + v.mapSize, v.mapY + v.mapSize);
        switch (stage) {
            case CENTER, START -> {
                boolean ok = requestContaining(v.worldX(mx), v.worldZ(my)) != null;
                if (ok) ArtZoneRenderer.drawCross(gui, mx, my, ArtZoneRenderer.CROSS_RED, 5);   // outside the zone: no cross at all
                hint = Component.translatable(ok
                        ? (stage == Stage.CENTER ? "aas.art.select.center" : "aas.art.select.start")
                        : "aas.art.select.outside");
            }
            case RADIUS -> {
                double rad = radiusAt(mx, my, v);
                ArtZoneRenderer.drawStrike(gui, v, ArtStrike.Type.CIRCLE, ax, az, 0, 0, rad,
                        ArtZoneRenderer.STRIKE_FILL, ArtZoneRenderer.STRIKE_OUTLINE);
                hint = Component.translatable("aas.art.select.radius", Math.round(rad));
            }
            case END -> {
                double[] e = endAt(mx, my, v);
                ArtZoneRenderer.drawStrike(gui, v, ArtStrike.Type.RECT, ax, az, e[0], e[1],
                        Math.max(2, ClientData.artRectMinWidth()),
                        ArtZoneRenderer.STRIKE_FILL, ArtZoneRenderer.STRIKE_OUTLINE);
                hint = Component.translatable("aas.art.select.end", Math.round(Math.hypot(e[0] - ax, e[1] - az)));
            }
            default -> {        // WIDTH
                double w = widthAt(mx, my, v);
                ArtZoneRenderer.drawStrike(gui, v, ArtStrike.Type.RECT, ax, az, bx, bz, w,
                        ArtZoneRenderer.STRIKE_FILL, ArtZoneRenderer.STRIKE_OUTLINE);
                hint = Component.translatable("aas.art.select.width", Math.round(w));
            }
        }
        gui.disableScissor();

        int w = font.width(hint);
        int x = v.mapX + v.mapSize / 2 - w / 2;
        int y = v.mapY + 6;
        gui.fill(x - 5, y - 3, x + w + 5, y + 11, 0xCC000000);
        gui.renderOutline(x - 5, y - 3, w + 10, 14, 0xFFD01818);
        gui.drawString(font, hint, x, y, 0xFFFF8080, false);
    }
}