package com.example.aas.client.gui;

import com.example.aas.config.AASConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * AAS configuration screen: tabs + scrollable list + search + tooltips.
 * All texts are translatable: keys "aas.cfg.*" in en_us / ru_ru / uk_ua.
 *
 * To add a new setting: add one line in the constructor (t.add(new XxxSetting(...)))
 * and two lang keys: aas.cfg.<id> and aas.cfg.<id>.tip
 */
public class AASConfigScreen extends Screen {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    private static final int WIDGET_W = 130;
    private static final int ROW_H = 24;
    private static final int TAB_W = 80;
    private static final int TAB_H = 20;

    private static int lastTab = 0; // remembered between openings

    private final Screen parentScreen;
    private final List<Tab> tabs = new ArrayList<>();

    private int currentTab = lastTab;
    private String searchText = "";

    private final List<Button> tabButtons = new ArrayList<>();
    private EditBox searchBox;
    private SettingList list;
    private Setting hoveredSetting;
    private List<Setting> visible = new ArrayList<>();

    public AASConfigScreen(Screen parentScreen) {
        super(Component.translatable("aas.cfg.title"));
        this.parentScreen = parentScreen;

        Tab t;

        t = tab("general");
        t.add(new BoolSetting("ags_projectile_destruction", AASConfig.AGS_PROJECTILE_DESTRUCTION, AASConfig.SPEC));
        t.add(new BoolSetting("ammo_stack_destruction", AASConfig.AMMO_STACK_DESTRUCTION, AASConfig.SPEC));
        t.add(new DoubleSetting("digging_speed_multiplier", AASConfig.DIGGING_SPEED_MULTIPLIER, 0.1, 10.0, AASConfig.SPEC));
        t.add(new BoolSetting("restrict_interactions_to_doors_gates", AASConfig.RESTRICT_INTERACTIONS_TO_DOORS_GATES, AASConfig.SPEC));
        t.add(new BoolSetting("prevent_block_breaking", AASConfig.PREVENT_BLOCK_BREAKING, AASConfig.SPEC));
        t.add(new BoolSetting("allow_breaking_defenses", AASConfig.ALLOW_BREAKING_DEFENSES, AASConfig.SPEC));
        t.add(new BoolSetting("prevent_all_item_drops", AASConfig.PREVENT_ALL_ITEM_DROPS, AASConfig.SPEC));
        t.add(new BoolSetting("low_tickets_siren", AASConfig.LOW_TICKETS_SIREN, AASConfig.SPEC));
        t.add(new BoolSetting("auto_balance_teams", AASConfig.AUTO_BALANCE_TEAMS, AASConfig.SPEC));
        t.add(new BoolSetting("show_kit_ranks", AASConfig.SHOW_KIT_RANKS, AASConfig.SPEC));
        t.add(new IntSetting("invasion_prep_time_minutes", AASConfig.INVASION_PREP_TIME_MINUTES, 1, 30, AASConfig.SPEC));
        t.add(new IntSetting("vote_auto_start_time", AASConfig.VOTE_AUTO_START_TIME, 1, 60, AASConfig.SPEC));
        t.add(new IntSetting("vote_required_percentage", AASConfig.VOTE_REQUIRED_PERCENTAGE, 1, 100, AASConfig.SPEC));
        t.add(new BoolSetting("separate_team_destruction_messages", AASConfig.SEPARATE_TEAM_DESTRUCTION_MESSAGES, AASConfig.SPEC));

        t = tab("squads");
        t.add(new BoolSetting("auto_give_sl_radio", AASConfig.AUTO_GIVE_SL_RADIO, AASConfig.SPEC));
        t.add(new BoolSetting("require_officer_for_sl", AASConfig.REQUIRE_OFFICER_FOR_SL, AASConfig.SPEC));
        t.add(new BoolSetting("prevent_enemy_vehicle_entry", AASConfig.PREVENT_ENEMY_VEHICLE_ENTRY, AASConfig.SPEC));
        t.add(new BoolSetting("require_specialist_to_drive", AASConfig.REQUIRE_SPECIALIST_TO_DRIVE, AASConfig.SPEC));
        t.add(new BoolSetting("prevent_vehicle_inventory_access", AASConfig.PREVENT_VEHICLE_INVENTORY_ACCESS, AASConfig.SPEC));
        t.add(new BoolSetting("one_ammo_bag_per_player", AASConfig.ONE_AMMO_BAG_PER_PLAYER, AASConfig.SPEC));
        t.add(new BoolSetting("require_rifleman_for_ammo_bag", AASConfig.REQUIRE_RIFLEMAN_FOR_AMMO_BAG, AASConfig.SPEC));
        t.add(new IntSetting("voice_local_range", AASConfig.VOICE_LOCAL_RANGE, 5, 128, AASConfig.SPEC));

        t = tab("medic");
        t.add(new BoolSetting("enable_knockout", AASConfig.ENABLE_KNOCKOUT, AASConfig.SPEC));
        t.add(new StringSetting("revive_item", AASConfig.REVIVE_ITEM, AASConfig.SPEC));
        t.add(new IntSetting("revive_cooldown_seconds", AASConfig.REVIVE_COOLDOWN_SECONDS, 0, 600, AASConfig.SPEC));
        t.add(new IntSetting("revive_hold_seconds", AASConfig.REVIVE_HOLD_SECONDS, 1, 60, AASConfig.SPEC));
        t.add(new IntSetting("medic_revive_hold_seconds", AASConfig.MEDIC_REVIVE_HOLD_SECONDS, 1, 60, AASConfig.SPEC));
        t.add(new IntSetting("max_downed_time_seconds", AASConfig.MAX_DOWNED_TIME_SECONDS, 5, 3600, AASConfig.SPEC));
        t.add(new BoolSetting("main_supply_healing", AASConfig.MAIN_SUPPLY_HEALING, AASConfig.SPEC));
        t.add(new IntSetting("main_supply_heal_radius", AASConfig.MAIN_SUPPLY_HEAL_RADIUS, 1, 50, AASConfig.SPEC));

        t = tab("economy");
        t.add(new IntSetting("hub_resupply_cost", AASConfig.HUB_RESUPPLY_COST, 0, 1000, AASConfig.SPEC));
        t.add(new BoolSetting("hub_spawn_costs_materials", AASConfig.HUB_SPAWN_COSTS_MATERIALS, AASConfig.SPEC));
        t.add(new IntSetting("hub_spawn_material_cost", AASConfig.HUB_SPAWN_MATERIAL_COST, 0, 1000, AASConfig.SPEC));
        t.add(new BoolSetting("hub_placement_requires_crate", AASConfig.HUB_PLACEMENT_REQUIRES_CRATE, AASConfig.SPEC));
        t.add(new IntSetting("supply_truck_crates", AASConfig.SUPPLY_TRUCK_CRATES, 1, 20, AASConfig.SPEC));
        t.add(new IntSetting("supply_crate_materials", AASConfig.SUPPLY_CRATE_MATERIALS, 10, 1000, AASConfig.SPEC));
        t.add(new IntSetting("rifleman_crate_resupply_cost", AASConfig.RIFLEMAN_CRATE_RESUPPLY_COST, 0, 1000, AASConfig.SPEC));
        t.add(new DoubleSetting("rifleman_hub_cost_multiplier", AASConfig.RIFLEMAN_HUB_COST_MULTIPLIER, 1.0, 10.0, AASConfig.SPEC));
        t.add(new IntSetting("sapper_crate_resupply_cost", AASConfig.SAPPER_CRATE_RESUPPLY_COST, 0, 1000, AASConfig.SPEC));
        t.add(new IntSetting("sapper_vehicle_resupply_cost", AASConfig.SAPPER_VEHICLE_RESUPPLY_COST, 0, 1000, AASConfig.SPEC));
        t.add(new DoubleSetting("sapper_hub_cost_multiplier", AASConfig.SAPPER_HUB_COST_MULTIPLIER, 1.0, 10.0, AASConfig.SPEC));
        t.add(new IntSetting("drone_operator_crate_resupply_cost", AASConfig.DRONE_OPERATOR_CRATE_RESUPPLY_COST, 0, 1000, AASConfig.SPEC));
        t.add(new IntSetting("drone_operator_vehicle_resupply_cost", AASConfig.DRONE_OPERATOR_VEHICLE_RESUPPLY_COST, 0, 1000, AASConfig.SPEC));
        t.add(new DoubleSetting("drone_operator_hub_cost_multiplier", AASConfig.DRONE_OPERATOR_HUB_COST_MULTIPLIER, 1.0, 10.0, AASConfig.SPEC));

        t = tab("balance");
        t.add(new IntSetting("min_hub_distance", AASConfig.MIN_HUB_DISTANCE, 0, 10000, AASConfig.SPEC));
        t.add(new IntSetting("min_rally_point_distance", AASConfig.MIN_RALLY_POINT_DISTANCE, 0, 10000, AASConfig.SPEC));
        t.add(new IntSetting("hub_block_radius", AASConfig.HUB_BLOCK_RADIUS, 5, 200, AASConfig.SPEC));
        t.add(new IntSetting("rally_block_radius", AASConfig.RALLY_BLOCK_RADIUS, 5, 200, AASConfig.SPEC));
        t.add(new IntSetting("hub_sound_radius", AASConfig.HUB_SOUND_RADIUS, 1, 128, AASConfig.SPEC));
        t.add(new IntSetting("hub_build_radius", AASConfig.HUB_BUILD_RADIUS, 10, 200, AASConfig.SPEC));
        t.add(new IntSetting("crate_build_radius", AASConfig.CRATE_BUILD_RADIUS, 5, 200, AASConfig.SPEC));
        t.add(new IntSetting("hub_block_enemy_count", AASConfig.HUB_BLOCK_ENEMY_COUNT, 1, 20, AASConfig.SPEC));
        t.add(new IntSetting("rally_block_enemy_count", AASConfig.RALLY_BLOCK_ENEMY_COUNT, 1, 20, AASConfig.SPEC));

        t = tab("artillery");
        t.add(new IntSetting("art_request_cooldown_seconds", AASConfig.ART_REQUEST_COOLDOWN_SECONDS, 0, 7200, AASConfig.SPEC));
        t.add(new IntSetting("art_strike_cooldown_minutes", AASConfig.ART_STRIKE_COOLDOWN_MINUTES, 0, 120, AASConfig.SPEC));
        t.add(new IntSetting("art_initial_cooldown_minutes", AASConfig.ART_INITIAL_COOLDOWN_MINUTES, 0, 120, AASConfig.SPEC));
        t.add(new DoubleSetting("art_request_radius", AASConfig.ART_REQUEST_RADIUS, 5.0, 300.0, AASConfig.SPEC));
        t.add(new IntSetting("art_request_max_distance", AASConfig.ART_REQUEST_MAX_DISTANCE, 50, 8192, AASConfig.SPEC));
        t.add(new IntSetting("art_request_confirm_seconds", AASConfig.ART_REQUEST_CONFIRM_SECONDS, 5, 600, AASConfig.SPEC));
        t.add(new IntSetting("art_request_lifetime_seconds", AASConfig.ART_REQUEST_LIFETIME_SECONDS, 30, 7200, AASConfig.SPEC));
        t.add(new IntSetting("art_shell_spawn_height", AASConfig.ART_SHELL_SPAWN_HEIGHT, 64, 2000, AASConfig.SPEC));
        t.add(new IntSetting("art_silence_seconds", AASConfig.ART_SILENCE_SECONDS, 0, 120, AASConfig.SPEC));
        t.add(new IntSetting("art_salvo_seconds", AASConfig.ART_SALVO_SECONDS, 1, 120, AASConfig.SPEC));
        t.add(new IntSetting("art_rest_seconds", AASConfig.ART_REST_SECONDS, 0, 120, AASConfig.SPEC));
        t.add(new IntSetting("art_circle_spot_shells", AASConfig.ART_CIRCLE_SPOT_SHELLS, 1, 10, AASConfig.SPEC));
        t.add(new IntSetting("art_circle_salvo_shells", AASConfig.ART_CIRCLE_SALVO_SHELLS, 1, 500, AASConfig.SPEC));
        t.add(new IntSetting("art_rect_spot_shells", AASConfig.ART_RECT_SPOT_SHELLS, 1, 10, AASConfig.SPEC));
        t.add(new IntSetting("art_rect_salvo_shells", AASConfig.ART_RECT_SALVO_SHELLS, 1, 500, AASConfig.SPEC));
        t.add(new IntSetting("art_circle_min_radius", AASConfig.ART_CIRCLE_MIN_RADIUS, 1, 500, AASConfig.SPEC));
        t.add(new IntSetting("art_circle_max_radius", AASConfig.ART_CIRCLE_MAX_RADIUS, 1, 300, AASConfig.SPEC));
        t.add(new IntSetting("art_rect_min_length", AASConfig.ART_RECT_MIN_LENGTH, 1, 600, AASConfig.SPEC));
        t.add(new IntSetting("art_rect_max_length", AASConfig.ART_RECT_MAX_LENGTH, 1, 600, AASConfig.SPEC));
        t.add(new IntSetting("art_rect_min_width", AASConfig.ART_RECT_MIN_WIDTH, 1, 300, AASConfig.SPEC));
        t.add(new IntSetting("art_rect_max_width", AASConfig.ART_RECT_MAX_WIDTH, 1, 300, AASConfig.SPEC));

        t = tab("uav");
        t.add(new IntSetting("uav_cooldown_seconds", AASConfig.UAV_COOLDOWN_SECONDS, 0, 36000, AASConfig.SPEC));
        t.add(new IntSetting("uav_min_radius", AASConfig.UAV_MIN_RADIUS, 10, 4000, AASConfig.SPEC));
        t.add(new IntSetting("uav_max_radius", AASConfig.UAV_MAX_RADIUS, 10, 4000, AASConfig.SPEC));
        t.add(new IntSetting("uav_duration_seconds", AASConfig.UAV_DURATION_SECONDS, 10, 3600, AASConfig.SPEC));
        t.add(new IntSetting("uav_altitude", AASConfig.UAV_ALTITUDE, 20, 600, AASConfig.SPEC));
        t.add(new IntSetting("uav_health", AASConfig.UAV_HEALTH, 1, 100000, AASConfig.SPEC));
        t.add(new DoubleSetting("uav_speed", AASConfig.UAV_SPEED, 0.1, 5.0, AASConfig.SPEC));
        t.add(new ZoomSetting("uav_zoom_fov", AASConfig.UAV_ZOOM_FOV, AASConfig.SPEC));
        t.add(new IntSetting("uav_max_tilt", AASConfig.UAV_MAX_TILT, 1, 120, AASConfig.SPEC));

        t = tab("factions");
        t.add(new StringSetting("blue_team_custom_name", AASConfig.BLUE_TEAM_CUSTOM_NAME, AASConfig.SPEC));
        t.add(new StringSetting("red_team_custom_name", AASConfig.RED_TEAM_CUSTOM_NAME, AASConfig.SPEC));
        t.add(new IntSetting("compass_scale", AASConfig.COMPASS_SCALE, 1, 3, AASConfig.CLIENT_SPEC));

        if (currentTab < 0 || currentTab >= tabs.size()) currentTab = 0;
    }

