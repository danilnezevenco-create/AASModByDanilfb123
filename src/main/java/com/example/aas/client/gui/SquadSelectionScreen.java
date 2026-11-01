package com.example.aas.client.gui;

import com.example.aas.client.ClientData;
import com.example.aas.client.art.ArtMapUi;
import com.example.aas.client.gui.*;
import com.example.aas.client.gui.AASMapRenderer;
import com.example.aas.network.*;
import com.example.aas.network.MapPlayerInfo;
import com.example.aas.world.AASWorldData;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import com.example.aas.client.KitRankIcons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;
import net.minecraft.core.BlockPos;
import java.util.*;
import java.util.stream.Collectors;
import com.example.aas.client.KitIconStyle;
import com.example.aas.client.OwnVoiceIndicator;

public class SquadSelectionScreen extends Screen {
    private SquadButton applyCmdButton;
    private static final int SIDEBAR_WIDTH = 195;
    private static final int TOP_BAR_HEIGHT = 30;

    private static final ResourceLocation LOCK_ICON = new ResourceLocation("aas", "textures/gui/squad_lock.png");
    private static final ResourceLocation ARROW_DOWN = new ResourceLocation("aas", "textures/gui/arrow_down.png");
    private static final ResourceLocation ARROW_UP = new ResourceLocation("aas", "textures/gui/arrow_up.png");
    private static final ResourceLocation CENTER_ICON = new ResourceLocation("minecraft", "textures/item/compass_16.png");
    private static final ResourceLocation CIRCLE_BADGE = new ResourceLocation("aas", "textures/gui/map_icons/player_circle.png");
    private static final ResourceLocation ICON_DEAD      = new ResourceLocation("aas", "textures/gui/stats/deaths.png");
    private static final ResourceLocation ICON_HEARTBEAT = new ResourceLocation("aas", "textures/gui/heartbeat.png");
    private static final ResourceLocation ICON_DISCONNECT = new ResourceLocation("aas", "textures/gui/disconnect.png");

    private static final ResourceLocation FLAG_UKRAINE = new ResourceLocation("aas", "textures/gui/flags/ukraine.png");
    private static final ResourceLocation FLAG_RUSSIA = new ResourceLocation("aas", "textures/gui/flags/russia.png");
    private static final ResourceLocation FLAG_USA = new ResourceLocation("aas", "textures/gui/flags/usa.png");
    private static final ResourceLocation FLAG_NATO = new ResourceLocation("aas", "textures/gui/flags/nato.png");
    private static final ResourceLocation FLAG_BLUEFOR = new ResourceLocation("aas", "textures/gui/flags/bluefor.png");
    private static final ResourceLocation FLAG_REDFOR = new ResourceLocation("aas", "textures/gui/flags/redfor.png");
    private static final ResourceLocation FLAG_INSURGENCY = new ResourceLocation("aas", "textures/gui/flags/insurgency.png");
    private static final ResourceLocation FLAG_PMC = new ResourceLocation("aas", "textures/gui/flags/pmc.png");
    private static final ResourceLocation FLAG_GERMANY = new ResourceLocation("aas", "textures/gui/flags/germany.png");
    private static final ResourceLocation FLAG_MILITIA = new ResourceLocation("aas", "textures/gui/flags/militia.png");

    private EditBox nameInput;
    private Button createButton;
    private EditBox chatInput;
    private Button chatModeButton;
    private int chatMode = 1;
    private boolean showContextMenu = false;
    private int contextMenuX = 0;
    private int contextMenuY = 0;
    private String contextTargetPlayer = "";
    private int contextTargetSquadId = -1;
    private boolean contextTargetIsSquad = false;

    private int mapX, mapY, mapSize;

    private final Set<Integer> expandedSquads = new HashSet<>();
    private int lastOwnSquadId = -1; // отслеживаем свой отряд, чтобы авторазворачивать его при создании/входе без переоткрытия экрана
    private final AASMapRenderer mapRenderer = new AASMapRenderer();
    private final MarkerPanelOverlay markerPanel = new MarkerPanelOverlay();
    private final VehicleListPanel vehiclePanel = new VehicleListPanel();
    /** Shared artillery + UAV map UI (same component as on the death screen). */
    private final ArtMapUi artUi = new ArtMapUi(true, 0);
    public SquadSelectionScreen() {
        super(Component.literal("Squad Selection"));
    }

