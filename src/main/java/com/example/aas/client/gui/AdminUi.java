package com.example.aas.client.gui;

import com.example.aas.client.KitKeyUtil;
import com.example.aas.deck.DeckType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Small shared pieces of the kit / deck admin screens (ASCII only on purpose). */
public final class AdminUi {
    private AdminUi() {}

    public static final int C_BLUE = 0xFF5B9BFF;
    public static final int C_RED = 0xFFFF6B6B;
    public static final int C_GREEN = 0xFF55FF55;
    public static final int C_GOLD = 0xFFFFAA00;
    public static final int C_AQUA = 0xFF55FFFF;
    public static final int C_GRAY = 0xFFAAAAAA;
    public static final int C_DIM = 0xFF777777;
    public static final int C_WHITE = 0xFFFFFFFF;
    public static final int C_BORDER = 0xFF999999;

    public static int teamColor(String team) {
        return "RED".equalsIgnoreCase(team) ? C_RED : C_BLUE;
    }

    public static String otherTeam(String team) {
        return "RED".equalsIgnoreCase(team) ? "BLUE" : "RED";
    }

    /** 0 = BLUE, 1 = RED (index used by PacketKitAdminInfo). */
    public static int teamIndex(String team) {
        return "RED".equalsIgnoreCase(team) ? 1 : 0;
    }

    // ------------------------------------------------------------------ translation helpers (keys: aas.kadm.*)

    /** Translated text as String (String.format style %s arguments). */
    public static String t(String key, Object... args) {
        return I18n.get(key, args);
    }

    /** Translated text as Component. */
    public static Component c(String key, Object... args) {
        return Component.translatable(key, args);
    }

    /** Translated team name for "BLUE" / "RED". */
    public static String team(String team) {
        return t("aas.kadm.team." + team.toLowerCase(Locale.ROOT));
    }

    /** Team name as a label argument (resolved by {@link #label}). */
    public static String teamRef(String team) {
        return "@aas.kadm.team." + team.toLowerCase(Locale.ROOT);
    }

    /**
     * Undo-history label: "key" + TAB + args. It is stored on the server and shown translated by
     * the client. An argument that starts with '@' is a translation key.
     */
    public static String lbl(String key, String... args) {
        StringBuilder sb = new StringBuilder(key);
        for (String a : args) sb.append('\t').append(a);
        return sb.toString();
    }

    public static String label(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String[] parts = raw.split("\t", -1);
        if (!I18n.exists(parts[0])) return raw;
        Object[] args = new Object[parts.length - 1];
        for (int i = 1; i < parts.length; i++) {
            String a = parts[i];
            args[i - 1] = a.startsWith("@") ? I18n.get(a.substring(1)) : a;
        }
        return I18n.get(parts[0], args);
    }

    public static String deckName(DeckType d) {
        return Component.translatable(d.classLangKey()).getString();
    }

    /** Cuts the text with ".." so that it fits into maxW pixels. */
    public static String fit(Font font, String s, int maxW) {
        if (s == null) return "";
        if (font.width(s) <= maxW) return s;
        return font.plainSubstrByWidth(s, Math.max(0, maxW - font.width(".."))) + "..";
    }

    public static void toast(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(s), true);
    }

    /** Type ("KIT" / "TEAM" / "ALL") of the kit key in the system clipboard, null if there is none. */
    public static String clipboardKeyType() {
        try {
            return KitKeyUtil.decode(Minecraft.getInstance().keyboardHandler.getClipboard()).type;
        } catch (Exception e) {
            return null;
        }
    }

    /** Flat dark button used by all admin screens. */
    public static class Btn extends Button {
        public int accent = C_BORDER;
        public int textColor = 0xFFFFFFFF;
        public boolean selected = false;
        /** Optional second small line (used by the deck tabs for the "12/19" counter). */
        public String sub = null;
        public int subColor = C_GRAY;

        public Btn(int x, int y, int w, int h, Component msg, Button.OnPress onPress) {
            super(x, y, w, h, msg, onPress, DEFAULT_NARRATION);
        }

        public Btn accent(int c) {
            this.accent = c;
            return this;
        }

        public Btn color(int c) {
            this.textColor = c;
            return this;
        }

        public Btn tip(String s) {
            setTooltip(Tooltip.create(Component.literal(s)));
            return this;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            int bg;
            int border;
            if (!this.active) {
                bg = 0xAA181818;
                border = 0xFF3A3A3A;
            } else if (selected) {
                bg = 0xEE2B4A63;
                border = 0xFFFFFFFF;
            } else if (this.isHovered()) {
                bg = 0xEE333333;
                border = 0xFFFFFFFF;
            } else {
                bg = 0xCC111111;
                border = accent;
            }
            g.fill(x, y, x + width, y + height, bg);
            g.renderOutline(x, y, width, height, border);
            Font f = Minecraft.getInstance().font;
            String s = fit(f, getMessage().getString(), width - 4);
            int col = this.active ? textColor : 0xFF777777;
            if (sub != null && height >= 22) {
                g.drawCenteredString(f, s, x + width / 2, y + 3, col);
                g.drawCenteredString(f, fit(f, sub, width - 4), x + width / 2, y + height - 11, this.active ? subColor : 0xFF777777);
            } else {
                g.drawCenteredString(f, s, x + width / 2, y + (height - 8) / 2, col);
            }
        }
    }
}