    private Tab tab(String id) {
        Tab t = new Tab(id);
        tabs.add(t);
        return t;
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;

        // --- tab buttons (wrap into several rows on narrow screens)
        tabButtons.clear();
        int perRow = Math.max(1, (this.width - 16) / (TAB_W + 2));
        int n = tabs.size();
        int rows = (n + perRow - 1) / perRow;
        for (int i = 0; i < n; i++) {
            final int idx = i;
            int row = i / perRow;
            int inRow = Math.min(perRow, n - row * perRow);
            int startX = (this.width - (inRow * (TAB_W + 2) - 2)) / 2;
            int x = startX + (i % perRow) * (TAB_W + 2);
            int y = 20 + row * (TAB_H + 2);
            Button b = Button.builder(Component.translatable("aas.cfg.tab." + tabs.get(i).id), btn -> selectTab(idx))
                    .bounds(x, y, TAB_W, TAB_H).build();
            this.addRenderableWidget(b);
            tabButtons.add(b);
        }

        // --- search box
        int searchY = 20 + rows * (TAB_H + 2) + 2;
        searchBox = new EditBox(this.font, cx - 100, searchY, 200, 18, Component.translatable("aas.cfg.search"));
        searchBox.setHint(Component.translatable("aas.cfg.search"));
        searchBox.setMaxLength(64);
        searchBox.setValue(searchText);
        this.addRenderableWidget(searchBox);

        // --- list
        int top = searchY + 24;
        int bottom = this.height - 50;
        list = new SettingList(this.minecraft, this.width, this.height, top, bottom);
        this.addRenderableWidget(list);
        searchBox.setResponder(s -> {
            searchText = s;
            refreshList(false);
        });
        refreshList(false);

        // --- bottom buttons
        this.addRenderableWidget(Button.builder(Component.translatable("aas.cfg.save"), b -> {
            saveAll();
            this.onClose();
        }).bounds(cx - 155, this.height - 28, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("aas.cfg.reset_tab"), b -> {
            for (Setting s : visible) s.reset();
            refreshList(true);
        }).bounds(cx - 50, this.height - 28, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("aas.cfg.cancel"), b -> this.onClose())
                .bounds(cx + 55, this.height - 28, 100, 20).build());
    }

