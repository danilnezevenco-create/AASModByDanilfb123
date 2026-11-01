package com.example.aas.client.gui;

import com.example.aas.client.AASClipboard;
import com.example.aas.client.DeckClient;
import com.example.aas.client.KitAdminClient;
import com.example.aas.client.KitIconStyle;
import com.example.aas.client.KitKeyUtil;
import com.example.aas.client.gui.AdminUi.Btn;
import com.example.aas.deck.DeckType;
import com.example.aas.network.PacketCopyDeckKits;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketKitAdmin;
import com.example.aas.network.PacketKitAdminInfo;
import com.example.aas.network.PacketOpenKitEditor;
import com.example.aas.network.PacketPasteKit;
import com.example.aas.network.PacketPasteTeam;
import com.example.aas.network.PacketRequestKitData;
import com.example.aas.world.AASWorldData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * All kits of one team in the deck that is being edited.
 * Deck tabs, a status card per kit (items, ALT, leader only, limits), search + filter, scrolling,
 * per-kit actions (edit / alt / copy / paste / clear / copy to the other team) and bulk actions.
 */
public class KitListScreen extends Screen implements KitAdminClient.Listener {
    private static final String[] KITS = AASWorldData.KIT_NAMES;
    private static final String[] FILTERS = {"ALL", "EMPTY", "FILLED", "HAS ALT"};

    private static final int MARGIN = 6;
    private static final int CARD_W = 128;
    private static final int CARD_H = 59;
    private static final int GAP = 4;
    private static final int BTN_Y = 44;   // y of the card buttons inside a card
    private static final int BTN_H = 13;

    /** Remembered between screens. */
    private static int filterMode = 0;

    private final String team;
    private String searchText = "";
    private int scroll = 0;
    private boolean synced = false;
    private DeckType copyTarget = null;

    private EditBox searchBox;
    private Btn filterBtn;
    private Btn undoBtn;
    private Btn pasteAllBtn;
    private Btn keyPasteBtn;
    private final Btn[] deckTabs = new Btn[DeckType.COUNT];

    // card widgets (registered with addWidget, drawn by hand inside the scroll area)
    private final Btn[] bEdit = new Btn[KITS.length];
    private final Btn[] bAlt = new Btn[KITS.length];
    private final Btn[] bCopy = new Btn[KITS.length];
    private final Btn[] bPaste = new Btn[KITS.length];
    private final Btn[] bClear = new Btn[KITS.length];
    private final Btn[] bMirror = new Btn[KITS.length];
    private final int[] cardX = new int[KITS.length];
    private final int[] cardY = new int[KITS.length];
    private final boolean[] cardShown = new boolean[KITS.length];
    private final List<Integer> shown = new ArrayList<>();

    private int gridTop = 100;
    private int gridBottom = 200;
    private int maxScroll = 0;

    // toolbar flow layout
    private int flowX;
    private int flowY;

    public KitListScreen(String team) {
        super(AdminUi.c("aas.kadm.list.title", AdminUi.team(team)));
        this.team = team;
    }

    // ------------------------------------------------------------------ layout

    private <T extends AbstractWidget> T flow(T w) {
        if (flowX + w.getWidth() > width - MARGIN) {
            flowX = MARGIN;
            flowY += 20;
        }
        w.setX(flowX);
        w.setY(flowY);
        flowX += w.getWidth() + 4;
        return addRenderableWidget(w);
    }

