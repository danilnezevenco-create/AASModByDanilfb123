package com.example.aas.deck;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data table: for every faction and every one of the 7 decks the display name (lang key
 * aas.deck.<faction>.<deckId>, translated in en_us / ru_ru / uk_ua) and the emblem texture path.
 *
 * Names that are invented (no real unit fits the class) are flagged here and in the lang files with the key
 * aas.deck.<faction>.<deckId>._fictional, so they are easy to find and replace.
 * Adding a faction = add it to FACTIONS, add 7 reg(...) lines and 7 lang keys. No logic depends on the names.
 */
public final class DeckRegistry {
    private DeckRegistry() {}

    /** Every faction that has all 7 decks. */
    public static final List<String> FACTIONS = List.of(
            "ukraine", "russia", "usa", "nato", "bluefor", "redfor", "insurgency", "pmc", "germany", "militia");

    public record Entry(String nameKey, boolean fictional) {}

    private static final Map<String, Map<DeckType, Entry>> TABLE = new HashMap<>();

    private static void reg(String faction, DeckType deck, boolean fictional) {
        TABLE.computeIfAbsent(faction, k -> new HashMap<>())
                .put(deck, new Entry("aas.deck." + faction + "." + deck.id, fictional));
    }

    static {
        reg("ukraine", DeckType.AIRBORNE, false);
        reg("ukraine", DeckType.LIGHT_INF, false);
        reg("ukraine", DeckType.LOGISTICS, false);
        reg("ukraine", DeckType.MOTORIZED, false);
        reg("ukraine", DeckType.COMBINED, false);
        reg("ukraine", DeckType.SSO, false);
        reg("ukraine", DeckType.ARMOR, false);
        reg("russia", DeckType.AIRBORNE, false);
        reg("russia", DeckType.LIGHT_INF, false);
        reg("russia", DeckType.LOGISTICS, false);
        reg("russia", DeckType.MOTORIZED, false);
        reg("russia", DeckType.COMBINED, false);
        reg("russia", DeckType.SSO, false);
        reg("russia", DeckType.ARMOR, false);
        reg("usa", DeckType.AIRBORNE, false);
        reg("usa", DeckType.LIGHT_INF, false);
        reg("usa", DeckType.LOGISTICS, false);
        reg("usa", DeckType.MOTORIZED, false);
        reg("usa", DeckType.COMBINED, false);
        reg("usa", DeckType.SSO, false);
        reg("usa", DeckType.ARMOR, false);
        reg("nato", DeckType.AIRBORNE, true);
        reg("nato", DeckType.LIGHT_INF, true);
        reg("nato", DeckType.LOGISTICS, true);
        reg("nato", DeckType.MOTORIZED, true);
        reg("nato", DeckType.COMBINED, false);
        reg("nato", DeckType.SSO, true);
        reg("nato", DeckType.ARMOR, true);
        reg("bluefor", DeckType.AIRBORNE, true);
        reg("bluefor", DeckType.LIGHT_INF, true);
        reg("bluefor", DeckType.LOGISTICS, true);
        reg("bluefor", DeckType.MOTORIZED, true);
        reg("bluefor", DeckType.COMBINED, true);
        reg("bluefor", DeckType.SSO, true);
        reg("bluefor", DeckType.ARMOR, true);
        reg("redfor", DeckType.AIRBORNE, true);
        reg("redfor", DeckType.LIGHT_INF, true);
        reg("redfor", DeckType.LOGISTICS, true);
        reg("redfor", DeckType.MOTORIZED, true);
        reg("redfor", DeckType.COMBINED, true);
        reg("redfor", DeckType.SSO, true);
        reg("redfor", DeckType.ARMOR, true);
        reg("insurgency", DeckType.AIRBORNE, true);
        reg("insurgency", DeckType.LIGHT_INF, true);
        reg("insurgency", DeckType.LOGISTICS, true);
        reg("insurgency", DeckType.MOTORIZED, true);
        reg("insurgency", DeckType.COMBINED, true);
        reg("insurgency", DeckType.SSO, false);
        reg("insurgency", DeckType.ARMOR, true);
        reg("pmc", DeckType.AIRBORNE, true);
        reg("pmc", DeckType.LIGHT_INF, false);
        reg("pmc", DeckType.LOGISTICS, false);
        reg("pmc", DeckType.MOTORIZED, true);
        reg("pmc", DeckType.COMBINED, false);
        reg("pmc", DeckType.SSO, false);
        reg("pmc", DeckType.ARMOR, true);
        reg("germany", DeckType.AIRBORNE, false);
        reg("germany", DeckType.LIGHT_INF, false);
        reg("germany", DeckType.LOGISTICS, false);
        reg("germany", DeckType.MOTORIZED, false);
        reg("germany", DeckType.COMBINED, false);
        reg("germany", DeckType.SSO, false);
        reg("germany", DeckType.ARMOR, false);
        reg("militia", DeckType.AIRBORNE, false);
        reg("militia", DeckType.LIGHT_INF, true);
        reg("militia", DeckType.LOGISTICS, true);
        reg("militia", DeckType.MOTORIZED, true);
        reg("militia", DeckType.COMBINED, true);
        reg("militia", DeckType.SSO, false);
        reg("militia", DeckType.ARMOR, true);
    }

    /**
     * Maps a faction id coming from the world data ("none", unknown ids, null) to a faction that has a table:
     * BLUE falls back to "bluefor", RED to "redfor".
     */
    public static String resolveFaction(String faction, boolean blueTeam) {
        if (faction != null) {
            String f = faction.toLowerCase();
            if (TABLE.containsKey(f)) return f;
        }
        return blueTeam ? "bluefor" : "redfor";
    }

    public static Entry entry(String faction, boolean blueTeam, DeckType deck) {
        return TABLE.get(resolveFaction(faction, blueTeam)).get(deck);
    }

    /** Lang key of the unit name, e.g. aas.deck.russia.armor */
    public static String nameKey(String faction, boolean blueTeam, DeckType deck) {
        return entry(faction, blueTeam, deck).nameKey();
    }

    /** assets/aas/textures/gui/decks/emblem/<faction>/<deckId>.png (may not exist - the client draws a circle then). */
    public static ResourceLocation emblemTexture(String faction, boolean blueTeam, DeckType deck) {
        return new ResourceLocation("aas", "textures/gui/decks/emblem/" + resolveFaction(faction, blueTeam) + "/" + deck.id + ".png");
    }

    /** Also accepted: assets/aas/textures/gui/decks/<faction>/<deckId>.png (without the "emblem" folder). */
    public static ResourceLocation emblemTextureAlt(String faction, boolean blueTeam, DeckType deck) {
        return new ResourceLocation("aas", "textures/gui/decks/" + resolveFaction(faction, blueTeam) + "/" + deck.id + ".png");
    }

    /** assets/aas/textures/gui/decks/class/<deckId>.png */
    public static ResourceLocation classIcon(DeckType deck) {
        return new ResourceLocation("aas", "textures/gui/decks/class/" + deck.id + ".png");
    }
}