    private void selectTab(int idx) {
        currentTab = idx;
        lastTab = idx;
        searchText = "";
        if (searchBox != null) searchBox.setValue(""); // triggers refresh via responder
        refreshList(false);
    }

    private boolean searching() {
        return !searchText.isBlank();
    }

    private void refreshList(boolean keepScroll) {
        if (list == null) return;
        double scroll = list.getScrollAmount();

        visible = new ArrayList<>();
        if (searching()) {
            String q = searchText.trim().toLowerCase(Locale.ROOT);
            String qId = q.replace(' ', '_');
            for (Tab t : tabs) {
                for (Setting s : t.settings) {
                    if (s.label().getString().toLowerCase(Locale.ROOT).contains(q) || s.id.contains(qId)) {
                        visible.add(s);
                    }
                }
            }
        } else {
            visible.addAll(tabs.get(currentTab).settings);
        }
        list.reload(visible);
        list.setScrollAmount(keepScroll ? scroll : 0);

        for (int i = 0; i < tabButtons.size(); i++) {
            tabButtons.get(i).active = searching() || i != currentTab;
        }
    }

    // ------------------------------------------------------------------ save

    private int countDirty() {
        int n = 0;
        for (Tab t : tabs) for (Setting s : t.settings) if (s.isDirty()) n++;
        return n;
    }

