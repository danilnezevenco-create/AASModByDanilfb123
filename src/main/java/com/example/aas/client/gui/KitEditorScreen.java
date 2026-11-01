package com.example.aas.client.gui;

import com.example.aas.client.AASClipboard;
import com.example.aas.client.DeckClient;
import com.example.aas.client.KitAdminClient;
import com.example.aas.client.KitKeyUtil;
import com.example.aas.client.gui.AdminUi.Btn;
import com.example.aas.deck.DeckType;
import com.example.aas.menu.KitEditorMenu;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketKitAdmin;
import com.example.aas.network.PacketOpenKitEditor;
import com.example.aas.network.PacketPasteKit;
import com.example.aas.network.PacketRequestKitData;
import com.example.aas.network.PacketSaveKit;
import com.example.aas.world.AASWorldData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Kit editor (admin). Top: kit navigation, team / deck switch, STANDARD / ALTERNATIVE tabs, name, limits.
 * Right: tools panel. Unsaved changes are tracked: leaving, switching variant / kit / team / deck asks first.
 * Slot layout is defined by KitEditorMenu and was not changed.
 */
public class KitEditorScreen extends AbstractContainerScreen<KitEditorMenu> {

    private static final int TOOLS_W = 108;
    private static final int RESUPPLY_COLOR = 0x50FFFF00;
    private static final int NBT_COLOR = 0x500060FF;

    private EditBox nameBox;
    private EditBox maxTeamBox;
    private EditBox maxSquadBox;
    private EditBox minPlayersBox;
    private final List<EditBox> boxes = new ArrayList<>();
    private Btn leaderBtn;
    private Btn hasAltBtn;
    private Btn tabAlt;

    /** State hash of the saved version; taken a few ticks after opening (the slot contents arrive after init). */
    private Integer baseHash = null;
    private int ticks = 0;

    // modal question drawn on top of everything
    private String ovTitle = null;
    private String[] ovLines = new String[0];
    private final List<Btn> ovBtns = new ArrayList<>();

    public KitEditorScreen(KitEditorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 216;
        this.imageHeight = 322;
        this.inventoryLabelY = 228;
        this.titleLabelY = 6;
    }

    // ------------------------------------------------------------------ build

    private Btn add(Btn b) {
        return addRenderableWidget(b);
    }

    private EditBox box(int x, int y, int w, int h, int maxLen, String value, String filter) {
        EditBox e = new EditBox(font, x, y, w, h, Component.empty());
        e.setMaxLength(maxLen);
        if (filter != null) e.setFilter(s -> s.matches(filter));
        e.setValue(value);
        boxes.add(e);
        addRenderableWidget(e);
        return e;
    }

    private String otherTeam() {
        return AdminUi.otherTeam(menu.team);
    }

