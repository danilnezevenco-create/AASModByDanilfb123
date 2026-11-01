package com.example.aas.client.gui;

import com.example.aas.client.DeckClient;
import com.example.aas.client.KitAdminClient;
import com.example.aas.client.KitKeyUtil;
import com.example.aas.client.gui.AdminUi.Btn;
import com.example.aas.deck.DeckType;
import com.example.aas.network.PacketKitAdminInfo;
import com.example.aas.world.AASWorldData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Entry point of the kit admin menu (opened with the kit setup item):
 * choose the deck you edit, open the kits of a team, open the deck settings, undo, copy / paste keys.
 */
public class KitTeamSelectScreen extends Screen implements KitAdminClient.Listener {
    private static final int CARD_W = 170;
    private static final int CARD_H = 76;

    private boolean synced = false;
    private Btn undoBtn;

    public KitTeamSelectScreen() {
        super(AdminUi.c("aas.kadm.hub.title"));
    }

    @Override
    protected void init() {
        if (!synced) {
            KitAdminClient.syncEditDeck();
            synced = true;
        }
        int cx = width / 2;
        DeckType cur = DeckClient.editDeck;

        // --- deck selector ---
        int dy = 30;
        addRenderableWidget(new Btn(cx - 122, dy, 22, 18, Component.literal("<"), b -> cycleDeck(-1)).tip(AdminUi.t("aas.kadm.deck.prev")));
        addRenderableWidget(new Btn(cx - 98, dy, 196, 18,
                AdminUi.c("aas.kadm.hub.editing_deck", AdminUi.deckName(cur)), b -> cycleDeck(hasShiftDown() ? -1 : 1))
                .accent(AdminUi.C_GOLD).tip(AdminUi.t("aas.kadm.deck.click_tip")));
        addRenderableWidget(new Btn(cx + 100, dy, 22, 18, Component.literal(">"), b -> cycleDeck(1)).tip(AdminUi.t("aas.kadm.deck.next")));

        // --- team cards (the cards themselves are drawn in render()) ---
        int cy = 72;
        addRenderableWidget(new Btn(cx - CARD_W - 5 + 8, cy + CARD_H - 24, CARD_W - 16, 18,
                AdminUi.c("aas.kadm.hub.open_kits", AdminUi.team("BLUE")), b -> minecraft.setScreen(new KitListScreen("BLUE")))
                .accent(AdminUi.C_BLUE));
        addRenderableWidget(new Btn(cx + 5 + 8, cy + CARD_H - 24, CARD_W - 16, 18,
                AdminUi.c("aas.kadm.hub.open_kits", AdminUi.team("RED")), b -> minecraft.setScreen(new KitListScreen("RED")))
                .accent(AdminUi.C_RED));

        // --- deck settings / undo ---
        int ry = cy + CARD_H + 10;
        addRenderableWidget(new Btn(cx - CARD_W - 5, ry, CARD_W, 18, AdminUi.c("aas.kadm.hub.deck_settings"),
                b -> minecraft.setScreen(new DeckParamsScreen("BLUE", this)))
                .tip(AdminUi.t("aas.kadm.hub.deck_settings_tip")));
        undoBtn = new Btn(cx + 5, ry, CARD_W, 18, AdminUi.c("aas.kadm.undo"), b -> KitAdminClient.undo());
        addRenderableWidget(undoBtn);
        refreshUndo();

        // --- keys ---
        int ky = ry + 44;
        addRenderableWidget(new Btn(cx - CARD_W - 5, ky, CARD_W, 18, AdminUi.c("aas.kadm.hub.key_copy"),
                b -> KitKeyUtil.requestCopy("ALL", "BOTH", "ALL", false))
                .tip(AdminUi.t("aas.kadm.hub.key_copy_tip")));
        addRenderableWidget(new Btn(cx + 5, ky, CARD_W, 18, AdminUi.c("aas.kadm.hub.key_paste"),
                b -> pasteKey()).tip(AdminUi.t("aas.kadm.hub.key_paste_tip")));
    }

    private void cycleDeck(int step) {
        KitAdminClient.setEditDeck(DeckClient.editDeck.next(step));
        rebuildWidgets();
    }

