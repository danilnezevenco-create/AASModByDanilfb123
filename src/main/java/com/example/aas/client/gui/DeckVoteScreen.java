package com.example.aas.client.gui;

import com.example.aas.client.ClientData;
import com.example.aas.client.DeckClient;
import com.example.aas.client.DeckIcons;
import com.example.aas.deck.DeckRegistry;
import com.example.aas.deck.DeckType;
import com.example.aas.network.PacketDeckInfo;
import com.example.aas.network.PacketDeckVote;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketRequestDeckInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Deck vote. Cannot be closed (ESC and onClose() are ignored); DeckClient closes it when the phase ends.
 * A card = one enabled deck of the player's team. Click on the card = vote (changeable), click on "?" = info window.
 */
public class DeckVoteScreen extends Screen {
    private static final int CW = 170, CH = 92, GAP = 10, STRIP = 26;

    private final boolean blue;
    private PacketDeckInfo popup = null;
    private boolean waitingForInfo = false;
    private float scale = 1f;
    private int ox, oy, cols, total;

    private long openMs = 0L;                      // for the short fade-in

    public DeckVoteScreen(boolean blue) {
        super(Component.translatable("aas.deck.vote.title"));
        this.blue = blue;
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public void onClose() { /* cannot be closed while the vote runs */ }

    @Override
    public boolean isPauseScreen() { return false; }

    public void onInfo(PacketDeckInfo msg) {
        if (waitingForInfo) {
            popup = msg;
            waitingForInfo = false;
        }
    }

    private List<DeckType> decks() {
        List<DeckType> out = new ArrayList<>();
        for (DeckType d : DeckType.VALUES) if (DeckClient.state.isEnabled(blue, d)) out.add(d);
        return out;
    }

    private void layout(int count) {
        total = count;
        cols = Math.max(1, Math.min(4, count));
        int rows = (count + cols - 1) / cols;
        int lw = cols * CW + (cols - 1) * GAP;
        int lh = rows * CH + (rows - 1) * GAP;
        float availW = width - 20f, availH = height - 70f;
        scale = Math.min(1f, Math.min(availW / lw, availH / lh));
        if (scale <= 0.1f) scale = 0.1f;
        ox = (int) ((width - lw * scale) / 2f);
        oy = (int) (50 + (availH - lh * scale) / 2f);
    }

    /** Cards of an incomplete last row are centred under the full rows. */
    private int cardX(int i) {
        int row = i / cols;
        int inRow = Math.min(cols, total - row * cols);
        return (i % cols) * (CW + GAP) + (cols - inRow) * (CW + GAP) / 2;
    }
    private int cardY(int i) { return (i / cols) * (CH + GAP); }

    private static boolean in(double x, double y, int rx, int ry, int rw, int rh) {
        return x >= rx && x < rx + rw && y >= ry && y < ry + rh;
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();
        if (openMs == 0L) openMs = now;
        float fade = DeckAnim.easeOutCubic((now - openMs) / 220f);          // short fade-in of the whole screen

        renderBackground(gui);
        gui.fill(0, 0, width, height, 0x80101010);

        gui.drawCenteredString(font, Component.translatable("aas.deck.vote.title"), width / 2, 12, 0xFFFFFF);
        gui.drawCenteredString(font, Component.translatable("aas.deck.vote.timer", DeckClient.secondsLeft()), width / 2, 28, 0xFFD700);

        List<DeckType> decks = decks();
        layout(decks.size());
        String faction = DeckClient.factionOf(blue);
        int[] votes = DeckClient.state.votes(blue);
        double vx = (mouseX - ox) / (double) scale, vy = (mouseY - oy) / (double) scale;

        gui.pose().pushPose();
        gui.pose().translate(ox, oy, 0);
        gui.pose().scale(scale, scale, 1f);
        for (int i = 0; i < decks.size(); i++) {
            DeckType d = decks.get(i);
            int cx = cardX(i), cy = cardY(i);
            boolean selected = DeckClient.myVote == d.ordinal();
            boolean hover = popup == null && in(vx, vy, cx, cy, CW, CH);

            gui.fill(cx, cy, cx + CW, cy + CH, hover ? 0xE6262626 : 0xE6181818);
            gui.renderOutline(cx, cy, CW, CH, selected ? 0xFFFFD700 : (hover ? 0xFF888888 : 0xFF555555));
            if (selected) gui.renderOutline(cx + 1, cy + 1, CW - 2, CH - 2, 0xFFFFD700);

            DeckIcons.drawEmblem(gui, faction, blue, d, cx + 6, cy + 6, 60);
            DeckIcons.drawFlag(gui, faction, blue, cx + CW - 42, cy + 6, 36, 20);

            int sy = cy + CH - STRIP;
            gui.fill(cx, sy, cx + CW, cy + CH, 0xFF000000);
            DeckIcons.drawClass(gui, d, cx + 4, sy + 4, 18);

            Component name = Component.translatable(DeckRegistry.nameKey(faction, blue, d));
            List<FormattedCharSequence> lines = font.split(name, CW - 4 - 18 - 6 - 22 - 4);
            for (int l = 0; l < Math.min(2, lines.size()); l++) {
                gui.drawString(font, lines.get(l), cx + 26, sy + 4 + l * 10, 0xFFFFFF, false);
            }

            int qx = cx + CW - 22, qy = sy + 4;
            boolean qHover = in(vx, vy, qx, qy, 18, 18);
            gui.fill(qx, qy, qx + 18, qy + 18, qHover ? 0xFF666666 : 0xFF3C3C3C);
            gui.renderOutline(qx, qy, 18, 18, 0xFFFFFFFF);
            gui.drawCenteredString(font, "?", qx + 9, qy + 5, 0xFFFFFF);

            int n = votes[d.ordinal()];
            if (n > 0) {                                  // nobody voted: show nothing
                String count = String.valueOf(n);
                gui.drawString(font, count, cx + CW - 6 - font.width(count), sy - 12, 0xFFFFFF, true);
            }
        }
        gui.pose().popPose();

        if (popup != null) renderPopup(gui);
        else if (waitingForInfo) gui.drawCenteredString(font, "...", width / 2, height - 20, 0xAAAAAA);

        DeckAnim.veil(gui, 0, 0, width, height, fade);     // flushes the text first, then covers everything evenly
    }

    private static String limit(int v) {
        if (v < 0) return "\u221E";
        if (v == 0) return I18n.get("aas.deck.info.none");
        return String.valueOf(v);
    }

    // ------------------------------------------------------------------ "?" window
    private static final int P_W = 300, P_PAD = 8, P_ROW = 14, P_SEC_H = 13, P_HEAD_H = 36;
    private static final int COL_BG = 0xF0101010, COL_BORDER = 0xFF555555, COL_SEC_BG = 0x40FFFFFF;
    private static final int COL_STRIPE = 0x14FFFFFF, COL_DIM = 0xFF999999, COL_TEXT = 0xFFFFFFFF;
    private static final int COL_GOOD = 0xFF55FF55, COL_BAD = 0xFFFF5555, COL_TICKETS = 0xFFFF8888;
    private static final int P_ICON = 12;

    /** Section bar: dim caption on a light strip. */
    private void popupSection(GuiGraphics gui, int x, int y, int w, String caption) {
        gui.fill(x + 1, y, x + w - 1, y + P_SEC_H, COL_SEC_BG);
        gui.drawString(font, caption.toUpperCase(java.util.Locale.ROOT), x + P_PAD, y + 3, COL_DIM, false);
    }

    /** One "label ........ value" row of the parameters block; iconTex != null draws that icon left of the value. */
    private void popupStat(GuiGraphics gui, int x, int y, int w, int idx, String label, String value, int color,
                           net.minecraft.resources.ResourceLocation iconTex) {
        if ((idx & 1) == 1) gui.fill(x + 1, y, x + w - 1, y + P_ROW, COL_STRIPE);
        int ty = y + (P_ROW - font.lineHeight) / 2 + 1;
        gui.drawString(font, label, x + P_PAD, ty, COL_TEXT, false);
        int vr = x + w - P_PAD;
        gui.drawString(font, value, vr - font.width(value), ty, color, false);
        if (iconTex != null) {
            VehicleListPanel.drawTexOrStub(gui, iconTex, vr - font.width(value) - P_ICON - 3, y + (P_ROW - P_ICON) / 2, P_ICON);
        }
    }

    private void renderPopup(GuiGraphics gui) {
        PacketDeckInfo p = popup;
        DeckType d = DeckType.byOrdinal(p.deck);
        String faction = DeckClient.factionOf(blue);

        // fit the vehicle list on small screens: extra lines collapse into "+N"
        int fixed = P_HEAD_H + P_SEC_H * 2 + P_ROW * 5 + 6 + 20;
        int maxVeh = Math.max(1, (height - 8 - fixed) / P_ROW);
        int total = p.vehicles.isEmpty() ? 1 : p.vehicles.size();
        int shown = Math.min(total, maxVeh);
        boolean more = !p.vehicles.isEmpty() && p.vehicles.size() > shown;
        if (more) shown = Math.max(1, shown - 1);          // one line is taken by "+N"
        int vehLines = p.vehicles.isEmpty() ? 1 : shown + (more ? 1 : 0);

        int w = P_W;
        int h = P_HEAD_H + P_SEC_H + vehLines * P_ROW + 6 + P_SEC_H + P_ROW * 5 + 20;
        int x = (width - w) / 2, y = Math.max(4, (height - h) / 2);

        gui.fill(x, y, x + w, y + h, COL_BG);
        gui.renderOutline(x, y, w, h, COL_BORDER);

        // ---- header: emblem, name, class, flag
        gui.fill(x + 1, y + 1, x + w - 1, y + P_HEAD_H, 0x30FFFFFF);
        if (d != null) {
            DeckIcons.drawEmblem(gui, faction, blue, d, x + P_PAD, y + 6, 24);
            gui.drawString(font, Component.translatable(DeckRegistry.nameKey(faction, blue, d)), x + P_PAD + 24 + 6, y + 8, 0xFFFFD700, false);
            gui.drawString(font, Component.translatable(d.classLangKey()), x + P_PAD + 24 + 6, y + 20, COL_DIM, false);
        }
        DeckIcons.drawFlag(gui, faction, blue, x + w - P_PAD - 30, y + 10, 30, 18);
        gui.fill(x + 1, y + P_HEAD_H, x + w - 1, y + P_HEAD_H + 1, COL_BORDER);

        // ---- vehicles block
        int ry = y + P_HEAD_H + 1;
        popupSection(gui, x, ry, w, I18n.get("aas.deck.info.title_vehicles"));
        VehicleListPanel.drawTexOrStub(gui, VehicleListPanel.ICON_TICKETS, x + w - P_PAD - 10, ry + 2, 10);   // column: tickets lost
        ry += P_SEC_H;
        int colTickets = x + w - P_PAD;       // right edge of the tickets column
        int colCount = colTickets - 40;       // right edge of the "x2" column
        if (p.vehicles.isEmpty()) {
            gui.drawCenteredString(font, I18n.get("aas.deck.info.no_vehicles"), x + w / 2, ry + 3, COL_DIM);
            ry += P_ROW;
        } else {
            for (int i = 0; i < shown; i++) {
                PacketDeckInfo.VehicleLine l = p.vehicles.get(i);
                if ((i & 1) == 1) gui.fill(x + 1, ry, x + w - 1, ry + P_ROW, COL_STRIPE);
                var cat = com.example.aas.util.VehicleCatalog.resolve(null, l.markerType());
                VehicleListPanel.drawTexOrStub(gui, VehicleListPanel.mapIcon(l.markerType(), cat), x + P_PAD, ry + (P_ROW - P_ICON) / 2, P_ICON);
                int ty = ry + (P_ROW - font.lineHeight) / 2 + 1;
                String cnt = "x" + l.count();
                int nameL = x + P_PAD + P_ICON + 4;
                String name = I18n.get(l.nameKey());
                int nameMax = colCount - font.width(cnt) - nameL - 8;
                if (font.width(name) > nameMax) name = font.plainSubstrByWidth(name, Math.max(0, nameMax - font.width("..."))) + "...";
                gui.drawString(font, name, nameL, ty, COL_TEXT, false);
                gui.drawString(font, cnt, colCount - font.width(cnt), ty, COL_DIM, false);
                String pen = l.penalty() > 0 ? "-" + l.penalty() : "0";
                gui.drawString(font, pen, colTickets - font.width(pen), ty, l.penalty() > 0 ? COL_TICKETS : COL_DIM, false);
                ry += P_ROW;
            }
            if (more) {
                int rest = p.vehicles.size() - shown;
                gui.drawCenteredString(font, "+" + rest, x + w / 2, ry + 3, COL_DIM);
                ry += P_ROW;
            }
        }
        ry += 6;

        // ---- deck parameters block
        gui.fill(x + 1, ry - 1, x + w - 1, ry, COL_BORDER);
        popupSection(gui, x, ry, w, I18n.get("aas.deck.info.title_params"));
        ry += P_SEC_H;
        int i = 0;
        popupStat(gui, x, ry, w, i++, I18n.get("aas.deck.info.l_hubs"), limit(p.hubLimit), limitColor(p.hubLimit), null); ry += P_ROW;
        popupStat(gui, x, ry, w, i++, I18n.get("aas.deck.info.l_aa"), limit(p.antiAirMax), limitColor(p.antiAirMax), null); ry += P_ROW;
        popupStat(gui, x, ry, w, i++, I18n.get("aas.deck.info.l_hat"), limit(p.hatMax), limitColor(p.hatMax), null); ry += P_ROW;
        popupStat(gui, x, ry, w, i++, I18n.get("aas.deck.info.l_crate"), String.valueOf(p.crateTotal), COL_TEXT, null); ry += P_ROW;
        popupStat(gui, x, ry, w, i, I18n.get("aas.deck.info.l_tickets"), String.valueOf(p.tickets), COL_TEXT, VehicleListPanel.ICON_TICKETS);

        gui.drawCenteredString(font, Component.translatable("aas.deck.info.click_close"), x + w / 2, y + h - 13, 0xFF888888);
    }

    private static int limitColor(int v) {
        return v < 0 ? COL_GOOD : (v == 0 ? COL_BAD : COL_TEXT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return true;
        if (popup != null || waitingForInfo) {   // the window closes with a click, the vote keeps running
            popup = null;
            waitingForInfo = false;
            return true;
        }
        List<DeckType> decks = decks();
        layout(decks.size());
        double vx = (mouseX - ox) / (double) scale, vy = (mouseY - oy) / (double) scale;
        for (int i = 0; i < decks.size(); i++) {
            int cx = cardX(i), cy = cardY(i);
            if (!in(vx, vy, cx, cy, CW, CH)) continue;
            DeckType d = decks.get(i);
            if (in(vx, vy, cx + CW - 22, cy + CH - STRIP + 4, 18, 18)) {
                waitingForInfo = true;
                PacketHandler.INSTANCE.sendToServer(new PacketRequestDeckInfo(blue ? "BLUE" : "RED", d.ordinal()));
            } else {
                DeckClient.myVote = d.ordinal();
                PacketHandler.INSTANCE.sendToServer(new PacketDeckVote(d.ordinal()));
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;   // ESC and everything else is swallowed
    }
}