    @Override
    protected void init() {
        super.init();
        boxes.clear();
        ovBtns.clear();
        int x = leftPos;
        int y = topPos;
        final KitEditorMenu m = menu;

        // ---- header: previous / next kit ----
        add(new Btn(x + 4, y + 2, 14, 13, Component.literal("<"), b -> openKit(-1)).tip(AdminUi.t("aas.kadm.ed.prev")));
        add(new Btn(x + 198, y + 2, 14, 13, Component.literal(">"), b -> openKit(1)).tip(AdminUi.t("aas.kadm.ed.next")));

        // ---- team + deck ----
        add(new Btn(x + 4, y + 17, 62, 14, AdminUi.c("aas.kadm.ed.team", AdminUi.team(m.team)), b ->
                guard(() -> open(otherTeam(), m.kitName, m.isAlt)))
                .accent(AdminUi.teamColor(m.team)).tip(AdminUi.t("aas.kadm.ed.team_tip", AdminUi.team(otherTeam()))));
        add(new Btn(x + 68, y + 17, 144, 14, AdminUi.c("aas.kadm.ed.deck", AdminUi.deckName(DeckClient.editDeck)), b ->
                guard(() -> changeDeck(hasShiftDown() ? -1 : 1)))
                .accent(AdminUi.C_GOLD).tip(AdminUi.t("aas.kadm.deck.click_tip")));

        // ---- variant tabs ----
        Btn tabStd = new Btn(x + 4, y + 33, 102, 14, AdminUi.c("aas.kadm.ed.std"), b -> {
            if (m.isAlt) guard(this::switchVariant);
        });
        tabStd.selected = !m.isAlt;
        tabStd.accent(AdminUi.C_GREEN);
        add(tabStd);
        tabAlt = new Btn(x + 110, y + 33, 102, 14, AdminUi.c("aas.kadm.ed.alt"), b -> {
            if (!m.isAlt) guard(this::switchVariant);
        });
        tabAlt.selected = m.isAlt;
        tabAlt.accent(AdminUi.C_GOLD);
        tabAlt.active = m.isAlt || m.hasAlt;
        tabAlt.tip(AdminUi.t("aas.kadm.ed.alt_tip"));
        add(tabAlt);

        // ---- name ----
        nameBox = box(x + 36, y + 49, 172, 12, 32, m.displayName == null ? "" : m.displayName, null);
        nameBox.setSuggestion(m.kitName + (m.isAlt ? AdminUi.t("aas.kadm.ed.alt_suffix") : ""));

        // ---- limits ----
        maxTeamBox = box(x + 84, y + 65, 36, 14, 5, String.valueOf(m.maxPerTeam), "-?\\d*");
        maxSquadBox = box(x + 84, y + 81, 36, 14, 5, String.valueOf(m.maxPerSquad), "-?\\d*");
        minPlayersBox = box(x + 84, y + 97, 36, 14, 5, String.valueOf(m.minSquadPlayers), "\\d*");
        for (EditBox e : new EditBox[]{maxTeamBox, maxSquadBox, minPlayersBox}) e.setEditable(!m.isAlt);

        leaderBtn = new Btn(x + 124, y + 65, 88, 14, Component.empty(), b -> {
            m.isLeaderOnly = !m.isLeaderOnly;
            updateToggles();
        });
        leaderBtn.active = !m.isAlt;
        leaderBtn.tip(AdminUi.t("aas.kadm.ed.leader_tip"));
        add(leaderBtn);
        hasAltBtn = new Btn(x + 124, y + 81, 88, 14, Component.empty(), b -> {
            m.hasAlt = !m.hasAlt;
            updateToggles();
        });
        hasAltBtn.active = !m.isAlt;
        hasAltBtn.tip(AdminUi.t("aas.kadm.ed.hasalt_tip"));
        add(hasAltBtn);
        updateToggles();

        add(new Btn(x + 108, y + 205, 104, 18, AdminUi.c("aas.kadm.ed.save"), b -> saveKit())
                .accent(AdminUi.C_GREEN).tip("Ctrl+S"));

        buildTools(x + imageWidth + 6, y);
    }

    private void updateToggles() {
        leaderBtn.setMessage(AdminUi.c("aas.kadm.ed.leader", AdminUi.t(menu.isLeaderOnly ? "aas.kadm.on" : "aas.kadm.off")));
        leaderBtn.color(menu.isLeaderOnly ? AdminUi.C_AQUA : 0xFFFFFFFF);
        hasAltBtn.setMessage(AdminUi.c("aas.kadm.ed.altkit", AdminUi.t(menu.hasAlt ? "aas.kadm.on" : "aas.kadm.off")));
        hasAltBtn.color(menu.hasAlt ? AdminUi.C_GOLD : 0xFFFFFFFF);
        if (tabAlt != null) tabAlt.active = menu.isAlt || menu.hasAlt;
    }

