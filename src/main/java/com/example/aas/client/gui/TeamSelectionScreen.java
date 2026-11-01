package com.example.aas.client.gui;

import com.example.aas.client.ClientData;
import com.example.aas.config.AASConfig;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketTeamSelect;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class TeamSelectionScreen extends Screen {

    private static final ResourceLocation FLAG_UKRAINE = new ResourceLocation("aas", "textures/gui/flags/ukraine.png");
    private static final ResourceLocation FLAG_RUSSIA = new ResourceLocation("aas", "textures/gui/flags/russia.png");
    private static final ResourceLocation FLAG_USA = new ResourceLocation("aas", "textures/gui/flags/usa.png");
    private static final ResourceLocation FLAG_NATO = new ResourceLocation("aas", "textures/gui/flags/nato.png");
    private static final ResourceLocation FLAG_BLUEFOR = new ResourceLocation("aas", "textures/gui/flags/bluefor.png");
    private static final ResourceLocation FLAG_REDFOR = new ResourceLocation("aas", "textures/gui/flags/redfor.png");
    private static final ResourceLocation FLAG_INSURGENCY = new ResourceLocation("aas", "textures/gui/flags/insurgency.png");
    private static final ResourceLocation FLAG_PMC = new ResourceLocation("aas", "textures/gui/flags/pmc.png");
    private static final ResourceLocation FLAG_GERMANY = new ResourceLocation("aas", "textures/gui/flags/germany.png");
    private static final ResourceLocation FLAG_MILITIA = new ResourceLocation("aas", "textures/gui/flags/militia.png");

    /** Preparation mode: shows the countdown, cannot be closed by hand (DeckClient closes it when a team is assigned). */
    private final boolean prep;

    public TeamSelectionScreen() {
        this(false);
    }

    public TeamSelectionScreen(boolean prep) {
        super(Component.literal("Choose Team"));
        this.prep = prep;
    }

    public boolean isPrep() { return prep; }

    @Override
    public boolean shouldCloseOnEsc() { return !prep; }

    @Override
    public void onClose() {
        if (prep) return;
        super.onClose();
    }

    // Р’СЃРїРѕРјРѕРіР°С‚РµР»СЊРЅС‹Р№ РјРµС‚РѕРґ РґР»СЏ РїРѕРґСЃС‡РµС‚Р° Р±Р°Р»Р°РЅСЃР° (С‡С‚РѕР±С‹ РЅРµ РґСѓР±Р»РёСЂРѕРІР°С‚СЊ РєРѕРґ)
    private boolean[] checkTeamBalance() {
        int blueCount = 0;
        int redCount = 0;

        // 1. РЎС‡РёС‚Р°РµРј РёРіСЂРѕРєРѕРІ
        if (this.minecraft.getConnection() != null && this.minecraft.player != null) {
            for (net.minecraft.client.multiplayer.PlayerInfo info : this.minecraft.getConnection().getOnlinePlayers()) {
                // РСЃРєР»СЋС‡Р°РµРј СЃР°РјРѕРіРѕ СЃРµР±СЏ РёР· РїРѕРґСЃС‡РµС‚Р°
                if (info.getProfile().getId().equals(this.minecraft.player.getUUID())) continue;

                if (info.getTeam() != null) {
                    if (info.getTeam().getName().equalsIgnoreCase("Blue")) blueCount++;
                    else if (info.getTeam().getName().equalsIgnoreCase("Red")) redCount++;
                }
            }
        }

        // 2. РџСЂРѕРІРµСЂСЏРµРј СЂРµР¶РёРј РєСЂРµР°С‚РёРІР°
        boolean isCreative = this.minecraft.player != null && this.minecraft.player.isCreative();

        // 3. РСЃРїРѕР»СЊР·СѓРµРј РўРћР›Р¬РљРћ РґР°РЅРЅС‹Рµ, РїСЂРёСЃР»Р°РЅРЅС‹Рµ СЃРµСЂРІРµСЂРѕРј РІ ClientData
        // РњС‹ РќР• РёСЃРїРѕР»СЊР·СѓРµРј Р·РґРµСЃСЊ AASConfig РЅР°РїСЂСЏРјСѓСЋ!
        boolean autoBalanceActive = com.example.aas.client.ClientData.serverAutoBalance && !isCreative;

        // 4. Р›РѕРіРёРєР° Р±Р»РѕРєРёСЂРѕРІРєРё
        boolean blueFull = autoBalanceActive && (blueCount > redCount);
        boolean redFull = autoBalanceActive && (redCount > blueCount);

        return new boolean[]{blueFull, redFull};
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gui);
        gui.fill(0, 0, this.width, this.height, 0x80000000);

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int flagWidth = 64;
        int flagHeight = 36;
        int offset = 60;

        // === РџР РћР’Р•Р РљРђ Р‘РђР›РђРќРЎРђ РР“Р РћРљРћР’ ===
        boolean[] balance = checkTeamBalance();
        boolean blueFull = balance[0];
        boolean redFull = balance[1];
        // ================================

        // === Р Р•РќР”Р•Р  РЎРРќР•Р™ РљРћРњРђРќР”Р« ===
        int blueX = centerX - offset - flagWidth;
        int blueY = centerY - (flagHeight / 2);
        boolean isHoveringBlue = (mouseX >= blueX && mouseX <= blueX + flagWidth && mouseY >= blueY && mouseY <= blueY + flagHeight + 20);
        ResourceLocation blueFlag = getFlagTexture(ClientData.BLUE_FACTION);

        if (blueFlag != null) {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, blueFull ? 0.3f : 1.0f); // Р•СЃР»Рё Р·Р°Р±РёС‚Рѕ, С„Р»Р°Рі С‚СѓСЃРєР»С‹Р№
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            gui.blit(blueFlag, blueX, blueY, 0, 0, flagWidth, flagHeight, flagWidth, flagHeight);
        } else {
            gui.fill(blueX, blueY, blueX + flagWidth, blueY + flagHeight, blueFull ? 0x880000AA : 0xFF0000AA);
        }
        if (isHoveringBlue && !blueFull) {
            gui.renderOutline(blueX - 1, blueY - 1, flagWidth + 2, flagHeight + 2, 0xFFFFFFFF);
        }

        String blueTeamName = ClientData.customBlueName;
        if (ClientData.BLUE_FACTION != null && !ClientData.BLUE_FACTION.equals("none") && !ClientData.BLUE_FACTION.equals("bluefor")) {
            blueTeamName = ClientData.BLUE_FACTION.replace("_", " ").toUpperCase();
        }

        String blueJoinText = blueFull ? "FULL" : "JOIN";
        ChatFormatting blueJoinFormat = blueFull ? ChatFormatting.RED : ChatFormatting.GOLD;
        int blueJoinColor = blueFull ? 0xFFFF5555 : (isHoveringBlue ? 0xFFFFFFFF : 0xFFFFAA00);

        gui.drawCenteredString(this.font, Component.literal(blueJoinText).withStyle(blueJoinFormat), blueX + flagWidth / 2, blueY + flagHeight + 10, blueJoinColor);
        gui.drawCenteredString(this.font, Component.literal(blueTeamName).withStyle(ChatFormatting.BLUE), blueX + flagWidth / 2, blueY - 15, 0xFFFFFF);

        // === Р Р•РќР”Р•Р  РљР РђРЎРќРћР™ РљРћРњРђРќР”Р« ===
        int redX = centerX + offset;
        int redY = centerY - (flagHeight / 2);
        boolean isHoveringRed = (mouseX >= redX && mouseX <= redX + flagWidth && mouseY >= redY && mouseY <= redY + flagHeight + 20);
        ResourceLocation redFlag = getFlagTexture(ClientData.RED_FACTION);

        if (redFlag != null) {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, redFull ? 0.3f : 1.0f);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            gui.blit(redFlag, redX, redY, 0, 0, flagWidth, flagHeight, flagWidth, flagHeight);
        } else {
            gui.fill(redX, redY, redX + flagWidth, redY + flagHeight, redFull ? 0x88AA0000 : 0xFFAA0000);
        }
        if (isHoveringRed && !redFull) {
            gui.renderOutline(redX - 1, redY - 1, flagWidth + 2, flagHeight + 2, 0xFFFFFFFF);
        }

        String redTeamName = ClientData.customRedName;
        if (ClientData.RED_FACTION != null && !ClientData.RED_FACTION.equals("none") && !ClientData.RED_FACTION.equals("redfor")) {
            redTeamName = ClientData.RED_FACTION.replace("_", " ").toUpperCase();
        }

        String redJoinText = redFull ? "FULL" : "JOIN";
        ChatFormatting redJoinFormat = redFull ? ChatFormatting.RED : ChatFormatting.GOLD;
        int redJoinColor = redFull ? 0xFFFF5555 : (isHoveringRed ? 0xFFFFFFFF : 0xFFFFAA00);

        gui.drawCenteredString(this.font, Component.literal(redJoinText).withStyle(redJoinFormat), redX + flagWidth / 2, redY + flagHeight + 10, redJoinColor);
        gui.drawCenteredString(this.font, Component.literal(redTeamName).withStyle(ChatFormatting.RED), redX + flagWidth / 2, redY - 15, 0xFFFFFF);

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f); // РЎР±СЂРѕСЃ С†РІРµС‚Р°
        if (prep) {
            gui.drawCenteredString(this.font, Component.translatable("aas.prepare.team_timer", com.example.aas.client.DeckClient.secondsLeft()), centerX, 20, 0xFFFFD700);
        }
        super.render(gui, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int centerX = this.width / 2;
            int centerY = this.height / 2;
            int flagWidth = 64;
            int flagHeight = 36;
            int offset = 60;

            boolean[] balance = checkTeamBalance();
            boolean blueFull = balance[0];
            boolean redFull = balance[1];

            int blueX = centerX - offset - flagWidth;
            int blueY = centerY - (flagHeight / 2);
            if (mouseX >= blueX && mouseX <= blueX + flagWidth && mouseY >= blueY && mouseY <= blueY + flagHeight + 20) {
                if (!blueFull) {
                    PacketHandler.INSTANCE.sendToServer(new PacketTeamSelect("BLUE"));
                    this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(com.example.aas.sound.ModSounds.TEAM_SELECT.get(), 1.0F));
                    if (!prep) this.onClose();
                }
                return true;
            }

            int redX = centerX + offset;
            int redY = centerY - (flagHeight / 2);
            if (mouseX >= redX && mouseX <= redX + flagWidth && mouseY >= redY && mouseY <= redY + flagHeight + 20) {
                if (!redFull) {
                    PacketHandler.INSTANCE.sendToServer(new PacketTeamSelect("RED"));
                    this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(com.example.aas.sound.ModSounds.TEAM_SELECT.get(), 1.0F));
                    if (!prep) this.onClose();
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private ResourceLocation getFlagTexture(String faction) {
        if (faction == null || faction.equals("none")) return null;
        switch (faction.toLowerCase()) {
            case "ukraine": return FLAG_UKRAINE;
            case "russia": return FLAG_RUSSIA;
            case "usa": return FLAG_USA;
            case "bluefor": return new ResourceLocation("aas", "textures/gui/flags/bluefor.png");
            case "redfor": return new ResourceLocation("aas", "textures/gui/flags/redfor.png");
            case "nato": return FLAG_NATO;
            case "insurgency": return FLAG_INSURGENCY;
            case "pmc": return FLAG_PMC;
            case "germany": return FLAG_GERMANY;
            case "militia": return FLAG_MILITIA;
            default: return null;
        }
    }
}
