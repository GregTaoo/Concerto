package top.gregtao.concerto.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import top.gregtao.concerto.core.enums.OrderType;
import top.gregtao.concerto.core.event.ConcertoEvents;
import top.gregtao.concerto.core.event.Event;
import top.gregtao.concerto.core.player.MusicPlayerHandler;
import top.gregtao.concerto.core.player.PlayerPermissions;
import top.gregtao.concerto.core.room.MusicRoom;
import top.gregtao.concerto.network.room.ServerMusicAgentManager;
import top.gregtao.concerto.screen.widget.ConcertoListWidget;
import top.gregtao.concerto.screen.widget.MainPlaylistWidget;
import top.gregtao.concerto.screen.widget.VolumeControlWidget;

public class MainPlaylistScreen extends ApplyDraggedFileScreen {
    private static final int ICON_BUTTON_W = 20;
    private static final int SEARCH_BUTTON_W = 50;

    private MainPlaylistWidget widget;
    protected EditBox searchBox;
    private Button previousButton;
    private Button nextButton;
    private Button pauseButton;
    private Button infoButton;
    private Button requestButton;
    private Button clearButton;
    private CycleButton<@NotNull OrderType> orderButton;
    private VolumeControlWidget volumeControl;
    private Event.Subscription listSubscription, musicSubscription, orderSubscription;

    public MainPlaylistScreen(Screen parent) {
        super(Component.translatable("concerto.screen.main_list"), parent);
    }

    public void toggleSearch() {
        if (!this.searchBox.getValue().isEmpty()) {
            this.widget.reset(this.searchBox.getValue());
        } else {
            this.widget.reset();
        }
    }

