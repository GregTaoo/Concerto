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
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import top.gregtao.concerto.core.enums.OrderType;
import top.gregtao.concerto.core.event.ConcertoEvents;
import top.gregtao.concerto.core.event.Event;
import top.gregtao.concerto.core.player.MusicPlayerHandler;
import top.gregtao.concerto.core.player.PlayerPermissions;
import top.gregtao.concerto.screen.widget.ConcertoListWidget;
import top.gregtao.concerto.screen.widget.GeneralPlaylistWidget;

public class GeneralPlaylistScreen extends ApplyDraggedFileScreen {
    private GeneralPlaylistWidget widget;
    protected EditBox searchBox;
    private Button nextButton;
    private Button playButton;
    private Button deleteButton;
    private Button pauseButton;
    private Button clearButton;
    private CycleButton<@NotNull OrderType> orderButton;
    private Event.Subscription listSubscription, musicSubscription, orderSubscription;

    public GeneralPlaylistScreen(Screen parent) {
        super(Component.translatable("concerto.screen.general_list"), parent);
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
        this.widget = new GeneralPlaylistWidget(this.width, this.height - 75, 40, 18);

        this.addWidget(this.widget);

        this.searchBox = new EditBox(this.font, this.width / 2 - 185, 18, 300, 18,
                this.searchBox, Component.translatable("concerto.screen.search"));
        this.addWidget(this.searchBox);
        this.addRenderableWidget(this.searchBox);

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.search"), button ->
                this.toggleSearch()).pos(this.width / 2 + 125, 17).size(50, 20).build());

        this.nextButton = Button.builder(Component.translatable("concerto.screen.next"),
                button -> MusicPlayerHandler.INSTANCE.playNextAsync(1)).pos(this.width / 2 - 185, this.height - 30).size(50, 20).build();
        this.addRenderableWidget(this.nextButton);

        this.playButton = Button.builder(Component.translatable("concerto.screen.play"), button -> {
            ConcertoListWidget<GeneralPlaylistWidget.Entry>.Entry entry = this.widget.getSelected();
            if (entry != null) {
                MusicPlayerHandler.INSTANCE.setCurrentIndex(entry.item.index());
            }
        }).pos(this.width / 2 - 135, this.height - 30).size(50, 20).build();
        this.addRenderableWidget(this.playButton);

        this.deleteButton = Button.builder(Component.translatable("concerto.screen.delete"), button -> {
            ConcertoListWidget<GeneralPlaylistWidget.Entry>.Entry entry = this.widget.getSelected();
            if (entry != null) {
                MusicPlayerHandler.INSTANCE.removeAsync(entry.item.index(), () -> {
                });
            }
        }).pos(this.width / 2 - 85, this.height - 30).size(50, 20).build();
        this.addRenderableWidget(this.deleteButton);

        this.orderButton = CycleButton.builder((orderType) -> Component.literal(orderType.getName()), MusicPlayerHandler.INSTANCE.getOrderType())
                .withValues(OrderType.values())
                .create(
                        this.width / 2 - 35, this.height - 30, 60, 20, Component.translatable("concerto.screen.order"),
                        (widget, orderType) -> MusicPlayerHandler.INSTANCE.setOrderType(orderType));
        this.addRenderableWidget(this.orderButton);

        this.pauseButton = Button.builder(Component.translatable("concerto.screen.pause"), button -> {
            boolean paused = MusicPlayerHandler.INSTANCE.isPaused();
            MusicPlayerHandler.INSTANCE.tryForcePause(!paused);
        }).pos(this.width / 2 + 25, this.height - 30).size(50, 20).build();
        this.addRenderableWidget(this.pauseButton);

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.info"), button -> {
            ConcertoListWidget<GeneralPlaylistWidget.Entry>.Entry entry = this.widget.getSelected();
            if (entry != null) {
                Minecraft.getInstance().setScreen(new MusicInfoScreen(entry.item.music(), this));
            }
        }).pos(this.width / 2 + 75, this.height - 30).size(50, 20).build());

        this.clearButton = Button.builder(Component.translatable("concerto.screen.clear"), button -> {
            MusicPlayerHandler.INSTANCE.clear();
            Minecraft.getInstance().setScreen(null);
        }).pos(this.width / 2 + 125, this.height - 30).size(50, 20).build();
        this.addRenderableWidget(this.clearButton);

        this.listSubscription = ConcertoEvents.ON_MUSIC_LIST_UPDATE.subscribe(this::toggleSearch);
        this.musicSubscription = ConcertoEvents.ON_NEW_MUSIC_STARTED.subscribe(
                music -> this.widget.setSelected(MusicPlayerHandler.INSTANCE.getCurrentIndex()));
        this.orderSubscription = ConcertoEvents.ON_PLAYER_ORDER_UPDATE.subscribe(
                orderType -> this.orderButton.setValue(orderType));

        this.updateButtonStates();
    }

    private void updateButtonStates() {
        this.nextButton.active = PlayerPermissions.canChangeMusicIndex();
        this.playButton.active = PlayerPermissions.canChangeMusicIndex();
        this.deleteButton.active = PlayerPermissions.canModifyMusicList();
        this.orderButton.active = PlayerPermissions.canChangeOrderType();
        this.pauseButton.active = PlayerPermissions.canControlPlayback();
        this.clearButton.active = PlayerPermissions.canModifyMusicList();
    }

    @Override
    public void render(GuiGraphics matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        this.widget.render(matrices, mouseX, mouseY, delta);
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
