// PATH: src/main/java/com/example/aas/client/BadgeCircle.java
// FULL REPLACEMENT of the file (v2: star 1.3x smaller + CMD colour option).
package com.example.aas.client;

import com.example.aas.client.art.ArtIcons;
import com.example.aas.world.AASWorldData;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Squad badge: a circle with the squad number (HUD, squad lists, voice rows ...).
 * The squad of the team's CMD is drawn as a STAR (art_star.png) with the number instead of the circle.
 *
 * Circle texture: assets/aas/textures/gui/squad_badge.png (falls back to map_icons/player_circle.png).
 */
public final class BadgeCircle {
    public static final int TEX_SIZE = 32;

    /**
     * Used by blitStar / drawStarRaw / draw(..., cmd = true).
     * false = the star is drawn as it is in art_star.png (its own colours).
     * true  = the star is multiplied by the given colour (use it only if art_star.png is white/grey).
     * blitStarCmd(...) always tints, whatever this constant says.
     */
    public static final boolean TINT_STAR = false;

    /** Colour of the CMD squad (same green as the CMD badge in the squad lists). */
    public static final int CMD_COLOR = 0xFF4CD964;

    /** The star is first made this many pixels bigger than the circle on every side ... */
    private static final int STAR_PAD = 2;

    /**
     * How many times SMALLER the star is (1.3 = 30% smaller). Used by blitStarCmd (squad panel, SquadSelection,
     * death screen), blitStarTinted / draw(..., cmd) (voice rows) and drawStarCmdCentered (map).
     * Statistics and victory screens are not shrunk (blitStar).
     */
    public static final float STAR_SHRINK = 1.3f;

    private static final ResourceLocation TEX =
            new ResourceLocation("aas", "textures/gui/squad_badge.png");
    private static final ResourceLocation OLD_TEX =
            new ResourceLocation("aas", "textures/gui/map_icons/player_circle.png");

    private static Boolean hasNewTex = null;

    private BadgeCircle() {}

    private static boolean hasNew() {
        if (hasNewTex == null) {
            hasNewTex = Minecraft.getInstance().getResourceManager().getResource(TEX).isPresent();
        }
        return hasNewTex;
    }

    /** true if this squad is the CMD squad of its team. */
    public static boolean isCmdSquad(AASWorldData.Squad s) {
        if (s == null || s.team == null) return false;
        int cmdId = s.team.toUpperCase().contains("BLUE") ? ClientData.blueCMDId : ClientData.redCMDId;
        return cmdId != -1 && s.id == cmdId;
    }

    /** Draws the circle (set the colour with RenderSystem.setShaderColor before the call). */
    public static void blit(GuiGraphics gui, int x, int y, int size) {
        if (hasNew()) {
            Minecraft.getInstance().getTextureManager().getTexture(TEX).setFilter(true, false);
            gui.blit(TEX, x, y, size, size, 0f, 0f, TEX_SIZE, TEX_SIZE, TEX_SIZE, TEX_SIZE);
        } else {
            gui.blit(OLD_TEX, x, y, 0, 0, size, size, size, size);
        }
    }

    /**
     * Draws the CMD star in place of a circle with the same (x, y, size), centred on the circle.
     * argb: alpha (and the tint, if TINT_STAR). Restores the shader colour itself.
     */
    public static void blitStar(GuiGraphics gui, int x, int y, int size, int argb) {
        drawStarFitted(gui, x, y, size, argb, ((argb >>> 24) & 0xFF) / 255f, TINT_STAR, 1.0f);
    }

    /** Same as blitStar, but the star is ALWAYS painted in CMD_COLOR. alpha: 0..1 (panel fade). */
    public static void blitStarCmd(GuiGraphics gui, int x, int y, int size, float alpha) {
        drawStarFitted(gui, x, y, size, CMD_COLOR, alpha, true, STAR_SHRINK);
    }

    /**
     * Star in CMD_COLOR with the size of the circle + padding (NOT shrunk). Used by the statistics and victory
     * screens. alpha: 0..1.
     */
    public static void blitStarCmdColor(GuiGraphics gui, int x, int y, int size, float alpha) {
        drawStarFitted(gui, x, y, size, CMD_COLOR, alpha, true, 1.0f);
    }

    /**
     * Star painted in the given colour (alpha is taken from argb), STAR_SHRINK times smaller.
     * Used by the voice rows: the colour there is the colour of the channel the player speaks to.
     */
    public static void blitStarTinted(GuiGraphics gui, int x, int y, int size, int argb) {
        drawStarFitted(gui, x, y, size, argb, ((argb >>> 24) & 0xFF) / 255f, true, STAR_SHRINK);
    }

    /**
     * Map: the CMD star centred on (cx, cy), painted in CMD_COLOR.
     * size = the old (unshrunk) size, e.g. 10; the star is drawn STAR_SHRINK times smaller.
     */
    public static void drawStarCmdCentered(GuiGraphics gui, int cx, int cy, int size) {
        int s = Math.max(4, Math.round(size / STAR_SHRINK));
        drawStar(gui, cx - s / 2, cy - s / 2, s, CMD_COLOR, 1.0f, true);
    }

    /** Draws the star exactly into the given square (no padding, no shrink). */
    public static void drawStarRaw(GuiGraphics gui, int x, int y, int size, int argb, float alpha) {
        drawStar(gui, x, y, size, argb, alpha, TINT_STAR);
    }

    private static void drawStarFitted(GuiGraphics gui, int x, int y, int size, int argb, float alpha, boolean tint, float shrink) {
        int full = size + STAR_PAD * 2;
        int s = Math.max(4, Math.round(full / shrink));
        int off = Math.round((size - s) / 2f);   // keep the star centred on the circle position
        drawStar(gui, x + off, y + off, s, argb, alpha, tint);
    }

    private static void drawStar(GuiGraphics gui, int x, int y, int size, int argb, float alpha, boolean tint) {
        if (tint) {
            ArtIcons.drawTinted(gui, ArtIcons.STAR, x, y, size, size, argb, alpha);
        } else {
            ArtIcons.draw(gui, ArtIcons.STAR, x, y, size, size, alpha);
        }
    }

    /** Circle + number. argb = circle colour (with alpha). */
    public static void draw(GuiGraphics gui, Font font, int x, int y, int size, int argb, String label) {
        draw(gui, font, x, y, size, argb, label, false);
    }

    /** Circle + number, or STAR + number when cmd == true. */
    public static void draw(GuiGraphics gui, Font font, int x, int y, int size, int argb, String label, boolean cmd) {
        if (cmd) {
            RenderSystem.enableBlend();
            blitStarTinted(gui, x, y, size, argb);   // colour of the row = colour of the voice channel
        } else {
            float a = ((argb >>> 24) & 0xFF) / 255f;
            float r = ((argb >> 16) & 0xFF) / 255f;
            float g = ((argb >> 8) & 0xFF) / 255f;
            float b = (argb & 0xFF) / 255f;
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(r, g, b, a);
            blit(gui, x, y, size);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
        if (label != null && !label.isEmpty()) {
            gui.drawCenteredString(font, label, x + size / 2, y + (size - 8) / 2, 0xFFFFFFFF);
        }
    }
}