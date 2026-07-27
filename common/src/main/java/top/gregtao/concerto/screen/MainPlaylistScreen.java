package top.gregtao.concerto.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import top.gregtao.concerto.core.enums.OrderType;
import top.gregtao.concerto.core.event.ConcertoEvents;
import top.gregtao.concerto.core.event.Event;
import top.gregtao.concerto.core.player.MusicPlayerHandler;
import top.gregtao.concerto.core.player.PlayerPermissions;
import top.gregtao.concerto.screen.widget.ConcertoListWidget;
import top.gregtao.concerto.screen.widget.MainPlaylistWidget;
import top.gregtao.concerto.screen.widget.VolumeControlWidget;

public class MainPlaylistScreen extends ApplyDraggedFileScreen {
    private static final int ICON_BUTTON_W = 20;
    private static final int TEXT_BUTTON_W = 58;
    private static final int ORDER_BUTTON_W = 80;
    private static final int BUTTON_GAP = 2;

    private MainPlaylistWidget widget;
    protected EditBox searchBox;
    private Button previousButton;
    private Button nextButton;
    private Button deleteButton;
    private Button pauseButton;
    private Button infoButton;
    private Button clearButton;
    private CycleButton<OrderType> orderButton;
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
        this.widget = new MainPlaylistWidget(this.width, this.height - 75, 40, 18);

        // Registered before the playlist widget so the pop-up slider gets clicks
        // in the area where it overlaps the list
        this.addWidget(this.widget);

        this.searchBox = new EditBox(this.font, this.width / 2 - 185, 18, 300, 18,
                this.searchBox, Component.translatable("concerto.screen.search"));
        this.addWidget(this.searchBox);
        this.addRenderableWidget(this.searchBox);

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.search"), button ->
                this.toggleSearch()).pos(this.width / 2 + 125, 17).size(50, 20).build());

        int totalWidth = 4 * ICON_BUTTON_W + 3 * TEXT_BUTTON_W + ORDER_BUTTON_W + 7 * BUTTON_GAP;
        int x = (this.width - totalWidth) / 2;
        int y = this.height - 30;

        this.previousButton = Button.builder(Component.literal("⏮"),
                button -> MusicPlayerHandler.INSTANCE.playPreviousAsync()).pos(x, y).size(ICON_BUTTON_W, 20).build();
        this.addRenderableWidget(this.previousButton);
        x += ICON_BUTTON_W + BUTTON_GAP;

        this.pauseButton = Button.builder(this.pauseLabel(), button -> {
            boolean paused = MusicPlayerHandler.INSTANCE.isPaused();
            MusicPlayerHandler.INSTANCE.tryForcePause(!paused);
        }).pos(x, y).size(ICON_BUTTON_W, 20).build();
        this.addRenderableWidget(this.pauseButton);
        x += ICON_BUTTON_W + BUTTON_GAP;

        this.nextButton = Button.builder(Component.literal("⏭"),
                button -> MusicPlayerHandler.INSTANCE.playNextAsync(1)).pos(x, y).size(ICON_BUTTON_W, 20).build();
        this.addRenderableWidget(this.nextButton);
        x += ICON_BUTTON_W + BUTTON_GAP;

        this.orderButton = CycleButton.builder((OrderType orderType) -> Component.literal(orderType.getName())).withValues(OrderType.values())
                .withInitialValue(MusicPlayerHandler.INSTANCE.getOrderType()).create(
                        x, y, ORDER_BUTTON_W, 20, Component.translatable("concerto.screen.order"),
                        (widget, orderType) -> MusicPlayerHandler.INSTANCE.setOrderType(orderType));
        this.addRenderableWidget(this.orderButton);
        x += ORDER_BUTTON_W + BUTTON_GAP;

        this.infoButton = Button.builder(Component.translatable("concerto.screen.info"), button -> {
            ConcertoListWidget<MainPlaylistWidget.Entry>.Entry entry = this.widget.getSelected();
            if (entry != null) {
                Minecraft.getInstance().setScreen(new MusicInfoScreen(entry.item.music(), this));
            }
        }).pos(x, y).size(TEXT_BUTTON_W, 20).build();
        this.addRenderableWidget(this.infoButton);
        x += TEXT_BUTTON_W + BUTTON_GAP;

        this.deleteButton = Button.builder(Component.translatable("concerto.screen.delete"), button -> {
            ConcertoListWidget<MainPlaylistWidget.Entry>.Entry entry = this.widget.getSelected();
            if (entry != null) {
                MusicPlayerHandler.INSTANCE.removeAsync(entry.item.index(), () -> {
                });
            }
        }).pos(x, y).size(TEXT_BUTTON_W, 20).build();
        this.addRenderableWidget(this.deleteButton);
        x += TEXT_BUTTON_W + BUTTON_GAP;

        this.clearButton = Button.builder(Component.translatable("concerto.screen.clear"), button -> {
            MusicPlayerHandler.INSTANCE.clear();
            Minecraft.getInstance().setScreen(null);
        }).pos(x, y).size(TEXT_BUTTON_W, 20).build();
        this.addRenderableWidget(this.clearButton);
        x += TEXT_BUTTON_W + BUTTON_GAP;

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
        this.deleteButton.active = PlayerPermissions.canModifyMusicList();
        this.orderButton.active = PlayerPermissions.canChangeOrderType();
        this.pauseButton.active = PlayerPermissions.canControlPlayback();
        this.clearButton.active = PlayerPermissions.canModifyMusicList();
        this.pauseButton.setMessage(this.pauseLabel());
    }

    private Component pauseLabel() {
        return Component.literal(MusicPlayerHandler.INSTANCE.isPaused() ? "▶" : "⏸");
    }

    @Override
    public void render(GuiGraphics matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        this.updateButtonStates();
        this.widget.render(matrices, mouseX, mouseY, delta);
        this.volumeControl.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.volumeControl.collapseIfClickedOutside(mouseX, mouseY);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER && this.searchBox.isHoveredOrFocused()) {
            this.toggleSearch();
            return true;
        }
        return this.searchBox.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return this.searchBox.charTyped(chr, modifiers);
    }

    @Override
    public void onClose() {
        super.onClose();
        Event.unsubscribe(this.listSubscription);
        Event.unsubscribe(this.musicSubscription);
        Event.unsubscribe(this.orderSubscription);
    }
}