    private void saveAll() {
        Set<ForgeConfigSpec> touched = new HashSet<>();
        for (Tab t : tabs) {
            for (Setting s : t.settings) {
                if (!s.isDirty()) continue;
                try {
                    s.apply();
                    touched.add(s.spec);
                } catch (Exception e) {
                    LOGGER.error("AAS config: failed to apply {}", s.id, e);
                }
            }
        }
        for (ForgeConfigSpec spec : touched) {
            try {
                spec.save();
            } catch (Exception e) {
                LOGGER.error("AAS config: failed to save spec", e);
            }
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(parentScreen);
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(GuiGraphics gui, int mx, int my, float pt) {
        hoveredSetting = null;
        this.renderBackground(gui);
        super.render(gui, mx, my, pt);

        gui.drawCenteredString(this.font, this.title, this.width / 2, 6, 0xFFFF55);

        int dirty = countDirty();
        if (dirty > 0) {
            gui.drawString(this.font, Component.translatable("aas.cfg.unsaved", dirty), 8, 6, 0xFFFF55);
        }

        if (visible.isEmpty()) {
            gui.drawCenteredString(this.font, Component.translatable("aas.cfg.no_results"),
                    this.width / 2, (list.listTop() + list.listBottom()) / 2, 0xAAAAAA);
        }

        boolean remote = this.minecraft != null && this.minecraft.level != null
                && this.minecraft.getSingleplayerServer() == null;
        if (remote) {
            gui.drawCenteredString(this.font, Component.translatable("aas.cfg.remote_warning"),
                    this.width / 2, this.height - 42, 0xFFAA00);
        }

        if (hoveredSetting != null) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            lines.addAll(this.font.split(hoveredSetting.label().copy().withStyle(ChatFormatting.YELLOW), 240));
            lines.addAll(this.font.split(hoveredSetting.tip().copy().withStyle(ChatFormatting.GRAY), 240));
            Component extra = hoveredSetting.extra();
            if (extra != null) lines.addAll(this.font.split(extra.copy().withStyle(ChatFormatting.AQUA), 240));
            lines.addAll(this.font.split(Component.translatable("aas.cfg.default", hoveredSetting.defaultText())
                    .withStyle(ChatFormatting.DARK_GRAY), 240));
            gui.renderTooltip(this.font, lines, mx, my);
        }
    }

    // ------------------------------------------------------------------ list

    private class SettingList extends ContainerObjectSelectionList<SettingList.Row> {
        SettingList(Minecraft mc, int w, int h, int y0, int y1) {
            super(mc, w, h, y0, y1, ROW_H);
        }

        void reload(List<Setting> settings) {
            this.clearEntries();
            for (Setting s : settings) {
                this.addEntry(new Row(s, s.createWidget(AASConfigScreen.this.font)));
            }
        }

        int listTop() {
            return this.y0;
        }

        int listBottom() {
            return this.y1;
        }

        @Override
        public int getRowWidth() {
            return Math.min(this.width - 40, 400);
        }

        @Override
        protected int getScrollbarPosition() {
            return this.width / 2 + getRowWidth() / 2 + 8;
        }

        private class Row extends ContainerObjectSelectionList.Entry<Row> {
            private final Setting setting;
            private final AbstractWidget widget;

            Row(Setting setting, AbstractWidget widget) {
                this.setting = setting;
                this.widget = widget;
            }

            @Override
            public void render(GuiGraphics gui, int index, int top, int left, int width, int height,
                               int mx, int my, boolean hovered, float pt) {
                Font font = AASConfigScreen.this.font;
                if (hovered) {
                    gui.fill(left - 2, top, left + width + 2, top + height - 2, 0x22FFFFFF);
                    hoveredSetting = setting;
                }
                int labelW = width - WIDGET_W - 10;
                String text = font.plainSubstrByWidth(setting.label().getString(), labelW);
                int color = setting.isDirty() ? 0xFFFF55 : 0xFFFFFF;
                gui.drawString(font, text, left + 2, top + (height - 2 - 8) / 2, color);

                widget.setX(left + width - WIDGET_W);
                widget.setY(top + (height - 2 - widget.getHeight()) / 2);
                widget.render(gui, mx, my, pt);
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return List.of(widget);
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return List.of(widget);
            }
        }
    }

    // ------------------------------------------------------------------ model

    private static final class Tab {
        final String id;
        final List<Setting> settings = new ArrayList<>();

        Tab(String id) {
            this.id = id;
        }

        void add(Setting s) {
            settings.add(s);
        }
    }

    private static String fmt(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e15) return String.valueOf((long) d);
        return String.valueOf(d);
    }

