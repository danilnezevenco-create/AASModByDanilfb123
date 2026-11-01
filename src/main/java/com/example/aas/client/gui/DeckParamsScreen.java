package com.example.aas.client.gui;

import com.example.aas.client.DeckClient;
import com.example.aas.client.KitAdminClient;
import com.example.aas.client.gui.AdminUi.Btn;
import com.example.aas.deck.DeckParams;
import com.example.aas.deck.DeckType;
import com.example.aas.network.PacketDeckInfo;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketKitAdmin;
import com.example.aas.network.PacketKitAdminInfo;
import com.example.aas.network.PacketUpdateDeckParams;
import com.example.aas.world.AASWorldData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Admin screen: enabled / hub limit / crate bonus / start tickets of ALL decks of one team in one table.
 * Changed rows are highlighted, "Set all decks" fills a column, SAVE sends only the changed rows.
 */
public class DeckParamsScreen extends Screen implements KitAdminClient.Listener {
    private static final int TABLE_W = 440;
    private static final int ROW_H = 19;

    private static class Row {
        final DeckType deck;
        boolean enabled = true;
        String hub = "0";
        String crate = "0";
        String tickets = "0";
        // values as stored on the server
        boolean oEnabled = true;
        int oHub = 0;
        int oCrate = 0;
        int oTickets = 0;
        EditBox hubBox;
        EditBox crateBox;
        EditBox ticketBox;
        Btn toggle;

        Row(DeckType deck) {
            this.deck = deck;
        }
    }

    private String team;
    private final Screen parent;
    private final Row[] rows = new Row[DeckType.COUNT];
    private String bulkHub = "";
    private String bulkCrate = "";
    private String bulkTickets = "";
    private boolean loaded = false;
    private boolean synced = false;
    private Btn saveBtn;
    private Btn undoBtn;

    public DeckParamsScreen(String team, Screen parent) {
        super(Component.translatable("aas.admin.deck_params"));
        this.team = team;
        this.parent = parent;
        for (int i = 0; i < rows.length; i++) rows[i] = new Row(DeckType.VALUES[i]);
    }

    // ------------------------------------------------------------------ data

    /** Called by DeckClient for the old per-deck info packet; this screen uses PacketKitAdminInfo instead. */
    public void onInfo(PacketDeckInfo msg) {
    }

    private void loadFromInfo() {
        PacketKitAdminInfo inf = KitAdminClient.info;
        if (inf == null) return;
        int t = AdminUi.teamIndex(team);
        for (int d = 0; d < rows.length; d++) {
            Row r = rows[d];
            r.oEnabled = inf.enabled[t][d];
            r.oHub = inf.hub[t][d];
            r.oCrate = inf.crate[t][d];
            r.oTickets = inf.tickets[t][d];
            r.enabled = r.oEnabled;
            r.hub = String.valueOf(r.oHub);
            r.crate = String.valueOf(r.oCrate);
            r.tickets = String.valueOf(r.oTickets);
            if (r.hubBox != null) r.hubBox.setValue(r.hub);
            if (r.crateBox != null) r.crateBox.setValue(r.crate);
            if (r.ticketBox != null) r.ticketBox.setValue(r.tickets);
            updateToggle(r);
        }
        loaded = true;
    }

    @Override
    public void onKitAdminInfo() {
        loadFromInfo();
        refreshUndo();
    }

