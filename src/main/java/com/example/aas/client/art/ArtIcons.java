package com.example.aas.client.art;


import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Placeholder icons of the artillery (drawn by the mod author). The real texture size is read once, so the
 * pictures can be any square size (16, 32, 64 ...) and are always drawn scaled to the requested size.
 */
public final class ArtIcons {
    private ArtIcons() {}

    public static final ResourceLocation REQUEST = new ResourceLocation("aas", "textures/gui/map_icons/art_request.png");
    public static final ResourceLocation STAR    = new ResourceLocation("aas", "textures/gui/map_icons/art_star.png");
    public static final ResourceLocation CONFIRM = new ResourceLocation("aas", "textures/gui/map_icons/art_confirm.png");
    public static final ResourceLocation CANCEL  = new ResourceLocation("aas", "textures/gui/map_icons/art_cancel.png");
    /** Strike marker (draw it pointing to the RIGHT, white/grey: it is tinted gold in code). */
    public static final ResourceLocation STRIKE  = new ResourceLocation("aas", "textures/gui/map_icons/art_strike.png");

    public static final int COLOR_GREEN = 0xFF3CB043;
    public static final int COLOR_RED   = 0xFFE03030;
    public static final int COLOR_GOLD  = 0xFFFFC21A;

    private static final Map<ResourceLocation, int[]> SIZES = new HashMap<>();

    private static int[] sizeOf(ResourceLocation tex) {
        return SIZES.computeIfAbsent(tex, k -> {
            try (InputStream in = Minecraft.getInstance().getResourceManager().getResourceOrThrow(k).open();
                 NativeImage img = NativeImage.read(in)) {
                return new int[]{Math.max(1, img.getWidth()), Math.max(1, img.getHeight())};
            } catch (Exception e) {
                return new int[]{16, 16};   // texture not drawn yet: the missing-texture square is shown instead
            }
        });
    }

    public static void draw(GuiGraphics gui, ResourceLocation tex, int x, int y, int w, int h, float alpha) {
        int[] s = sizeOf(tex);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        gui.blit(tex, x, y, w, h, 0f, 0f, s[0], s[1], s[0], s[1]);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** Same as draw(), multiplied by an ARGB tint (the texture itself is drawn white/grey). */
    public static void drawTinted(GuiGraphics gui, ResourceLocation tex, int x, int y, int w, int h, int argb, float alpha) {
        int[] s = sizeOf(tex);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(((argb >> 16) & 255) / 255f, ((argb >> 8) & 255) / 255f, (argb & 255) / 255f, alpha);
        gui.blit(tex, x, y, w, h, 0f, 0f, s[0], s[1], s[0], s[1]);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** Tinted icon centred on (cx, cy), rotated by angleRad around its centre (0 = the picture as drawn, pointing right). */
    public static void drawRotatedTinted(GuiGraphics gui, ResourceLocation tex, float cx, float cy, int size,
                                         float angleRad, int argb, float alpha) {
        gui.pose().pushPose();
        gui.pose().translate(cx, cy, 0f);
        gui.pose().mulPose(com.mojang.math.Axis.ZP.rotation(angleRad));
        drawTinted(gui, tex, -size / 2, -size / 2, size, size, argb, alpha);
        gui.pose().popPose();
    }
}