    private void pasteKey() {
        String type = AdminUi.clipboardKeyType();
        if (type == null) {
            AdminUi.toast(AdminUi.t("aas.kadm.key.invalid"));
            return;
        }
        if (type.equals("KIT")) {
            AdminUi.toast(AdminUi.t("aas.kadm.key.single_hub"));
            return;
        }
        KitAdminClient.snapshot("BOTH", DeckClient.editDeck, true, false, AdminUi.lbl("aas.kadm.log.paste_key_both"));
        KitKeyUtil.importAllTeams();
        KitAdminClient.request();
    }

    private void refreshUndo() {
        if (undoBtn == null) return;
        int n = KitAdminClient.undoDepth();
        undoBtn.setMessage(Component.literal(n > 0 ? AdminUi.t("aas.kadm.undo_n", n) : AdminUi.t("aas.kadm.undo")));
        undoBtn.active = n > 0;
        String label = KitAdminClient.info == null ? "" : KitAdminClient.info.undoLabel;
        undoBtn.tip(n > 0 ? AdminUi.t("aas.kadm.undo_tip", AdminUi.label(label)) : AdminUi.t("aas.kadm.undo_none"));
    }

    @Override
    public void onKitAdminInfo() {
        refreshUndo();
    }

    @Override
    public void render(GuiGraphics gui, int mx, int my, float pt) {
        renderBackground(gui);
        int cx = width / 2;
        gui.drawCenteredString(font, AdminUi.t("aas.kadm.hub.header"), cx, 10, 0xFFFFFFFF);
        gui.drawCenteredString(font, AdminUi.t("aas.kadm.hub.note"), cx, 52, AdminUi.C_GRAY);

        drawCard(gui, cx - CARD_W - 5, 72, "BLUE");
        drawCard(gui, cx + 5, 72, "RED");

        int ky = 72 + CARD_H + 10 + 44;
        gui.drawCenteredString(font, AdminUi.t("aas.kadm.hub.keys_note"), cx, ky - 12, AdminUi.C_GRAY);
        gui.drawCenteredString(font, AdminUi.t("aas.kadm.hub.tip_undo"), cx, ky + 26, AdminUi.C_DIM);
        gui.drawCenteredString(font, AdminUi.t("aas.kadm.hub.tip_editor"), cx, ky + 38, AdminUi.C_DIM);
        super.render(gui, mx, my, pt);
    }

    private void drawCard(GuiGraphics gui, int x, int y, String team) {
        int col = AdminUi.teamColor(team);
        gui.fill(x, y, x + CARD_W, y + CARD_H, 0xCC101010);
        gui.renderOutline(x, y, CARD_W, CARD_H, col);
        gui.drawString(font, AdminUi.t("aas.kadm.hub.team_card", AdminUi.team(team)), x + 8, y + 6, col, false);

        PacketKitAdminInfo inf = KitAdminClient.info;
        if (inf == null) {
            gui.drawString(font, AdminUi.t("aas.kadm.loading"), x + 8, y + 20, AdminUi.C_DIM, false);
            return;
        }
        int t = AdminUi.teamIndex(team);
        int d = DeckClient.editDeck.ordinal();
        int total = AASWorldData.KIT_NAMES.length;
        int filled = inf.filled[t][d];
        gui.drawString(font, AdminUi.t("aas.kadm.hub.kits_filled", filled, total), x + 8, y + 19, filled == total ? AdminUi.C_GREEN : (filled == 0 ? AdminUi.C_RED : 0xFFFFFFFF), false);
        gui.drawString(font, inf.enabled[t][d] ? AdminUi.t("aas.kadm.hub.deck_on") : AdminUi.t("aas.kadm.hub.deck_off"), x + 8, y + 29,
                inf.enabled[t][d] ? AdminUi.C_GREEN : AdminUi.C_RED, false);
        gui.drawString(font, AdminUi.t("aas.kadm.hub.stats", inf.hub[t][d], inf.crate[t][d], inf.tickets[t][d]),
                x + 8, y + 39, AdminUi.C_GRAY, false);
    }
}