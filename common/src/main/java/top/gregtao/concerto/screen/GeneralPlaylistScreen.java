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
import top.gregtao.concerto.screen.widget.GeneralPlaylistWidget;
import top.gregtao.concerto.screen.widget.VolumeButton;
import top.gregtao.concerto.screen.widget.VolumeSliderWidget;

public class GeneralPlaylistScreen extends ApplyDraggedFileScreen {
    private GeneralPlaylistWidget widget;
    protected EditBox searchBox;
    private Button nextButton;
    private Button playButton;
    private Button deleteButton;
    private Button pauseButton;
    private Button clearButton;
    private CycleButton<OrderType> orderButton;
    private VolumeButton volumeButton;
    private VolumeSliderWidget volumeSlider;
    private boolean volumeSliderVisible = false;
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

        int buttonWidth = 46;
        int gap = 2;
        int x = this.width / 2 - 185;
        int y = this.height - 30;

        this.nextButton = Button.builder(Component.translatable("concerto.screen.next"),
                button -> MusicPlayerHandler.INSTANCE.playNextAsync(1)).pos(x, y).size(buttonWidth, 20).build();
        this.addRenderableWidget(this.nextButton);
        x += buttonWidth + gap;

        this.playButton = Button.builder(Component.translatable("concerto.screen.play"), button -> {
            ConcertoListWidget<GeneralPlaylistWidget.Entry>.Entry entry = this.widget.getSelected();
            if (entry != null) {
                MusicPlayerHandler.INSTANCE.setCurrentIndex(entry.item.index());
            }
        }).pos(x, y).size(buttonWidth, 20).build();
        this.addRenderableWidget(this.playButton);
        x += buttonWidth + gap;

        this.deleteButton = Button.builder(Component.translatable("concerto.screen.delete"), button -> {
            ConcertoListWidget<GeneralPlaylistWidget.Entry>.Entry entry = this.widget.getSelected();
            if (entry != null) {
                MusicPlayerHandler.INSTANCE.removeAsync(entry.item.index(), () -> {
                });
            }
        }).pos(x, y).size(buttonWidth, 20).build();
        this.addRenderableWidget(this.deleteButton);
        x += buttonWidth + gap;

        this.orderButton = CycleButton.builder((OrderType orderType) -> Component.literal(orderType.getName())).withValues(OrderType.values())
                .withInitialValue(MusicPlayerHandler.INSTANCE.getOrderType()).create(
                        x, y, buttonWidth, 20, Component.translatable("concerto.screen.order"),
                        (widget, orderType) -> MusicPlayerHandler.INSTANCE.setOrderType(orderType));
        this.addRenderableWidget(this.orderButton);
        x += buttonWidth + gap;

        this.pauseButton = Button.builder(Component.translatable("concerto.screen.pause"), button -> {
            boolean paused = MusicPlayerHandler.INSTANCE.isPaused();
            MusicPlayerHandler.INSTANCE.tryForcePause(!paused);
        }).pos(x, y).size(buttonWidth, 20).build();
        this.addRenderableWidget(this.pauseButton);
        x += buttonWidth + gap;

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.info"), button -> {
            ConcertoListWidget<GeneralPlaylistWidget.Entry>.Entry entry = this.widget.getSelected();
            if (entry != null) {
                Minecraft.getInstance().setScreen(new MusicInfoScreen(entry.item.music(), this));
            }
        }).pos(x, y).size(buttonWidth, 20).build());
        x += buttonWidth + gap;

        this.clearButton = Button.builder(Component.translatable("concerto.screen.clear"), button -> {
            MusicPlayerHandler.INSTANCE.clear();
            Minecraft.getInstance().setScreen(null);
        }).pos(x, y).size(buttonWidth, 20).build();
        this.addRenderableWidget(this.clearButton);
        x += buttonWidth + gap;

        this.volumeButton = new VolumeButton(
                x, y, 20, 20,
                button -> this.setVolumeSliderVisible(!this.volumeSliderVisible)
        );
        this.volumeSlider = new VolumeSliderWidget(this.font, x, y - 86, 20, 84);
        this.volumeSlider.visible = false;

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

    private void setVolumeSliderVisible(boolean visible) {
        this.volumeSliderVisible = visible;
        this.volumeSlider.visible = visible;
    }

    @Override
    public void render(GuiGraphics matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        this.widget.render(matrices, mouseX, mouseY, delta);
        this.volumeButton.render(matrices, mouseX, mouseY, delta);
        if (this.volumeSlider.visible) {
            this.volumeSlider.render(matrices, mouseX, mouseY, delta);
        }
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.volumeSlider.visible && this.volumeSlider.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (this.volumeButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (this.volumeSliderVisible && !this.volumeSlider.isMouseOver(mouseX, mouseY) &&
                !this.volumeButton.isMouseOver(mouseX, mouseY)) {
            this.setVolumeSliderVisible(false);
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.volumeSlider.visible && this.volumeSlider.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.volumeSlider.visible && this.volumeSlider.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
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