    private abstract static class Setting {
        final String id;
        final ForgeConfigSpec spec;

        Setting(String id, ForgeConfigSpec spec) {
            this.id = id;
            this.spec = spec;
        }

        Component label() {
            return Component.translatable("aas.cfg." + id);
        }

        Component tip() {
            return Component.translatable("aas.cfg." + id + ".tip");
        }

        /** Optional extra tooltip line (e.g. range). */
        Component extra() {
            return null;
        }

        abstract String defaultText();

        abstract AbstractWidget createWidget(Font font);

        abstract boolean isDirty();

        abstract void apply();

        abstract void reset();
    }

    // ---- boolean
    private static final class BoolSetting extends Setting {
        final ForgeConfigSpec.BooleanValue v;
        boolean pending;

        BoolSetting(String id, ForgeConfigSpec.BooleanValue v, ForgeConfigSpec spec) {
            super(id, spec);
            this.v = v;
            this.pending = v.get();
        }

        @Override String defaultText() {
            return Component.translatable(v.getDefault() ? "options.on" : "options.off").getString();
        }

        @Override AbstractWidget createWidget(Font font) {
            return CycleButton.onOffBuilder(pending).displayOnlyValue()
                    .create(0, 0, WIDGET_W, 20, Component.empty(), (b, val) -> pending = val);
        }

