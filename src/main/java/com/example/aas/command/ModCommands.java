// PATH: src\main\java\com\example\aas\command\ModCommands.java
package com.example.aas.command;

import com.example.aas.events.GameLogicEvents;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketSquadAction;
import com.example.aas.world.AASWorldData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import com.example.aas.config.AASConfig;

public class ModCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("aas")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("rebuildvehiclelist")
                        .executes(ctx -> com.example.aas.world.VehicleRegistryRebuilder.startFromCommand(ctx.getSource()))
                )
                .then(Commands.literal("art")
                        .then(Commands.literal("resetcooldown")
                                .executes(ctx -> {
                                    com.example.aas.art.ArtManager.resetCooldowns(ctx.getSource().getLevel());
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "Artillery cooldowns reset (all SL requests + both team strikes)")
                                            .withStyle(ChatFormatting.GREEN), true);
                                    return 1;
                                })))
                .then(Commands.literal("uav")
                        .then(Commands.literal("resetcooldown")
                                .executes(ctx -> {
                                    com.example.aas.uav.UavManager.resetCooldowns(ctx.getSource().getLevel());
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "UAV cooldowns reset (both teams)")
                                            .withStyle(ChatFormatting.GREEN), true);
                                    return 1;
                                })))
                // --- РЈРџР РђР’Р›Р•РќРР• РР“Р РћР™ ---
                // --- PREPARATION: teams -> deck vote -> VS screen -> countdown ---
                .then(Commands.literal("prepare")
                        .then(Commands.literal("start").executes(ctx -> prepareStart(ctx.getSource())))
                        .then(Commands.literal("cancel").executes(ctx -> prepareCancel(ctx.getSource()))))
                .then(Commands.literal("gamestart")
                        .then(Commands.argument("active", BoolArgumentType.bool())
                                .executes(ctx -> setGameStart(ctx.getSource(), BoolArgumentType.getBool(ctx, "active")))))
                // --- РЎР‘Р РћРЎ Р­РљР РђРќРђ РџРћР‘Р•Р”Р« (CAPS СЃРЅРѕРІР° РѕС‚РєСЂС‹РІР°РµС‚ РѕР±С‹С‡РЅСѓСЋ СЃС‚Р°С‚РёСЃС‚РёРєСѓ) ---
                .then(Commands.literal("resetvictoryscreen")
                        .executes(ctx -> resetVictoryScreen(ctx.getSource())))

                .then(Commands.literal("gamemode")
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("aas", "invasion"), builder))
                                .executes(ctx -> setGameMode(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "mode")
                                ))
                        )
                )
                .then(Commands.literal("invasiondefend")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("blue", "red"), builder))
                                .executes(ctx -> setInvasionDefender(ctx.getSource(), StringArgumentType.getString(ctx, "team")))
                        )
                )
                .then(Commands.literal("deathtimer")
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0))
                                .executes(ctx -> setRespawnTime(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds")))))

                .then(Commands.literal("deathtickets")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                .executes(ctx -> setDeathTickets(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "amount")))))
                // === Р›РРњРРў РҐРђР‘РћР’ ===
                .then(Commands.literal("maxhub")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("blue", "red"), builder))
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                        .executes(ctx -> setMaxHubs(ctx.getSource(), StringArgumentType.getString(ctx, "team"), IntegerArgumentType.getInteger(ctx, "amount")))
                                )
                        )
                )
                // === PVP TOGGLE ===
                .then(Commands.literal("pvp")
                        .then(Commands.literal("on")
                                .executes(ctx -> setPvPState(ctx.getSource(), true))
                        )
                        .then(Commands.literal("off")
                                .executes(ctx -> setPvPState(ctx.getSource(), false))
                        )
                )
                // === Р’РЎРўРђР’РРўР¬ Р’ РњР•РўРћР” register ===
                .then(Commands.literal("clearsquad")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("blue", "red"), builder))
                                .executes(ctx -> clearSquads(ctx.getSource(), StringArgumentType.getString(ctx, "team")))
                        )
                )
                // --- РўРРљР•РўР« ---
                .then(Commands.literal("teamtickets")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("blue", "red"), builder))
                                .then(Commands.argument("amount", IntegerArgumentType.integer())
                                        .executes(ctx -> setTickets(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "team"),
                                                IntegerArgumentType.getInteger(ctx, "amount")
                                        ))
                                )
                        )
                )
                .then(Commands.literal("mainzone")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("blue", "red"), builder))
                                .then(Commands.argument("shape", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("cube", "cylinder"), builder))
                                        .then(Commands.argument("pos1", BlockPosArgument.blockPos())
                                                .then(Commands.argument("pos2", BlockPosArgument.blockPos())
                                                        .executes(ctx -> addMainZone(ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "team"),
                                                                StringArgumentType.getString(ctx, "shape"),
                                                                // РР—РњР•РќР•РќРћ РўРЈРў: getSpawnablePos РІРјРµСЃС‚Рѕ getLoadedBlockPos
                                                                BlockPosArgument.getSpawnablePos(ctx, "pos1"),
                                                                BlockPosArgument.getSpawnablePos(ctx, "pos2")
                                                        ))
                                                )
                                        )
                                )
                        )
                )
                .then(Commands.literal("addcenterlobbyzone")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> addCenterLobbyZone(ctx.getSource(), BlockPosArgument.getSpawnablePos(ctx, "pos")))
                        )
                )
                .then(Commands.literal("addlobbyzone")
                        .then(Commands.argument("shape", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("cube", "cylinder"), builder))
                                .then(Commands.argument("pos1", BlockPosArgument.blockPos())
                                        .then(Commands.argument("pos2", BlockPosArgument.blockPos())
                                                .executes(ctx -> addLobbyZone(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "shape"),
                                                        BlockPosArgument.getSpawnablePos(ctx, "pos1"),
                                                        BlockPosArgument.getSpawnablePos(ctx, "pos2")
                                                ))
                                        )
                                )
                        )
                )
                .then(Commands.literal("removelobbyzone")
                        .executes(ctx -> removeLobbyZone(ctx.getSource()))
                )
                .then(Commands.literal("removemainzone")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("blue", "red"), builder))
                                .executes(ctx -> removeMainZone(ctx.getSource(), StringArgumentType.getString(ctx, "team")))
                        )
                )
                .then(Commands.literal("removemainzone")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("blue", "red"), builder))
                                .executes(ctx -> removeMainZone(ctx.getSource(), StringArgumentType.getString(ctx, "team")))
                        )
                )
                .then(Commands.literal("teamjoin")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("blue", "red"), builder))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> joinTeam(ctx.getSource(), StringArgumentType.getString(ctx, "team"), EntityArgument.getPlayer(ctx, "player"))))))

                .then(Commands.literal("editpoint")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> suggestLocalPoints(ctx, builder)) // РђРІС‚РѕРїРѕРґСЃС‚Р°РЅРѕРІРєР°
                                .executes(ctx -> openEditPointGui(ctx.getSource(), StringArgumentType.getString(ctx, "name")))
                        )
                )

                .then(Commands.literal("addpoint")
                        .then(Commands.argument("shape", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("cube", "cylinder"), builder))
                                .then(Commands.argument("pos1", BlockPosArgument.blockPos())
                                        .then(Commands.argument("pos2", BlockPosArgument.blockPos())
                                                .then(Commands.argument("name", StringArgumentType.string())
                                                        .then(Commands.argument("bluePriority", IntegerArgumentType.integer(0, 999))
                                                                .then(Commands.argument("redPriority", IntegerArgumentType.integer(0, 999))
                                                                        .then(Commands.argument("timeMin", IntegerArgumentType.integer(1))
                                                                                .then(Commands.argument("penalty", IntegerArgumentType.integer(0))
                                                                                        .then(Commands.argument("captureDeduct", IntegerArgumentType.integer(0))
                                                                                                .then(Commands.argument("lockMinutes", IntegerArgumentType.integer(0))
                                                                                                        .then(Commands.argument("gainNeut", IntegerArgumentType.integer(0))
                                                                                                                .then(Commands.argument("gainCap", IntegerArgumentType.integer(0))
                                                                                                                        .executes(ctx -> addPoint(ctx.getSource(),
                                                                                                                                StringArgumentType.getString(ctx, "shape"),
                                                                                                                                BlockPosArgument.getSpawnablePos(ctx, "pos1"),
                                                                                                                                BlockPosArgument.getSpawnablePos(ctx, "pos2"),
                                                                                                                                StringArgumentType.getString(ctx, "name"),
                                                                                                                                IntegerArgumentType.getInteger(ctx, "bluePriority"),
                                                                                                                                IntegerArgumentType.getInteger(ctx, "redPriority"),
                                                                                                                                IntegerArgumentType.getInteger(ctx, "timeMin"),
                                                                                                                                IntegerArgumentType.getInteger(ctx, "penalty"),
                                                                                                                                IntegerArgumentType.getInteger(ctx, "captureDeduct"),
                                                                                                                                IntegerArgumentType.getInteger(ctx, "lockMinutes"),
                                                                                                                                IntegerArgumentType.getInteger(ctx, "gainNeut"),
                                                                                                                                IntegerArgumentType.getInteger(ctx, "gainCap")
                                                                                                                        ))
                                                                                                                )
                                                                                                        )
                                                                                                )
                                                                                        )
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
                .then(Commands.literal("warn")
                        .then(Commands.argument("target", EntityArgument.player()) // РђРІС‚Рѕ-РїРѕРґСЃРєР°Р·РєР° РѕРЅР»Р°Р№РЅР°
                                .then(Commands.argument("message", StringArgumentType.greedyString()) // Р§РёС‚Р°РµС‚ РІРµСЃСЊ РѕСЃС‚Р°Р»СЊРЅРѕР№ С‚РµРєСЃС‚
                                        .executes(ctx -> issueWarning(
                                                ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "target"),
                                                StringArgumentType.getString(ctx, "message")
                                        ))
                                )
                        )
                )
                // === РЈР”РђР›Р•РќРР• РўРћР§РљР (РўРѕР»СЊРєРѕ С‚РµРєСѓС‰РёР№ РјРёСЂ) ===
                .then(Commands.literal("removepoint")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> suggestLocalPoints(ctx, builder)) // Р›РѕРєР°Р»СЊРЅС‹Рµ РїРѕРґСЃРєР°Р·РєРё
                                .executes(ctx -> removePoint(ctx.getSource(), StringArgumentType.getString(ctx, "name")))
                        )
                )
                // === РЎРРЎРўР•РњРђ РћР§РљРћР’ ===
                .then(Commands.literal("stats")
                        // РџСЂРѕСЃРјРѕС‚СЂ СЃРІРѕРµР№ СЃС‚Р°С‚С‹ (СѓР¶Рµ Р±С‹Р»Рѕ)
                        .executes(ctx -> showStats(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        // РџСЂРѕСЃРјРѕС‚СЂ С‚РѕРїР°
                        .then(Commands.literal("top")
                                .then(Commands.literal("all")
                                        .executes(ctx -> showTopStats(ctx.getSource(), "ALL")))
                                .then(Commands.literal("team")
                                        .then(Commands.argument("teamName", StringArgumentType.word())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("blue", "red"), builder))
                                                .executes(ctx -> showTopStats(ctx.getSource(), StringArgumentType.getString(ctx, "teamName").toUpperCase()))
                                        )
                                )
                        )
                        // РџСЂРѕСЃРјРѕС‚СЂ СЃС‚Р°С‚С‹ РєРѕРЅРєСЂРµС‚РЅРѕРіРѕ РёРіСЂРѕРєР° (СѓР¶Рµ Р±С‹Р»Рѕ)
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> showStats(ctx.getSource(), EntityArgument.getPlayer(ctx, "target")))
                        )
                )
                // === РЎР‘Р РћРЎ РўРћР§РљР (РўРѕР»СЊРєРѕ С‚РµРєСѓС‰РёР№ РјРёСЂ) ===
                .then(Commands.literal("pointclear")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> suggestLocalPoints(ctx, builder)) // Р›РѕРєР°Р»СЊРЅС‹Рµ РїРѕРґСЃРєР°Р·РєРё
                                .executes(ctx -> clearSpecificPoint(ctx.getSource(), StringArgumentType.getString(ctx, "name")))
                        )
                )

                // === РџР РРќРЈР”РРўР•Р›Р¬РќР«Р™ Р—РђРҐР’РђРў РўРћР§РљР ===
                .then(Commands.literal("pointcapture")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("blue", "red"), builder))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .suggests((ctx, builder) -> suggestLocalPoints(ctx, builder))
                                        .executes(ctx -> forceCapturePoint(ctx.getSource(), StringArgumentType.getString(ctx, "team"), StringArgumentType.getString(ctx, "name")))
                                )
                        )
                )

                // --- РЎРџРђР’РќР« ---
                .then(Commands.literal("teamspawn")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("blue", "red", "none"), builder))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        // РР—РњР•РќР•РќРћ РўРЈРў: getSpawnablePos
                                        .executes(ctx -> setTeamSpawn(ctx.getSource(), StringArgumentType.getString(ctx, "team"), BlockPosArgument.getSpawnablePos(ctx, "pos")))
                                )
                        )
                )
                .then(Commands.literal("map")
                        .then(Commands.literal("setimage")
                                .then(Commands.argument("imagename", StringArgumentType.word())
                                        // РџРѕРґСЃРєР°Р·РєРё РѕС‚ map1 РґРѕ map12
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                List.of("map1", "map2", "map3", "map4", "map5", "map6", "map7", "map8", "map9", "map10", "map11", "map12"), builder))
                                        .executes(ctx -> {
                                            String imgName = StringArgumentType.getString(ctx, "imagename");
                                            ServerLevel level = ctx.getSource().getLevel();
                                            AASWorldData data = AASWorldData.get(level);

                                            data.currentMapImage = imgName;
                                            data.setDirty();
                                            PacketHandler.sendToAllClients(level, data);

                                            ctx.getSource().sendSuccess(() -> Component.literal("Map image set to: " + imgName + ".png"), true);
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("setcenter")
                                // Р”РѕР±Р°РІР»СЏРµРј Р°СЂРіСѓРјРµРЅС‚С‹ РґР»СЏ X Рё Z
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(ctx -> {
                                                    // РџРѕР»СѓС‡Р°РµРј Р·РЅР°С‡РµРЅРёСЏ РёР· РєРѕРјР°РЅРґС‹
                                                    int x = IntegerArgumentType.getInteger(ctx, "x");
                                                    int z = IntegerArgumentType.getInteger(ctx, "z");

                                                    ServerLevel level = ctx.getSource().getLevel();
                                                    AASWorldData data = AASWorldData.get(level);

                                                    data.mapCenterX = x;
                                                    data.mapCenterZ = z;
                                                    data.setDirty();

                                                    // РЎРёРЅС…СЂРѕРЅРёР·РёСЂСѓРµРј РґР°РЅРЅС‹Рµ СЃРѕ РІСЃРµРјРё РёРіСЂРѕРєР°РјРё
                                                    PacketHandler.sendToAllClients(level, data);

                                                    ctx.getSource().sendSuccess(() -> Component.literal("Map center manually set to X: " + x + ", Z: " + z), true);
                                                    return 1;
                                                })
                                        )
                                )
                        )
                        .then(Commands.literal("setsize")
                                .then(Commands.argument("blocks", IntegerArgumentType.integer(128))
                                        .executes(ctx -> {
                                            int size = IntegerArgumentType.getInteger(ctx, "blocks");
                                            AASWorldData data = AASWorldData.get(ctx.getSource().getLevel());
                                            data.mapSizeBlocks = size;
                                            data.setDirty();
                                            PacketHandler.sendToAllClients(ctx.getSource().getLevel(), data);
                                            ctx.getSource().sendSuccess(() -> Component.literal("Map world size set to " + size + " blocks."), true);
                                            return 1;
                                        })))
                )

                // --- Р¤Р РђРљР¦РР ---
                .then(Commands.literal("fraction")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("blue", "red"), builder))
                                .then(Commands.argument("faction", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of(
                                                "ukraine", "russia", "usa", "nato", "bluefor", "redfor", "insurgency", "pmc", "germany", "militia", "clear"
                                        ), builder))
                                        .executes(ctx -> setFaction(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "team"),
                                                StringArgumentType.getString(ctx, "faction")
                                        ))
                                )
                        )
                )
                .then(Commands.literal("votestart")
                        .then(Commands.argument("active", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    ServerLevel level = ctx.getSource().getLevel();
                                    AASWorldData data = AASWorldData.get(level);
                                    boolean active = BoolArgumentType.getBool(ctx, "active");

                                    data.voteActive = active;

                                    if (active) {
                                        // РРЅРёС†РёР°Р»РёР·Р°С†РёСЏ РїСЂРё СЃС‚Р°СЂС‚Рµ РіРѕР»РѕСЃРѕРІР°РЅРёСЏ
                                        data.voteTimer = AASConfig.VOTE_AUTO_START_TIME.get() * 60;
                                        data.votes.clear();

                                        // РЎР±СЂР°СЃС‹РІР°РµРј С„Р»Р°РіРё, С‡С‚РѕР±С‹ СЃРѕРѕР±С‰РµРЅРёСЏ Рѕ РіРѕС‚РѕРІРЅРѕСЃС‚Рё РєРѕРјР°РЅРґ РјРѕРіР»Рё СЃСЂР°Р±РѕС‚Р°С‚СЊ СЃРЅРѕРІР°
                                        data.blueReady = false;
                                        data.redReady = false;
                                    }

                                    data.setDirty();
                                    PacketHandler.sendToAllClients(level, data);

                                    String status = active ? "started" : "stopped";
                                    ctx.getSource().sendSuccess(() -> Component.literal("Voting process " + status), true);

                                    return 1;
                                })))
        );
    }

    // === Р’РЎРџРћРњРћР“РђРўР•Р›Р¬РќР«Р™ РњР•РўРћР”: РџРћР”РЎРљРђР—РљР РўРћР›Р¬РљРћ Р”Р›РЇ РўР•РљРЈР©Р•Р“Рћ РњРР Рђ ===
    private static CompletableFuture<Suggestions> suggestLocalPoints(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        // Р‘РµСЂРµРј СѓСЂРѕРІРµРЅСЊ, РІ РєРѕС‚РѕСЂРѕРј РЅР°С…РѕРґРёС‚СЃСЏ РёРіСЂРѕРє/РєРѕРјР°РЅРґРЅС‹Р№ Р±Р»РѕРє
        ServerLevel level = ctx.getSource().getLevel();
        AASWorldData data = AASWorldData.get(level);

        List<String> pointNames = new ArrayList<>();
        for (AASWorldData.CapturePoint p : data.capturePoints) {
            pointNames.add(p.name);
        }
        return SharedSuggestionProvider.suggest(pointNames, builder);
    }

    // === Р›РћР“РРљРђ РљРћРњРђРќР” ===

    private static int resetVictoryScreen(CommandSourceStack source) {
        PacketHandler.INSTANCE.send(net.minecraftforge.network.PacketDistributor.ALL.noArg(),
                new com.example.aas.network.PacketResetVictoryScreen());
        source.sendSuccess(() -> Component.translatable("aas.command.resetvictoryscreen").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int prepareStart(CommandSourceStack source) {
        switch (com.example.aas.deck.DeckManager.start(source.getLevel())) {
            case OK -> {
                source.sendSuccess(() -> Component.translatable("aas.command.prepare.started").withStyle(ChatFormatting.GREEN), true);
                return 1;
            }
            case GAME_RUNNING -> source.sendFailure(Component.translatable("aas.command.prepare.game_running"));
            case ALREADY_PREPARING -> source.sendFailure(Component.translatable("aas.command.prepare.already"));
        }
        return 0;
    }

    private static int prepareCancel(CommandSourceStack source) {
        if (com.example.aas.deck.DeckManager.cancel(source.getLevel()) == com.example.aas.deck.DeckManager.CancelResult.OK) {
            source.sendSuccess(() -> Component.translatable("aas.command.prepare.cancelled").withStyle(ChatFormatting.YELLOW), true);
            return 1;
        }
        source.sendFailure(Component.translatable("aas.command.prepare.nothing"));
        return 0;
    }

    private static int setGameStart(CommandSourceStack source, boolean active) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);

        if (active) {
            data.playedBlueSiren = false;
            data.playedRedSiren = false;
            GameLogicEvents.startGameCountdown(level);
            source.sendSuccess(() -> Component.literal("Countdown started in this world!").withStyle(ChatFormatting.GREEN), true);
            for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
                // TP, SP, РєРёР»Р»С‹, СЃРјРµСЂС‚Рё Рё СЃС‡С‘С‚С‡РёРєРё СѓРЅРёС‡С‚РѕР¶РµРЅРЅРѕР№ С‚РµС…РЅРёРєРё (РѕР±С‰РёР№ Рё РїРѕ РєР°С‚РµРіРѕСЂРёСЏРј)
                com.example.aas.events.StatsHandler.resetStats(p);
            }
            com.example.aas.events.OfflineStats.resetAll();
        } else {
            data.isGameStarted = false;
            com.example.aas.uav.UavManager.onMatchEnd(level, data);
            com.example.aas.deck.DeckManager.onMatchEnd(level, data);
            com.example.aas.art.ArtManager.onMatchEnd(level, data);
            GameLogicEvents.cancelCountdown(level);
            data.setDirty();
            syncDataToAll(level, data);
            source.sendSuccess(() -> Component.literal("Game Stopped in this world!").withStyle(ChatFormatting.RED), true);
        }
        return 1;
    }

    private static int setRespawnTime(CommandSourceStack source, int seconds) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);
        data.respawnTimer = seconds;
        data.setDirty();
        syncDataToAll(level, data);
        source.sendSuccess(() -> Component.literal("Respawn timer set to " + seconds + "s for current world").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setFaction(CommandSourceStack source, String team, String faction) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);
        String cleanFaction = faction.toLowerCase();
        String valueToSave = cleanFaction.equals("clear") ? "none" : cleanFaction;
        if (team.equalsIgnoreCase("blue")) data.blueFaction = valueToSave;
        else if (team.equalsIgnoreCase("red")) data.redFaction = valueToSave;
        data.setDirty(); syncDataToAll(level, data); return 1;
    }

    private static int setTickets(CommandSourceStack source, String team, int amount) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);
        if (team.equalsIgnoreCase("blue")) {
            data.blueTickets = amount;
            if (amount > 50) data.playedBlueSiren = false; // РЎР±СЂР°СЃС‹РІР°РµРј С„Р»Р°Рі, С‡С‚РѕР±С‹ СЃРёСЂРµРЅР° РјРѕРіР»Р° РїСЂРѕРїРµС‚СЊ СЃРЅРѕРІР°
        } else if (team.equalsIgnoreCase("red")) {
            data.redTickets = amount;
            if (amount > 50) data.playedRedSiren = false; // РЎР±СЂР°СЃС‹РІР°РµРј С„Р»Р°Рі
        }
        data.setDirty(); syncDataToAll(level, data);
        source.sendSuccess(() -> Component.literal(team.toUpperCase() + " tickets set to " + amount).withStyle(ChatFormatting.GOLD), true); return 1;
    }

    private static int setDeathTickets(CommandSourceStack source, int amount) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);
        data.deathTicketCost = amount; data.setDirty(); syncDataToAll(level, data);
        source.sendSuccess(() -> Component.literal("Death cost set to " + amount).withStyle(ChatFormatting.GOLD), true); return 1;
    }

    private static int joinTeam(CommandSourceStack source, String teamName, ServerPlayer player) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);
        PacketSquadAction.leaveCurrentSquad(player, data);
        data.setDirty();
        syncDataToAll(level, data);
        PacketHandler.INSTANCE.send(net.minecraftforge.network.PacketDistributor.ALL.noArg(), new com.example.aas.network.PacketSyncSquads(data.squads));
        Scoreboard scoreboard = source.getServer().getScoreboard();
        String internalTeamName = teamName.equalsIgnoreCase("blue") ? "Blue" : "Red";
        ChatFormatting color = teamName.equalsIgnoreCase("blue") ? ChatFormatting.BLUE : ChatFormatting.RED;
        PlayerTeam team = scoreboard.getPlayerTeam(internalTeamName);
        if (team == null) team = scoreboard.addPlayerTeam(internalTeamName);
        team.setColor(color);
        scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
        source.sendSuccess(() -> Component.literal("Player joined " + internalTeamName).withStyle(color), true);
        return 1;
    }

    private static int addPoint(CommandSourceStack source, String shape, BlockPos pos1, BlockPos pos2, String name, int bp, int rp, int time, int penalty, int captureDeduct, int lockMinutes, int gainNeut, int gainCap) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);

        AABB area;
        if (shape.equalsIgnoreCase("cylinder")) {
            double radius = Math.sqrt(pos1.distSqr(new BlockPos(pos2.getX(), pos1.getY(), pos2.getZ())));
            double minY = Math.min(pos1.getY(), pos2.getY());
            double maxY = Math.max(pos1.getY(), pos2.getY()) + 1;
            area = new AABB(pos1.getX() - radius, minY, pos1.getZ() - radius, pos1.getX() + radius, maxY, pos1.getZ() + radius);
        } else {
            double minX = Math.min(pos1.getX(), pos2.getX());
            double minY = Math.min(pos1.getY(), pos2.getY());
            double minZ = Math.min(pos1.getZ(), pos2.getZ());
            double maxX = Math.max(pos1.getX(), pos2.getX()) + 1;
            double maxY = Math.max(pos1.getY(), pos2.getY()) + 1;
            double maxZ = Math.max(pos1.getZ(), pos2.getZ()) + 1;
            area = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        }

        String upperShape = shape.toUpperCase();

        // РќРћР’РћР•: РµСЃР»Рё С‚РѕС‡РєР° СЃ С‚Р°РєРёРј РёРјРµРЅРµРј СѓР¶Рµ РµСЃС‚СЊ Рё С…Р°СЂР°РєС‚РµСЂРёСЃС‚РёРєРё СЃРѕРІРїР°РґР°СЋС‚ вЂ”
        // РЅРµ СЃРѕР·РґР°С‘Рј РЅРѕРІСѓСЋ С‚РѕС‡РєСѓ, Р° РґРѕР±Р°РІР»СЏРµРј СЌС‚Сѓ Р·РѕРЅСѓ РєР°Рє РµС‰С‘ РѕРґРЅСѓ Р·РѕРЅСѓ СЃСѓС‰РµСЃС‚РІСѓСЋС‰РµР№ С‚РѕС‡РєРё.
        // РўР°Рє РјРѕР¶РЅРѕ РґРµР»Р°С‚СЊ СЃРєРѕР»СЊРєРѕ СѓРіРѕРґРЅРѕ СЂР°Р· РїРѕРґСЂСЏРґ.
        for (AASWorldData.CapturePoint existing : data.capturePoints) {
            if (existing.name.equalsIgnoreCase(name)) {
                if (existing.sameCharacteristics(bp, rp, time, penalty, captureDeduct, upperShape, lockMinutes, gainNeut, gainCap)) {
                    existing.addLinkedArea(area);
                    data.setDirty();
                    int totalZones = existing.getAllAreas().size();
                    source.sendSuccess(() -> Component.literal("Zone added to point '" + name + "'! It now has " + totalZones + " linked zone(s).").withStyle(ChatFormatting.GREEN), true);
                    PacketHandler.sendToAllClients(level, data);
                    return 1;
                } else {
                    source.sendFailure(Component.literal("Point '" + name + "' already exists with different settings. Use identical settings to link a new zone to it, or choose another name."));
                    return 0;
                }
            }
        }

        data.capturePoints.add(new AASWorldData.CapturePoint(name, area, bp, rp, time, penalty, captureDeduct, upperShape, lockMinutes, gainNeut, gainCap));
        data.setDirty();
        source.sendSuccess(() -> Component.literal("Point '" + name + "' (" + shape + ") added! Lock: " + lockMinutes + " min."), true);
        PacketHandler.sendToAllClients(level, data);
        return 1;
    }

    // === РЈР”РђР›Р•РќРР• РўРћР§РљР (РС‰РµС‚ РўРћР›Р¬РљРћ РІ С‚РµРєСѓС‰РµРј РјРёСЂРµ) ===
    private static int removePoint(CommandSourceStack source, String name) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);

        boolean removed = data.capturePoints.removeIf(p -> p.name.equals(name));

        if (removed) {
            data.setDirty();
            syncDataToAll(level, data);
            source.sendSuccess(() -> Component.literal("Point '" + name + "' removed from " + level.dimension().location()).withStyle(ChatFormatting.RED), true);
            return 1;
        }

        source.sendFailure(Component.literal("Point '" + name + "' not found in THIS world!"));
        return 0;
    }

    // === РЎР‘Р РћРЎ РљРћРќРљР Р•РўРќРћР™ РўРћР§РљР (РС‰РµС‚ РўРћР›Р¬РљРћ РІ С‚РµРєСѓС‰РµРј РјРёСЂРµ) ===
    private static int clearSpecificPoint(CommandSourceStack source, String name) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);

        for (AASWorldData.CapturePoint point : data.capturePoints) {
            if (point.name.equals(name)) {
                // РЎР±СЂРѕСЃ РїР°СЂР°РјРµС‚СЂРѕРІ
                point.owner = "NEUTRAL";
                point.progress = 0.0f;
                point.capturingTeam = "NONE";

                data.setDirty();
                syncDataToAll(level, data);

                source.sendSuccess(() -> Component.literal("Point '" + name + "' reset to NEUTRAL in this world!").withStyle(ChatFormatting.YELLOW), true);
                return 1;
            }
        }

        source.sendFailure(Component.literal("Point '" + name + "' not found in THIS world!"));
        return 0;
    }

    // === РџР РРќРЈР”РРўР•Р›Р¬РќР«Р™ Р—РђРҐР’РђРў РљРћРќРљР Р•РўРќРћР™ РўРћР§РљР ===
    private static int forceCapturePoint(CommandSourceStack source, String teamInput, String name) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);
        String targetTeam = teamInput.toUpperCase();

        if (!targetTeam.equals("BLUE") && !targetTeam.equals("RED")) {
            source.sendFailure(Component.literal("Invalid team! Please use 'blue' or 'red'."));
            return 0;
        }

        for (AASWorldData.CapturePoint point : data.capturePoints) {
            if (point.name.equals(name)) {
                point.owner = targetTeam;
                point.progress = 1.0f; // 100% РїСЂРѕРіСЂРµСЃСЃР°
                point.capturingTeam = "NONE";

                data.setDirty();
                syncDataToAll(level, data); // РћР±РЅРѕРІР»СЏРµРј Сѓ РІСЃРµС… РёРіСЂРѕРєРѕРІ

                ChatFormatting color = targetTeam.equals("BLUE") ? ChatFormatting.BLUE : ChatFormatting.RED;

                // РЎРѕРѕР±С‰РµРЅРёРµ Р°РґРјРёРЅСѓ
                source.sendSuccess(() -> Component.literal("Point '" + name + "' forcefully captured by " + targetTeam + "!").withStyle(color), true);

                // РЈРІРµРґРѕРјР»РµРЅРёРµ РІ РіР»РѕР±Р°Р»СЊРЅС‹Р№ С‡Р°С‚ Рѕ Р·Р°С…РІР°С‚Рµ
                level.getServer().getPlayerList().broadcastSystemMessage(
                        Component.literal("[ADMIN] Point " + name + " forcefully captured by " + targetTeam).withStyle(color), false
                );

                return 1;
            }
        }

        source.sendFailure(Component.literal("Point '" + name + "' not found in THIS world!"));
        return 0;
    }

    private static int setTeamSpawn(CommandSourceStack source, String teamName, BlockPos pos) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);
        String currentDim = level.dimension().location().toString();
        if (teamName.equalsIgnoreCase("blue")) data.blueSpawns.put(currentDim, pos);
        else if (teamName.equalsIgnoreCase("red")) data.redSpawns.put(currentDim, pos);
        else if (teamName.equalsIgnoreCase("none")) data.neutralSpawns.put(currentDim, pos);
        data.setDirty(); syncDataToAll(level, data);
        source.sendSuccess(() -> Component.literal("Spawn set for this dimension.").withStyle(ChatFormatting.GREEN), true); return 1;
    }
    private static int issueWarning(CommandSourceStack source, ServerPlayer target, String message) {
        // 1. РќР°СЃС‚СЂРѕР№РєР° С‚Р°Р№РјРёРЅРіРѕРІ: 10 С‚РёРєРѕРІ (0.5СЃ) РїРѕСЏРІР»РµРЅРёРµ, 140 С‚РёРєРѕРІ (7СЃ) РЅР° СЌРєСЂР°РЅРµ, 20 С‚РёРєРѕРІ (1СЃ) Р·Р°С‚СѓС…Р°РЅРёРµ
        target.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, 140, 20));

        // 2. РЎРЅР°С‡Р°Р»Р° РѕС‚РїСЂР°РІР»СЏРµРј РїРѕРґР·Р°РіРѕР»РѕРІРѕРє (РЎР°Рј С‚РµРєСЃС‚ СЃРѕРѕР±С‰РµРЅРёСЏ - Р¶РµР»С‚С‹Рј С†РІРµС‚РѕРј)
        target.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                Component.literal(message).withStyle(ChatFormatting.YELLOW)
        ));

        // 3. РћС‚РїСЂР°РІР»СЏРµРј РіР»Р°РІРЅС‹Р№ Р·Р°РіРѕР»РѕРІРѕРє (РљСЂР°СЃРЅС‹Р№, Р¶РёСЂРЅС‹Р№)
        target.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                Component.literal("!WARNING!").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
        ));

        // 4. РџСЂРѕРёРіСЂС‹РІР°РµРј РЅРµРїСЂРёСЏС‚РЅС‹Р№ Р·РІСѓРє СѓРґР°СЂР° РЅР°РєРѕРІР°Р»СЊРЅРё (С‡С‚РѕР±С‹ РёРіСЂРѕРє С‚РѕС‡РЅРѕ РѕР±СЂР°С‚РёР» РІРЅРёРјР°РЅРёРµ)
        target.playNotifySound(net.minecraft.sounds.SoundEvents.ANVIL_LAND, net.minecraft.sounds.SoundSource.MASTER, 1.0f, 0.8f);

        // 5. Р”СѓР±Р»РёСЂСѓРµРј СЃРѕРѕР±С‰РµРЅРёРµ РІ С‡Р°С‚ РёРіСЂРѕРєР°, С‡С‚РѕР±С‹ РѕРЅРѕ РѕСЃС‚Р°Р»РѕСЃСЊ РІ РёСЃС‚РѕСЂРёРё (РµСЃР»Рё С‚РµРєСЃС‚ РґР»РёРЅРЅС‹Р№ Рё РѕРЅ РЅРµ СѓСЃРїРµР» РґРѕС‡РёС‚Р°С‚СЊ)
        target.sendSystemMessage(Component.literal("[ADMIN WARN] " + message).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));

        // 6. РџРѕРґС‚РІРµСЂР¶РґР°РµРј Р°РґРјРёРЅСѓ, С‡С‚Рѕ РїСЂРµРґСѓРїСЂРµР¶РґРµРЅРёРµ РѕС‚РїСЂР°РІР»РµРЅРѕ
        source.sendSuccess(() -> Component.literal("Successfully warned " + target.getScoreboardName() + "!")
                .withStyle(ChatFormatting.GREEN), true);

        return 1;
    }
    private static void syncDataToAll(ServerLevel level, AASWorldData data) {
        PacketHandler.sendToAllClients(level, data);
    }
    private static int addMainZone(CommandSourceStack source, String team, String shape, BlockPos pos1, BlockPos pos2) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);

        AABB area;
        if (shape.equalsIgnoreCase("cylinder")) {
            double radius = Math.sqrt(pos1.distSqr(new BlockPos(pos2.getX(), pos1.getY(), pos2.getZ())));
            double minY = Math.min(pos1.getY(), pos2.getY());
            double maxY = Math.max(pos1.getY(), pos2.getY()) + 1;
            area = new AABB(pos1.getX() - radius, minY, pos1.getZ() - radius, pos1.getX() + radius, maxY, pos1.getZ() + radius);
        } else {
            area = new AABB(Math.min(pos1.getX(), pos2.getX()), Math.min(pos1.getY(), pos2.getY()), Math.min(pos1.getZ(), pos2.getZ()),
                    Math.max(pos1.getX(), pos2.getX()) + 1, Math.max(pos1.getY(), pos2.getY()) + 1, Math.max(pos1.getZ(), pos2.getZ()) + 1);
        }

        // РЈРґР°Р»СЏРµРј СЃС‚Р°СЂСѓСЋ Р·РѕРЅСѓ СЌС‚РѕР№ РєРѕРјР°РЅРґС‹ (РµСЃР»Рё Р±С‹Р»Р°)
        data.mainZones.removeIf(z -> z.team.equalsIgnoreCase(team));

        data.mainZones.add(new AASWorldData.MainProtectionZone(team.toUpperCase(), shape.toUpperCase(), area));
        data.setDirty();

        source.sendSuccess(() -> Component.literal(team.toUpperCase() + " Main Protection Zone successfully added!").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int removeMainZone(CommandSourceStack source, String team) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);

        // РџС‹С‚Р°РµРјСЃСЏ СѓРґР°Р»РёС‚СЊ Р·РѕРЅСѓ РєРѕРјР°РЅРґС‹
        boolean removed = data.mainZones.removeIf(z -> z.team.equalsIgnoreCase(team));

        if (removed) {
            data.setDirty(); // РЎРѕС…СЂР°РЅСЏРµРј РјРёСЂ
            source.sendSuccess(() -> Component.literal(team.toUpperCase() + " Main Protection Zone removed!").withStyle(ChatFormatting.GREEN), true);
        } else {
            source.sendFailure(Component.literal("No protection zone found for team: " + team.toUpperCase()));
        }
        return 1;
    }
    // === РњР•РўРћР” Р”Р›РЇ РћР§РРЎРўРљР РћРўР РЇР”РћР’ (Clear Squads) ===
    private static int clearSquads(CommandSourceStack source, String teamName) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);
        String targetTeam = teamName.toUpperCase();

        // 1. РЎР±СЂР°СЃС‹РІР°РµРј С‚РµРіРё Сѓ РёРіСЂРѕРєРѕРІ
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            if (player.getPersistentData().contains("AAS_SquadID")) {
                boolean belongsToTeam = data.squads.stream()
                        .anyMatch(s -> s.id == player.getPersistentData().getInt("AAS_SquadID") && s.team.equalsIgnoreCase(targetTeam));

                if (belongsToTeam) {
                    player.getPersistentData().remove("AAS_SquadID");
                    player.getPersistentData().remove("AAS_IsSquadLeader");
                }
            }
        }

        // 2. РЈРґР°Р»СЏРµРј РѕС‚СЂСЏРґС‹
        data.squads.removeIf(squad -> squad.team.equalsIgnoreCase(targetTeam));

        // === 3. РЎР‘Р РђРЎР«Р’РђР•Рњ CMD (Р”РѕР±Р°РІСЊ СЌС‚Рё СЃС‚СЂРѕРєРё) ===
        if (targetTeam.equals("BLUE")) {
            data.blueCMDId = -1;
        } else if (targetTeam.equals("RED")) {
            data.redCMDId = -1;
        }
        // ============================================

        data.setDirty();
        PacketHandler.sendToAllClients(level, data);
        // РќРћР’РћР•: РѕР±РЅРѕРІР»СЏРµРј СЃРїРёСЃРѕРє РѕС‚СЂСЏРґРѕРІ Сѓ Р’РЎР•РҐ РёРіСЂРѕРєРѕРІ
        PacketHandler.INSTANCE.send(net.minecraftforge.network.PacketDistributor.ALL.noArg(),
                new com.example.aas.network.PacketSyncSquads(data.squads));

        source.sendSuccess(() -> Component.literal("Cleared squads and CMD for " + targetTeam).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
    private static int openEditPointGui(CommandSourceStack source, String name) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);

        for (AASWorldData.CapturePoint p : data.capturePoints) {
            if (p.name.equals(name)) {

                // Р’РѕСЃСЃС‚Р°РЅР°РІР»РёРІР°РµРј РѕСЂРёРіРёРЅР°Р»СЊРЅС‹Рµ РєРѕРѕСЂРґРёРЅР°С‚С‹ pos1 Рё pos2 РёР· РіРѕС‚РѕРІРѕРіРѕ С…РёС‚Р±РѕРєСЃР° AABB
                int p1x, p1y, p1z, p2x, p2y, p2z;
                if (p.shapeType.equals("CYLINDER")) {
                    p1x = (int) ((p.area.minX + p.area.maxX) / 2);
                    p1y = (int) p.area.minY;
                    p1z = (int) ((p.area.minZ + p.area.maxZ) / 2);
                    p2x = (int) p.area.maxX;
                    p2y = (int) p.area.maxY - 1;
                    p2z = p1z;
                } else {
                    p1x = (int) p.area.minX;
                    p1y = (int) p.area.minY;
                    p1z = (int) p.area.minZ;
                    p2x = (int) p.area.maxX - 1;
                    p2y = (int) p.area.maxY - 1;
                    p2z = (int) p.area.maxZ - 1;
                }

                PacketHandler.INSTANCE.send(
                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                        new com.example.aas.network.PacketOpenPointEditor(
                                p.name, p.shapeType,
                                p1x, p1y, p1z, p2x, p2y, p2z,
                                p.bluePriority, p.redPriority,
                                p.captureTimeMinutes, p.ticketPenalty,
                                p.captureDeduction, p.lockDurationMinutes,
                                p.ticketGainNeutralize, p.ticketGainCapture
                        )
                );
                return 1;
            }
        }
        source.sendFailure(Component.literal("Point '" + name + "' not found!"));
        return 0;
    }
    private static int setMaxHubs(CommandSourceStack source, String team, int amount) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);

        if (team.equalsIgnoreCase("blue")) {
            data.maxBlueHubs = amount;
        } else if (team.equalsIgnoreCase("red")) {
            data.maxRedHubs = amount;
        } else {
            source.sendFailure(Component.literal("Invalid team! Please use 'blue' or 'red'."));
            return 0;
        }

        data.setDirty();
        // Р’РђР–РќРћ: РЎРёРЅС…СЂРѕРЅРёР·РёСЂРѕРІР°С‚СЊ СЃ РєР»РёРµРЅС‚РѕРј РЅРµ РѕР±СЏР·Р°С‚РµР»СЊРЅРѕ, С‚Р°Рє РєР°Рє СѓСЃС‚Р°РЅРѕРІРєР° РҐР°Р±Р° РїСЂРѕРІРµСЂСЏРµС‚СЃСЏ С‚РѕР»СЊРєРѕ РЅР° СЃРµСЂРІРµСЂРµ
        source.sendSuccess(() -> Component.literal("Max FOBs for " + team.toUpperCase() + " set to " + amount).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
    private static int setPvPState(CommandSourceStack source, boolean state) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);

        data.pvpEnabled = state;
        data.setDirty(); // РЎРѕС…СЂР°РЅСЏРµРј РёР·РјРµРЅРµРЅРёСЏ РІ РјРёСЂ

        String status = state ? "ENABLED" : "DISABLED";
        ChatFormatting color = state ? ChatFormatting.GREEN : ChatFormatting.YELLOW;
        Component msg = Component.literal("[AAS] PvP between players is now " + status + " in this world.")
                .withStyle(color);

        // РћС‚РїСЂР°РІР»СЏРµРј СЃРѕРѕР±С‰РµРЅРёРµ РўРћР›Р¬РљРћ РјРѕРґРµСЂР°С‚РѕСЂР°Рј (СѓСЂРѕРІРµРЅСЊ РїСЂР°РІ 2+)
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            if (player.hasPermissions(2)) {
                player.sendSystemMessage(msg);
            }
        }

        // Р•СЃР»Рё РєРѕРјР°РЅРґСѓ РІРІРµР»Р° РєРѕРЅСЃРѕР»СЊ СЃРµСЂРІРµСЂР°, РЅСѓР¶РЅРѕ РѕС‚РїСЂР°РІРёС‚СЊ РѕС‚РІРµС‚ Рё РµР№
        if (!(source.getEntity() instanceof ServerPlayer)) {
            source.sendSuccess(() -> msg, true);
        }

        return 1;
    }
    // === РЎРњР•РќРђ РР“Р РћР’РћР“Рћ Р Р•Р–РРњРђ ===
    private static int setGameMode(CommandSourceStack source, String mode) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);

        String newMode = mode.toUpperCase();

        if (!newMode.equals("AAS") && !newMode.equals("INVASION")) {
            source.sendFailure(Component.literal("Invalid game mode! Use 'aas' or 'invasion'."));
            return 0;
        }

        data.gameMode = newMode;
        data.setDirty();
        syncDataToAll(level, data); // РЎРёРЅС…СЂРѕРЅРёР·РёСЂСѓРµРј РёР·РјРµРЅРµРЅРёСЏ СЃ РёРіСЂРѕРєР°РјРё

        source.sendSuccess(() -> Component.literal("Game Mode successfully set to: " + newMode).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
    private static int setInvasionDefender(CommandSourceStack source, String teamInput) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);
        String team = teamInput.toUpperCase();

        if (!team.equals("BLUE") && !team.equals("RED")) {
            source.sendFailure(Component.literal("Use 'blue' or 'red'!"));
            return 0;
        }

        data.invasionDefender = team;
        data.setDirty();

        // РЎРёРЅС…СЂРѕРЅРёР·РёСЂСѓРµРј СЃРѕ РІСЃРµРјРё
        PacketHandler.sendToAllClients(level, data);

        String attacker = team.equals("BLUE") ? "RED" : "BLUE";
        source.sendSuccess(() -> Component.literal("Invasion Setup: ")
                .append(Component.literal(team).withStyle(team.equals("BLUE") ? ChatFormatting.BLUE : ChatFormatting.RED))
                .append(" is DEFENDING, ")
                .append(Component.literal(attacker).withStyle(attacker.equals("BLUE") ? ChatFormatting.BLUE : ChatFormatting.RED))
                .append(" is ATTACKING."), true);

        return 1;
    }
    private static int showStats(CommandSourceStack source, ServerPlayer target) {
        int tp = target.getPersistentData().getInt("AAS_Stats_TeamPoints");
        int sp = target.getPersistentData().getInt("AAS_Stats_SquadPoints");
        int total = tp + sp;

        source.sendSuccess(() -> Component.literal("=== STATS: " + target.getScoreboardName() + " ===").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("Team Points (TP): ").withStyle(ChatFormatting.AQUA).append(Component.literal(String.valueOf(tp)).withStyle(ChatFormatting.WHITE)), false);
        source.sendSuccess(() -> Component.literal("Squad Points (SP): ").withStyle(ChatFormatting.GREEN).append(Component.literal(String.valueOf(sp)).withStyle(ChatFormatting.WHITE)), false);
        source.sendSuccess(() -> Component.literal("Total Score: ").withStyle(ChatFormatting.YELLOW).append(Component.literal(String.valueOf(total)).withStyle(ChatFormatting.WHITE)), false);

        return 1;
    }
    private static int showTopStats(CommandSourceStack source, String scope) {
        // РџРѕР»СѓС‡Р°РµРј РІСЃРµС… РёРіСЂРѕРєРѕРІ РЅР° СЃРµСЂРІРµСЂРµ
        List<ServerPlayer> players = source.getServer().getPlayerList().getPlayers();

        // Р’СЃРїРѕРјРѕРіР°С‚РµР»СЊРЅС‹Р№ РєР»Р°СЃСЃ РґР»СЏ СЃРѕСЂС‚РёСЂРѕРІРєРё
        class PlayerScore {
            final String name;
            final int totalPoints;
            final String team;

            PlayerScore(ServerPlayer p) {
                this.name = p.getScoreboardName();
                int tp = p.getPersistentData().getInt("AAS_Stats_TeamPoints");
                int sp = p.getPersistentData().getInt("AAS_Stats_SquadPoints");
                this.totalPoints = tp + sp;
                this.team = (p.getTeam() != null) ? p.getTeam().getName().toUpperCase() : "NEUTRAL";
            }
        }

        // РЎРѕР±РёСЂР°РµРј СЃРїРёСЃРѕРє РїРѕРґС…РѕРґСЏС‰РёС… РёРіСЂРѕРєРѕРІ
        List<PlayerScore> scores = new ArrayList<>();
        for (ServerPlayer p : players) {
            PlayerScore ps = new PlayerScore(p);

            // Р¤РёР»СЊС‚СЂ 1: РћС‡РєРѕРІ Р±РѕР»СЊС€Рµ 0
            if (ps.totalPoints <= 0) continue;

            // Р¤РёР»СЊС‚СЂ 2: РџРѕ РєРѕРјР°РЅРґРµ (РµСЃР»Рё РЅРµ ALL)
            if (!scope.equals("ALL")) {
                if (!ps.team.contains(scope)) continue; // РџСЂРѕРІРµСЂСЏРµРј РІС…РѕР¶РґРµРЅРёРµ СЃС‚СЂРѕРєРё (BLUE/RED)
            }

            scores.add(ps);
        }

        // РЎРѕСЂС‚РёСЂРѕРІРєР° РѕС‚ Р±РѕР»СЊС€РµРіРѕ Рє РјРµРЅСЊС€РµРјСѓ
        scores.sort((a, b) -> Integer.compare(b.totalPoints, a.totalPoints));

        // Р’С‹РІРѕРґ Р·Р°РіРѕР»РѕРІРєР°
        String header = scope.equals("ALL") ? "--- GLOBAL TOP PLAYERS ---" : "--- TOP PLAYERS: " + scope + " ---";
        source.sendSuccess(() -> Component.literal(header).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        if (scores.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No players with points found in this category.").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        // Р’С‹РІРѕРґ СЃРїРёСЃРєР°
        for (int i = 0; i < scores.size(); i++) {
            PlayerScore ps = scores.get(i);
            int rank = i + 1;

            ChatFormatting teamColor = ps.team.contains("BLUE") ? ChatFormatting.BLUE :
                    (ps.team.contains("RED") ? ChatFormatting.RED : ChatFormatting.GRAY);

            // Р¤РѕСЂРјР°С‚: "1. NickName - 150 pts"
            source.sendSuccess(() -> Component.literal(rank + ". ")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(ps.name).withStyle(teamColor))
                    .append(Component.literal(" - " + ps.totalPoints + " pts").withStyle(ChatFormatting.WHITE)), false);
        }

        return 1;
    }
    private static int addCenterLobbyZone(CommandSourceStack source, BlockPos pos) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);

        data.lobbyCenter = pos;
        data.setDirty();

        source.sendSuccess(() -> Component.literal("Lobby teleport point set to " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ())
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int addLobbyZone(CommandSourceStack source, String shape, BlockPos pos1, BlockPos pos2) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);

        AABB area;
        if (shape.equalsIgnoreCase("cylinder")) {
            double radius = Math.sqrt(pos1.distSqr(new BlockPos(pos2.getX(), pos1.getY(), pos2.getZ())));
            double minY = Math.min(pos1.getY(), pos2.getY());
            double maxY = Math.max(pos1.getY(), pos2.getY()) + 1;
            area = new AABB(pos1.getX() - radius, minY, pos1.getZ() - radius, pos1.getX() + radius, maxY, pos1.getZ() + radius);
        } else {
            area = new AABB(Math.min(pos1.getX(), pos2.getX()), Math.min(pos1.getY(), pos2.getY()), Math.min(pos1.getZ(), pos2.getZ()),
                    Math.max(pos1.getX(), pos2.getX()) + 1, Math.max(pos1.getY(), pos2.getY()) + 1, Math.max(pos1.getZ(), pos2.getZ()) + 1);
        }

        data.lobbyZones.add(new AASWorldData.LobbyZone(shape.toUpperCase(), area));
        data.setDirty();

        source.sendSuccess(() -> Component.literal("Lobby zone added! (" + data.lobbyZones.size() + " total)")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int removeLobbyZone(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);

        boolean removed = !data.lobbyZones.isEmpty();
        data.lobbyZones.clear();

        if (removed) {
            data.setDirty();
            source.sendSuccess(() -> Component.literal("Lobby zone(s) removed!").withStyle(ChatFormatting.GREEN), true);
        } else {
            source.sendFailure(Component.literal("No lobby zone found."));
        }
        return 1;
    }
}