    private static int num(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private boolean rowDirty(Row r) {
        return r.enabled != r.oEnabled || num(r.hub, r.oHub) != r.oHub
                || num(r.crate, r.oCrate) != r.oCrate || num(r.tickets, r.oTickets) != r.oTickets;
    }

    private int dirtyCount() {
        if (!loaded) return 0;
        int n = 0;
        for (Row r : rows) if (rowDirty(r)) n++;
        return n;
    }

    private int enabledCount() {
        int n = 0;
        for (Row r : rows) if (r.enabled) n++;
        return n;
    }

    // ------------------------------------------------------------------ widgets

    private EditBox box(int x, int y, int w, int maxLen, String value, Consumer<String> onChange) {
        EditBox e = new EditBox(font, x, y, w, 16, Component.empty());
        e.setMaxLength(maxLen);
        e.setFilter(s -> s.matches("\\d*"));
        e.setValue(value);
        e.setResponder(onChange);
        addRenderableWidget(e);
        return e;
    }

    private void updateToggle(Row r) {
        if (r.toggle == null) return;
        r.toggle.setMessage(Component.literal(AdminUi.t(r.enabled ? "aas.kadm.on" : "aas.kadm.off")));
        r.toggle.color(r.enabled ? AdminUi.C_GREEN : AdminUi.C_RED);
    }

    @Override
    protected void init() {
        if (!synced) {
            KitAdminClient.syncEditDeck();
            synced = true;
        }
        if (!loaded) loadFromInfo();

        int x0 = Math.max(4, (width - TABLE_W) / 2);
        final String other = AdminUi.otherTeam(team);

        // ---- team tabs + tools ----
        int ty = 22;
        addRenderableWidget(teamTab("BLUE", x0, ty));
        addRenderableWidget(teamTab("RED", x0 + 64, ty));
        addRenderableWidget(new Btn(x0 + 132, ty, 150, 16, AdminUi.c("aas.kadm.deck.copy_to", AdminUi.team(other)), b -> confirm(
                AdminUi.t("aas.kadm.deck.copy_q"), AdminUi.t("aas.kadm.deck.copy_msg", AdminUi.team(team), AdminUi.team(other)),
                () -> KitAdminClient.send(PacketKitAdmin.paramsToOtherTeam(team))))
                .accent(AdminUi.teamColor(other)).tip(AdminUi.t("aas.kadm.deck.copy_tip")));
        undoBtn = new Btn(x0 + TABLE_W - 80, ty, 80, 16, AdminUi.c("aas.kadm.undo"), b -> KitAdminClient.undo());
        addRenderableWidget(undoBtn);
        refreshUndo();

        // ---- table rows ----
        int y0 = 58;
        for (int i = 0; i < rows.length; i++) {
            final Row r = rows[i];
            int y = y0 + i * ROW_H;
            r.toggle = new Btn(x0 + 110, y, 48, 16, AdminUi.c("aas.kadm.on"), b -> {
                r.enabled = !r.enabled;
                updateToggle(r);
            });
            r.toggle.tip(AdminUi.t("aas.kadm.deck.toggle_tip"));
            addRenderableWidget(r.toggle);
            updateToggle(r);
            r.hubBox = box(x0 + 162, y, 40, 3, r.hub, s -> r.hub = s);
            r.crateBox = box(x0 + 206, y, 44, 4, r.crate, s -> r.crate = s);
            r.ticketBox = box(x0 + 254, y, 56, 6, r.tickets, s -> r.tickets = s);
            addRenderableWidget(new Btn(x0 + 354, y, 38, 16, AdminUi.c("aas.kadm.deck.reset"), b -> resetRow(r))
                    .tip(AdminUi.t("aas.kadm.deck.reset_tip")));
            addRenderableWidget(new Btn(x0 + 396, y, 44, 16, AdminUi.c("aas.kadm.deck.kits"), b -> guard(() -> {
                KitAdminClient.setEditDeck(r.deck);
                minecraft.setScreen(new KitListScreen(team));
            })).accent(AdminUi.C_GOLD).tip(AdminUi.t("aas.kadm.tab.edit_tip")));
        }

        // ---- bulk row ----
        int by = y0 + rows.length * ROW_H + 6;
        box(x0 + 162, by, 40, 3, bulkHub, s -> bulkHub = s);
        box(x0 + 206, by, 44, 4, bulkCrate, s -> bulkCrate = s);
        box(x0 + 254, by, 56, 6, bulkTickets, s -> bulkTickets = s);
        addRenderableWidget(new Btn(x0 + 314, by, 126, 16, AdminUi.c("aas.kadm.deck.apply"), b -> applyBulk())
                .tip(AdminUi.t("aas.kadm.deck.apply_tip")));

        // ---- footer ----
        int fy = by + 26;
        saveBtn = new Btn(width / 2 - 125, fy, 120, 18, AdminUi.c("aas.kadm.deck.save"), b -> save());
        saveBtn.accent(AdminUi.C_GREEN);
        addRenderableWidget(saveBtn);
        addRenderableWidget(new Btn(width / 2 - 2, fy, 62, 18, AdminUi.c("aas.kadm.deck.revert"), b -> {
            loadFromInfo();
            AdminUi.toast(AdminUi.t("aas.kadm.deck.reverted"));
        }).tip(AdminUi.t("aas.kadm.common.discard_tip")));
        addRenderableWidget(new Btn(width / 2 + 62, fy, 62, 18, Component.translatable("aas.admin.back"),
                b -> guard(() -> minecraft.setScreen(parent))));
    }

    private Btn teamTab(String t, int x, int y) {
        Btn b = new Btn(x, y, 60, 16, Component.literal(AdminUi.team(t)), btn -> {
            if (!t.equals(team)) guard(() -> {
                team = t;
                loaded = false;
                loadFromInfo();
                rebuildWidgets();
            });
        });
        b.accent(AdminUi.teamColor(t));
        b.selected = t.equals(team);
        return b;
    }

    private void refreshUndo() {
        if (undoBtn == null) return;
        int n = KitAdminClient.undoDepth();
        undoBtn.setMessage(Component.literal(n > 0 ? AdminUi.t("aas.kadm.undo_n", n) : AdminUi.t("aas.kadm.undo")));
        undoBtn.active = n > 0;
        String label = KitAdminClient.info == null ? "" : KitAdminClient.info.undoLabel;
        undoBtn.tip(n > 0 ? AdminUi.t("aas.kadm.undo_tip", AdminUi.label(label)) : AdminUi.t("aas.kadm.undo_none"));
    }

    // ------------------------------------------------------------------ actions

    private void resetRow(Row r) {
        DeckParams d = DeckParams.defaultsFor(r.deck);
        r.enabled = true;
        r.hub = String.valueOf(d.hubLimit);
        r.crate = String.valueOf(d.crateBonus);
        r.tickets = String.valueOf(d.tickets);
        r.hubBox.setValue(r.hub);
        r.crateBox.setValue(r.crate);
        r.ticketBox.setValue(r.tickets);
        updateToggle(r);
    }

    private void applyBulk() {
        for (Row r : rows) {
            if (!bulkHub.isEmpty()) {
                r.hub = bulkHub;
                r.hubBox.setValue(bulkHub);
            }
            if (!bulkCrate.isEmpty()) {
                r.crate = bulkCrate;
                r.crateBox.setValue(bulkCrate);
            }
            if (!bulkTickets.isEmpty()) {
                r.tickets = bulkTickets;
                r.ticketBox.setValue(bulkTickets);
            }
        }
    }

    private void save() {
        if (!loaded) return;
        if (enabledCount() == 0) {
            AdminUi.toast(AdminUi.t("aas.kadm.deck.min_one"));
            return;
        }
        int n = dirtyCount();
        if (n == 0) {
            AdminUi.toast(AdminUi.t("aas.kadm.deck.nochange"));
            return;
        }
        KitAdminClient.snapshot(team, null, false, true, AdminUi.lbl("aas.kadm.log.deck_settings", AdminUi.teamRef(team)));
        // enabling rows first: the server refuses to disable the last enabled deck
        for (int pass = 0; pass < 2; pass++) {
            for (Row r : rows) {
                if (!rowDirty(r) || (pass == 0) != r.enabled) continue;
                int hub = Math.max(0, Math.min(999, num(r.hub, r.oHub)));
                int crate = Math.max(0, Math.min(DeckParams.MAX_CRATE_BONUS, num(r.crate, r.oCrate)));
                int tickets = Math.max(0, num(r.tickets, r.oTickets));
                PacketHandler.INSTANCE.sendToServer(new PacketUpdateDeckParams(team, r.deck.ordinal(), r.enabled, hub, crate, tickets));
            }
        }
        KitAdminClient.request();   // reloads what the server really stored (it clamps the values)
        AdminUi.toast(AdminUi.t("aas.kadm.deck.saved", n));
    }

    private void guard(Runnable go) {
        if (dirtyCount() == 0) {
            go.run();
            return;
        }
        confirm(AdminUi.t("aas.kadm.common.discard_q"), AdminUi.t("aas.kadm.deck.unsaved_msg"), go);
    }

    private void confirm(String title, String message, Runnable yes) {
        minecraft.setScreen(new ConfirmScreen(ok -> {
            minecraft.setScreen(this);
            if (ok) yes.run();
        }, Component.literal(title), Component.literal(message)));
    }

    @Override
    public void onClose() {
        guard(() -> minecraft.setScreen(parent));
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(GuiGraphics gui, int mx, int my, float pt) {
        renderBackground(gui);
        int x0 = Math.max(4, (width - TABLE_W) / 2);
        gui.drawCenteredString(font, Component.translatable("aas.admin.deck_params_title", team), width / 2, 8, AdminUi.teamColor(team));

        int hy = 47;
        int hc = AdminUi.C_GRAY;
        gui.drawString(font, AdminUi.t("aas.kadm.deck.h_deck"), x0 + 2, hy, hc, false);
        gui.drawString(font, AdminUi.t("aas.kadm.deck.h_enabled"), x0 + 112, hy, hc, false);
        gui.drawString(font, AdminUi.t("aas.kadm.deck.h_hubs"), x0 + 164, hy, hc, false);
        gui.drawString(font, AdminUi.t("aas.kadm.deck.h_crate"), x0 + 208, hy, hc, false);
        gui.drawString(font, AdminUi.t("aas.kadm.deck.h_tickets"), x0 + 256, hy, hc, false);
        gui.drawString(font, AdminUi.t("aas.kadm.deck.h_kits"), x0 + 318, hy, hc, false);

        PacketKitAdminInfo inf = KitAdminClient.info;
        int t = AdminUi.teamIndex(team);
        int y0 = 58;
        for (int i = 0; i < rows.length; i++) {
            Row r = rows[i];
            int y = y0 + i * ROW_H;
            boolean dirty = rowDirty(r);
            boolean current = r.deck == DeckClient.editDeck;
            gui.fill(x0 - 2, y - 2, x0 + TABLE_W + 2, y + 17, dirty ? 0x55FFFF00 : (current ? 0x33FFFFFF : 0x22000000));
            int nameCol = r.enabled ? 0xFFFFFFFF : AdminUi.C_DIM;
            gui.drawString(font, AdminUi.fit(font, AdminUi.deckName(r.deck), 104), x0 + 2, y + 4, nameCol, false);
            if (inf != null) {
                int f = inf.filled[t][i];
                int total = AASWorldData.KIT_NAMES.length;
                gui.drawString(font, f + "/" + total, x0 + 318, y + 4, f == total ? AdminUi.C_GREEN : (f == 0 ? AdminUi.C_RED : AdminUi.C_GRAY), false);
            }
            r.crateBox.setTextColor(num(r.crate, 0) > DeckParams.MAX_CRATE_BONUS ? 0xFF5555 : 0xE0E0E0);
        }

        int by = y0 + rows.length * ROW_H + 6;
        gui.drawString(font, AdminUi.t("aas.kadm.deck.set_all"), x0 + 2, by + 4, AdminUi.C_GRAY, false);

        int dirty = dirtyCount();
        saveBtn.setMessage(Component.literal(dirty > 0 ? AdminUi.t("aas.kadm.deck.save_n", dirty) : AdminUi.t("aas.kadm.deck.save")));
        saveBtn.active = loaded && dirty > 0 && enabledCount() > 0;

        super.render(gui, mx, my, pt);

        int fy = by + 26 + 22;
        if (!loaded) {
            gui.drawCenteredString(font, AdminUi.t("aas.kadm.loading"), width / 2, fy, AdminUi.C_GRAY);
        } else if (enabledCount() == 0) {
            gui.drawCenteredString(font, AdminUi.t("aas.kadm.deck.min_one_banner"), width / 2, fy, AdminUi.C_RED);
        } else {
            gui.drawCenteredString(font, AdminUi.t("aas.kadm.deck.hint", DeckParams.MAX_CRATE_BONUS), width / 2, fy, AdminUi.C_DIM);
        }
    }
}