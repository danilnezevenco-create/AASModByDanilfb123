package com.example.aas.deck;

import net.minecraft.nbt.CompoundTag;

/** Per-team, per-deck settings edited in the admin menu. */
public final class DeckParams {
    public static final int DEFAULT_HUB_LIMIT = 4;
    public static final int LOGISTICS_HUB_LIMIT = 6;
    public static final int LOGISTICS_CRATE_BONUS = 50;
    public static final int DEFAULT_TICKETS = 800;
    public static final int MAX_CRATE_BONUS = 1000;

    /** A disabled deck is not shown in the vote and cannot win. */
    public boolean enabled = true;
    /** Max hubs of the team when this deck wins (copied to maxBlueHubs / maxRedHubs at match start). */
    public int hubLimit = DEFAULT_HUB_LIMIT;
    /** Added to AASConfig.SUPPLY_CRATE_MATERIALS for every crate the team drops. 0..1000. */
    public int crateBonus = 0;
    /** Starting tickets of the team. >= 0. */
    public int tickets = DEFAULT_TICKETS;

    public static DeckParams defaultsFor(DeckType deck) {
        DeckParams p = new DeckParams();
        if (deck == DeckType.LOGISTICS) {
            p.hubLimit = LOGISTICS_HUB_LIMIT;
            p.crateBonus = LOGISTICS_CRATE_BONUS;
        }
        return p;
    }

    public DeckParams copy() {
        DeckParams p = new DeckParams();
        p.enabled = enabled;
        p.hubLimit = hubLimit;
        p.crateBonus = crateBonus;
        p.tickets = tickets;
        return p;
    }

    /** Brings every value into its legal range. */
    public void clamp() {
        if (hubLimit < 0) hubLimit = 0;
        if (crateBonus < 0) crateBonus = 0;
        if (crateBonus > MAX_CRATE_BONUS) crateBonus = MAX_CRATE_BONUS;
        if (tickets < 0) tickets = 0;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putBoolean("Enabled", enabled);
        t.putInt("HubLimit", hubLimit);
        t.putInt("CrateBonus", crateBonus);
        t.putInt("Tickets", tickets);
        return t;
    }

    public static DeckParams load(CompoundTag t, DeckType deck) {
        DeckParams p = defaultsFor(deck);
        if (t.contains("Enabled")) p.enabled = t.getBoolean("Enabled");
        if (t.contains("HubLimit")) p.hubLimit = t.getInt("HubLimit");
        if (t.contains("CrateBonus")) p.crateBonus = t.getInt("CrateBonus");
        if (t.contains("Tickets")) p.tickets = t.getInt("Tickets");
        p.clamp();
        return p;
    }
}
