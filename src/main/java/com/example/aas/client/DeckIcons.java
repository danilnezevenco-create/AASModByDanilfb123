package com.example.aas.client;

import com.example.aas.deck.DeckRegistry;
import com.example.aas.deck.DeckType;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** Draws deck emblems, class icons and faction flags. A missing emblem texture is replaced by a filled team-coloured circle. */
public final class DeckIcons {
    private DeckIcons() {}

    private static final Map<ResourceLocation, Boolean> EXISTS = new HashMap<>();

    /** ResourceManager lookup, cached (texture packs are only re-read on F3+T, call clearCache() from a reload listener if needed). */
    public static boolean exists(ResourceLocation rl) {
        return EXISTS.computeIfAbsent(rl, k -> Minecraft.getInstance().getResourceManager().getResource(k).isPresent());
    }

    public static void clearCache() {
        EXISTS.clear();
    }

    private static void circle(GuiGraphics g, int x, int y, int size, float r, float gr, float b) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(r, gr, b, 1.0f);
        BadgeCircle.blit(g, x, y, size);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    /** A crisp filled square drawn by code (a scaled-up tiny texture would be blurry). */
    private static void square(GuiGraphics g, int x, int y, int size, int argb) {
        int dark = 0xFF000000 | (((argb >> 16 & 0xFF) * 6 / 10) << 16) | (((argb >> 8 & 0xFF) * 6 / 10) << 8) | ((argb & 0xFF) * 6 / 10);
        g.fill(x, y, x + size, y + size, dark);
        g.fill(x + 2, y + 2, x + size - 2, y + size - 2, argb);
    }

    public static void drawEmblem(GuiGraphics g, String faction, boolean blueTeam, DeckType deck, int x, int y, int size) {
        ResourceLocation tex = DeckRegistry.emblemTexture(faction, blueTeam, deck);
        if (!exists(tex)) {
            ResourceLocation alt = DeckRegistry.emblemTextureAlt(faction, blueTeam, deck);
            if (exists(alt)) tex = alt;
        }
        if (exists(tex)) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            g.blit(tex, x, y, 0, 0, size, size, size, size);
        } else if (blueTeam) {
            square(g, x, y, size, 0xFF2F5FD9);
        } else {
            square(g, x, y, size, 0xFFD92F2F);
        }
    }

    public static void drawClass(GuiGraphics g, DeckType deck, int x, int y, int size) {
        ResourceLocation tex = DeckRegistry.classIcon(deck);
        if (exists(tex)) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            g.blit(tex, x, y, 0, 0, size, size, size, size);
        } else {
            square(g, x, y, size, 0xFF8C8C8C);
        }
    }

    public static void drawFlag(GuiGraphics g, String faction, boolean blueTeam, int x, int y, int w, int h) {
        ResourceLocation tex = new ResourceLocation("aas", "textures/gui/flags/" + DeckRegistry.resolveFaction(faction, blueTeam) + ".png");
        if (exists(tex)) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            g.blit(tex, x, y, 0, 0, w, h, w, h);
        } else {
            g.fill(x, y, x + w, y + h, blueTeam ? 0xFF0000AA : 0xFFAA0000);
        }
    }
}
