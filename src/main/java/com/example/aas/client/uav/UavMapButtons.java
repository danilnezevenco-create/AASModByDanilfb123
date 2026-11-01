package com.example.aas.client.uav;

import com.example.aas.client.ClientData;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketUavView;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * The "eye" button that follows the flying UAV (CMD leader only). The launch button is no longer drawn here:
 * the UAV is launched from the "Support" tab of the artillery panel (client/art/ArtMapUi), which uses launchBlockReason().
 */
public class UavMapButtons {
    private static final ResourceLocation EYE_ICON = new ResourceLocation("aas", "textures/gui/eye_icon.png");
    private static final int EYE = 20;

    private int ex, ey;
    private boolean eyeVisible;
    private Component tooltip;

    /** null = launch is possible, otherwise the reason (also used as the tooltip). */
    public Component launchBlockReason(Minecraft mc) {
        if (!ClientData.isGameStarted) return Component.translatable("aas.uav.tooltip.not_started");
        if (!mc.player.isAlive()) return Component.translatable("aas.uav.tooltip.dead");
        if (UavClientState.isDowned(mc)) return Component.translatable("aas.uav.tooltip.downed");
        if (UavClientState.active) return Component.translatable("aas.uav.tooltip.active");
        long left = UavClientState.cooldownTicksLeft(mc);
        if (left > 0) return Component.translatable("aas.uav.tooltip.cooldown", (left + 19) / 20);
        return null;
    }

    public void render(GuiGraphics gui, int mouseX, int mouseY, int mapX, int mapY, int mapSize,
                       double panX, double panZ, double bpp, UavMapSelection sel) {
        Minecraft mc = Minecraft.getInstance();
        tooltip = null;
        eyeVisible = false;
        if (mc.player == null || !UavClientState.isCmdLeader(mc)) return;

        // --- eye button at the UAV position ---
        if (UavClientState.active && !UavViewClient.isViewing() && mc.level != null) {
            Vec3 p = UavClientState.position(mc.level.getGameTime() + mc.getPartialTick());
            double sx = mapX + mapSize / 2.0 + (p.x - panX) / bpp;
            double sy = mapY + mapSize / 2.0 + (p.z - panZ) / bpp;
            double by = sy + 8 + EYE / 2.0 + 1;   // button centre: right under the 16px UAV icon
            if (sx >= mapX + EYE / 2.0 && sx <= mapX + mapSize - EYE / 2.0 && by >= mapY + EYE / 2.0 && by <= mapY + mapSize - EYE / 2.0) {
                ex = (int) sx - EYE / 2;
                ey = (int) by - EYE / 2;
                eyeVisible = true;
                boolean downed = UavClientState.isDowned(mc);
                boolean eh = inside(mouseX, mouseY, ex, ey, EYE);
                gui.fill(ex, ey, ex + EYE, ey + EYE, 0xCC000000);
                gui.renderOutline(ex, ey, EYE, EYE, downed ? 0xFF4A4A4A : (eh ? 0xFFFFFFFF : 0xFFB8860B));
                RenderSystem.enableBlend();
                RenderSystem.setShaderColor(1f, 1f, 1f, downed ? 0.35f : 1f);
                gui.blit(EYE_ICON, ex + 2, ey + 2, 16, 16, 0, 0, 32, 32, 32, 32);
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                if (eh) tooltip = Component.translatable(downed ? "aas.uav.eye.downed" : "aas.uav.eye.tooltip");
            }
        }
    }

    public void renderTooltip(GuiGraphics gui, Font font, int mouseX, int mouseY) {
        if (tooltip != null) gui.renderTooltip(font, tooltip, mouseX, mouseY);
    }

    public boolean mouseClicked(double mx, double my, int button, UavMapSelection sel) {
        if (button != 0) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !UavClientState.isCmdLeader(mc)) return false;
        if (eyeVisible && inside(mx, my, ex, ey, EYE)) {
            if (!UavClientState.isDowned(mc)) PacketHandler.INSTANCE.sendToServer(new PacketUavView(true));
            return true;
        }
        return false;
    }

    private static boolean inside(double mx, double my, int x, int y, int size) {
        return mx >= x && mx <= x + size && my >= y && my <= y + size;
    }
}

