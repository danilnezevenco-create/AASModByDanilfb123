package com.example.aas.client.gui;

import com.example.aas.client.DeckClient;
import com.example.aas.client.DeckIcons;
import com.example.aas.deck.DeckRegistry;
import com.example.aas.deck.DeckType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** "Your deck  VS  enemy deck" shown for a few seconds before the countdown. Closed by DeckClient. */
public class DeckVersusScreen extends Screen {
    private static final int PW = 190, PH = 190, PTOP = 100;   // panel size, distance from centre to its top edge

    private final boolean blue;
    private long openMs = 0L;

    public DeckVersusScreen(boolean blue) {
        super(Component.translatable("aas.deck.versus.title"));
        this.blue = blue;
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public void onClose() { }

    @Override
    public boolean isPauseScreen() { return false; }

    /** One side in local coordinates (0,0 = centre of the composition). slideFrom = start offset of the short slide-in. */
    private void side(GuiGraphics gui, boolean teamBlue, DeckType d, int cx, float slideFrom, float appear, boolean mine) {
        String faction = DeckClient.factionOf(teamBlue);
        int x = cx + Math.round((1f - appear) * slideFrom);
        int top = -PTOP;

        DeckIcons.drawFlag(gui, faction, teamBlue, x - 18, top + 8, 36, 20);
        DeckIcons.drawEmblem(gui, faction, teamBlue, d, x - 48, top + 34, 96);
        DeckIcons.drawClass(gui, d, x - 10, top + 134, 20);
        Component name = Component.translatable(DeckRegistry.nameKey(faction, teamBlue, d));
        int line = 0;
        for (var seq : font.split(name, PW - 16)) {
            if (line >= 3) break;
            gui.drawCenteredString(font, seq, x, top + 158 + line * 10, 0xFFFFFF);
            line++;
        }
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();
        if (openMs == 0L) openMs = now;
        float ms = now - openMs;

        renderBackground(gui);
        gui.fill(0, 0, width, height, 0x80101010);

        DeckType mine = DeckClient.state.chosen(blue);
        DeckType enemy = DeckClient.state.chosen(!blue);
        if (mine == null || enemy == null) return;

        float s = Math.min(1f, (height - 70f) / (PH + 40f));
        if (s < 0.4f) s = 0.4f;
        int off = (int) Math.min(150f, (width / s) / 4f);
        float a = DeckAnim.easeOutCubic(ms / 300f);          // both panels, one short slide (no overshoot)

        gui.pose().pushPose();
        gui.pose().translate(width / 2f, height / 2f, 0);
        gui.pose().scale(s, s, 1f);
        side(gui, blue, mine, -off, -60f, a, true);
        side(gui, !blue, enemy, off, 60f, a, false);
        gui.pose().pushPose();
        gui.pose().scale(3f, 3f, 1f);
        gui.drawCenteredString(font, "VS", 0, -4, 0xFFD700);
        gui.pose().popPose();
        gui.pose().popPose();

        gui.drawCenteredString(font, Component.translatable("aas.deck.versus.start_in", DeckClient.secondsLeft()), width / 2, height - 24, 0xAAAAAA);

        DeckAnim.veil(gui, 0, 0, width, height, DeckAnim.easeOutCubic(ms / 200f));   // quick fade-in of the whole screen
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }
}