    @Override
    protected void init() {
        if (!synced) {
            KitAdminClient.syncEditDeck();
            synced = true;
        }
        DeckType cur = DeckClient.editDeck;
        if (copyTarget == null || copyTarget == cur) copyTarget = cur.next(1);
        final DeckType target = copyTarget;
        final String other = AdminUi.otherTeam(team);

        // ---- row 1: hub + team tabs ----
        int y = 5;
        addRenderableWidget(new Btn(MARGIN, y, 50, 16, AdminUi.c("aas.kadm.list.hub"),
                b -> minecraft.setScreen(new KitTeamSelectScreen())).tip(AdminUi.t("aas.kadm.list.hub_tip")));
        addRenderableWidget(teamTab("BLUE", MARGIN + 56, y));
        addRenderableWidget(teamTab("RED", MARGIN + 120, y));

        // ---- row 2: deck tabs ----
        y = 25;
        int tabW = (width - 2 * MARGIN - (DeckType.COUNT - 1) * 2) / DeckType.COUNT;
        for (int i = 0; i < DeckType.COUNT; i++) {
            final DeckType d = DeckType.VALUES[i];
            Btn tab = new Btn(MARGIN + i * (tabW + 2), y, tabW, 24, Component.literal(AdminUi.deckName(d)), b -> {
                if (d != DeckClient.editDeck) {
                    KitAdminClient.setEditDeck(d);
                    scroll = 0;
                    copyTarget = null;
                    rebuildWidgets();
                }
            });
            tab.selected = d == cur;
            deckTabs[i] = tab;
            addRenderableWidget(tab);
        }
        refreshTabs();

        // ---- toolbar (flows over several lines on small screens) ----
        flowX = MARGIN;
        flowY = 53;

        searchBox = new EditBox(font, 0, 0, 104, 16, Component.empty());
        searchBox.setMaxLength(24);
        searchBox.setValue(searchText);
        searchBox.setSuggestion(searchText.isEmpty() ? AdminUi.t("aas.kadm.list.search") : null);
        searchBox.setResponder(s -> {
            searchText = s;
            searchBox.setSuggestion(s.isEmpty() ? AdminUi.t("aas.kadm.list.search") : null);
            scroll = 0;
            layoutCards();
        });
        flow(searchBox);

        filterBtn = flow(new Btn(0, 0, 70, 16, AdminUi.c("aas.kadm.list.show", filterName()), b -> {
            filterMode = (filterMode + 1) % FILTERS.length;
            b.setMessage(AdminUi.c("aas.kadm.list.show", filterName()));
            scroll = 0;
            layoutCards();
        }).tip(AdminUi.t("aas.kadm.list.filter_tip")));

        flow(new Btn(0, 0, 62, 16, AdminUi.c("aas.kadm.list.copy_all"), b -> {
            PacketHandler.INSTANCE.sendToServer(new PacketRequestKitData(team, "ALL"));
            AdminUi.toast(AdminUi.t("aas.kadm.list.copy_all_done", AdminUi.team(team)));
        }).tip(AdminUi.t("aas.kadm.list.copy_all_tip")));

        pasteAllBtn = flow(new Btn(0, 0, 62, 16, AdminUi.c("aas.kadm.list.paste_all"), b -> confirm(
                AdminUi.t("aas.kadm.list.paste_all_q"), AdminUi.t("aas.kadm.list.paste_all_msg", AdminUi.team(team)),
                () -> {
                    if (AASClipboard.teamKitsData == null) return;
                    KitAdminClient.snapshot(team, DeckClient.editDeck, true, false, AdminUi.lbl("aas.kadm.log.paste_all", AdminUi.teamRef(team)));
                    PacketHandler.INSTANCE.sendToServer(new PacketPasteTeam(team, AASClipboard.teamKitsData));
                    KitAdminClient.request();
                    AdminUi.toast(AdminUi.t("aas.kadm.list.paste_all_done"));
                })).tip(AdminUi.t("aas.kadm.list.paste_all_tip")));

        flow(new Btn(0, 0, 62, 16, AdminUi.c("aas.kadm.list.key_copy"), b ->
                KitKeyUtil.requestCopy("TEAM", team, "ALL", false))
                .tip(AdminUi.t("aas.kadm.list.key_copy_tip")));

        keyPasteBtn = flow(new Btn(0, 0, 62, 16, AdminUi.c("aas.kadm.list.key_paste"), b -> pasteKey())
                .tip(AdminUi.t("aas.kadm.list.key_paste_tip")));

        flow(new Btn(0, 0, 118, 16, AdminUi.c("aas.kadm.list.copy_to", AdminUi.deckName(target)), b -> {
            DeckType n = target.next(hasShiftDown() ? -1 : 1);
            if (n == DeckClient.editDeck) n = n.next(hasShiftDown() ? -1 : 1);
            copyTarget = n;
            rebuildWidgets();
        }).tip(AdminUi.t("aas.kadm.list.copy_to_tip")));

        flow(new Btn(0, 0, 70, 16, AdminUi.c("aas.kadm.list.copy_deck"), b -> confirm(
                AdminUi.t("aas.kadm.list.copy_deck_q"),
                AdminUi.t("aas.kadm.list.copy_deck_msg", AdminUi.team(team), AdminUi.deckName(DeckClient.editDeck), AdminUi.deckName(target)),
                () -> {
                    KitAdminClient.snapshot(team, target, true, false, AdminUi.lbl("aas.kadm.log.copy_deck"));
                    PacketHandler.INSTANCE.sendToServer(new PacketCopyDeckKits(team, DeckClient.editDeck.ordinal(), target.ordinal()));
                    KitAdminClient.request();
                    AdminUi.toast(AdminUi.t("aas.kadm.list.copy_deck_done", AdminUi.deckName(target)));
                })).accent(AdminUi.C_GOLD).tip(AdminUi.t("aas.kadm.list.copy_deck_tip")));

        flow(new Btn(0, 0, 78, 16, AdminUi.c("aas.kadm.list.to_team", AdminUi.team(other)), b -> confirm(
                AdminUi.t("aas.kadm.list.to_team_q", AdminUi.team(other)), AdminUi.t("aas.kadm.list.to_team_msg", AdminUi.team(team), AdminUi.team(other)),
                () -> KitAdminClient.send(PacketKitAdmin.copyTeamToOtherTeam(team))))
                .accent(AdminUi.teamColor(other)).tip(AdminUi.t("aas.kadm.list.to_team_tip")));

        flow(new Btn(0, 0, 62, 16, AdminUi.c("aas.kadm.list.clear_all"), b -> confirm(
                AdminUi.t("aas.kadm.list.clear_all_q"), AdminUi.t("aas.kadm.list.clear_all_msg", AdminUi.team(team), AdminUi.deckName(DeckClient.editDeck)),
                () -> KitAdminClient.send(PacketKitAdmin.clearTeam(team))))
                .accent(AdminUi.C_RED).tip(AdminUi.t("aas.kadm.list.clear_all_tip")));

        flow(new Btn(0, 0, 92, 16, AdminUi.c("aas.kadm.list.deck_settings"),
                b -> minecraft.setScreen(new DeckParamsScreen(team, this)))
                .tip(AdminUi.t("aas.kadm.list.deck_settings_tip")));

        undoBtn = flow(new Btn(0, 0, 72, 16, AdminUi.c("aas.kadm.undo"), b -> KitAdminClient.undo()));
        refreshUndo();

        gridTop = flowY + 22;
        gridBottom = height - 14;

        // ---- card buttons ----
        for (int i = 0; i < KITS.length; i++) {
            final int idx = i;
            final String kit = KITS[i];
            bEdit[i] = card(new Btn(0, 0, 34, BTN_H, AdminUi.c("aas.kadm.card.edit"), b -> openEditor(idx, false))
                    .tip(AdminUi.t("aas.kadm.card.edit_tip")));
            bAlt[i] = card(new Btn(0, 0, 24, BTN_H, AdminUi.c("aas.kadm.card.alt"), b -> openEditor(idx, true))
                    .accent(AdminUi.C_GOLD).tip(AdminUi.t("aas.kadm.card.alt_tip")));
            bCopy[i] = card(new Btn(0, 0, 14, BTN_H, AdminUi.c("aas.kadm.card.c"), b -> {
                PacketHandler.INSTANCE.sendToServer(new PacketRequestKitData(team, kit));
                AdminUi.toast(AdminUi.t("aas.kadm.card.copied", kit));
            }).tip(AdminUi.t("aas.kadm.card.copy_tip")));
            bPaste[i] = card(new Btn(0, 0, 14, BTN_H, AdminUi.c("aas.kadm.card.p"), b -> {
                if (AASClipboard.kitData == null) {
                    AdminUi.toast(AdminUi.t("aas.kadm.card.clip_empty"));
                    return;
                }
                KitAdminClient.snapshot(team, DeckClient.editDeck, true, false, AdminUi.lbl("aas.kadm.log.paste", kit));
                PacketHandler.INSTANCE.sendToServer(new PacketPasteKit(team, kit, AASClipboard.kitData));
                KitAdminClient.request();
                AdminUi.toast(AdminUi.t("aas.kadm.card.pasted", kit));
            }).tip(AdminUi.t("aas.kadm.card.paste_tip")));
            bClear[i] = card(new Btn(0, 0, 14, BTN_H, AdminUi.c("aas.kadm.card.x"), b -> confirm(
                    AdminUi.t("aas.kadm.card.clear_q", kit), AdminUi.t("aas.kadm.card.clear_msg"),
                    () -> KitAdminClient.send(PacketKitAdmin.clearKit(team, kit, false))))
                    .accent(AdminUi.C_RED).tip(AdminUi.t("aas.kadm.card.clear_tip")));
            bMirror[i] = card(new Btn(0, 0, 17, BTN_H, Component.literal(">>"), b -> mirrorKit(idx))
                    .accent(AdminUi.teamColor(other)).tip(AdminUi.t("aas.kadm.card.mirror_tip", AdminUi.team(other))));
        }
        layoutCards();
    }

