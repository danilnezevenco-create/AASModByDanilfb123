package com.example.aas.network;

import com.example.aas.deck.DeckManager;
import com.example.aas.deck.DeckParams;
import com.example.aas.deck.DeckType;
import com.example.aas.deck.KitUndo;
import com.example.aas.menu.KitEditorMenu;
import com.example.aas.world.AASWorldData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Admin (creative) -> server: one packet for all the "bulk" operations of the kit / deck admin menu.
 * Every operation that changes kits / deck params stores an undo snapshot first (see KitUndo) and
 * answers with a fresh PacketKitAdminInfo.
 *
 * Fields (which ones are used depends on the op):
 *   team  BLUE / RED (SNAPSHOT also accepts BOTH)
 *   kit   kit name (SNAPSHOT: the label of the undo entry)
 *   deck  deck ordinal, -1 = all decks / not used
 *   mask  CLONE/COPY_KIT_DECKS: bit mask of target decks; SNAPSHOT: 1 = kits, 2 = params
 *   flag  CLEAR_KIT: only remove the ALT variant; CLONE_VARIANT: the source is the ALT variant
 */
public class PacketKitAdmin {
    public static final int REQUEST = 0;           // just send a fresh PacketKitAdminInfo
    public static final int UNDO = 1;              // revert the last change
    public static final int SNAPSHOT = 2;          // store an undo snapshot (sent BEFORE the old packets that change data)
    public static final int CLEAR_KIT = 3;
    public static final int CLEAR_TEAM = 4;        // all kits of the team in the edit deck
    public static final int COPY_KIT_TEAM = 5;     // kit -> same kit of the other team (edit deck)
    public static final int COPY_TEAM_TEAM = 6;    // all kits -> other team (edit deck)
    public static final int COPY_KIT_DECKS = 7;    // kit (edit deck) -> same kit in the decks of the mask
    public static final int CLONE_VARIANT = 8;     // STANDARD -> ALT or ALT -> STANDARD
    public static final int PARAMS_TEAM = 9;       // deck params of all decks -> other team
    public static final int EDITOR_CLEAR = 10;     // kit editor: empty all kit slots
    public static final int EDITOR_FROM_INV = 11;  // kit editor: kit slots = my inventory
    public static final int EDITOR_TO_INV = 12;    // kit editor: my inventory = kit slots

    public final int op;
    public final String team;
    public final String kit;
    public final int deck;
    public final int mask;
    public final boolean flag;

    public PacketKitAdmin(int op, String team, String kit, int deck, int mask, boolean flag) {
        this.op = op;
        this.team = team == null ? "" : team;
        this.kit = kit == null ? "" : kit;
        this.deck = deck;
        this.mask = mask;
        this.flag = flag;
    }

    // ------------------------------------------------------------------ factories

    public static PacketKitAdmin request() { return new PacketKitAdmin(REQUEST, "", "", -1, 0, false); }
    public static PacketKitAdmin undo() { return new PacketKitAdmin(UNDO, "", "", -1, 0, false); }
    public static PacketKitAdmin snapshot(String team, int deck, boolean kits, boolean params, String label) {
        return new PacketKitAdmin(SNAPSHOT, team, label, deck, (kits ? 1 : 0) | (params ? 2 : 0), false);
    }
    public static PacketKitAdmin clearKit(String team, String kit, boolean altOnly) {
        return new PacketKitAdmin(CLEAR_KIT, team, kit, -1, 0, altOnly);
    }
    public static PacketKitAdmin clearTeam(String team) { return new PacketKitAdmin(CLEAR_TEAM, team, "", -1, 0, false); }
    public static PacketKitAdmin copyKitToOtherTeam(String team, String kit) {
        return new PacketKitAdmin(COPY_KIT_TEAM, team, kit, -1, 0, false);
    }
    public static PacketKitAdmin copyTeamToOtherTeam(String team) {
        return new PacketKitAdmin(COPY_TEAM_TEAM, team, "", -1, 0, false);
    }
    public static PacketKitAdmin copyKitToDecks(String team, String kit, int deckMask) {
        return new PacketKitAdmin(COPY_KIT_DECKS, team, kit, -1, deckMask, false);
    }
    public static PacketKitAdmin cloneVariant(String team, String kit, boolean sourceIsAlt) {
        return new PacketKitAdmin(CLONE_VARIANT, team, kit, -1, 0, sourceIsAlt);
    }
    public static PacketKitAdmin paramsToOtherTeam(String team) {
        return new PacketKitAdmin(PARAMS_TEAM, team, "", -1, 0, false);
    }
    public static PacketKitAdmin editorClear() { return new PacketKitAdmin(EDITOR_CLEAR, "", "", -1, 0, false); }
    public static PacketKitAdmin editorFromInventory() { return new PacketKitAdmin(EDITOR_FROM_INV, "", "", -1, 0, false); }
    public static PacketKitAdmin editorToInventory() { return new PacketKitAdmin(EDITOR_TO_INV, "", "", -1, 0, false); }

