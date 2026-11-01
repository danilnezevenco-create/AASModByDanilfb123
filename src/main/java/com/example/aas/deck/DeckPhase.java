package com.example.aas.deck;

/** Phases of the "preparation" that precedes a match (see DeckManager). */
public enum DeckPhase {
    NONE,
    TEAM_SELECT,
    DECK_VOTE,
    VERSUS;

    public static DeckPhase byOrdinal(int i) {
        return i >= 0 && i < values().length ? values()[i] : NONE;
    }

    public static DeckPhase fromName(String s) {
        for (DeckPhase p : values()) if (p.name().equalsIgnoreCase(s)) return p;
        return NONE;
    }
}
