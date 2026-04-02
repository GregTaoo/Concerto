package top.gregtao.concerto.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
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
    protected TextFieldWidget searchBox;
    private ButtonWidget nextButton;
    private ButtonWidget playButton;
    private ButtonWidget deleteButton;
    private ButtonWidget pauseButton;
    private ButtonWidget clearButton;
    private CyclingButtonWidget<OrderType> orderButton;
    private Event.Subscription listSubscription, musicSubscription, orderSubscription;

    public GeneralPlaylistScreen(Screen parent) {
        super(Text.translatable("concerto.screen.general_list"), parent);
    }

    public void toggleSearch() {
        if (!this.searchBox.getText().isEmpty()) {
            this.widget.reset(this.searchBox.getText());
        } else {
            this.widget.reset();
        }
    }

    @Override
    protected void init() {
        super.init();
        this.widget = new GeneralPlaylistWidget(this.width, this.height - 75, 40, 18);

        this.addSelectableChild(this.widget);

        this.searchBox = new TextFieldWidget(this.textRenderer, this.width / 2 - 185, 18, 300, 18,
                this.searchBox, Text.translatable("concerto.screen.search"));
        this.addSelectableChild(this.searchBox);
        this.addDrawableChild(this.searchBox);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.search"), button ->
                this.toggleSearch()).position(this.width / 2 + 125, 17).size(50, 20).build());

        this.nextButton = ButtonWidget.builder(Text.translatable("concerto.screen.next"),
                button -> MusicPlayerHandler.INSTANCE.playNextAsync(1)).position(this.width / 2 - 185, this.height - 30).size(50, 20).build();
        this.addDrawableChild(this.nextButton);

        this.playButton = ButtonWidget.builder(Text.translatable("concerto.screen.play"), button -> {
            ConcertoListWidget<GeneralPlaylistWidget.Entry>.Entry entry = this.widget.getSelectedOrNull();
            if (entry != null) {
                MusicPlayerHandler.INSTANCE.setCurrentIndex(entry.item.index());
            }
        }).position(this.width / 2 - 135, this.height - 30).size(50, 20).build();
        this.addDrawableChild(this.playButton);

        this.deleteButton = ButtonWidget.builder(Text.translatable("concerto.screen.delete"), button -> {
            ConcertoListWidget<GeneralPlaylistWidget.Entry>.Entry entry = this.widget.getSelectedOrNull();
            if (entry != null) {
                MusicPlayerHandler.INSTANCE.removeAsync(entry.item.index(), () -> {});
            }
        }).position(this.width / 2 - 85, this.height - 30).size(50, 20).build();
        this.addDrawableChild(this.deleteButton);

        this.orderButton = CyclingButtonWidget.builder((OrderType x) -> Text.literal(x.getName())).values(OrderType.values())
                .initially(MusicPlayerHandler.INSTANCE.getOrderType()).build(
                        this.width / 2 - 35, this.height - 30, 60, 20, Text.translatable("concerto.screen.order"),
                        (widget, orderType) -> MusicPlayerHandler.INSTANCE.setOrderType(orderType));
        this.addDrawableChild(this.orderButton);

        this.pauseButton = ButtonWidget.builder(Text.translatable("concerto.screen.pause"), button -> {
            boolean paused = MusicPlayerHandler.INSTANCE.isPaused();
            MusicPlayerHandler.INSTANCE.tryForcePause(!paused);
        }).position(this.width / 2 + 25, this.height - 30).size(50, 20).build();
        this.addDrawableChild(this.pauseButton);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.info"), button -> {
            ConcertoListWidget<GeneralPlaylistWidget.Entry>.Entry entry = this.widget.getSelectedOrNull();
            if (entry != null) {
                MinecraftClient.getInstance().setScreen(new MusicInfoScreen(entry.item.music(), this));
            }
        }).position(this.width / 2 + 75, this.height - 30).size(50, 20).build());

        this.clearButton = ButtonWidget.builder(Text.translatable("concerto.screen.clear"), button -> {
            MusicPlayerHandler.INSTANCE.clear();
            MinecraftClient.getInstance().setScreen(null);
        }).position(this.width / 2 + 125, this.height - 30).size(50, 20).build();
        this.addDrawableChild(this.clearButton);

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
    public void render(DrawContext matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        this.widget.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER && this.searchBox.isSelected()) {
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
    public void close() {
        super.close();
        Event.unsubscribe(this.listSubscription);
        Event.unsubscribe(this.musicSubscription);
        Event.unsubscribe(this.orderSubscription);
    }
}
