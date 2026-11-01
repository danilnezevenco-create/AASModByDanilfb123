package com.example.aas.client.art;


import com.example.aas.art.ArtRequest;
import com.example.aas.client.ClientData;
import com.example.aas.client.uav.UavClientState;
import com.example.aas.client.uav.UavMapButtons;
import com.example.aas.client.uav.UavMapSelection;
import com.example.aas.client.uav.UavZoneRenderer;
import com.example.aas.network.PacketArtRequestAction;
import com.example.aas.network.PacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The shared artillery UI of the map. ONE component used by both SquadSelectionScreen (normal map) and
 * AASDeathScreen, so everything works the same on both:
 *  - SL: "Confirm" / "Cancel" buttons inside the own PENDING request circle;
 *  - CMD: the star button (where the UAV button used to be) -> panel with tabs "Support" (UAV, unchanged) and
 *    "Tactical" (circle / rectangle strike), choosing a confirmed request circle and placing the strike inside it.
 * It also owns the UAV zone selection and the UAV "eye" button, so the screens have a single hook point.
 */
public final class ArtMapUi {
    private static final int SIZE = 24, PANEL_W = 160, TAB_H = 16, BTN_H = 20, PAD = 4;
    private static final int GOLD = 0xFFB8860B;

    private record Hit(int x, int y, int w, int h, Runnable action, Component tip) {
        boolean contains(double mx, double my) { return mx >= x && mx <= x + w && my >= y && my <= y + h; }
    }

    private final boolean showUavEye;
    private final int leftInset;
    private final UavMapSelection uavSel = new UavMapSelection();
    private final UavMapButtons uavButtons = new UavMapButtons();
    private final ArtMapSelection artSel = new ArtMapSelection();

    private boolean panelOpen;
    private boolean tacticalTab;
    private ArtMapView view;
    private final List<Hit> hits = new ArrayList<>();
    private int[] panelRect;           // x, y, w, h of the open panel (clicks inside are swallowed)
    private Component tooltip;

    /**
     * @param showUavEye whether the "look through the UAV" button follows the UAV (normal map only; unchanged UAV behaviour)
     * @param leftInset  the buttons never start left of this x (the death screen has a sidebar over the left part of the map)
     */
    public ArtMapUi(boolean showUavEye, int leftInset) {
        this.showUavEye = showUavEye;
        this.leftInset = leftInset;
    }

    public boolean isSelecting() { return uavSel.isActive() || artSel.isActive(); }

    /** Esc: leave the selection mode or close the panel. @return true if something was closed. */
    public boolean cancelSelection() {
        if (uavSel.isActive()) { uavSel.cancel(); return true; }
        if (artSel.isActive()) { artSel.cancel(); return true; }
        if (panelOpen) { panelOpen = false; return true; }
        return false;
    }

    // ------------------------------------------------------------------ render
    public void render(GuiGraphics gui, Font font, int mx, int my, int mapX, int mapY, int mapSize,
                       double panX, double panZ, double bpp) {
        Minecraft mc = Minecraft.getInstance();
        hits.clear();
        panelRect = null;
        tooltip = null;
        if (mc.player == null) return;
        view = new ArtMapView(mapX, mapY, mapSize, panX, panZ, bpp);

        renderOwnRequestButtons(gui, font, mc, mx, my);
        uavSel.render(gui, font, mx, my, mapX, mapY, mapSize, panX, panZ, bpp);
        artSel.render(gui, font, mx, my, view);
        if (showUavEye) uavButtons.render(gui, mx, my, mapX, mapY, mapSize, panX, panZ, bpp, uavSel);
        if (UavClientState.isCmdLeader(mc)) renderStarAndPanel(gui, font, mc, mx, my);

        for (Hit h : hits) {
            if (h.tip() != null && h.contains(mx, my)) { tooltip = h.tip(); break; }
        }
    }

    public void renderTooltip(GuiGraphics gui, Font font, int mx, int my) {
        if (tooltip != null) gui.renderTooltip(font, tooltip, mx, my);
        else if (showUavEye) uavButtons.renderTooltip(gui, font, mx, my);
    }