    @Override
    protected void init() {
        super.init();
        String myName = this.minecraft.player.getScoreboardName();
        String myTeam = getPlayerTeam().toUpperCase();
        boolean isInSquad = isPlayerInSquad();

        // Объединяем поиск своего отряда и расширение списка
        AASWorldData.Squad mySquad = null;
        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.members.contains(myName)) {
                expandedSquads.add(s.id);
                if (s.leader.equals(myName)) mySquad = s;
                lastOwnSquadId = s.id; // добавить эту строку
            }
        }

        int teamCMDId_init = myTeam.contains("BLUE") ? ClientData.blueCMDId : ClientData.redCMDId;
        this.applyCmdButton = new SquadButton(10, 10, SIDEBAR_WIDTH - 20, 20, Component.translatable("aas.gui.squad.apply_cmd"), b -> {
            PacketHandler.INSTANCE.sendToServer(new PacketRequestCMD());
            b.visible = false;
        });
        this.addRenderableWidget(this.applyCmdButton);

        // Поля ввода (nameInput и createButton)
        nameInput = new EditBox(this.font, 10, this.height - 55, SIDEBAR_WIDTH - 20, 20, Component.literal("Squad Name"));
        nameInput.setMaxLength(12);
        nameInput.setVisible(!isInSquad);
        this.addRenderableWidget(nameInput);

        createButton = this.addRenderableWidget(new SquadButton(10, this.height - 30, SIDEBAR_WIDTH - 20, 20, Component.translatable("aas.gui.squad.create"), button -> {
            PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(0, 0, nameInput.getValue()));
            this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(com.example.aas.sound.ModSounds.SQUAD_JOIN.get(), 1.0F));
        }));
        createButton.visible = !isInSquad;

        // Карта
        int rightAreaWidth = this.width - SIDEBAR_WIDTH;
        int mapMargin = 2;
        int availableHeight = this.height - TOP_BAR_HEIGHT - 50;
        this.mapSize = Math.min(rightAreaWidth - (mapMargin * 2), availableHeight);
        this.mapX = this.width - mapSize - mapMargin;
        this.mapY = TOP_BAR_HEIGHT + mapMargin;
        mapRenderer.init(this.mapX, this.mapY, this.mapSize);

        // Чат
        int inputY = this.height - 25;
        int chatX = SIDEBAR_WIDTH + 5;
        int chatWidth = this.width - SIDEBAR_WIDTH - 10;
        chatModeButton = this.addRenderableWidget(new SquadButton(chatX, inputY, 50, 20, getChatModeText(), button -> {
            chatMode++;
            if (chatMode > 2) chatMode = 0;
            button.setMessage(getChatModeText());
        }));

        chatInput = new EditBox(this.font, chatX + 55, inputY, chatWidth - 55, 20, Component.literal("Chat"));
        chatInput.setMaxLength(256);
        this.addRenderableWidget(chatInput);
    }

    private Component getChatModeText() {
        switch (chatMode) {
            case 0: return Component.translatable("aas.gui.chat.all").withStyle(ChatFormatting.LIGHT_PURPLE);
            case 1: return Component.translatable("aas.gui.chat.team").withStyle(ChatFormatting.BLUE);
            case 2: return Component.translatable("aas.gui.chat.squad").withStyle(ChatFormatting.GREEN);
            default: return Component.literal("???");
        }
    }


    @Override
    public void tick() {
        super.tick();
        nameInput.tick();
        chatInput.tick();
        if (this.applyCmdButton != null) {
            String myName = this.minecraft.player.getScoreboardName();
            String myTeam = getPlayerTeam().toUpperCase();

            AASWorldData.Squad mySquad = null;
            for (AASWorldData.Squad s : ClientData.clientSquads) {
                if (s.members.contains(myName)) {
                    mySquad = s;
                    break;
                }
            }

            // 2. Определяем данные КОНКРЕТНО ДЛЯ МОЕЙ команды
            boolean isBlue = myTeam.contains("BLUE");
            int myTeamCMDId = isBlue ? ClientData.blueCMDId : ClientData.redCMDId;
            boolean myTeamVoteActive = isBlue ? ClientData.blueCmdVoteActive : ClientData.redCmdVoteActive;

            // 3. Кнопка видна если:
            // - Я в отряде
            // - Я лидер своего отряда
            // - В моей команде еще нет Командира
            // - В моей команде прямо сейчас не идет голосование
            boolean isLeader = (mySquad != null && mySquad.leader.equals(myName));

            this.applyCmdButton.visible = (isLeader && myTeamCMDId == -1 && !myTeamVoteActive);
        }
        boolean isInSquad = isPlayerInSquad();
        if (nameInput.isVisible() == isInSquad) {
            nameInput.setVisible(!isInSquad);
            createButton.visible = !isInSquad;
        }
        // Авторазворачивание строки отряда при создании/входе, пока экран открыт
        String myNameTick = this.minecraft.player.getScoreboardName();
        AASWorldData.Squad currentOwnSquad = null;
        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.members.contains(myNameTick)) {
                currentOwnSquad = s;
                break;
            }
        }
        int currentOwnSquadId = currentOwnSquad != null ? currentOwnSquad.id : -1;
        if (currentOwnSquadId != lastOwnSquadId) {
            if (currentOwnSquadId != -1) {
                expandedSquads.add(currentOwnSquadId);
            }
            lastOwnSquadId = currentOwnSquadId;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // UAV / artillery selection or panel: Esc leaves it without closing the screen
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && artUi.cancelSelection()) return true;
        if (vehiclePanel.handleEscapePressed(keyCode)) return true;
        if (markerPanel.handleEscapePressed(keyCode)) return true;   // <-- ВСТАВИТЬ ЭТУ СТРОКУ

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (chatInput.isFocused()) {
                String msg = chatInput.getValue().trim();
                if (!msg.isEmpty()) {
                    PacketHandler.INSTANCE.sendToServer(new PacketSquadChat(msg, chatMode));
                    chatInput.setValue("");
                }
                return true;
            }
            if (nameInput.isFocused() && nameInput.isVisible()) {
                String name = nameInput.getValue();
                PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(0, 0, name));
                this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(com.example.aas.sound.ModSounds.SQUAD_JOIN.get(), 1.0F));
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private List<String> getSortedMembers(AASWorldData.Squad squad) {
        List<String> sorted = new ArrayList<>();
        // 1. Сквад лидер
        if (!squad.leader.isEmpty() && squad.members.contains(squad.leader)) sorted.add(squad.leader);

        // 2. Alpha (не в фаертиме и не СЛ)
        for (String m : squad.members) {
            if (!m.equals(squad.leader) && !squad.bravoMembers.contains(m) && !squad.charlieMembers.contains(m)) {
                sorted.add(m);
            }
        }

        // 3. Bravo FTL
        if (!squad.bravoLeader.isEmpty() && squad.members.contains(squad.bravoLeader)) sorted.add(squad.bravoLeader);
        // 4. Bravo Members
        for (String m : squad.bravoMembers) {
            if (!m.equals(squad.bravoLeader) && squad.members.contains(m)) sorted.add(m);
        }

        // 5. Charlie FTL
        if (!squad.charlieLeader.isEmpty() && squad.members.contains(squad.charlieLeader)) sorted.add(squad.charlieLeader);
        // 6. Charlie Members
        for (String m : squad.charlieMembers) {
            if (!m.equals(squad.charlieLeader) && squad.members.contains(m)) sorted.add(m);
        }

        return sorted;
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        RankPopup.beginFrame(this);
        boolean isDowned = this.minecraft.player != null && this.minecraft.player.getPersistentData().getBoolean("AAS_IsDowned");

        // 1. Фон (если в ноке - красный, если нет - обычный)
        if (isDowned) {
            // Отрисовка красной виньетки/фона
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            gui.fill(0, 0, this.width, this.height, 0x50FF0000);
            RenderSystem.enableDepthTest();
        }

        // Обычные панели
        gui.fill(0, 0, SIDEBAR_WIDTH, this.height, 0xCC000000);
        gui.fill(SIDEBAR_WIDTH, 0, this.width, TOP_BAR_HEIGHT, 0xCC000000);
        gui.fill(SIDEBAR_WIDTH, TOP_BAR_HEIGHT, this.width, this.height, 0x95000000);

        renderSquadList(gui, mouseX, mouseY);
        mapRenderer.render(gui, mouseX, mouseY, partialTick);
        {
            double uavPanX = mapRenderer.getCenterX(this.minecraft.player), uavPanZ = mapRenderer.getCenterZ(this.minecraft.player);
            double uavBpp = mapRenderer.getBlocksPerPixel();
            artUi.render(gui, this.font, mouseX, mouseY, mapX, mapY, mapSize, uavPanX, uavPanZ, uavBpp);
        }
        renderChatHistory(gui, mapY, mapSize);
        renderTopBar(gui);

        super.render(gui, mouseX, mouseY, partialTick);

        if (showContextMenu) {
            renderContextMenu(gui, mouseX, mouseY);
        }

        // Контекстное меню удаления метки (серый квадратик с красной иконкой удаления под меткой)
        mapRenderer.renderMarkerDeleteMenu(gui, mouseX, mouseY);

        // 2. Таймер (рисуем ПОВЕРХ всего в самом конце)
        if (isDowned) {
            renderDownedTimerHUD(gui);
        }

        markerPanel.render(gui, mouseX, mouseY);
        vehiclePanel.render(gui, mouseX, mouseY);
        RankPopup.render(gui, this.font, this.width, this.height);
        com.example.aas.client.uav.UavNoticeOverlay.renderPlate(gui);   // the map covers the HUD: draw the CMD notice here
        artUi.renderTooltip(gui, this.font, mouseX, mouseY);

        // кто говорит (другие игроки): поверх меню, на высоком слое (z=600). Только рисуется, клики проходят насквозь
        OwnVoiceIndicator.renderOthers(gui, this.font, 5, this.height / 2 - 40);

        // свой индикатор голоса: у самого левого края, последним и на высоком слое (z=600)
        OwnVoiceIndicator.render(gui, this.font, 5, this.height - 70, SIDEBAR_WIDTH - 10);
    }

    private void renderDownedTimerHUD(GuiGraphics gui) {
        int maxSeconds = com.example.aas.config.AASConfig.MAX_DOWNED_TIME_SECONDS.get();
        long startTime = ClientData.globalDeathTimestamp;
        if (startTime == 0) return;

        long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
        long remaining = maxSeconds - elapsedSeconds;
        if (remaining < 0) remaining = 0;

        String text = I18n.get("aas.gui.squad.bleeding_out", remaining);
        int tw = this.font.width(text);
        int cx = this.width / 2;

        // Сдвинул на y=35, чтобы было ПОД полоской тикетов (TOP_BAR_HEIGHT = 30)
        int yPos = 35;

        gui.fill(cx - (tw/2) - 5, yPos, cx + (tw/2) + 5, yPos + 12, 0xAA000000);
        gui.renderOutline(cx - (tw/2) - 5, yPos, tw + 10, 12, 0xFFFF5555);
        gui.drawCenteredString(this.font, text, cx, yPos + 2, 0xFFFF5555);
    }
    @Override
    public void onClose() {
        if (this.minecraft.player != null && this.minecraft.player.getPersistentData().getBoolean("AAS_IsDowned")) {
            this.minecraft.setScreen(new DownedScreen());
        } else {
            super.onClose();
        }
    }
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (vehiclePanel.mouseScrolled(mouseX, mouseY, delta)) return true;
        if (mapRenderer.mouseScrolled(mouseX, mouseY, delta)) return true;
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    // PATH: src\main\java\com\example\aas\client\gui\SquadSelectionScreen.java
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // UAV / artillery selection takes RMB BEFORE the marker menu (handleMapRightClick) sees it
        if (button == 1 && artUi.isSelecting()) {
            if (artUi.onRightClick(mouseX, mouseY)) return true;
        }
        if (RankPopup.mouseClicked(mouseX, mouseY, button)) return true;
        if (vehiclePanel.mouseClicked(mouseX, mouseY, button)) return true;
        if (markerPanel.mouseClicked(mouseX, mouseY, button)) return true;
        if (artUi.mouseClicked(mouseX, mouseY, button)) return true;

        // --- Меню удаления метки на карте ...
        if (mapRenderer.isMarkerDeleteMenuOpen()) {
            if (mapRenderer.handleMarkerDeleteMenuClick(mouseX, mouseY, button)) return true;
        }

        // --- 0. ЛОГИКА ВСПЛЫВАЮЩЕГО КОНТЕКСТНОГО МЕНЮ ---
        if (showContextMenu) {
            // FIX: раньше любой клик (ЛКМ или ПКМ) безусловно поглощался (return true),
            // даже если он пришёлся мимо меню. Из-за этого ПКМ по ДРУГОМУ отряду/игроку,
            // когда меню уже было открыто, только закрывал старое меню и "съедал" клик,
            // не долетая до блока №3 ниже, который открывает новое меню. Приходилось
            // кликать дважды. Теперь поглощаем клик только если реально нажали пункт меню,
            // либо это был ЛКМ (чтобы не триггерить виджеты под меню). ПКМ мимо меню
            // проваливается в этот же обработчик события и сразу открывает нужное меню.
            boolean menuOptionClicked = false;
            if (button == 0) { // ЛКМ по открытому меню
                String myName = this.minecraft.player.getScoreboardName();
                AASWorldData.Squad s = null;
                for (AASWorldData.Squad sq : ClientData.clientSquads) {
                    if (sq.id == contextTargetSquadId) { s = sq; break; }
                }

                if (s != null) {
                    List<String> options = new ArrayList<>();

                    if (contextTargetIsSquad) {
                        options.add("Disband Squad");
                    } else {
                        boolean amISL = s.leader.equals(myName);
                        boolean amIBravoFTL = s.bravoLeader.equals(myName);
                        boolean amICharlieFTL = s.charlieLeader.equals(myName);

                        if (amISL) {
                            options.add("Promote to SL");
                            if (!s.bravoLeader.equals(contextTargetPlayer) && !s.charlieLeader.equals(contextTargetPlayer)) {
                                options.add("Set FTL Bravo");
                                options.add("Set FTL Charlie");
                            }
                            options.add("Add to Bravo");
                            options.add("Add to Charlie");
                            options.add("Remove from FT");
                            options.add("Kick from Squad");
                        } else if (amIBravoFTL) {
                            if (!s.leader.equals(contextTargetPlayer) && !s.charlieLeader.equals(contextTargetPlayer)) options.add("Pass FTL Bravo");
                            options.add("Add to Bravo");
                            options.add("Remove from FT");
                        } else if (amICharlieFTL) {
                            if (!s.leader.equals(contextTargetPlayer) && !s.bravoLeader.equals(contextTargetPlayer)) options.add("Pass FTL Charlie");
                            options.add("Add to Charlie");
                            options.add("Remove from FT");
                        }
                    }

                    int w = ClientData.squadMenuWidth(this.font, options);
                    int h = options.size() * 12 + 4;

                    // Если кликнули ровно по пункту меню
                    if (mouseX >= contextMenuX && mouseX <= contextMenuX + w && mouseY >= contextMenuY && mouseY <= contextMenuY + h) {
                        int clickedIdx = (int) (mouseY - contextMenuY - 2) / 12;
                        if (clickedIdx >= 0 && clickedIdx < options.size()) {
                            String opt = options.get(clickedIdx);

                            if (contextTargetIsSquad) {
                                if (opt.equals("Disband Squad")) {
                                    PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(11, s.id, ""));
                                }
                            } else {
                                // Отправляем пакет на сервер в зависимости от выбранного пункта
                                if (opt.equals("Promote to SL")) PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(4, s.id, contextTargetPlayer));
                                else if (opt.equals("Set FTL Bravo") || opt.equals("Pass FTL Bravo")) PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(6, s.id, contextTargetPlayer));
                                else if (opt.equals("Set FTL Charlie") || opt.equals("Pass FTL Charlie")) PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(7, s.id, contextTargetPlayer));
                                else if (opt.equals("Add to Bravo")) PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(8, s.id, contextTargetPlayer));
                                else if (opt.equals("Add to Charlie")) PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(9, s.id, contextTargetPlayer));
                                else if (opt.equals("Remove from FT")) PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(10, s.id, contextTargetPlayer));
                                else if (opt.equals("Kick from Squad")) PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(3, s.id, contextTargetPlayer));
                            }

                            playClickSound();
                            menuOptionClicked = true;
                        }
                    }
                }
            }

            showContextMenu = false;
            if (menuOptionClicked || button == 0) {
                return true;
            }
            // ПКМ мимо меню: не возвращаем true здесь — проваливаемся ниже в этом же
            // вызове mouseClicked(), чтобы блок №3 сразу открыл новое меню.
        }

        // --- 1. ПРОВЕРЯЕМ КАРТУ ---
        if (mapRenderer.isMouseOver(mouseX, mouseY)) {
            if (mapRenderer.mouseClicked(mouseX, mouseY, button)) {
                if (button == 1) handleMapRightClick(mouseX, mouseY);
                return true;
            }
        }

        // --- 2. ОБЫЧНЫЕ КЛИКИ (Текстовые поля, Кнопка Create Squad и т.д.) ---
        if (super.mouseClicked(mouseX, mouseY, button)) return true;


        // --- 3. ПРАВЫЙ КЛИК ПО СПИСКУ (ВЫЗОВ КОНТЕКСТНОГО МЕНЮ) ---
        if (button == 1 && mouseX < SIDEBAR_WIDTH && mouseY < (this.height - 60)) {
            String myName = this.minecraft.player.getScoreboardName();
            String myTeam = getPlayerTeam().toUpperCase();
            String myDim = this.minecraft.level.dimension().location().toString();

            // --- ИСПРАВЛЕНИЕ ТУТ ---
            boolean isBlue = myTeam.contains("BLUE");
            int teamCMDId = isBlue ? ClientData.blueCMDId : ClientData.redCMDId;
            boolean myTeamVoteActive = isBlue ? ClientData.blueCmdVoteActive : ClientData.redCmdVoteActive;

            boolean amISL_Anywhere = false;
            for (AASWorldData.Squad s : ClientData.clientSquads) {
                if (s.leader.equals(myName)) { amISL_Anywhere = true; break; }
            }

            // Используем myTeamVoteActive вместо cmdVoteActive
            int currentY = (amISL_Anywhere && teamCMDId == -1 && !myTeamVoteActive) ? 35 : 10;
            // ------------------------------------------

            // Проверка: я Командир своей команды?
            // ВАЖНО: считаем через ClientData.clientSquads (синхронизируется PacketSyncSquads),
            // а НЕ через player.getPersistentData() — эти теги выставляются только на сервере
            // и не рассылаются клиенту по сети, поэтому на реальном dedicated-сервере
            // (не singleplayer) проверка всегда была false и меню командира не появлялось.
            AASWorldData.Squad myOwnSquadForCmdCheck = getMySquad(myName);
            boolean amICommander = (isPlayerInSquad() && myOwnSquadForCmdCheck != null
                    && myOwnSquadForCmdCheck.id == teamCMDId
                    && myOwnSquadForCmdCheck.leader.equals(myName));

            List<AASWorldData.Squad> myTeamSquads = ClientData.clientSquads.stream()
                    .filter(s -> s.team.equalsIgnoreCase(myTeam))
                    .filter(s -> s.dimension != null && s.dimension.equals(myDim))
                    .collect(Collectors.toList());

            // Сортировка (чтобы совпадало с отрисовкой)
            myTeamSquads.sort((s1, s2) -> {
                if (s1.id == teamCMDId && teamCMDId != -1) return -1;
                if (s2.id == teamCMDId && teamCMDId != -1) return 1;
                return Integer.compare(s1.id, s2.id);
            });

            for (AASWorldData.Squad squad : myTeamSquads) {
                // 1. ПРОВЕРКА КЛИКА ПО ШАПКЕ ОТРЯДА (Командирская функция)
                if (mouseY >= currentY && mouseY <= currentY + 11) {
                    if (amICommander) {
                        showContextMenu = true;
                        contextTargetIsSquad = true; // Цель - отряд
                        contextTargetSquadId = squad.id;
                        contextMenuX = (int) mouseX;
                        contextMenuY = (int) mouseY;
                        playClickSound();
                        return true;
                    }
                }

                currentY += squad.leader.isEmpty() ? 12 : 21; // Пропускаем заголовок отряда (+ строку лидера)

                if (expandedSquads.contains(squad.id)) {
                    if (squad.members.contains(myName)) {
                        List<String> sortedMembers = getSortedMembers(squad);
                        for (String member : sortedMembers) {
                            // Хитбокс строки игрока: высота строки 12, используем +11
                            // для согласованности с рендером и с AASDeathScreen
                            if (mouseY >= currentY && mouseY <= currentY + 11 && mouseX >= 30) {
                                if (!member.equals(myName)) {
                                    boolean amISL = squad.leader.equals(myName);
                                    boolean amIBravo = squad.bravoLeader.equals(myName);
                                    boolean amICharlie = squad.charlieLeader.equals(myName);

                                    // Проверка прав: Только Лидер или Командиры ФТ могут открывать меню
                                    if (amISL || amIBravo || amICharlie) {
                                        showContextMenu = true;
                                        contextTargetIsSquad = false; // Цель - игрок
                                        contextMenuX = (int) mouseX;
                                        contextMenuY = (int) mouseY;
                                        contextTargetPlayer = member;
                                        contextTargetSquadId = squad.id;
                                        playClickSound();
                                        return true; // Клик обработан
                                    }
                                }
                            }
                            currentY += 12;
                        }
                    } else {
                        currentY += squad.members.size() * 12; // Пропускаем чужой сквад
                    }
                    currentY += 4;
                }
                currentY += 4;
            }
        }

        // --- 4. ЛЕВЫЙ КЛИК ПО СПИСКУ (РАЗВОРАЧИВАНИЕ/ВЫБОР КИТА И Т.Д.) ---
        if (button == 0 && mouseX < SIDEBAR_WIDTH && mouseY < (this.height - 60)) {
            // Вызов вашего старого метода (который мы обновили ранее, удалив оттуда кнопки P/K)
            handleSquadListClicks(mouseX, mouseY);
            return true;
        }

        return false;
    }
    private void handleSquadListClicks(double mouseX, double mouseY) {
        String myName = this.minecraft.player.getScoreboardName();
        String myTeam = getPlayerTeam().toUpperCase();
        String myDim = this.minecraft.level.dimension().location().toString();
        boolean amIInSquad = isPlayerInSquad();

        boolean isBlue = myTeam.contains("BLUE");
        int teamCMDId = isBlue ? ClientData.blueCMDId : ClientData.redCMDId;
        boolean myTeamVoteActive = isBlue ? ClientData.blueCmdVoteActive : ClientData.redCmdVoteActive;

        boolean amISquadLeaderAnywhere = false;
        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.leader.equals(myName)) { amISquadLeaderAnywhere = true; break; }
        }

        int currentY = (amISquadLeaderAnywhere && teamCMDId == -1 && !myTeamVoteActive) ? 35 : 10;

        List<AASWorldData.Squad> myTeamSquads = ClientData.clientSquads.stream()
                .filter(s -> s.team.equalsIgnoreCase(myTeam))
                .filter(s -> s.dimension != null && s.dimension.equals(myDim))
                .collect(Collectors.toList());

        myTeamSquads.sort((s1, s2) -> {
            if (s1.id == teamCMDId && teamCMDId != -1) return -1;
            if (s2.id == teamCMDId && teamCMDId != -1) return 1;
            return Integer.compare(s1.id, s2.id);
        });

        for (AASWorldData.Squad squad : myTeamSquads) {
            boolean isMySquad = squad.members.contains(myName);
            boolean amILeader = squad.leader.equals(myName);
            boolean isExpanded = expandedSquads.contains(squad.id);

            // 1. Рассчитываем ширину текста действия (JOIN/LEAVE), как в рендере
            String actionText = "";
            if (isMySquad) actionText = I18n.get("aas.gui.squad.leave");
            else if (!amIInSquad) {
                if (squad.isLocked) actionText = I18n.get("aas.gui.squad.locked");
                else if (squad.members.size() >= 9) actionText = I18n.get("aas.gui.squad.full");
                else actionText = I18n.get("aas.gui.squad.join");
            }

            int actionWidth = actionText.isEmpty() ? 0 : this.font.width(actionText);
            int actionX = actionWidth > 0 ? (SIDEBAR_WIDTH - actionWidth - 10) : 0;

            // 2. Рассчитываем точные координаты стрелочки и замка (в точности как в renderSquadList)
            int arrowX = (actionX > 0 ? actionX : SIDEBAR_WIDTH - 10) - 12;
            int lockX = arrowX - 12;

            // --- ОБРАБОТКА КЛИКОВ ---

            // Клик по тексту (JOIN/LEAVE)
            if (actionWidth > 0 && mouseX >= actionX && mouseX <= actionX + actionWidth && mouseY >= currentY && mouseY <= currentY + 9) {
                if (isMySquad) {
                    PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(2, squad.id, ""));
                    this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(com.example.aas.sound.ModSounds.SQUAD_LEAVE.get(), 1.0F));
                } else if (!squad.isLocked && squad.members.size() < 9) {
                    PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(1, squad.id, ""));
                    this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(com.example.aas.sound.ModSounds.SQUAD_JOIN.get(), 1.0F));
                }
                playClickSound();
                return;
            }

            // Клик по стрелочке (развернуть/свернуть)
            if (mouseX >= arrowX && mouseX <= arrowX + 10 && mouseY >= currentY && mouseY <= currentY + 10) {
                if (isExpanded) expandedSquads.remove(squad.id);
                else expandedSquads.add(squad.id);
                playClickSound();
                return;
            }

            if (amILeader && mouseX >= lockX && mouseX <= lockX + 10 && mouseY >= currentY && mouseY <= currentY + 10) {
                PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(5, squad.id, ""));
                playClickSound();
                return;
            }

            // ЛКМ по нику лидера под названием отряда: выделить/снять выделение на карте
            if (!squad.leader.isEmpty()
                    && mouseX >= 24 && mouseX <= 24 + this.font.width(squad.leader)
                    && mouseY >= currentY + 9 && mouseY < currentY + 18) {
                ClientData.toggleHighlight(squad.leader);
                playClickSound();
                return;
            }

            // Пропускаем заголовок (+ строку с именем лидера, если она есть)
            currentY += squad.leader.isEmpty() ? 12 : 21;

            // Клики по игрокам внутри (кнопка К)
            if (isExpanded) {
                List<String> sortedMembers = getSortedMembers(squad);
                for (String member : sortedMembers) {
                    if (isMySquad && member.equals(myName)) {
                        // Координата кнопки К (x=30)
                        if (mouseX >= 30 && mouseX <= 40 && mouseY >= currentY && mouseY <= currentY + 10) {
                            PacketHandler.INSTANCE.sendToServer(new PacketRequestKitMenu());
                            playClickSound();
                            return;
                        }
                    }

                    // ЛКМ по нику игрока: выделить/снять выделение на карте
                    if (isOverMemberNick(member, member.equals(myName), mouseX)
                            && mouseY >= currentY && mouseY < currentY + 12) {
                        ClientData.toggleHighlight(member);
                        playClickSound();
                        return;
                    }
                    currentY += 12;
                }
                currentY += 4;
            }
            currentY += 4;
        }
    }

    // Ищет отряд, в котором состоит указанный игрок, среди клиентских данных
    private AASWorldData.Squad getMySquad(String playerName) {
        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.members.contains(playerName)) return s;
        }
        return null;
    }

    private List<AASWorldData.Squad> getSortedSquads() {
        String myTeam = getPlayerTeam();
        String myDim = this.minecraft.level.dimension().location().toString();
        int teamCMDId = myTeam.equalsIgnoreCase("Blue") ? ClientData.blueCMDId : ClientData.redCMDId;

        List<AASWorldData.Squad> list = ClientData.clientSquads.stream()
                .filter(s -> s.team.equalsIgnoreCase(myTeam))
                .filter(s -> s.dimension != null && s.dimension.equals(myDim))
                .collect(Collectors.toList());

        list.sort((s1, s2) -> {
            if (s1.id == teamCMDId && teamCMDId != -1) return -1;
            if (s2.id == teamCMDId && teamCMDId != -1) return 1;
            return Integer.compare(s1.id, s2.id);
        });
        return list;
    }

    private void handleMapRightClick(double mouseX, double mouseY) {
        // ИЗМЕНЕНО: Используем новый метод проверки
        if (!isSquadLeaderOrFTL(this.minecraft.player)) {
            this.minecraft.player.displayClientMessage(Component.translatable("aas.msg.sl_ftl_only").withStyle(ChatFormatting.RED), true);
            return;
        }

        // Если под курсором есть метка, которую игрок вправе удалить - открываем
        // маленькое меню удаления вместо радиального меню размещения новой метки.
        if (mapRenderer.tryOpenMarkerDeleteMenu(mouseX, mouseY)) {
            return;
        }

        // 2. Получаем масштаб и центр из рендерера
        double bpp = mapRenderer.getBlocksPerPixel();
        double centerX = mapRenderer.getCenterX(this.minecraft.player);
        double centerZ = mapRenderer.getCenterZ(this.minecraft.player);

        // 3. Рассчитываем мировые координаты точки клика
        // Используем поля mapX, mapY, mapSize, которые инициализированы в методе init() экрана
        int targetX = (int) (centerX + ((mouseX - (this.mapX + this.mapSize / 2.0)) * bpp));
        int targetZ = (int) (centerZ + ((mouseY - (this.mapY + this.mapSize / 2.0)) * bpp));

        // === ВОТ ТУТ ГЛАВНОЕ ИЗМЕНЕНИЕ ===
        // Вместо SquadMarkerRadialScreen открываем наше новое ГЛАВНОЕ тактическое меню
        markerPanel.openAt(targetX, targetZ, mouseX, mouseY, mapRenderer);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        mapRenderer.mouseReleased(button);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {

        if (mapRenderer.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private boolean isSquadLeaderOrFTL(Player player) {
        String pName = player.getScoreboardName();
        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.leader.equals(pName) || s.bravoLeader.equals(pName) || s.charlieLeader.equals(pName)) {
                return true;
            }
        }
        return false;
    }

    private void renderChatHistory(GuiGraphics gui, int mapY, int mapSize) {
        int chatX = SIDEBAR_WIDTH + 10;
        int chatBottomY = this.height - 35;
        int chatTopY = mapY + mapSize + 10;

        int count = 0;
        for (Component msg : ClientData.menuChatHistory) {
            int y = chatBottomY - (count * 10);
            if (y < chatTopY) break;
            gui.drawString(this.font, msg, chatX, y, 0xFFFFFFFF, true);
            count++;
        }
    }


    // Рисует круглый значок (перекрашенную текстуру player_circle.png) с числом по центру.
    // Используется для номера отряда в духе референса (цветной кружок слева от названия).
    private void drawBadgeCircle(GuiGraphics gui, int x, int y, int size, int colorARGB, String label, boolean cmd) {
        float a = ((colorARGB >> 24) & 0xFF) / 255f;
        float r = ((colorARGB >> 16) & 0xFF) / 255f;
        float g = ((colorARGB >> 8) & 0xFF) / 255f;
        float b = (colorARGB & 0xFF) / 255f;

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(r, g, b, a);
        if (cmd) com.example.aas.client.BadgeCircle.blitStarCmd(gui, x, y, size, a);
        else com.example.aas.client.BadgeCircle.blit(gui, x, y, size);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        if (label != null && !label.isEmpty()) {
            gui.drawCenteredString(this.font, label, x + size / 2, y + (size - 8) / 2, 0xFFFFFFFF);
        }
    }

    private void renderSquadList(GuiGraphics gui, int mouseX, int mouseY) {
        String myName = this.minecraft.player.getScoreboardName();
        String myTeam = getPlayerTeam().toUpperCase();
        String myDim = this.minecraft.level.dimension().location().toString();
        boolean amIInSquad = isPlayerInSquad();

        // --- ИСПРАВЛЕНИЕ ТУТ ---
        boolean isBlue = myTeam.contains("BLUE");
        int teamCMDId = isBlue ? ClientData.blueCMDId : ClientData.redCMDId;
        boolean myTeamVoteActive = isBlue ? ClientData.blueCmdVoteActive : ClientData.redCmdVoteActive;

        boolean amISquadLeaderAnywhere = false;
        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.leader.equals(myName)) { amISquadLeaderAnywhere = true; break; }
        }

        // Кнопка видна только если мы лидер, CMD нет и наше голосование не активно
        int currentY = (amISquadLeaderAnywhere && teamCMDId == -1 && !myTeamVoteActive) ? 35 : 10;

        List<AASWorldData.Squad> myTeamSquads = ClientData.clientSquads.stream()
                .filter(s -> s.team.equalsIgnoreCase(myTeam))
                .filter(s -> s.dimension != null && s.dimension.equals(myDim))
                .collect(Collectors.toList());

        // 5. Сортировка (CMD всегда первый)
        myTeamSquads.sort((s1, s2) -> {
            if (s1.id == teamCMDId && teamCMDId != -1) return -1;
            if (s2.id == teamCMDId && teamCMDId != -1) return 1;
            return Integer.compare(s1.id, s2.id);
        });

        int index = 1;
        for (AASWorldData.Squad squad : myTeamSquads) {
            boolean isMySquad = squad.members.contains(myName);
            boolean amILeader = squad.leader.equals(myName);
            boolean isExpanded = expandedSquads.contains(squad.id);

            // ЛОГИКА CMD
            boolean isCMD = (squad.id == teamCMDId && teamCMDId != -1);
            String prefix = isCMD ? I18n.get("aas.gui.squad.cmd_tag") + " " : "";
            int squadNameColor = isCMD ? 0xFF55FF55 : 0xFFFFD700;

            // Цветной кружок с номером отряда:
            // - ЦМД — свой цвет (как было, зелёный)
            // - твой отряд (не ЦМД) — rgba(37, 150, 190)
            // - остальные (чужие) отряды — синий 0xFF3399FF, как раньше
            int badgeColor;
            if (isCMD) badgeColor = 0xFF4CD964;
            else if (isMySquad) badgeColor = 0xFF00FF00;
            else badgeColor = 0xFF3399FF;
            drawBadgeCircle(gui, 4, currentY - 3, 14, badgeColor, String.valueOf(index), isCMD);

            String squadNameOnly = prefix + squad.name;
            gui.drawString(this.font, squadNameOnly, 24, currentY, squadNameColor, false);
            int nameCursorX = 24 + this.font.width(squadNameOnly) + 5;

            String countText = squad.members.size() + "/9";
            gui.drawString(this.font, countText, nameCursorX, currentY, 0xFFAAAAAA, false);

            // Имя лидера отряда — тусклым цветом, ВТОРОЙ строкой под названием отряда
            boolean hasLeaderLine = !squad.leader.isEmpty();
            if (hasLeaderLine) {
                gui.drawString(this.font, squad.leader, 24, currentY + 9, ClientData.isHighlighted(squad.leader) ? 0xFFFFFFFF : 0xFF999999, false);
            }

            String actionText = "";
            int actionColor = 0xFFFFFFFF;
            boolean clickable = true;

            if (isMySquad) {
                actionText = I18n.get("aas.gui.squad.leave"); actionColor = 0xFFFF5555;
            } else if (!amIInSquad) { // Теперь amIInSquad виден!
                if (squad.isLocked) { actionText = I18n.get("aas.gui.squad.locked"); actionColor = 0xFFFFAA00; clickable = false; }
                else if (squad.members.size() >= 9) { actionText = I18n.get("aas.gui.squad.full"); actionColor = 0xFF888888; clickable = false; }
                else { actionText = I18n.get("aas.gui.squad.join"); actionColor = 0xFF55FF55; }
            }

            int actionX = 0;
            if (!actionText.isEmpty()) {
                int actionWidth = this.font.width(actionText);
                actionX = SIDEBAR_WIDTH - actionWidth - 10;
                boolean hover = mouseX >= actionX && mouseX <= actionX + actionWidth && mouseY >= currentY && mouseY <= currentY + 9;
                int finalColor = (hover && clickable) ? 0xFFFFFFFF : actionColor;
                gui.drawString(this.font, actionText, actionX, currentY, finalColor, false);
            }

            int arrowX = (actionX > 0 ? actionX : SIDEBAR_WIDTH - 10) - 12;
            RenderSystem.enableBlend();
            if (isExpanded) {
                RenderSystem.setShaderColor(1.0f, 0.8f, 0.2f, 1.0f);
                gui.blit(ARROW_DOWN, arrowX, currentY + 1, 0, 0, 8, 8, 8, 8);
            } else {
                RenderSystem.setShaderColor(0.7f, 0.7f, 0.7f, 1.0f);
                gui.blit(ARROW_UP, arrowX, currentY + 1, 0, 0, 8, 8, 8, 8);
            }
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

            if (amILeader || squad.isLocked) {
                int lockX = arrowX - 12;
                if (squad.isLocked) RenderSystem.setShaderColor(1.0f, 0.8f, 0.2f, 1.0f);
                else RenderSystem.setShaderColor(0.6f, 0.6f, 0.6f, 1.0f);
                gui.blit(LOCK_ICON, lockX, currentY + 1, 0, 0, 8, 8, 8, 8);
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            }

            // Если у отряда есть лидер — резервируем дополнительные 9px под его имя
            currentY += hasLeaderLine ? 21 : 12;

            if (isExpanded) {
                List<String> sortedMembers = getSortedMembers(squad);

                // Предварительный проход: РЅР°С…РѕРґРёРј РґРёР°РїР°Р·РѕРЅ СЃС‚СЂРѕРє РєР°Р¶РґРѕРіРѕ С„Р°РµСЂС‚РёРјР° (Bravo/Charlie),
                // чтобы нарисовать ОДНУ сплошную полоску без разрывов на всех его бойцов сразу,
                // вместо отдельного кусочка на каждую строку.
                int membersStartY = currentY;
                int bravoFirstIdx = -1, bravoLastIdx = -1;
                int charlieFirstIdx = -1, charlieLastIdx = -1;
                for (int i = 0; i < sortedMembers.size(); i++) {
                    String m = sortedMembers.get(i);
                    boolean mInBravo = m.equals(squad.bravoLeader) || squad.bravoMembers.contains(m);
                    boolean mInCharlie = m.equals(squad.charlieLeader) || squad.charlieMembers.contains(m);
                    if (mInBravo) {
                        if (bravoFirstIdx == -1) bravoFirstIdx = i;
                        bravoLastIdx = i;
                    }
                    if (mInCharlie) {
                        if (charlieFirstIdx == -1) charlieFirstIdx = i;
                        charlieLastIdx = i;
                    }
                }
                if (bravoFirstIdx != -1) {
                    int barY1 = membersStartY + bravoFirstIdx * 12;
                    int barY2 = membersStartY + bravoLastIdx * 12 + 10;
                    gui.fill(20, barY1, 21, barY2, 0xFFCC77FF);
                }
                if (charlieFirstIdx != -1) {
                    int barY1 = membersStartY + charlieFirstIdx * 12;
                    int barY2 = membersStartY + charlieLastIdx * 12 + 10;
                    gui.fill(20, barY1, 21, barY2, 0xFF5FE075);
                }

                for (String member : sortedMembers) {
                    boolean isLeaderMember = member.equals(squad.leader);
                    boolean isOnline = this.minecraft.getConnection().getPlayerInfo(member) != null;

                    int col = 0xFFFFFFFF;
                    if (isLeaderMember) col = 0xFFFFD700;
                    else if (member.equals(squad.bravoLeader)) col = 0xFFFF55FF;   // розовый/magenta
                    else if (squad.bravoMembers.contains(member)) col = 0xFFAA00AA; // тёмно-фиолетовый
                    else if (member.equals(squad.charlieLeader)) col = 0xFF55FF55;  // зелёный
                    else if (squad.charlieMembers.contains(member)) col = 0xFF00AA00; // тёмно-зелёный

                    if (!isOnline) col = 0xFFAAAAAA;
                    if (ClientData.isHighlighted(member)) col = 0xFFFFFFFF; // выделен на карте - белый ник

                    // Букву рисуем только у ФТЛ (лидера фаертима): Bravo -> "B", Charlie -> "C".
                    boolean isBravoFTL = member.equals(squad.bravoLeader);
                    boolean isCharlieFTL = member.equals(squad.charlieLeader);
                    if (isBravoFTL) {
                        gui.drawString(this.font, "B", 8, currentY + 1, 0xFFCC77FF, false);
                    } else if (isCharlieFTL) {
                        gui.drawString(this.font, "C", 8, currentY + 1, 0xFF5FE075, false);
                    }

                    int xOffset = 30;

                    if (isMySquad && member.equals(myName)) {
                        int btnX = xOffset;
                        boolean btnHover = mouseX >= btnX && mouseX <= btnX + 10 && mouseY >= currentY && mouseY <= currentY + 10;
                        gui.fill(btnX, currentY, btnX + 10, currentY + 10, btnHover ? 0xFF666666 : 0xFF444444);
                        gui.drawString(this.font, "K", btnX + 2, currentY + 1, 0xFFFFFFFF, false);
                        xOffset += 14;
                    }

                    String kName = ClientData.playerKits.getOrDefault(member, "Unassigned");
                    if (!isOnline) {
                        // Игрок дисконектнулся — рисуем иконку дисконекта (без tint)
                        RenderSystem.enableBlend();
                        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 0.7f);
                        gui.blit(ICON_DISCONNECT, xOffset, currentY, 0, 0, 10, 10, 10, 10);
                        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                        xOffset += 12;
                    } else if (!kName.equals("Unassigned") && !kName.isEmpty()) {
                        ResourceLocation kitIcon = new ResourceLocation("aas", "textures/gui/kits/" + kName.toLowerCase().replace(" ", "_") + ".png");
                        KitIconStyle.apply(KitIconStyle.DEFAULT_COLOR, 1.0f);
                        gui.blit(kitIcon, xOffset, currentY, 0, 0, 10, 10, 10, 10);
                        KitIconStyle.reset();
                        xOffset += 12;

                        int rankLvl = KitRankIcons.levelOf(member);
                        if (rankLvl > 0) {
                            KitRankIcons.draw(gui, rankLvl, xOffset, currentY);
                            RankPopup.registerIcon(gui, member, xOffset, currentY, 10, mouseX, mouseY);
                            xOffset += KitRankIcons.ROW_ADVANCE;
                        }
                    }

                    gui.drawString(this.font, member, xOffset, currentY + 1, col, false);

                    // Череп / нок — в самом правом месте сайдбара, так же как в SquadPanelOverlay
                    MapPlayerInfo memberInfo = ClientData.mapPlayers.get(member);
                    boolean memberIsDead   = memberInfo != null && memberInfo.isDead;
                    boolean memberIsDowned = memberInfo != null && memberInfo.isDowned;

                    if (memberIsDead || memberIsDowned) {
                        RenderSystem.enableBlend();
                        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                        int statusIconX = SIDEBAR_WIDTH - 16;
                        int statusIconY = currentY;
                        if (memberIsDead) {
                            gui.blit(ICON_DEAD, statusIconX, statusIconY, 8, 8, 0f, 0f, 10, 10, 10, 10);
                        } else {
                            RenderSystem.setShaderColor(0.831f, 0.204f, 0.208f, 1f); // #D43435
                            gui.blit(ICON_HEARTBEAT, statusIconX, statusIconY, 8, 8, 0f, 0f, 36, 36, 36, 36);
                        }
                        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                    }

                    currentY += 12;
                }
                currentY += 4;
            }
            currentY += 4;
            index++;
        }
    }

    private void renderContextMenu(GuiGraphics gui, int mx, int my) {
        String myName = this.minecraft.player.getScoreboardName();
        AASWorldData.Squad s = null;
        for(AASWorldData.Squad sq : ClientData.clientSquads) { if(sq.id == contextTargetSquadId) s = sq; }
        if (s == null) { showContextMenu = false; return; }

        List<String> options = new ArrayList<>();

        if (contextTargetIsSquad) {
            // Меню командира на шапке отряда
            options.add("Disband Squad");
        } else {
            boolean amISL = s.leader.equals(myName);
            boolean amIBravoFTL = s.bravoLeader.equals(myName);
            boolean amICharlieFTL = s.charlieLeader.equals(myName);

            if (amISL) {
                options.add("Promote to SL");
                if (!s.bravoLeader.equals(contextTargetPlayer) && !s.charlieLeader.equals(contextTargetPlayer)) {
                    options.add("Set FTL Bravo");
                    options.add("Set FTL Charlie");
                }
                options.add("Add to Bravo");
                options.add("Add to Charlie");
                options.add("Remove from FT");
                options.add("Kick from Squad");
            } else if (amIBravoFTL) {
                if (!s.leader.equals(contextTargetPlayer) && !s.charlieLeader.equals(contextTargetPlayer)) {
                    options.add("Pass FTL Bravo");
                }
                options.add("Add to Bravo");
                options.add("Remove from FT");
            } else if (amICharlieFTL) {
                if (!s.leader.equals(contextTargetPlayer) && !s.bravoLeader.equals(contextTargetPlayer)) {
                    options.add("Pass FTL Charlie");
                }
                options.add("Add to Charlie");
                options.add("Remove from FT");
            }
        }

        if (options.isEmpty()) { showContextMenu = false; return; }

        int w = ClientData.squadMenuWidth(this.font, options);
        int h = options.size() * 12 + 4;
        gui.fill(contextMenuX, contextMenuY, contextMenuX + w, contextMenuY + h, 0xEE111111);
        gui.renderOutline(contextMenuX, contextMenuY, w, h, 0xFF555555);

        for (int i = 0; i < options.size(); i++) {
            int y = contextMenuY + 2 + (i * 12);
            boolean hover = mx >= contextMenuX && mx <= contextMenuX + w && my >= y && my < y + 12;
            if (hover) gui.fill(contextMenuX + 1, y, contextMenuX + w - 1, y + 12, 0xFF444444);
            gui.drawString(this.font, ClientData.squadMenuOption(options.get(i)), contextMenuX + 4, y + 2, hover ? 0xFFFFFF : 0xAAAAAA, false);
        }
    }

    private void renderTopBar(GuiGraphics gui) {
        String myName = this.minecraft.player.getScoreboardName();
        String myTeam = getPlayerTeam();
        String teamUpper = myTeam.toUpperCase();

        // 1. Находим свой отряд
        AASWorldData.Squad mySquad = null;
        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.members.contains(myName)) {
                mySquad = s;
                break;
            }
        }

        int tickets = 0;
        ResourceLocation flag = null;

        // 2. Используем teamUpper для выбора тикетов и флага
        if (teamUpper.contains("BLUE")) {
            tickets = ClientData.BLUE_TICKETS;
            flag = getFlagTexture(ClientData.BLUE_FACTION);
        } else if (teamUpper.contains("RED")) {
            tickets = ClientData.RED_TICKETS;
            flag = getFlagTexture(ClientData.RED_FACTION);
        }

        // 3. ЛОГИКА КНОПКИ CMD
        boolean isBlue = teamUpper.contains("BLUE");
        int myTeamCMDId = isBlue ? ClientData.blueCMDId : ClientData.redCMDId;
        boolean myTeamVoteActive = isBlue ? ClientData.blueCmdVoteActive : ClientData.redCmdVoteActive;

        boolean isLeader = (mySquad != null && mySquad.leader.equals(myName));
        this.applyCmdButton.visible = (isLeader && myTeamCMDId == -1 && !myTeamVoteActive);

        // === 4. ЦЕНТРИРОВАНИЕ ПО ЭКРАНУ ===
        String ticketText = String.valueOf(tickets);
        int textWidth = this.font.width(ticketText);
        int iconSize = 12;
        int gap = 5;
        int flagWidth = 32;
        int flagHeight = 18;

        // Считаем общую ширину всего блока (флаг + иконка + текст + отступы)
        int totalContentWidth = textWidth + gap + iconSize;
        if (flag != null) {
            totalContentWidth += flagWidth + gap;
        }

        // ИСПРАВЛЕНИЕ: Теперь берем центр ВСЕГО экрана (this.width / 2)
        int topBarCenterX = this.width / 2;
        int currentX = topBarCenterX - (totalContentWidth / 2);

        // Отрисовка флага
        if (flag != null) {
            gui.pose().pushPose();
            gui.pose().translate(0, 0, 100);
            int flagY = (TOP_BAR_HEIGHT - flagHeight) / 2;
            gui.blit(flag, currentX, flagY, 0, 0, flagWidth, flagHeight, flagWidth, flagHeight);
            gui.pose().popPose();

            currentX += flagWidth + gap; // Сдвигаем курсор вправо
        }

        // Отрисовка иконки билета
        ResourceLocation ticketIcon = new ResourceLocation("aas", "textures/gui/minimap_tickets.png");
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        int iconY = (TOP_BAR_HEIGHT - iconSize) / 2;
        gui.blit(ticketIcon, currentX, iconY, iconSize, iconSize, 0, 0, 16, 16, 16, 16);

        currentX += iconSize + gap; // Сдвигаем курсор вправо

        // Отрисовка текста
        int textY = (TOP_BAR_HEIGHT - 8) / 2 + 1; // Центрируем шрифт по вертикали
        gui.drawString(this.font, ticketText, currentX, textY, 0xFFFFFFFF, false);
    }

    // Попал ли курсор по нику игрока (вместе с иконкой кита) в строке списка отряда.
    // Отступы такие же, как в renderSquadList: у себя слева кнопка K (30..40), ник начинается с 44.
    private boolean isOverMemberNick(String member, boolean isSelf, double mouseX) {
        int start = isSelf ? 44 : 30;
        int end = start;
        String kName = ClientData.playerKits.getOrDefault(member, "Unassigned");
        if (!kName.equals("Unassigned") && !kName.isEmpty()) {
            end += 12; // иконка кита
            if (KitRankIcons.levelOf(member) > 0) end += KitRankIcons.ROW_ADVANCE; // иконка ранга
        }
        end += this.font.width(member);
        return mouseX >= start && mouseX <= end;
    }

    private void playClickSound() {
        this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private boolean isPlayerInSquad() {
        String myName = this.minecraft.player.getScoreboardName();
        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.members.contains(myName)) return true;
        }
        return false;
    }
    private static class SquadButton extends Button {
        public SquadButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible) return;

            // Стиль: яркая рамка при наведении
            int borderColor = this.isHovered() ? 0xFFFFFFFF : 0xFF999999;

            // Если кнопка выключена (уже нажата или нельзя нажать)
            if (!this.active) borderColor = 0xFF444444;

            // Фон
            gui.fill(getX(), getY(), getX() + width, getY() + height, 0xCC111111);
            // Окантовка
            gui.renderOutline(getX(), getY(), width, height, borderColor);

            int textColor = this.active ? 0xFFFFFFFF : 0xFF777777;

            // Текст по центру
            gui.drawCenteredString(Minecraft.getInstance().font, this.getMessage(), getX() + width / 2, getY() + (height - 8) / 2, textColor);

            // Маленький белый уголок снизу при наведении
            if (this.active && this.isHovered()) {
                gui.fill(getX(), getY() + height - 2, getX() + 2, getY() + height, 0xFFFFFFFF);
            }
        }
    }
    private String getPlayerTeam() {
        if (this.minecraft.player.getTeam() != null) {
            return this.minecraft.player.getTeam().getName();
        }
        return "NEUTRAL";
    }

    private ResourceLocation getFlagTexture(String faction) {
        if (faction == null || faction.equalsIgnoreCase("none")) return null;
        switch (faction.toLowerCase()) {
            case "ukraine": return FLAG_UKRAINE;
            case "russia": return FLAG_RUSSIA;
            case "usa": return FLAG_USA;
            case "nato": return FLAG_NATO;
            case "bluefor": return FLAG_BLUEFOR;
            case "redfor": return FLAG_REDFOR;
            case "insurgency": return FLAG_INSURGENCY;
            case "pmc": return FLAG_PMC;
            case "germany": return FLAG_GERMANY;
            case "militia": return FLAG_MILITIA;
            default: return null;
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
