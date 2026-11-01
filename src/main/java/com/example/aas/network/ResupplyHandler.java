package com.example.aas.network;

import com.example.aas.world.AASWorldData;
import com.example.aas.events.GameLogicEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.Map;

public class ResupplyHandler {

    /**
     * Р’Р«Р”РђР§Рђ РљРРўРђ: РѕС‡РёС‰Р°РµС‚ РёРЅРІРµРЅС‚Р°СЂСЊ Рё Р·Р°РїРѕР»РЅСЏРµС‚ РµРіРѕ.
     * РЎР»РѕС‚С‹ 41-48 РїС‹С‚Р°СЋС‚СЃСЏ СЃРЅР°С‡Р°Р»Р° РїРѕРїР°СЃС‚СЊ РІ Curios.
     */
    public static void applyKitToPlayer(ServerPlayer player, AASWorldData.KitInfo kit) { applyKitToPlayer(player, kit, false); }

    public static void applyKitToPlayer(ServerPlayer player, AASWorldData.KitInfo kit, boolean isAlt) {
        Inventory pInv = player.getInventory();
        pInv.clearContent();
        clearCurios(player);

        Map<Integer, CompoundTag> savedTags = GameLogicEvents.PERSISTENT_NBT_STORAGE.remove(player.getUUID());

        for (int i = 0; i < 49; i++) {
            ItemStack kitStack = kit.inventory.get(i);
            if (kitStack.isEmpty()) continue;

            ItemStack itemToGive = kitStack.copy();

            if (i < kit.saveNbtFlags.length && kit.saveNbtFlags[i] && savedTags != null && savedTags.containsKey(i)) {
                itemToGive.setTag(savedTags.get(i));
            }

            if (i < 41) {
                pInv.setItem(i, itemToGive);
            } else {
                boolean equipped = false;
                if (ModList.get().isLoaded("curios")) {
                    equipped = tryEquipInCurios(player, itemToGive);
                }

                if (!equipped) {
                    if (!pInv.add(itemToGive)) {
                        player.drop(itemToGive, false);
                    }
                }
            }
        }
        player.getPersistentData().putString("AAS_CurrentKit", kit.name);
        player.getPersistentData().putBoolean("AAS_CurrentKitAlt", isAlt);
        com.example.aas.network.PacketHandler.INSTANCE.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new com.example.aas.network.PacketSyncMyKit(kit.name));
    }

    /** РћС‡РёСЃС‚РєР° РІСЃРµС… СЃР»РѕС‚РѕРІ Curios */
    public static void clearCurios(ServerPlayer player) {
        if (ModList.get().isLoaded("curios")) {
            try {
                top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
                    for (var entry : handler.getCurios().values()) {
                        var stacks = entry.getStacks();
                        for (int i = 0; i < stacks.getSlots(); i++) {
                            stacks.setStackInSlot(i, ItemStack.EMPTY);
                        }
                    }
                });
            } catch (Exception ignored) {}
        }
    }

    /** РђРІС‚РѕРјР°С‚РёС‡РµСЃРєР°СЏ СЌРєРёРїРёСЂРѕРІРєР° РІ Curios (1.20.1). */
    private static boolean tryEquipInCurios(ServerPlayer player, ItemStack stack) {
        try {
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).map(handler -> {
                for (var entry : handler.getCurios().entrySet()) {
                    var stacksHandler = entry.getValue().getStacks();

                    for (int i = 0; i < stacksHandler.getSlots(); i++) {
                        if (stacksHandler.getStackInSlot(i).isEmpty() && stacksHandler.isItemValid(i, stack)) {
                            stacksHandler.setStackInSlot(i, stack.copy());
                            stack.setCount(0);
                            return true;
                        }
                    }
                }
                return false;
            }).orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * РџРћРџРћР›РќР•РќРР• РљРРўРђ. Р’СЃСЏ Р»РѕРіРёРєР° РІС‹РЅРµСЃРµРЅР° РІ KitResupplier
     * (СЃСЂР°РІРЅРµРЅРёРµ РїСЂРµРґРјРµС‚РѕРІ Р±РµР· РёР·РјРµРЅС‡РёРІРѕРіРѕ NBT, СЃСѓРјРјР°СЂРЅС‹Р№ РїРѕРґСЃС‡С‘С‚, Р»РёРјРёС‚ РїРѕ СЃР»РѕС‚Р°Рј РєРёС‚Р°).
     */
    public static boolean resupplyPlayer(ServerPlayer player, AASWorldData.KitInfo kit, boolean isAmmoBagSource) {
        return KitResupplier.resupply(player, kit, isAmmoBagSource);
    }

    public static void tryApplyPendingKit(ServerPlayer player, AASWorldData data) {
        // --- 1. РћРџР Р•Р”Р•Р›РЇР•Рњ Р¦Р•Р›Р•Р’РћР™ РљРРў ---
        String pending = player.getPersistentData().getString("AAS_PendingKit");
        String current = player.getPersistentData().getString("AAS_CurrentKit");
        boolean pendingAlt = player.getPersistentData().getBoolean("AAS_PendingKitAlt");
        boolean currentAlt = player.getPersistentData().getBoolean("AAS_CurrentKitAlt");

        // Р•СЃР»Рё РµСЃС‚СЊ РѕС‡РµСЂРµРґСЊ (Pending) вЂ” Р±РµСЂС‘Рј РµС‘, РµСЃР»Рё РїСѓСЃС‚Р° вЂ” Р±РµСЂС‘Рј С‚РµРєСѓС‰РёР№ РєРёС‚
        String targetKitName = !pending.isEmpty() ? pending : current;
        boolean targetIsAlt = !pending.isEmpty() ? pendingAlt : currentAlt;

        // Р•СЃР»Рё РєРёС‚Р° РЅРµС‚ РёР»Рё СЌС‚Рѕ "Unassigned", РїСЂРѕСЃС‚Рѕ РѕС‡РёС‰Р°РµРј РѕС‡РµСЂРµРґСЊ Рё РІС‹С…РѕРґРёРј
        if (targetKitName.isEmpty() || targetKitName.equals("Unassigned")) {
            player.getPersistentData().remove("AAS_PendingKit");
            return;
        }

        // --- РџР РћР’Р•Р РљРђ: РЎРўРђР Рў РР“Р Р« ---
        // Р•СЃР»Рё СЂР°СѓРЅРґ РµС‰С‘ РЅРµ РЅР°С‡Р°С‚, РЅРµ РІС‹РґР°С‘Рј РїСЂРµРґРјРµС‚С‹, РїСЂРѕСЃС‚Рѕ РѕСЃС‚Р°РІР»СЏРµРј Pending.
        if (!data.isGameStarted && !player.isCreative()) {
            player.displayClientMessage(Component.translatable("aas.msg.kit_reserved", targetKitName).withStyle(ChatFormatting.YELLOW), true);
            return;
        }

        // --- 2. РџР РћР’Р•Р РљРђ РџРћР”Р“РћРўРћР’РљР Р’ INVASION ---
        if (data.gameMode.equalsIgnoreCase("INVASION") && data.invasionPrepTicks > 0) {
            String pTeam = player.getTeam() != null ? player.getTeam().getName().toUpperCase() : "NEUTRAL";
            if (!pTeam.equals(data.invasionDefender) && !player.isCreative()) {
                player.displayClientMessage(Component.translatable("aas.msg.invasion_prep").withStyle(ChatFormatting.RED), true);
                return;
            }
        }

        String teamName = player.getTeam() != null ? player.getTeam().getName().toUpperCase() : "";
        if (teamName.isEmpty()) return;

        AASWorldData.KitInfo kit = data.getKitVariant(teamName, targetKitName, targetIsAlt);
        if (kit == null) return;

        // --- 3. РџР РћР’Р•Р РљРђ Р›РРњРРўРћР’ (РљР РћРњР• РЎРђРњРћР“Рћ РР“Р РћРљРђ) ---
        int teamCount = 0;
        int squadCount = 0;
        String pName = player.getScoreboardName();
        AASWorldData.Squad mySquad = null;

        for (AASWorldData.Squad s : data.squads) {
            if (s.members.contains(pName)) {
                mySquad = s; break;
            }
        }

        for (ServerPlayer p : player.server.getPlayerList().getPlayers()) {
            if (p == player) continue; // РќРµ СЃС‡РёС‚Р°РµРј СЃР°РјРѕРіРѕ СЃРµР±СЏ
            if (p.getTeam() != null && p.getTeam().getName().toUpperCase().equals(teamName)) {
                String otherCurrent = p.getPersistentData().getString("AAS_CurrentKit");
                String otherPending = p.getPersistentData().getString("AAS_PendingKit");
                // Р•СЃР»Рё РґСЂСѓРіРѕР№ РёРіСЂРѕРє СѓР¶Рµ РёСЃРїРѕР»СЊР·СѓРµС‚ РёР»Рё Р·Р°Р±СЂРѕРЅРёСЂРѕРІР°Р» СЌС‚РѕС‚ РєРёС‚
                if (otherCurrent.equals(targetKitName) || otherPending.equals(targetKitName)) {
                    teamCount++;
                    if (mySquad != null && mySquad.members.contains(p.getScoreboardName())) {
                        squadCount++;
                    }
                }
            }
        }

        boolean allowed = true;
        if (kit.maxPerTeam > 0 && teamCount >= kit.maxPerTeam) allowed = false;
        if (kit.maxPerSquad > 0 && squadCount >= kit.maxPerSquad) allowed = false;
        if (kit.minSquadPlayers > 0 && (mySquad == null || mySquad.members.size() < kit.minSquadPlayers)) allowed = false;
        if (kit.isLeaderOnly && (mySquad == null || !mySquad.leader.equals(pName))) allowed = false;

        // --- 4. РџР РРњР•РќР•РќРР• РљРРўРђ ---
        if (allowed) {
            // applyKitToPlayer СЃР°Рј РѕС‡РёСЃС‚РёС‚ РёРЅРІРµРЅС‚Р°СЂСЊ РїРµСЂРµРґ РІС‹РґР°С‡РµР№
            applyKitToPlayer(player, kit, targetIsAlt);
        } else {
            // Р•СЃР»Рё Р·Р° РІСЂРµРјСЏ СЃРјРµСЂС‚Рё Р»РёРјРёС‚С‹ РЅР° РєРёС‚ Р·Р°Р±РёР»РёСЃСЊ РґСЂСѓРіРёРјРё РёРіСЂРѕРєР°РјРё
            player.sendSystemMessage(Component.translatable("aas.msg.kit_blocked", targetKitName).withStyle(ChatFormatting.RED));
            AASWorldData.KitInfo unassigned = data.activeKits(teamName).get("Unassigned");
            if (unassigned != null) applyKitToPlayer(player, unassigned, false);
        }

        // РћС‡РёС‰Р°РµРј Pending (Р±СЂРѕРЅСЊ), С‚Р°Рє РєР°Рє РјС‹ РµС‘ РѕР±СЂР°Р±РѕС‚Р°Р»Рё
        player.getPersistentData().remove("AAS_PendingKit");
        player.getPersistentData().remove("AAS_PendingKitAlt");
        // РЎРёРЅС…СЂРѕРЅРёР·РёСЂСѓРµРј РёРєРѕРЅРєРё РєРёС‚РѕРІ РЅР° РєР°СЂС‚Рµ
        PacketHandler.sendToAllClients(player.serverLevel(), data);
    }
}