    /** SL: confirm / cancel buttons inside the own PENDING circle, with the time left. */
    private void renderOwnRequestButtons(GuiGraphics gui, Font font, Minecraft mc, int mx, int my) {
        for (ArtRequest r : ArtClientState.requests) {
            if (r.status != ArtRequest.PENDING || !r.ownerUuid.equals(mc.player.getUUID())) continue;
            int sx = Math.round(view.screenX(r.centerX())), sy = Math.round(view.screenY(r.centerZ()));
            if (!view.inside(sx, sy)) continue;

            int bs = 16, gap = 4;
            int by = sy + 11;
            int cx1 = sx - bs - gap / 2, cx2 = sx + gap / 2;
            iconButton(gui, mx, my, cx1, by, bs, ArtIcons.CONFIRM, ArtIcons.COLOR_GREEN, true,
                    Component.translatable("aas.art.button.confirm"),
                    () -> PacketHandler.INSTANCE.sendToServer(new PacketArtRequestAction(r.id, true)));
            iconButton(gui, mx, my, cx2, by, bs, ArtIcons.CANCEL, ArtIcons.COLOR_RED, true,
                    Component.translatable("aas.art.button.cancel"),
                    () -> PacketHandler.INSTANCE.sendToServer(new PacketArtRequestAction(r.id, false)));

            long left = mc.level != null ? Math.max(0, r.expireTick - mc.level.getGameTime()) : 0;
            String t = ArtClientState.formatTicks(left);
            gui.drawCenteredString(font, t, sx, by + bs + 2, 0xFFFFFFFF);
        }
    }

    private void renderStarAndPanel(GuiGraphics gui, Font font, Minecraft mc, int mx, int my) {
        int starX = Math.max(view.mapX, leftInset) + 6, starY = view.mapY + 6;
        boolean active = panelOpen || isSelecting();
        boolean hover = hitTest(mx, my, starX, starY, SIZE, SIZE);

        // panel first, so its hits come before the star's in the click order
        if (panelOpen) renderPanel(gui, font, mc, mx, my, starX, starY + SIZE + 4);

        gui.fill(starX, starY, starX + SIZE, starY + SIZE, active ? 0xCC705800 : 0xAA000000);
        gui.renderOutline(starX, starY, SIZE, SIZE, hover || active ? 0xFFFFFFFF : GOLD);
        ArtIcons.draw(gui, ArtIcons.STAR, starX + 4, starY + 4, 16, 16, 1f);
        hits.add(new Hit(starX, starY, SIZE, SIZE, this::onStarClick, Component.translatable("aas.art.star.tooltip")));
    }

    private void onStarClick() {
        if (uavSel.isActive()) uavSel.cancel();
        else if (artSel.isActive()) artSel.cancel();
        else panelOpen = !panelOpen;
    }

    private void renderPanel(GuiGraphics gui, Font font, Minecraft mc, int mx, int my, int px, int py) {
        int rows = tacticalTab ? 2 : 1;
        int h = TAB_H + PAD + rows * (BTN_H + PAD);
        gui.fill(px, py, px + PANEL_W, py + h, 0xDD000000);
        gui.renderOutline(px, py, PANEL_W, h, GOLD);
        panelRect = new int[]{px, py, PANEL_W, h};

        // tabs
        int half = PANEL_W / 2;
        tab(gui, font, mx, my, px, py, half, Component.translatable("aas.art.tab.support"), !tacticalTab, () -> tacticalTab = false);
        tab(gui, font, mx, my, px + half, py, PANEL_W - half, Component.translatable("aas.art.tab.tactical"), tacticalTab, () -> tacticalTab = true);

        int bx = px + PAD, bw = PANEL_W - 2 * PAD, by = py + TAB_H + PAD;
        if (!tacticalTab) {
            Component reason = uavButtons.launchBlockReason(mc);
            textButton(gui, font, mx, my, bx, by, bw, UavZoneRenderer.UAV_ICON, Component.translatable("aas.art.button.uav"),
                    reason == null, reason == null ? Component.translatable("aas.uav.tooltip.ready") : reason,
                    () -> {
                        if (uavButtons.launchBlockReason(Minecraft.getInstance()) == null) { uavSel.start(); panelOpen = false; }
                    });
        } else {
            Component reason = ArtClientState.strikeBlockReason();
            boolean ok = reason == null;
            textButton(gui, font, mx, my, bx, by, bw, null, Component.translatable("aas.art.button.circle"), ok,
                    ok ? Component.translatable("aas.art.button.circle.tooltip") : reason,
                    () -> startStrike(false));
            textButton(gui, font, mx, my, bx, by + BTN_H + PAD, bw, null, Component.translatable("aas.art.button.rect"), ok,
                    ok ? Component.translatable("aas.art.button.rect.tooltip") : reason,
                    () -> startStrike(true));
        }
    }

    private void startStrike(boolean rect) {
        if (ArtClientState.strikeBlockReason() != null) return;
        artSel.start(rect);
        panelOpen = false;
    }