        @Override boolean isDirty() {
            return pending != v.get();
        }

        @Override void apply() {
            v.set(pending);
        }

        @Override void reset() {
            pending = v.getDefault();
        }
    }

    // ---- integer
    private static final class IntSetting extends Setting {
        final ForgeConfigSpec.IntValue v;
        final int lo, hi;
        String text;

        IntSetting(String id, ForgeConfigSpec.IntValue v, int lo, int hi, ForgeConfigSpec spec) {
            super(id, spec);
            this.v = v;
            this.lo = lo;
            this.hi = hi;
            this.text = String.valueOf(v.get());
        }

        Long raw() {
            try {
                return Long.parseLong(text.trim());
            } catch (Exception e) {
                return null;
            }
        }

        boolean valid() {
            Long r = raw();
            return r != null && r >= lo && r <= hi;
        }

        @Override Component extra() {
            return Component.translatable("aas.cfg.range", String.valueOf(lo), String.valueOf(hi));
        }

        @Override String defaultText() {
            return String.valueOf(v.getDefault());
        }

        @Override AbstractWidget createWidget(Font font) {
            EditBox b = new EditBox(font, 0, 0, WIDGET_W, 20, Component.empty());
            b.setMaxLength(10);
            b.setFilter(s -> s.matches("\\d*"));
            b.setValue(text);
            b.setTextColor(valid() ? 0xE0E0E0 : 0xFF5555);
            b.setResponder(s -> {
                text = s;
                b.setTextColor(valid() ? 0xE0E0E0 : 0xFF5555);
            });
            return b;
        }

