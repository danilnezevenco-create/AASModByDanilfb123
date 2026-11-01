package com.example.aas.network;

import com.example.aas.world.AASWorldData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class PacketRequestKitMenu {
    public PacketRequestKitMenu() {}
    public static void encode(PacketRequestKitMenu msg, FriendlyByteBuf buf) {}
    public static PacketRequestKitMenu decode(FriendlyByteBuf buf) { return new PacketRequestKitMenu(); }

    // PATH: src/main/java/com/example/aas/network/PacketRequestKitMenu.java

    public static void handle(PacketRequestKitMenu msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || player.getTeam() == null) return;

            AASWorldData data = AASWorldData.get(player.serverLevel());
            String teamName = player.getTeam().getName().toUpperCase();
            String pName = player.getScoreboardName();

            AASWorldData.Squad mySquad = null;
            for (AASWorldData.Squad s : data.squads) {
                if (s.members.contains(pName)) { mySquad = s; break; }
            }

            // РџСЂРѕРІРµСЂСЏРµРј, СЏРІР»СЏРµС‚СЃСЏ Р»Рё РёРіСЂРѕРє Р»РёРґРµСЂРѕРј СЃРІРѕРµРіРѕ РѕС‚СЂСЏРґР°
            boolean amILeader = (mySquad != null && mySquad.leader.equals(pName));

            List<PacketOpenPlayerKitMenu.KitDTO> dtoList = new ArrayList<>();

            for (String kitName : AASWorldData.KIT_NAMES) {
                AASWorldData.KitInfo kit = data.activeKits(teamName).get(kitName);
                if (kit == null) continue;

                // --- РќРћР’РђРЇ Р›РћР“РРљРђ Р¤РР›Р¬РўР РђР¦РР ---
                // Р•СЃР»Рё РєРёС‚ С‚РѕР»СЊРєРѕ РґР»СЏ Р»РёРґРµСЂРѕРІ, Р° РёРіСЂРѕРє РќР• Р»РёРґРµСЂ вЂ” РїРѕР»РЅРѕСЃС‚СЊСЋ СЃРєСЂС‹РІР°РµРј РµРіРѕ (РїСЂРѕРїСѓСЃРєР°РµРј РёС‚РµСЂР°С†РёСЋ)
                if (kit.isLeaderOnly && !amILeader && !player.isCreative()) {
                    continue;
                }
                // -------------------------------

                if (kit.maxPerTeam == 0 && !kitName.equals("Unassigned")) continue;

                int tCount = 0;
                int sCount = 0;

                for (ServerPlayer p : player.server.getPlayerList().getPlayers()) {
                    if (p == player) continue;
                    if (p.getTeam() != null && p.getTeam().getName().toUpperCase().equals(teamName)) {
                        String cKit = p.getPersistentData().getString("AAS_CurrentKit");
                        String pKit = p.getPersistentData().getString("AAS_PendingKit");
                        if (cKit.equals(kitName) || pKit.equals(kitName)) {
                            tCount++;
                            if (mySquad != null && mySquad.members.contains(p.getScoreboardName())) {
                                sCount++;
                            }
                        }
                    }
                }

                boolean available = true;
                String reason = "";

                // РџСЂРѕРІРµСЂРєРё Р»РёРјРёС‚РѕРІ (С‚РµРїРµС‚СЊ С‚СѓС‚ РЅРµ РЅСѓР¶РЅРѕ РїСЂРѕРІРµСЂСЏС‚СЊ РЅР° Р»РёРґРµСЂР°, С‚Р°Рє РєР°Рє РјС‹ СЃРєСЂС‹Р»Рё РєРёС‚ РІС‹С€Рµ)
                if (kit.maxPerTeam > 0 && tCount >= kit.maxPerTeam) {
                    available = false;
                    reason = "Team Full (" + tCount + "/" + kit.maxPerTeam + ")";
                }
                else if (kit.maxPerSquad > 0 && sCount >= kit.maxPerSquad) {
                    available = false;
                    reason = "Squad Full (" + sCount + "/" + kit.maxPerSquad + ")";
                }
                else if (kit.minSquadPlayers > 0 && (mySquad == null || mySquad.members.size() < kit.minSquadPlayers)) {
                    available = false;
                    reason = "Need " + kit.minSquadPlayers + " players in Squad";
                }

                List<ItemStack> kitPreviewItems = new ArrayList<>(kit.inventory);

                // === РђР›Р¬РўР•Р РќРђРўРР’РќРђРЇ Р’Р•Р РЎРРЇ РљРРўРђ (РµСЃР»Рё РІРєР»СЋС‡РµРЅР° Р°РґРјРёРЅРѕРј) ===
                // РћРіСЂР°РЅРёС‡РµРЅРёСЏ (Team/Squad/Leader) РѕР±С‰РёРµ РґР»СЏ РєР»Р°СЃСЃР° вЂ” РґРѕСЃС‚СѓРїРЅРѕСЃС‚СЊ Сѓ Р°Р»СЊС‚. РІРµСЂСЃРёРё С‚Р° Р¶Рµ,
                // С‡С‚Рѕ Рё Сѓ СЃС‚Р°РЅРґР°СЂС‚РЅРѕР№, С‚Р°Рє РєР°Рє СЌС‚Рѕ РїСЂРѕСЃС‚Рѕ РґСЂСѓРіРѕР№ РЅР°Р±РѕСЂ СЃРЅР°СЂСЏР¶РµРЅРёСЏ.
                boolean hasAlt = kit.hasAlt && kit.altKit != null;
                List<ItemStack> altPreviewItems = hasAlt ? new ArrayList<>(kit.altKit.inventory) : new ArrayList<>();

                // === РќРћР’РћР•: РїРѕРґСЃС‚Р°РІР»СЏРµРј РєР°СЃС‚РѕРјРЅС‹Рµ РЅР°Р·РІР°РЅРёСЏ, РµСЃР»Рё Р·Р°РґР°РЅС‹ ===
                String stdDisplay = (kit.displayName != null && !kit.displayName.isEmpty()) ? kit.displayName : kitName;
                String altDisplay = hasAlt
                        ? ((kit.altKit.displayName != null && !kit.altKit.displayName.isEmpty()) ? kit.altKit.displayName : kitName)
                        : kitName;

                dtoList.add(new PacketOpenPlayerKitMenu.KitDTO(kitName, available, reason, kitPreviewItems, hasAlt, altPreviewItems,
                        kit.isLeaderOnly, stdDisplay, altDisplay));
            }
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new PacketOpenPlayerKitMenu(dtoList));
        });
        ctx.get().setPacketHandled(true);
    }
}
