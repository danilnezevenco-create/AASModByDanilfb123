package com.example.aas.deck;

/**
 * The seven decks (brigade / formation classes) every faction has. The id is used in NBT, packets,
 * lang keys and texture paths:
 *   assets/aas/textures/gui/decks/class/<id>.png
 *   assets/aas/textures/gui/decks/emblem/<faction>/<id>.png
 */
public enum DeckType {
    AIRBORNE("airborne"),
    LIGHT_INF("light_inf"),
    LOGISTICS("logistics"),
    MOTORIZED("motorized"),
    COMBINED("combined"),
    SSO("sso"),
    ARMOR("armor");

    public static final DeckType[] VALUES = values();
    public static final int COUNT = VALUES.length;

    public final String id;

    DeckType(String id) {
        this.id = id;
    }

    /** Lang key of the CLASS name (not the faction-specific unit name). */
    public String classLangKey() {
        return "aas.deck.class." + id;
    }

    public static DeckType fromId(String id) {
        if (id == null) return null;
        for (DeckType d : VALUES) if (d.id.equalsIgnoreCase(id)) return d;
        return null;
    }

    /** @return the deck or null if the index is out of range (also for -1). */
    public static DeckType byOrdinal(int i) {
        return i >= 0 && i < VALUES.length ? VALUES[i] : null;
    }

    /** Next / previous deck in the enum order (wraps around), used by the cycle buttons in the admin UI. */
    public DeckType next(int step) {
        int n = (ordinal() + step) % COUNT;
        if (n < 0) n += COUNT;
        return VALUES[n];
    }
}