        @Override boolean isDirty() {
            Long r = raw();
            return r != null && r.longValue() != (long) v.get();
        }

        @Override void apply() {
            Long r = raw();
            if (r == null) return;
            v.set((int) Math.max(lo, Math.min(hi, r)));
        }

        @Override void reset() {
            text = String.valueOf(v.getDefault());
        }
    }

    // ---- double
    private static final class DoubleSetting extends Setting {
        final ForgeConfigSpec.DoubleValue v;
        final double lo, hi;
        String text;

        DoubleSetting(String id, ForgeConfigSpec.DoubleValue v, double lo, double hi, ForgeConfigSpec spec) {
            super(id, spec);
            this.v = v;
            this.lo = lo;
            this.hi = hi;
            this.text = fmt(v.get());
        }

        Double raw() {
            try {
                return Double.parseDouble(text.trim());
            } catch (Exception e) {
                return null;
            }
        }

        boolean valid() {
            Double r = raw();
            return r != null && r >= lo && r <= hi;
        }

        @Override Component extra() {
            return Component.translatable("aas.cfg.range", fmt(lo), fmt(hi));
        }

        @Override String defaultText() {
            return fmt(v.getDefault());
        }

        @Override AbstractWidget createWidget(Font font) {
            EditBox b = new EditBox(font, 0, 0, WIDGET_W, 20, Component.empty());
            b.setMaxLength(12);
            b.setFilter(s -> s.matches("\\d*\\.?\\d*"));
            b.setValue(text);
            b.setTextColor(valid() ? 0xE0E0E0 : 0xFF5555);
            b.setResponder(s -> {
                text = s;
                b.setTextColor(valid() ? 0xE0E0E0 : 0xFF5555);
            });
            return b;
        }