    private void buildTools(int tx, int y) {
        final KitEditorMenu m = menu;
        int half = (TOOLS_W - 4) / 2;

        add(new Btn(tx, y + 14, TOOLS_W, 14, AdminUi.c("aas.kadm.ed.save_exit"), b -> {
            saveKit();
            closeToList();
        }).accent(AdminUi.C_GREEN));
        add(new Btn(tx, y + 30, half, 14, AdminUi.c("aas.kadm.ed.revert"), b -> confirm(AdminUi.t("aas.kadm.common.discard_q"),
                new String[]{AdminUi.t("aas.kadm.ed.revert_msg")}, AdminUi.t("aas.kadm.common.discard"), () -> open(m.team, m.kitName, m.isAlt)))
                .tip(AdminUi.t("aas.kadm.common.discard_tip")));
        add(new Btn(tx + half + 4, y + 30, half, 14, AdminUi.c("aas.kadm.ed.undo"), b -> guard(() -> {
            KitAdminClient.undo();
            open(m.team, m.kitName, m.isAlt);
        })).tip(AdminUi.t("aas.kadm.ed.undo_tip")));

        // slots
        add(new Btn(tx, y + 58, half, 14, AdminUi.c("aas.kadm.ed.clear"), b -> {
            KitAdminClient.send(PacketKitAdmin.editorClear());
            Arrays.fill(m.resupplyFlags, false);
            Arrays.fill(m.saveNbtFlags, false);
        }).accent(AdminUi.C_RED).tip(AdminUi.t("aas.kadm.ed.clear_tip")));
        add(new Btn(tx + half + 4, y + 58, half, 14, AdminUi.c("aas.kadm.ed.from_inv"), b ->
                KitAdminClient.send(PacketKitAdmin.editorFromInventory()))
                .tip(AdminUi.t("aas.kadm.ed.from_inv_tip")));
        add(new Btn(tx, y + 74, TOOLS_W, 14, AdminUi.c("aas.kadm.ed.to_inv"), b -> {
            KitAdminClient.send(PacketKitAdmin.editorToInventory());
            AdminUi.toast(AdminUi.t("aas.kadm.ed.to_inv_done"));
        }).tip(AdminUi.t("aas.kadm.ed.to_inv_tip")));

        // flags
        add(new Btn(tx, y + 102, half, 14, AdminUi.c("aas.kadm.ed.resup_on"), b -> setFlags(true, true))
                .accent(0xFFFFFF55).tip(AdminUi.t("aas.kadm.ed.resup_on_tip")));
        add(new Btn(tx + half + 4, y + 102, half, 14, AdminUi.c("aas.kadm.ed.resup_off"), b -> setFlags(true, false))
                .tip(AdminUi.t("aas.kadm.ed.resup_off_tip")));
        add(new Btn(tx, y + 118, half, 14, AdminUi.c("aas.kadm.ed.nbt_on"), b -> setFlags(false, true))
                .accent(0xFF5599FF).tip(AdminUi.t("aas.kadm.ed.nbt_on_tip")));
        add(new Btn(tx + half + 4, y + 118, half, 14, AdminUi.c("aas.kadm.ed.nbt_off"), b -> setFlags(false, false))
                .tip(AdminUi.t("aas.kadm.ed.nbt_off_tip")));

        // copy / sync (they save first)
        add(new Btn(tx, y + 146, TOOLS_W, 14, AdminUi.c(m.isAlt ? "aas.kadm.ed.clone_to_std" : "aas.kadm.ed.clone_to_alt"), b -> {
            Runnable go = () -> saveThen(() -> {
                KitAdminClient.send(PacketKitAdmin.cloneVariant(m.team, m.kitName, m.isAlt));
                open(m.team, m.kitName, !m.isAlt);
            });
            if (m.isAlt || m.hasAlt) {
                confirm(AdminUi.t("aas.kadm.ed.clone_q"),
                        new String[]{AdminUi.t("aas.kadm.ed.clone_msg", AdminUi.t(m.isAlt ? "aas.kadm.ed.std" : "aas.kadm.ed.alt")), AdminUi.t("aas.kadm.ed.saved_first")},
                        AdminUi.t("aas.kadm.ed.overwrite"), go);
            } else {
                go.run();
            }
        }).tip(AdminUi.t("aas.kadm.ed.clone_tip")));
        add(new Btn(tx, y + 162, TOOLS_W, 14, AdminUi.c("aas.kadm.ed.to_team", AdminUi.team(otherTeam())), b -> saveThen(() -> {
            KitAdminClient.send(PacketKitAdmin.copyKitToOtherTeam(m.team, m.kitName));
        })).accent(AdminUi.teamColor(otherTeam())).tip(AdminUi.t("aas.kadm.ed.to_team_tip", AdminUi.team(otherTeam()))));
        add(new Btn(tx, y + 178, TOOLS_W, 14, AdminUi.c("aas.kadm.ed.to_decks"), b -> confirm(AdminUi.t("aas.kadm.ed.to_decks_q"),
                new String[]{AdminUi.t("aas.kadm.ed.to_decks_l1"), AdminUi.t("aas.kadm.ed.to_decks_l2", DeckType.COUNT - 1, AdminUi.team(m.team)), AdminUi.t("aas.kadm.ed.saved_first")},
                AdminUi.t("aas.kadm.ed.copy"), () -> saveThen(() -> {
                    int mask = ((1 << DeckType.COUNT) - 1) & ~(1 << DeckClient.editDeck.ordinal());
                    KitAdminClient.send(PacketKitAdmin.copyKitToDecks(m.team, m.kitName, mask));
                }))).accent(AdminUi.C_GOLD).tip(AdminUi.t("aas.kadm.ed.to_decks_tip")));

        // clipboard
        add(new Btn(tx, y + 206, half, 14, AdminUi.c("aas.kadm.ed.copy"), b -> {
            PacketHandler.INSTANCE.sendToServer(new PacketRequestKitData(m.team, m.kitName, m.isAlt));
            AdminUi.toast(AdminUi.t("aas.kadm.ed.copied"));
        }).tip(AdminUi.t("aas.kadm.ed.copy_tip")));
        add(new Btn(tx + half + 4, y + 206, half, 14, AdminUi.c("aas.kadm.ed.paste"), b -> {
            if (AASClipboard.kitData == null) {
                AdminUi.toast(AdminUi.t("aas.kadm.card.clip_empty"));
                return;
            }
            guard(() -> {
                KitAdminClient.snapshot(m.team, DeckClient.editDeck, true, false, AdminUi.lbl("aas.kadm.log.paste", m.kitName));
                PacketHandler.INSTANCE.sendToServer(new PacketPasteKit(m.team, m.kitName, m.isAlt, AASClipboard.kitData));
                open(m.team, m.kitName, m.isAlt);
            });
        }).tip(AdminUi.t("aas.kadm.ed.paste_tip")));
        add(new Btn(tx, y + 222, half, 14, AdminUi.c("aas.kadm.ed.get_key"), b ->
                KitKeyUtil.requestCopy("KIT", m.team, m.kitName, m.isAlt))
                .tip(AdminUi.t("aas.kadm.ed.get_key_tip")));
        add(new Btn(tx + half + 4, y + 222, half, 14, AdminUi.c("aas.kadm.ed.set_key"), b -> {
            String type = AdminUi.clipboardKeyType();
            if (type == null) {
                AdminUi.toast(AdminUi.t("aas.kadm.key.invalid"));
                return;
            }
            guard(() -> {
                KitAdminClient.snapshot(m.team, DeckClient.editDeck, true, false, AdminUi.lbl("aas.kadm.log.key_into", m.kitName));
                KitKeyUtil.importForEditor(m.team, m.kitName, m.isAlt);
            });
        }).tip(AdminUi.t("aas.kadm.ed.set_key_tip")));
    }