    // ------------------------------------------------------------------ network

    public static void encode(PacketKitAdmin m, FriendlyByteBuf b) {
        b.writeVarInt(m.op);
        b.writeUtf(m.team, 16);
        b.writeUtf(m.kit, 160);
        b.writeVarInt(m.deck + 1);
        b.writeVarInt(m.mask);
        b.writeBoolean(m.flag);
    }

    public static PacketKitAdmin decode(FriendlyByteBuf b) {
        int op = b.readVarInt();
        String team = b.readUtf(16);
        String kit = b.readUtf(160);
        int deck = b.readVarInt() - 1;
        int mask = b.readVarInt();
        boolean flag = b.readBoolean();
        return new PacketKitAdmin(op, team, kit, deck, mask, flag);
    }

    public static void handle(PacketKitAdmin msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.isCreative()) return;
            apply(msg, player);
        });
        ctx.get().setPacketHandled(true);
    }

    // ------------------------------------------------------------------ server logic

    private static String norm(String team) {
        if ("BLUE".equalsIgnoreCase(team)) return "BLUE";
        if ("RED".equalsIgnoreCase(team)) return "RED";
        return null;
    }

    private static AASWorldData.KitInfo copyOf(AASWorldData.KitInfo k) {
        return AASWorldData.KitInfo.load(k.save());
    }

    /** Message above the hotbar; key = aas.kadm.srv.*, translated by the client. */
    private static void say(ServerPlayer p, String key, Object... args) {
        p.displayClientMessage(Component.translatable(key, args), true);
    }

    private static Component teamName(String team) {
        return Component.translatable("aas.kadm.team." + team.toLowerCase(Locale.ROOT));
    }

    /** Undo label: key + TAB + args (an arg starting with '@' is a translation key); the client translates it. */
    private static String lbl(String key, String... args) {
        StringBuilder sb = new StringBuilder(key);
        for (String a : args) sb.append('\t').append(a);
        return sb.toString();
    }

    private static String teamRef(String team) {
        return "@aas.kadm.team." + team.toLowerCase(Locale.ROOT);
    }

    /** Turns an encoded undo label back into a translatable Component (for chat messages). */
    private static Component labelComponent(String raw) {
        String[] parts = raw.split("\t", -1);
        Object[] args = new Object[parts.length - 1];
        for (int i = 1; i < parts.length; i++) {
            args[i - 1] = parts[i].startsWith("@") ? Component.translatable(parts[i].substring(1)) : parts[i];
        }
        return Component.translatable(parts[0], args);
    }

    private static void finish(ServerPlayer player, AASWorldData data) {
        data.setDirty();
        PacketHandler.sendToAllClients(player.serverLevel(), data);
        PacketKitAdminInfo.send(player);
    }

    private static void apply(PacketKitAdmin m, ServerPlayer player) {
        AASWorldData data = AASWorldData.get(player.serverLevel());
        DeckType edit = DeckManager.editDeckOf(player);

        // ---- ops that need no valid team ----
        switch (m.op) {
            case REQUEST -> {
                PacketKitAdminInfo.send(player);
                return;
            }
            case UNDO -> {
                String label = KitUndo.undo(player, data);
                if (label == null) {
                    say(player, "aas.kadm.srv.nothing_undo");
                    PacketKitAdminInfo.send(player);
                } else {
                    say(player, "aas.kadm.srv.undone", labelComponent(label));
                    finish(player, data);
                }
                return;
            }
            case SNAPSHOT -> {
                DeckType d = m.deck < 0 ? null : DeckType.byOrdinal(m.deck);
                if (m.deck >= 0 && d == null) return;
                KitUndo.push(player, data, m.kit, m.team, d, (m.mask & 1) != 0, (m.mask & 2) != 0);
                PacketKitAdminInfo.send(player);
                return;
            }
            case EDITOR_CLEAR, EDITOR_FROM_INV, EDITOR_TO_INV -> {
                editorOp(m.op, player);
                return;
            }
            default -> { }
        }

        String team = norm(m.team);
        if (team == null) return;
        String other = team.equals("BLUE") ? "RED" : "BLUE";
        Map<String, AASWorldData.KitInfo> mine = data.kitsFor(team, edit);

        switch (m.op) {
            case CLEAR_KIT -> {
                AASWorldData.KitInfo base = mine.get(m.kit);
                if (base == null) return;
                KitUndo.push(player, data, m.flag ? lbl("aas.kadm.log.remove_alt", m.kit) : lbl("aas.kadm.log.clear", m.kit), team, edit, true, false);
                if (m.flag) {
                    base.altKit = null;
                    base.hasAlt = false;
                } else {
                    mine.put(m.kit, new AASWorldData.KitInfo(m.kit));
                }
                say(player, m.flag ? "aas.kadm.srv.alt_removed" : "aas.kadm.srv.kit_cleared", m.kit);
            }
            case CLEAR_TEAM -> {
                KitUndo.push(player, data, lbl("aas.kadm.log.clear_team", teamRef(team)), team, edit, true, false);
                for (String name : AASWorldData.KIT_NAMES) mine.put(name, new AASWorldData.KitInfo(name));
                say(player, "aas.kadm.srv.team_cleared", teamName(team));
            }
            case COPY_KIT_TEAM -> {
                AASWorldData.KitInfo base = mine.get(m.kit);
                if (base == null) return;
                KitUndo.push(player, data, lbl("aas.kadm.log.kit_to_team", m.kit, teamRef(other)), other, edit, true, false);
                data.kitsFor(other, edit).put(m.kit, copyOf(base));
                say(player, "aas.kadm.srv.kit_copied", m.kit, teamName(other));
            }
            case COPY_TEAM_TEAM -> {
                KitUndo.push(player, data, lbl("aas.kadm.log.team_to_team", teamRef(team), teamRef(other)), other, edit, true, false);
                Map<String, AASWorldData.KitInfo> target = data.kitsFor(other, edit);
                for (String name : AASWorldData.KIT_NAMES) {
                    AASWorldData.KitInfo src = mine.get(name);
                    if (src != null) target.put(name, copyOf(src));
                }
                say(player, "aas.kadm.srv.team_copied", teamName(team), teamName(other));
            }
            case COPY_KIT_DECKS -> {
                AASWorldData.KitInfo base = mine.get(m.kit);
                if (base == null) return;
                KitUndo.push(player, data, lbl("aas.kadm.log.kit_to_decks", m.kit), team, null, true, false);
                int n = 0;
                for (DeckType d : DeckType.VALUES) {
                    if (d == edit || (m.mask & (1 << d.ordinal())) == 0) continue;
                    data.kitsFor(team, d).put(m.kit, copyOf(base));
                    n++;
                }
                say(player, "aas.kadm.srv.decks_copied", m.kit, n);
            }
            case CLONE_VARIANT -> {
                AASWorldData.KitInfo base = mine.get(m.kit);
                if (base == null) return;
                if (!m.flag) {
                    // STANDARD -> ALT (copy of the standard kit, without its own alt)
                    KitUndo.push(player, data, lbl("aas.kadm.log.std_to_alt", m.kit), team, edit, true, false);
                    CompoundTag t = base.save();
                    t.remove("AltKit");
                    t.putBoolean("HasAlt", false);
                    AASWorldData.KitInfo alt = AASWorldData.KitInfo.load(t);
                    alt.name = m.kit;
                    alt.hasAlt = false;
                    alt.altKit = null;
                    base.altKit = alt;
                    base.hasAlt = true;
                    say(player, "aas.kadm.srv.std_to_alt", m.kit);
                } else {
                    // ALT -> STANDARD (items, flags and name; limits of the standard kit stay)
                    if (base.altKit == null) return;
                    KitUndo.push(player, data, lbl("aas.kadm.log.alt_to_std", m.kit), team, edit, true, false);
                    AASWorldData.KitInfo src = copyOf(base.altKit);
                    for (int i = 0; i < 49; i++) base.inventory.set(i, src.inventory.get(i));
                    base.resupplyFlags = src.resupplyFlags;
                    base.saveNbtFlags = src.saveNbtFlags;
                    base.displayName = src.displayName;
                    say(player, "aas.kadm.srv.alt_to_std", m.kit);
                }
            }
            case PARAMS_TEAM -> {
                KitUndo.push(player, data, lbl("aas.kadm.log.params_team", teamRef(team), teamRef(other)), other, null, false, true);
                for (DeckType d : DeckType.VALUES) {
                    DeckParams from = data.paramsFor(team, d);
                    DeckParams to = data.paramsFor(other, d);
                    to.enabled = from.enabled;
                    to.hubLimit = from.hubLimit;
                    to.crateBonus = from.crateBonus;
                    to.tickets = from.tickets;
                }
                say(player, "aas.kadm.srv.params_copied", teamName(other));
            }
            default -> {
                return;
            }
        }
        finish(player, data);
    }

    /** Operations on the kit editor window that is open on the server (player.containerMenu). */
    private static void editorOp(int op, ServerPlayer player) {
        if (!(player.containerMenu instanceof KitEditorMenu menu)) return;
        switch (op) {
            case EDITOR_CLEAR -> {
                for (int i = 0; i < KitEditorMenu.MAX_KIT_SLOTS; i++) menu.kitInventory.setItem(i, ItemStack.EMPTY);
            }
            case EDITOR_FROM_INV -> {
                // kit slot index == player inventory index: 0-8 hotbar, 9-35 main, 36-39 armor, 40 offhand
                for (int i = 0; i <= 40; i++) menu.kitInventory.setItem(i, player.getInventory().getItem(i).copy());
            }
            case EDITOR_TO_INV -> {
                for (int i = 0; i <= 40; i++) player.getInventory().setItem(i, menu.kitInventory.getItem(i).copy());
                player.getInventory().setChanged();
            }
            default -> { }
        }
        menu.broadcastChanges();
    }
}