    // ------------------------------------------------------------------ widgets
    private void tab(GuiGraphics gui, Font font, int mx, int my, int x, int y, int w, Component label, boolean selected, Runnable action) {
        boolean hover = hitTest(mx, my, x, y, w, TAB_H);
        gui.fill(x, y, x + w, y + TAB_H, selected ? 0xCC705800 : 0x66000000);
        gui.renderOutline(x, y, w, TAB_H, hover || selected ? 0xFFFFFFFF : GOLD);
        gui.drawCenteredString(font, label, x + w / 2, y + (TAB_H - 8) / 2, selected ? 0xFFFFFFFF : 0xFFCCCCCC);
        hits.add(new Hit(x, y, w, TAB_H, action, null));
    }

    private void textButton(GuiGraphics gui, Font font, int mx, int my, int x, int y, int w,
                            net.minecraft.resources.ResourceLocation icon, Component label,
                            boolean enabled, Component tip, Runnable action) {
        boolean hover = hitTest(mx, my, x, y, w, BTN_H);
        gui.fill(x, y, x + w, y + BTN_H, hover && enabled ? 0xCC705800 : 0xAA000000);
        gui.renderOutline(x, y, w, BTN_H, !enabled ? 0xFF4A4A4A : (hover ? 0xFFFFFFFF : GOLD));
        int tx = x + w / 2;
        if (icon != null) {
            ArtIcons.draw(gui, icon, x + 4, y + 2, 16, 16, enabled ? 1f : 0.35f);
            tx = x + 22 + (w - 22) / 2;
        }
        gui.drawCenteredString(font, label, tx, y + (BTN_H - 8) / 2, enabled ? 0xFFFFFFFF : 0xFF777777);
        hits.add(new Hit(x, y, w, BTN_H, enabled ? action : null, tip));
    }

    private void iconButton(GuiGraphics gui, int mx, int my, int x, int y, int size,
                            net.minecraft.resources.ResourceLocation icon, int accent, boolean enabled,
                            Component tip, Runnable action) {
        boolean hover = hitTest(mx, my, x, y, size, size);
        gui.fill(x, y, x + size, y + size, hover ? 0xDD202020 : 0xCC000000);
        gui.renderOutline(x, y, size, size, hover ? 0xFFFFFFFF : accent);
        ArtIcons.drawTinted(gui, icon, x + 1, y + 1, size - 2, size - 2, accent, enabled ? 1f : 0.35f);
        hits.add(new Hit(x, y, size, size, enabled ? action : null, tip));
    }

    private static boolean hitTest(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    // ------------------------------------------------------------------ input
    /** LMB. @return true if the click was consumed. */
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || view == null) return false;
        for (Hit h : hits) {
            if (!h.contains(mx, my)) continue;
            if (h.action() != null) h.action().run();
            return true;
        }
        if (panelOpen && panelRect != null && hitTest(mx, my, panelRect[0], panelRect[1], panelRect[2], panelRect[3])) return true;
        if (showUavEye && uavButtons.mouseClicked(mx, my, button, uavSel)) return true;

        Minecraft mc = Minecraft.getInstance();
        if (view.inside(mx, my) && UavClientState.isCmdLeader(mc)) {
            ArtRequest hit = requestAt(mx, my);
            if (hit != null) {
                if (!artSel.isActive() && !uavSel.isActive()) {
                    // plain click on a circle: choose / unchoose it, but let the map drag still start
                    ArtClientState.selectedRequest = hit.id.equals(ArtClientState.selectedRequest) ? null : hit.id;
                    return false;
                }
            }
        }
        return false;
    }

    /** RMB, only while a selection mode is active. @return true if consumed (the marker menu must not open). */
    public boolean onRightClick(double mx, double my) {
        if (view == null) return false;
        if (uavSel.isActive()) {
            return uavSel.onRightClick(mx, my, view.mapX, view.mapY, view.mapSize, view.panX, view.panZ, view.bpp);
        }
        if (artSel.isActive()) return artSel.onRightClick(mx, my, view);
        return false;
    }

    /** Topmost CONFIRMED request circle under the mouse. */
    private ArtRequest requestAt(double mx, double my) {
        double rpx = ClientData.artRequestRadius() / view.bpp;
        ArtRequest found = null;
        for (ArtRequest r : ArtClientState.requests) {
            if (r.status != ArtRequest.CONFIRMED) continue;
            if (Math.hypot(mx - view.screenX(r.centerX()), my - view.screenY(r.centerZ())) <= rpx) found = r;
        }
        return found;
    }
}