    // ------------------------------------------------------------------ state / dirty tracking

    private int stateHash() {
        int h = 17;
        for (int i = 0; i < KitEditorMenu.MAX_KIT_SLOTS; i++) {
            ItemStack s = menu.kitInventory.getItem(i);
            if (s.isEmpty()) {
                h = h * 31;
            } else {
                h = h * 31 + s.getItem().hashCode();
                h = h * 31 + s.getCount();
                h = h * 31 + (s.getTag() == null ? 0 : s.getTag().hashCode());
            }
            h = h * 31 + (menu.resupplyFlags[i] ? 1 : 0);
            h = h * 31 + (menu.saveNbtFlags[i] ? 1 : 0);
        }
        h = h * 31 + (menu.isLeaderOnly ? 1 : 0);
        h = h * 31 + (menu.hasAlt ? 1 : 0);
        for (EditBox e : boxes) h = h * 31 + e.getValue().hashCode();
        return h;
    }

    private boolean isDirty() {
        return baseHash != null && stateHash() != baseHash;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ticks++;
        if (baseHash == null && ticks >= 6) baseHash = stateHash();
    }

    private static int parse(EditBox b, int def) {
        try {
            return Integer.parseInt(b.getValue().trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private void saveKit() {
        if (!menu.isAlt) {
            menu.maxPerTeam = Math.max(-1, parse(maxTeamBox, menu.maxPerTeam));
            menu.maxPerSquad = Math.max(-1, parse(maxSquadBox, menu.maxPerSquad));
            menu.minSquadPlayers = Math.max(0, parse(minPlayersBox, menu.minSquadPlayers));
        }
        menu.displayName = nameBox.getValue().trim();

        KitAdminClient.snapshot(menu.team, DeckClient.editDeck, true, false,
                AdminUi.lbl(menu.isAlt ? "aas.kadm.log.save_alt" : "aas.kadm.log.save", menu.kitName));
        PacketHandler.INSTANCE.sendToServer(new PacketSaveKit(
                menu.team, menu.kitName, menu.isAlt, menu.hasAlt, menu.isLeaderOnly,
                menu.maxPerTeam, menu.maxPerSquad, menu.minSquadPlayers,
                menu.displayName, menu.resupplyFlags, menu.saveNbtFlags));
        KitAdminClient.request();
        baseHash = stateHash();
        AdminUi.toast(AdminUi.t(menu.isAlt ? "aas.kadm.ed.saved_alt" : "aas.kadm.ed.saved"));
    }

    private void saveThen(Runnable op) {
        if (isDirty()) saveKit();
        op.run();
    }

    private void setFlags(boolean resupply, boolean value) {
        for (int i = 0; i < KitEditorMenu.MAX_KIT_SLOTS; i++) {
            boolean filled = !menu.kitInventory.getItem(i).isEmpty();
            if (value && !filled) continue;
            if (resupply) menu.resupplyFlags[i] = value;
            else menu.saveNbtFlags[i] = value;
        }
    }

    // ------------------------------------------------------------------ navigation

    private void open(String team, String kit, boolean alt) {
        PacketHandler.INSTANCE.sendToServer(new PacketOpenKitEditor(team, kit, alt));
    }

    private void switchVariant() {
        open(menu.team, menu.kitName, !menu.isAlt);
    }

    private void openKit(int delta) {
        String[] names = AASWorldData.KIT_NAMES;
        int idx = 0;
        for (int i = 0; i < names.length; i++) if (names[i].equals(menu.kitName)) idx = i;
        String next = names[(idx + delta + names.length) % names.length];
        guard(() -> open(menu.team, next, false));
    }

    private void changeDeck(int step) {
        KitAdminClient.setEditDeck(DeckClient.editDeck.next(step));
        open(menu.team, menu.kitName, menu.isAlt);
    }

    private void closeToList() {
        this.minecraft.player.closeContainer();
        this.minecraft.setScreen(new KitListScreen(menu.team));
    }

    /** ESC: ALT tab goes back to STANDARD, STANDARD tab goes back to the kit list. */
    @Override
    public void onClose() {
        if (ovTitle != null) {
            closeOverlay();
            return;
        }
        if (menu.isAlt) guard(this::switchVariant);
        else guard(this::closeToList);
    }

    // ------------------------------------------------------------------ modal question

    /** Runs go now, or asks Save / Discard / Cancel when there are unsaved changes. */
    private void guard(Runnable go) {
        if (!isDirty()) {
            go.run();
            return;
        }
        openOverlay(AdminUi.t("aas.kadm.ed.unsaved_t"), new String[]{AdminUi.t("aas.kadm.ed.unsaved_l")},
                new String[]{AdminUi.t("aas.kadm.ed.save_continue"), AdminUi.t("aas.kadm.common.discard"), AdminUi.t("aas.kadm.common.cancel")},
                new Runnable[]{() -> {
                    saveKit();
                    go.run();
                }, go, null});
    }

    private void confirm(String title, String[] lines, String yesLabel, Runnable yes) {
        openOverlay(title, lines, new String[]{yesLabel, AdminUi.t("aas.kadm.common.cancel")}, new Runnable[]{yes, null});
    }

    private void openOverlay(String title, String[] lines, String[] labels, Runnable[] actions) {
        ovTitle = title;
        ovLines = lines;
        ovBtns.clear();
        int boxW = 240;
        int boxH = 40 + lines.length * 11 + 24;
        int bx = (width - boxW) / 2;
        int by = (height - boxH) / 2;
        int n = labels.length;
        int bw = (boxW - 16 - (n - 1) * 6) / n;
        for (int i = 0; i < n; i++) {
            final Runnable action = actions[i];
            Btn b = new Btn(bx + 8 + i * (bw + 6), by + boxH - 26, bw, 18, Component.literal(labels[i]), x -> {
                closeOverlay();
                if (action != null) action.run();
            });
            if (i == 0 && n > 1) b.accent(AdminUi.C_GREEN);
            ovBtns.add(b);
        }
    }

    private void closeOverlay() {
        ovTitle = null;
        ovBtns.clear();
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (ovTitle != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) closeOverlay();
            return true;
        }
        if (hasControlDown() && key == GLFW.GLFW_KEY_S) {
            saveKit();
            return true;
        }
        if (key != GLFW.GLFW_KEY_ESCAPE) {
            // typing in a text field must not trigger the inventory key (E) that closes the window
            for (EditBox e : boxes) {
                if (e.isFocused() && (e.keyPressed(key, scan, mods) || e.canConsumeInput())) return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (ovTitle != null) return true;
        return super.charTyped(c, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (ovTitle != null) {
            for (Btn b : ovBtns) {
                if (b.mouseClicked(mx, my, button)) return true;
            }
            return true;
        }
        if (button == 2) { // middle mouse: toggle resupply / save NBT of the hovered slot
            Slot slot = this.hoveredSlot;
            if (slot != null && slot.container == menu.kitInventory) {
                int idx = slot.getContainerSlot();
                if (idx >= 0 && idx < KitEditorMenu.MAX_KIT_SLOTS) {
                    if (hasShiftDown()) menu.saveNbtFlags[idx] = !menu.saveNbtFlags[idx];
                    else menu.resupplyFlags[idx] = !menu.resupplyFlags[idx];
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        return ovTitle != null || super.mouseScrolled(mx, my, delta);
    }

    // ------------------------------------------------------------------ render

    @Override
    protected void renderLabels(GuiGraphics gui, int mouseX, int mouseY) {
        gui.drawString(font, this.playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }

    @Override
    protected void renderBg(GuiGraphics gui, float pt, int mx, int my) {
        gui.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF333333);
        gui.fill(leftPos, topPos, leftPos + imageWidth, topPos + 2, menu.isAlt ? 0xFFFFAA00 : 0xFF55FF55);

        // extra / curios slots panel
        gui.fill(leftPos - 42, topPos + 122, leftPos - 2, topPos + 202, 0xFF222222);
        gui.renderOutline(leftPos - 42, topPos + 122, 40, 80, 0xFF000000);

        // tools panel
        int tx = leftPos + imageWidth + 3;
        gui.fill(tx, topPos, tx + TOOLS_W + 6, topPos + 300, 0xFF262626);
        gui.renderOutline(tx, topPos, TOOLS_W + 6, 300, 0xFF000000);

        for (Slot slot : menu.slots) {
            gui.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, 0xFF000000);
            gui.fill(leftPos + slot.x, topPos + slot.y, leftPos + slot.x + 16, topPos + slot.y + 16, 0xFF8B8B8B);
        }
    }

    private static String slotName(int idx) {
        if (idx < 9) return AdminUi.t("aas.kadm.slot.hotbar", idx + 1);
        if (idx < 36) return AdminUi.t("aas.kadm.slot.inventory", idx - 8);
        switch (idx) {
            case 36: return AdminUi.t("aas.kadm.slot.boots");
            case 37: return AdminUi.t("aas.kadm.slot.leggings");
            case 38: return AdminUi.t("aas.kadm.slot.chestplate");
            case 39: return AdminUi.t("aas.kadm.slot.helmet");
            case 40: return AdminUi.t("aas.kadm.slot.offhand");
            default: return AdminUi.t("aas.kadm.slot.extra", idx - 40);
        }
    }

    @Override
    public void render(GuiGraphics gui, int mx, int my, float pt) {
        boolean modal = ovTitle != null;
        renderBackground(gui);
        super.render(gui, modal ? -1000 : mx, modal ? -1000 : my, pt);

        int x = leftPos;
        int y = topPos;
        int gray = AdminUi.C_GRAY;

        // header title
        String title = AdminUi.team(menu.team) + " | " + menu.kitName.replace('_', ' ') + (menu.isAlt ? AdminUi.t("aas.kadm.ed.alt_tag") : "");
        gui.drawCenteredString(font, AdminUi.fit(font, title, 170), x + 108, y + 5, AdminUi.teamColor(menu.team));

        // field labels
        gui.drawString(font, AdminUi.t("aas.kadm.ed.l_name"), x + 4, y + 51, gray, false);
        gui.drawString(font, AdminUi.t("aas.kadm.ed.l_maxteam"), x + 4, y + 68, gray, false);
        gui.drawString(font, AdminUi.t("aas.kadm.ed.l_maxsquad"), x + 4, y + 84, gray, false);
        gui.drawString(font, AdminUi.t("aas.kadm.ed.l_minsquad"), x + 4, y + 100, gray, false);
        gui.drawString(font, AdminUi.t(menu.isAlt ? "aas.kadm.ed.hint_alt" : "aas.kadm.ed.hint_limits"),
                x + 4, y + 114, AdminUi.C_DIM, false);

        // slot group labels
        gui.drawString(font, AdminUi.t("aas.kadm.ed.extra_label"), x - 40, y + 113, gray, false);
        gui.drawString(font, AdminUi.t("aas.kadm.ed.armor_label"), x + 8, y + 201, gray, false);
        gui.drawString(font, AdminUi.t("aas.kadm.slot.offhand"), x + 84, y + 201, gray, false);

        // tools panel headings
        int tx = x + imageWidth + 6;
        gui.drawString(font, AdminUi.t("aas.kadm.ed.h_tools"), tx, y + 3, 0xFFFFFFFF, false);
        if (isDirty()) gui.drawString(font, AdminUi.t("aas.kadm.ed.unsaved_mark"), tx + 44, y + 3, 0xFFFFD700, false);
        gui.drawString(font, AdminUi.t("aas.kadm.ed.h_slots"), tx, y + 48, gray, false);
        gui.drawString(font, AdminUi.t("aas.kadm.ed.h_flags"), tx, y + 92, gray, false);
        gui.drawString(font, AdminUi.t("aas.kadm.ed.h_sync"), tx, y + 136, gray, false);
        gui.drawString(font, AdminUi.t("aas.kadm.ed.h_clip"), tx, y + 196, gray, false);

        // legend
        gui.fill(tx, y + 244, tx + 6, y + 250, 0xFFFFFF55);
        gui.drawString(font, AdminUi.t("aas.kadm.ed.leg_resup"), tx + 10, y + 243, gray, false);
        gui.fill(tx, y + 255, tx + 6, y + 261, 0xFF5599FF);
        gui.drawString(font, AdminUi.t("aas.kadm.ed.leg_nbt"), tx + 10, y + 254, gray, false);
        gui.drawString(font, AdminUi.t("aas.kadm.ed.leg_shift"), tx, y + 265, AdminUi.C_DIM, false);

        // hovered kit slot info
        if (!modal && hoveredSlot != null && hoveredSlot.container == menu.kitInventory) {
            int idx = hoveredSlot.getContainerSlot();
            if (idx >= 0 && idx < KitEditorMenu.MAX_KIT_SLOTS) {
                gui.drawString(font, slotName(idx), tx, y + 278, 0xFFFFFFFF, false);
                gui.drawString(font, AdminUi.t("aas.kadm.ed.slot_flags", AdminUi.t(menu.resupplyFlags[idx] ? "aas.kadm.on" : "aas.kadm.off"), AdminUi.t(menu.saveNbtFlags[idx] ? "aas.kadm.on" : "aas.kadm.off")),
                        tx, y + 288, menu.resupplyFlags[idx] || menu.saveNbtFlags[idx] ? AdminUi.C_GREEN : AdminUi.C_DIM, false);
            }
        }

        // resupply / NBT marks above the items
        gui.pose().pushPose();
        gui.pose().translate(0, 0, 200);
        for (Slot slot : menu.slots) {
            if (slot.container != menu.kitInventory) continue;
            int idx = slot.getContainerSlot();
            if (idx < 0 || idx >= KitEditorMenu.MAX_KIT_SLOTS) continue;
            int sx = leftPos + slot.x;
            int sy = topPos + slot.y;
            if (menu.resupplyFlags[idx]) {
                gui.fill(sx, sy, sx + 16, sy + 16, RESUPPLY_COLOR);
                gui.fill(sx, sy, sx + 4, sy + 4, 0xFFFFFF55);
            }
            if (menu.saveNbtFlags[idx]) {
                gui.fill(sx, sy, sx + 16, sy + 16, NBT_COLOR);
                gui.fill(sx + 12, sy, sx + 16, sy + 4, 0xFF5599FF);
            }
        }
        gui.pose().popPose();

        // modal question
        if (modal) {
            gui.pose().pushPose();
            gui.pose().translate(0, 0, 500);
            gui.fill(0, 0, width, height, 0xA0000000);
            int boxW = 240;
            int boxH = 40 + ovLines.length * 11 + 24;
            int bx = (width - boxW) / 2;
            int by = (height - boxH) / 2;
            gui.fill(bx, by, bx + boxW, by + boxH, 0xFF202020);
            gui.renderOutline(bx, by, boxW, boxH, 0xFFFFFFFF);
            gui.drawCenteredString(font, ovTitle, width / 2, by + 8, 0xFFFFD700);
            for (int i = 0; i < ovLines.length; i++) {
                gui.drawCenteredString(font, ovLines[i], width / 2, by + 26 + i * 11, 0xFFDDDDDD);
            }
            for (Btn b : ovBtns) b.render(gui, mx, my, pt);
            gui.pose().popPose();
        }
    }
}