    private Btn teamTab(String t, int x, int y) {
        Btn b = new Btn(x, y, 60, 16, Component.literal(AdminUi.team(t)), btn -> {
            if (!t.equals(team)) minecraft.setScreen(new KitListScreen(t));
        });
        b.accent(AdminUi.teamColor(t));
        b.selected = t.equals(team);
        return b;
    }

    private Btn card(Btn b) {
        return addWidget(b);
    }

    // ------------------------------------------------------------------ data

    private PacketKitAdminInfo.KitSummary summary(int kitIndex) {
        if (!KitAdminClient.ready()) return null;
        return KitAdminClient.info.kits[AdminUi.teamIndex(team)][kitIndex];
    }

    private void refreshTabs() {
        PacketKitAdminInfo inf = KitAdminClient.info;
        int t = AdminUi.teamIndex(team);
        for (int i = 0; i < DeckType.COUNT; i++) {
            if (deckTabs[i] == null) continue;
            String name = AdminUi.deckName(DeckType.VALUES[i]);
            deckTabs[i].setMessage(Component.literal(name));
            if (inf == null) {
                deckTabs[i].sub = null;
                continue;
            }
            boolean on = inf.enabled[t][i];
            int filled = inf.filled[t][i];
            deckTabs[i].sub = on ? AdminUi.t("aas.kadm.tab.kits", filled, KITS.length) : AdminUi.t("aas.kadm.tab.disabled");
            deckTabs[i].subColor = !on ? AdminUi.C_RED : (filled == KITS.length ? AdminUi.C_GREEN : (filled == 0 ? AdminUi.C_RED : AdminUi.C_GRAY));
            deckTabs[i].color(on ? 0xFFFFFFFF : AdminUi.C_DIM);
            deckTabs[i].tip(on ? AdminUi.t("aas.kadm.tab.edit_tip") : AdminUi.t("aas.kadm.tab.off_tip", AdminUi.team(team)));
        }
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
        refreshTabs();
        refreshUndo();
        layoutCards();
    }