    @Override
    protected void init() {
        super.init();
        this.widget = new MainPlaylistWidget(this.width, this.height - 77, 42, 18, this::openTitleEditor);

        // Registered before the playlist widget so the pop-up slider gets clicks
        // in the area where it overlaps the list
        this.addWidget(this.widget);

        int searchX = this.standardContentX();
        int searchButtonX = this.standardContentRight() - SEARCH_BUTTON_W;
        this.searchBox = new EditBox(this.font, searchX, 17, searchButtonX - searchX - STANDARD_ACTION_GAP, 20,
                this.searchBox, Component.translatable("concerto.screen.search"));
        this.addWidget(this.searchBox);
        this.addRenderableWidget(this.searchBox);
        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.search"), button ->
                this.toggleSearch()).pos(searchButtonX, 17).size(SEARCH_BUTTON_W, 20).build());

        int y = this.standardBottomActionY();
        int x = this.standardContentX();
        int wideButtonW = (this.standardContentWidth() - ICON_BUTTON_W * 4 - STANDARD_ACTION_GAP * 7) / 4;

        this.previousButton = Button.builder(Component.literal("⏮"),
                button -> MusicPlayerHandler.INSTANCE.playPreviousAsync()).pos(x, y).size(ICON_BUTTON_W, 20).build();
        this.addRenderableWidget(this.previousButton);
        x += ICON_BUTTON_W + STANDARD_ACTION_GAP;

        this.pauseButton = Button.builder(this.pauseLabel(), button -> {
            boolean paused = MusicPlayerHandler.INSTANCE.isPaused();
            MusicPlayerHandler.INSTANCE.tryForcePause(!paused);
        }).pos(x, y).size(ICON_BUTTON_W, 20).build();
        this.addRenderableWidget(this.pauseButton);
        x += ICON_BUTTON_W + STANDARD_ACTION_GAP;

        this.nextButton = Button.builder(Component.literal("⏭"),
                button -> MusicPlayerHandler.INSTANCE.playNextAsync(1)).pos(x, y).size(ICON_BUTTON_W, 20).build();
        this.addRenderableWidget(this.nextButton);
        x += ICON_BUTTON_W + STANDARD_ACTION_GAP;

        this.requestButton = Button.builder(Component.translatable("concerto.screen.request"), button -> {
            ConcertoListWidget<MainPlaylistWidget.Entry>.Entry entry = this.widget.getSelected();
            if (entry != null) {
                ServerMusicAgentManager.clientAddMusic(entry.item.music());
            }
        }).pos(x, y).size(wideButtonW, 20).build();
        this.addRenderableWidget(this.requestButton);
        x += wideButtonW + STANDARD_ACTION_GAP;

        this.orderButton = CycleButton.builder((orderType) -> Component.literal(orderType.getName()), MusicPlayerHandler.INSTANCE.getOrderType())
                .withValues(OrderType.values())
                .create(
                        x, y, wideButtonW, 20, Component.translatable("concerto.screen.order"),
                        (widget, orderType) -> MusicPlayerHandler.INSTANCE.setOrderType(orderType));
        this.addRenderableWidget(this.orderButton);
        x += wideButtonW + STANDARD_ACTION_GAP;

        this.infoButton = Button.builder(Component.translatable("concerto.screen.info"), button -> {
            ConcertoListWidget<MainPlaylistWidget.Entry>.Entry entry = this.widget.getSelected();
            if (entry != null) {
                Minecraft.getInstance().setScreen(new MusicInfoScreen(entry.item.music(), this));
            }
        }).pos(x, y).size(wideButtonW, 20).build();
        this.addRenderableWidget(this.infoButton);
        x += wideButtonW + STANDARD_ACTION_GAP;
        int clearButtonW = this.standardContentRight() - x - STANDARD_ACTION_GAP - ICON_BUTTON_W;

        this.clearButton = Button.builder(Component.translatable("concerto.screen.clear"), button -> {
            MusicPlayerHandler.INSTANCE.clear();
            Minecraft.getInstance().setScreen(null);
        }).pos(x, y).size(clearButtonW, 20).build();
        this.addRenderableWidget(this.clearButton);
        x += clearButtonW + STANDARD_ACTION_GAP;

        this.volumeControl = new VolumeControlWidget(this.font, x, y, ICON_BUTTON_W, 20);
        this.addWidget(this.volumeControl);

        this.listSubscription = ConcertoEvents.ON_MUSIC_LIST_UPDATE.subscribe(this::toggleSearch);
        this.musicSubscription = ConcertoEvents.ON_NEW_MUSIC_STARTED.subscribe(
                music -> this.widget.setSelected(MusicPlayerHandler.INSTANCE.getCurrentIndex()));
        this.orderSubscription = ConcertoEvents.ON_PLAYER_ORDER_UPDATE.subscribe(
                orderType -> this.orderButton.setValue(orderType));

        this.updateButtonStates();
    }

    private void updateButtonStates() {
        this.previousButton.active = PlayerPermissions.canChangeMusicIndex()
                && MusicPlayerHandler.INSTANCE.canPlayPrevious();
        this.nextButton.active = PlayerPermissions.canChangeMusicIndex()
                && !MusicPlayerHandler.INSTANCE.isEmpty();
        this.orderButton.active = PlayerPermissions.canChangeOrderType();
        this.pauseButton.active = PlayerPermissions.canControlPlayback();
        this.clearButton.active = PlayerPermissions.canModifyMusicList();
        this.infoButton.active = this.widget.getSelected() != null;
        this.requestButton.active = MusicRoom.clientGetState() == MusicRoom.ClientState.MUSIC_AGENT
                && this.widget.getSelected() != null;
        this.pauseButton.setMessage(this.pauseLabel());
    }

    private Component pauseLabel() {
        return Component.literal(MusicPlayerHandler.INSTANCE.isPaused() ? "▶" : "⏸");
    }

    private void openTitleEditor(MainPlaylistWidget.Entry entry) {
        Minecraft.getInstance().setScreen(new EditMusicTitleScreen(entry.music().getMeta().title(), title -> {
            if (MusicPlayerHandler.INSTANCE.renameMusic(entry.index(), title)) {
                this.toggleSearch();
            }
        }, this));
    }

    @Override
    public void render(GuiGraphics matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        this.updateButtonStates();
        this.widget.render(matrices, mouseX, mouseY, delta);
        this.volumeControl.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent event, boolean doubled) {
        this.volumeControl.collapseIfClickedOutside(event.x(), event.y());
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean keyPressed(@NotNull KeyEvent event) {
        if (super.keyPressed(event)) {
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ENTER && this.searchBox.isHoveredOrFocused()) {
            this.toggleSearch();
            return true;
        }
        return this.searchBox.keyPressed(event);
    }

    @Override
    public boolean charTyped(@NotNull CharacterEvent event) {
        return this.searchBox.charTyped(event);
    }

    @Override
    public void onClose() {
        super.onClose();
        Event.unsubscribe(this.listSubscription);
        Event.unsubscribe(this.musicSubscription);
        Event.unsubscribe(this.orderSubscription);
    }
}
