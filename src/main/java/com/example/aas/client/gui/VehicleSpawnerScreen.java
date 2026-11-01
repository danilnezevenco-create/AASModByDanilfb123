package com.example.aas.client.gui;

import com.example.aas.block.VehicleSpawnerBlockEntity;
import com.example.aas.menu.VehicleSpawnerMenu;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketUpdateSpawner;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class VehicleSpawnerScreen extends AbstractContainerScreen<VehicleSpawnerMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("aas", "textures/gui/spawner_gui.png");

    private EditBox vehicleIdField;
    private EditBox respawnTimeField;
    private EditBox initialTimeField;
    private EditBox yawField;

    // === РђР’РўРћ-Р’РћР—Р’Р РђРў (СЃРїСЂР°РІР° РѕС‚ РїР°РЅРµР»Рё) ===
    private Checkbox autoReturnCheckbox;
    private EditBox autoReturnTimeField;
    private Checkbox autoReturnDestroyCheckbox;

    // the deck whose spawner config is edited (a spawner stores one config per deck)
    private com.example.aas.deck.DeckType selectedDeck = com.example.aas.deck.DeckType.COMBINED;

    private com.example.aas.deck.DeckSpawnConfig c() {
        return menu.blockEntity.cfg(selectedDeck);
    }

    public VehicleSpawnerScreen(VehicleSpawnerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 196;
        this.imageHeight = 245;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        // РџРѕР»Рµ РІРІРѕРґР° СѓРіР»Р°
        yawField = new EditBox(this.font, x + 35, y + 40, 40, 14, Component.literal("Yaw"));
        yawField.setValue(String.valueOf((int)c().yaw));
        yawField.setFilter(s -> s.matches("-?\\d*"));
        yawField.setResponder(val -> {
            try {
                c().yaw = Float.parseFloat(val);
                sendUpdatePacket();
            } catch (Exception e) {}
        });
        addRenderableWidget(yawField);

        // === РџРћР›Р• ID (РЎР›Р•Р’Рђ) ===
        vehicleIdField = new EditBox(this.font, x + 35, y + 20, 80, 14, Component.literal("Vehicle ID"));
        vehicleIdField.setMaxLength(64);
        vehicleIdField.setValue(c().vehicleId);
        vehicleIdField.setBordered(true);
        vehicleIdField.setResponder(val -> sendUpdatePacket());
        addRenderableWidget(vehicleIdField);

        int rightCenterX = x + 155;
        int btnY_Respawn = y + 18;
        int btnY_Initial = y + 48;

        // --- 1. RESPAWN TIME ---
        addRenderableWidget(Button.builder(Component.literal("-"), b -> adjustTimer(false, -5))
                .bounds(rightCenterX - 42, btnY_Respawn, 15, 16).build());

        respawnTimeField = new EditBox(this.font, rightCenterX - 25, btnY_Respawn + 1, 46, 14, Component.literal("Respawn Time"));
        respawnTimeField.setValue(String.valueOf(c().respawnTime));
        respawnTimeField.setResponder(val -> onTimeFieldChanged(val, false));
        addRenderableWidget(respawnTimeField);

        addRenderableWidget(Button.builder(Component.literal("+"), b -> adjustTimer(false, 5))
                .bounds(rightCenterX + 23, btnY_Respawn, 15, 16).build());

        // --- 2. INITIAL TIME ---
        addRenderableWidget(Button.builder(Component.literal("-"), b -> adjustTimer(true, -5))
                .bounds(rightCenterX - 42, btnY_Initial, 15, 16).build());

        initialTimeField = new EditBox(this.font, rightCenterX - 25, btnY_Initial + 1, 46, 14, Component.literal("Initial Time"));
        initialTimeField.setValue(String.valueOf(c().initialTime));
        initialTimeField.setResponder(val -> onTimeFieldChanged(val, true));
        addRenderableWidget(initialTimeField);

        addRenderableWidget(Button.builder(Component.literal("+"), b -> adjustTimer(true, 5))
                .bounds(rightCenterX + 23, btnY_Initial, 15, 16).build());

        // РљРЅРѕРїРєРё РїРѕРІРѕСЂРѕС‚Р°
        addRenderableWidget(Button.builder(Component.literal("в†є"), b -> adjustYaw(-45))
                .bounds(x + 77, y + 40, 18, 14).build());
        addRenderableWidget(Button.builder(Component.literal("в†»"), b -> adjustYaw(45))
                .bounds(x + 97, y + 40, 18, 14).build());

        // === РќРћР’РђРЇ РљРћР›РћРќРљРђ РЎРџР РђР’Рђ: РђР’РўРћ-Р’РћР—Р’Р РђРў РўР•РҐРќРРљР ===
        int panelX = x + imageWidth + 8;

        // 1. Р“Р°Р»РѕС‡РєР° "РІРєР»СЋС‡РёС‚СЊ Р°РІС‚Рѕ-РІРѕР·РІСЂР°С‚"
        autoReturnCheckbox = new Checkbox(panelX, y + 4, 20, 20,
                Component.literal("Auto-return"), c().autoReturnEnabled) {
            @Override
            public void onPress() {
                super.onPress();
                c().autoReturnEnabled = this.selected();
                sendUpdatePacket();
            }
        };
        addRenderableWidget(autoReturnCheckbox);

        // 2. РўР°Р№РјРµСЂ (СЃРµРє. Р±РµР· РёРіСЂРѕРєР° РІ С‚РµС…РЅРёРєРµ РґРѕ СЃСЂР°Р±Р°С‚С‹РІР°РЅРёСЏ)
        int timerY = y + 32;
        addRenderableWidget(Button.builder(Component.literal("-"), b -> adjustAutoReturnTimer(-5))
                .bounds(panelX, timerY, 15, 16).build());

        autoReturnTimeField = new EditBox(this.font, panelX + 17, timerY + 1, 40, 14, Component.literal("Auto Return Time"));
        autoReturnTimeField.setValue(String.valueOf(c().autoReturnTime));
        autoReturnTimeField.setResponder(this::onAutoReturnTimeChanged);
        addRenderableWidget(autoReturnTimeField);

        addRenderableWidget(Button.builder(Component.literal("+"), b -> adjustAutoReturnTimer(5))
                .bounds(panelX + 59, timerY, 15, 16).build());

        // 3. Р“Р°Р»РѕС‡РєР° "СѓРЅРёС‡С‚РѕР¶Р°С‚СЊ РІРјРµСЃС‚Рѕ С‚РµР»РµРїРѕСЂС‚Р°"
        autoReturnDestroyCheckbox = new Checkbox(panelX, y + 56, 20, 20,
                Component.literal("Destroy"), c().autoReturnDestroy) {
            @Override
            public void onPress() {
                super.onPress();
                c().autoReturnDestroy = this.selected();
                sendUpdatePacket();
            }
        };
        addRenderableWidget(autoReturnDestroyCheckbox);

        // --- deck selector + per-deck "enabled" and "redstone on start" ---
        addRenderableWidget(Button.builder(Component.translatable("aas.spawner.deck", Component.translatable(selectedDeck.classLangKey())), b -> {
            selectedDeck = selectedDeck.next(hasShiftDown() ? -1 : 1);
            menu.blockEntity.setEditDeck(selectedDeck);   // modifier + vehicle inventory of the new deck
            rebuildWidgets();   // init() runs again and loads the fields of the new deck
            sendUpdatePacket(); // tells the server to switch the GUI inventory to this deck as well
        }).bounds(panelX, y + 84, 130, 20).build());

        addRenderableWidget(new Checkbox(panelX, y + 108, 20, 20,
                Component.translatable("aas.spawner.deck_enabled"), c().enabled) {
            @Override
            public void onPress() {
                super.onPress();
                c().enabled = this.selected();
                sendUpdatePacket();
            }
        });

        addRenderableWidget(new Checkbox(panelX, y + 132, 20, 20,
                Component.translatable("aas.spawner.redstone"), c().redstoneOnStart) {
            @Override
            public void onPress() {
                super.onPress();
                c().redstoneOnStart = this.selected();
                sendUpdatePacket();
            }
        });
    }

    // РћР±СЂР°Р±РѕС‚РєР° СЂСѓС‡РЅРѕРіРѕ РІРІРѕРґР° С‚РµРєСЃС‚Р°
    private void onTimeFieldChanged(String value, boolean isInitial) {
        if (value.isEmpty()) return;
        try {
            int time = Integer.parseInt(value);
            if (time < 0) time = 0;

            if (isInitial) {
                c().initialTime = time;
            } else {
                c().respawnTime = time;
            }
            sendUpdatePacket();
        } catch (NumberFormatException e) {}
    }

    private void adjustTimer(boolean isInitial, int change) {
        if (isInitial) {
            int newVal = Math.max(0, c().initialTime + change);
            c().initialTime = newVal;
            initialTimeField.setValue(String.valueOf(newVal));
        } else {
            int newVal = Math.max(0, c().respawnTime + change);
            c().respawnTime = newVal;
            respawnTimeField.setValue(String.valueOf(newVal));
        }
        sendUpdatePacket();
    }

    // === РђР’РўРћ-Р’РћР—Р’Р РђРў: РѕР±СЂР°Р±РѕС‚РєР° С‚Р°Р№РјРµСЂР° ===
    private void onAutoReturnTimeChanged(String value) {
        if (value.isEmpty()) return;
        try {
            int time = Integer.parseInt(value);
            if (time < 0) time = 0;
            c().autoReturnTime = time;
            sendUpdatePacket();
        } catch (NumberFormatException e) {}
    }

    private void adjustAutoReturnTimer(int change) {
        int newVal = Math.max(0, c().autoReturnTime + change);
        c().autoReturnTime = newVal;
        autoReturnTimeField.setValue(String.valueOf(newVal));
        sendUpdatePacket();
    }

    private void sendUpdatePacket() {
        if (c().vehicleId == null) c().vehicleId = "";

        PacketHandler.INSTANCE.sendToServer(new PacketUpdateSpawner(
                menu.blockEntity.getBlockPos(), selectedDeck.ordinal(), c().enabled, c().redstoneOnStart,
                c().respawnTime,
                c().initialTime,
                vehicleIdField.getValue(),
                c().yaw,
                c().autoReturnEnabled,
                c().autoReturnTime,
                c().autoReturnDestroy
        ));
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        renderBackground(gui);
        super.render(gui, mouseX, mouseY, partialTick);
        renderTooltip(gui, mouseX, mouseY);

        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        gui.drawString(font, "ID:", x + 10, y + 23, 0xAAAAAA, false);
        gui.drawCenteredString(font, "Mod", x + 23, y + 35, 0x55FFFF);

        int txtX = x + 155;
        gui.drawCenteredString(font, "Respawn (s)", txtX, y + 8, 0xAAAAAA);
        gui.drawCenteredString(font, "Initial (s)", txtX, y + 38, 0xAAAAAA);

        gui.drawString(font, "Items (32)", x + 26, y + 64, 0x55FF55, false);

        // РџРѕРґРїРёСЃРё РґР»СЏ РЅРѕРІРѕР№ РєРѕР»РѕРЅРєРё Р°РІС‚Рѕ-РІРѕР·РІСЂР°С‚Р°
        int panelX = x + imageWidth + 8;
        gui.drawString(font, "Time (s)", panelX, y + 24, 0xAAAAAA, false);
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        gui.drawString(font, "Yaw:", x + 10, y + 43, 0xAAAAAA, false);
        gui.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight);
        gui.blit(TEXTURE, x + 14, y + 44, 79, 19, 18, 18);

        RenderSystem.disableBlend();
    }

    private void adjustYaw(float amount) {
        float current = 0;
        try { current = Float.parseFloat(yawField.getValue()); } catch (Exception e) {}
        float next = (current + amount) % 360;
        if (next < 0) next += 360;
        yawField.setValue(String.valueOf((int)next));
    }
}