    // ------------------------------------------------------------------ actions

    private void openEditor(int idx, boolean alt) {
        PacketHandler.INSTANCE.sendToServer(new PacketOpenKitEditor(team, KITS[idx], alt));
    }

    private void mirrorKit(int idx) {
        String kit = KITS[idx];
        String other = AdminUi.otherTeam(team);
        PacketKitAdminInfo.KitSummary theirs = null;
        if (KitAdminClient.ready()) theirs = KitAdminClient.info.kits[AdminUi.teamIndex(other)][idx];
        if (theirs != null && theirs.items() > 0) {
            confirm(AdminUi.t("aas.kadm.card.mirror_q", AdminUi.team(other), kit), AdminUi.t("aas.kadm.card.mirror_msg", AdminUi.team(other), theirs.items()),
                    () -> KitAdminClient.send(PacketKitAdmin.copyKitToOtherTeam(team, kit)));
        } else {
            KitAdminClient.send(PacketKitAdmin.copyKitToOtherTeam(team, kit));
        }
    }

    private void pasteKey() {
        String type = AdminUi.clipboardKeyType();
        if (type == null) {
            AdminUi.toast(AdminUi.t("aas.kadm.key.invalid"));
            return;
        }
        if (type.equals("KIT")) {
            AdminUi.toast(AdminUi.t("aas.kadm.key.single_list"));
            return;
        }
        confirm(AdminUi.t("aas.kadm.list.pastekey_q"), AdminUi.t("aas.kadm.list.pastekey_msg", AdminUi.team(team)), () -> {
            KitAdminClient.snapshot(team, DeckClient.editDeck, true, false, AdminUi.lbl("aas.kadm.log.paste_key", AdminUi.teamRef(team)));
            KitKeyUtil.importForTeam(team);
            KitAdminClient.request();
        });
    }

    private void confirm(String title, String message, Runnable yes) {
        minecraft.setScreen(new ConfirmScreen(ok -> {
            minecraft.setScreen(this);
            if (ok) yes.run();
        }, Component.literal(title), Component.literal(message)));
    }

    // ------------------------------------------------------------------ cards: layout