        @Override boolean isDirty() {
            Double r = raw();
            return r != null && Math.abs(r - v.get()) > 1e-9;
        }

        @Override void apply() {
            Double r = raw();
            if (r == null) return;
            v.set(Math.max(lo, Math.min(hi, r)));
        }

        @Override void reset() {
            text = fmt(v.getDefault());
        }
    }

    // ---- string
    private static final class StringSetting extends Setting {
        final ForgeConfigSpec.ConfigValue<String> v;
        String text;

        StringSetting(String id, ForgeConfigSpec.ConfigValue<String> v, ForgeConfigSpec spec) {
            super(id, spec);
            this.v = v;
            this.text = v.get();
        }

        @Override String defaultText() {
            return v.getDefault();
        }

        @Override AbstractWidget createWidget(Font font) {
            EditBox b = new EditBox(font, 0, 0, WIDGET_W, 20, Component.empty());
            b.setMaxLength(256);
            b.setValue(text);
            b.setResponder(s -> text = s);
            return b;
        }

        @Override boolean isDirty() {
            return !text.isBlank() && !text.equals(v.get());
        }

        @Override void apply() {
            if (!text.isBlank()) v.set(text);
        }

        @Override void reset() {
            text = v.getDefault();
        }
    }

    // ---- list of 3 numbers (UAV zoom FOV)
    private static final class ZoomSetting extends Setting {
        final ForgeConfigSpec.ConfigValue<List<? extends Number>> v;
        String text;

        ZoomSetting(String id, ForgeConfigSpec.ConfigValue<List<? extends Number>> v, ForgeConfigSpec spec) {
            super(id, spec);
            this.v = v;
            this.text = join(v.get());
        }

        static String join(List<? extends Number> l) {
            StringBuilder sb = new StringBuilder();
            for (Number n : l) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(fmt(n.doubleValue()));
            }
            return sb.toString();
        }

        List<Double> parse() {
            try {
                String[] parts = text.trim().split("[,;\\s]+");
                if (parts.length != 3) return null;
                List<Double> out = new ArrayList<>();
                for (String p : parts) {
                    double d = Double.parseDouble(p);
                    if (d <= 0 || d > 170) return null;
                    out.add(d);
                }
                return out;
            } catch (Exception e) {
                return null;
            }
        }

        @Override String defaultText() {
            return join(v.getDefault());
        }

        @Override AbstractWidget createWidget(Font font) {
            EditBox b = new EditBox(font, 0, 0, WIDGET_W, 20, Component.empty());
            b.setMaxLength(32);
            b.setFilter(s -> s.matches("[0-9.,; ]*"));
            b.setValue(text);
            b.setTextColor(parse() != null ? 0xE0E0E0 : 0xFF5555);
            b.setResponder(s -> {
                text = s;
                b.setTextColor(parse() != null ? 0xE0E0E0 : 0xFF5555);
            });
            return b;
        }

        @Override boolean isDirty() {
            List<Double> p = parse();
            if (p == null) return false;
            List<? extends Number> cur = v.get();
            if (cur.size() != 3) return true;
            for (int i = 0; i < 3; i++) {
                if (Math.abs(p.get(i) - cur.get(i).doubleValue()) > 1e-9) return true;
            }
            return false;
        }

        @Override void apply() {
            List<Double> p = parse();
            if (p != null) v.set(p);
        }

        @Override void reset() {
            text = join(v.getDefault());
        }
    }
}