    private void layoutCards() {
        shown.clear();
        String q = searchText.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < KITS.length; i++) {
            PacketKitAdminInfo.KitSummary s = summary(i);
            if (!q.isEmpty()) {
                boolean match = KITS[i].toLowerCase(Locale.ROOT).replace('_', ' ').contains(q)
                        || (s != null && s.displayName() != null && s.displayName().toLowerCase(Locale.ROOT).contains(q));
                if (!match) continue;
            }
            if (s != null) {
                if (filterMode == 1 && s.items() > 0) continue;
                if (filterMode == 2 && s.items() == 0) continue;
                if (filterMode == 3 && !s.hasAlt()) continue;
            }
            shown.add(i);
        }

        int cols = Math.max(1, (width - 2 * MARGIN + GAP) / (CARD_W + GAP));
        int rows = (shown.size() + cols - 1) / cols;
        int viewH = gridBottom - gridTop;
        maxScroll = Math.max(0, rows * (CARD_H + GAP) - GAP - viewH);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        int gridX0 = (width - (cols * (CARD_W + GAP) - GAP)) / 2;

        for (int i = 0; i < KITS.length; i++) cardShown[i] = false;
        for (int p = 0; p < shown.size(); p++) {
            int i = shown.get(p);
            int x = gridX0 + (p % cols) * (CARD_W + GAP);
            int y = gridTop + (p / cols) * (CARD_H + GAP) - scroll;
            cardShown[i] = true;
            cardX[i] = x;
            cardY[i] = y;
            PacketKitAdminInfo.KitSummary s = summary(i);
            int by = y + BTN_Y;
            place(bEdit[i], x + 3, by, true);
            place(bAlt[i], x + 38, by, s != null && s.hasAlt());
            place(bCopy[i], x + 63, by, true);
            place(bPaste[i], x + 78, by, true);
            place(bClear[i], x + 93, by, true);
            place(bMirror[i], x + 108, by, true);
        }
        for (int i = 0; i < KITS.length; i++) {
            if (cardShown[i]) continue;
            bEdit[i].visible = false;
            bAlt[i].visible = false;
            bCopy[i].visible = false;
            bPaste[i].visible = false;
            bClear[i].visible = false;
            bMirror[i].visible = false;
        }
    }

    private void place(Btn b, int x, int y, boolean wanted) {
        b.setX(x);
        b.setY(y);
        b.visible = wanted && y >= gridTop && y + BTN_H <= gridBottom;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (maxScroll > 0) {
            scroll = (int) Math.max(0, Math.min(maxScroll, scroll - delta * 24));
            layoutCards();
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button == 0 && my >= gridTop && my <= gridBottom) {
            for (int p : shown) {
                if (mx >= cardX[p] && mx < cardX[p] + CARD_W && my >= cardY[p] && my < cardY[p] + CARD_H) {
                    openEditor(p, false);
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(GuiGraphics gui, int mx, int my, float pt) {
        renderBackground(gui);

        int col = AdminUi.teamColor(team);
        gui.drawString(font, AdminUi.t("aas.kadm.list.header", AdminUi.team(team)), MARGIN + 188, 10, col, false);

        pasteAllBtn.active = AASClipboard.teamKitsData != null;
        keyPasteBtn.active = true;
        for (int i = 0; i < KITS.length; i++) bPaste[i].active = AASClipboard.kitData != null;

        super.render(gui, mx, my, pt);

        // ---- scrolling card grid ----
        gui.enableScissor(0, gridTop, width, gridBottom);
        if (!KitAdminClient.ready()) {
            gui.drawCenteredString(font, AdminUi.t("aas.kadm.list.loading"), width / 2, gridTop + 20, AdminUi.C_GRAY);
        }
        if (shown.isEmpty() && KitAdminClient.ready()) {
            gui.drawCenteredString(font, AdminUi.t("aas.kadm.list.nomatch"), width / 2, gridTop + 20, AdminUi.C_GRAY);
        }
        for (int i : shown) {
            drawCard(gui, i, mx, my);
            for (Btn b : new Btn[]{bEdit[i], bAlt[i], bCopy[i], bPaste[i], bClear[i], bMirror[i]}) {
                if (b.visible) b.render(gui, mx, my, pt);
            }
        }
        gui.disableScissor();

        if (maxScroll > 0) {
            int viewH = gridBottom - gridTop;
            int barH = Math.max(12, viewH * viewH / (viewH + maxScroll));
            int barY = gridTop + (int) ((long) (viewH - barH) * scroll / maxScroll);
            gui.fill(width - 4, gridTop, width - 1, gridBottom, 0x55000000);
            gui.fill(width - 4, barY, width - 1, barY + barH, 0xFFAAAAAA);
        }

        drawStatus(gui);
    }

    private void drawCard(GuiGraphics gui, int i, int mx, int my) {
        int x = cardX[i];
        int y = cardY[i];
        if (y + CARD_H < gridTop || y > gridBottom) return;
        PacketKitAdminInfo.KitSummary s = summary(i);

        boolean hover = mx >= x && mx < x + CARD_W && my >= y && my < y + CARD_H && my >= gridTop && my <= gridBottom;
        int border = s == null ? 0xFF555555 : (s.items() == 0 ? 0xFFB04040 : 0xFF3F9F3F);
        gui.fill(x, y, x + CARD_W, y + CARD_H, hover ? 0xDD222222 : 0xCC101010);
        gui.renderOutline(x, y, CARD_W, CARD_H, hover ? 0xFFFFFFFF : border);

        String kit = KITS[i];
        String iconPath = kit.toLowerCase(Locale.ROOT).replace(" ", "_").replace("-", "_");
        ResourceLocation icon = new ResourceLocation("aas", "textures/gui/kits/" + iconPath + ".png");
        KitIconStyle.apply(KitIconStyle.DEFAULT_COLOR, 1.0f);
        gui.blit(icon, x + 3, y + 3, 0, 0, 24, 24, 24, 24);
        KitIconStyle.reset();

        gui.drawString(font, AdminUi.fit(font, kit.replace('_', ' '), CARD_W - 34), x + 30, y + 4, 0xFFFFFFFF, false);
        if (s == null) {
            gui.drawString(font, "...", x + 30, y + 14, AdminUi.C_DIM, false);
            return;
        }
        if (s.displayName() != null && !s.displayName().isEmpty()) {
            gui.drawString(font, AdminUi.fit(font, "\"" + s.displayName() + "\"", CARD_W - 34), x + 30, y + 14, 0xFFFFD27F, false);
        }

        // badges
        int bx = x + 30;
        int by = y + 24;
        String items = s.items() == 0 ? AdminUi.t("aas.kadm.card.empty") : AdminUi.t("aas.kadm.card.items", s.items());
        gui.drawString(font, items, bx, by, s.items() == 0 ? AdminUi.C_RED : AdminUi.C_GREEN, false);
        bx += font.width(items) + 5;
        if (s.hasAlt()) {
            String alt = AdminUi.t("aas.kadm.card.alt_n", s.altItems());
            gui.drawString(font, alt, bx, by, AdminUi.C_GOLD, false);
            bx += font.width(alt) + 5;
        }
        if (s.leader()) {
            gui.drawString(font, AdminUi.t("aas.kadm.card.ldr"), bx, by, AdminUi.C_AQUA, false);
        }

        // limits (only when they differ from the defaults)
        if (s.maxTeam() != -1 || s.maxSquad() != -1 || s.minSquad() != 0) {
            String lim = AdminUi.t("aas.kadm.card.limits", limit(s.maxTeam()), limit(s.maxSquad())) + (s.minSquad() > 0 ? AdminUi.t("aas.kadm.card.min", s.minSquad()) : "");
            gui.drawString(font, AdminUi.fit(font, lim, CARD_W - 8), x + 4, y + 34, AdminUi.C_GRAY, false);
        }
    }

    private static String filterName() {
        return AdminUi.t("aas.kadm.filter." + filterMode);
    }

    private static String limit(int v) {
        return v < 0 ? "inf" : String.valueOf(v);
    }

    private void drawStatus(GuiGraphics gui) {
        DeckType d = DeckClient.editDeck;
        String text;
        int color = AdminUi.C_GRAY;
        PacketKitAdminInfo inf = KitAdminClient.info;
        if (KitAdminClient.ready()) {
            int empty = 0;
            for (int i = 0; i < KITS.length; i++) {
                PacketKitAdminInfo.KitSummary s = summary(i);
                if (s != null && s.items() == 0) empty++;
            }
            boolean on = inf.enabled[AdminUi.teamIndex(team)][d.ordinal()];
            text = AdminUi.t("aas.kadm.list.status", shown.size(), KITS.length, empty, AdminUi.deckName(d))
                    + (on ? "" : AdminUi.t("aas.kadm.list.status_off")) + AdminUi.t("aas.kadm.list.status_tip");
            if (!on) color = AdminUi.C_RED;
        } else {
            text = AdminUi.t("aas.kadm.loading");
        }
        gui.drawString(font, AdminUi.fit(font, text, width - 2 * MARGIN), MARGIN, height - 11, color, false);
    }
}