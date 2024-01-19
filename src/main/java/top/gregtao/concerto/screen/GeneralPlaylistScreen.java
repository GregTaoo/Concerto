package top.gregtao.concerto.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import top.gregtao.concerto.enums.OrderType;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.player.MusicPlayer;
import top.gregtao.concerto.player.MusicPlayerHandler;
import top.gregtao.concerto.screen.widget.ConcertoListWidget;
import top.gregtao.concerto.screen.widget.GeneralPlaylistWidget;

public class GeneralPlaylistScreen extends ConcertoScreen {
    private GeneralPlaylistWidget widget;
    protected TextFieldWidget searchBox;

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
        this.widget = new GeneralPlaylistWidget(this.width, this.height, 40, this.height - 35, 18);
        this.widget.setRenderHorizontalShadows(false);
        this.widget.setRenderBackground(false);

        this.addSelectableChild(this.widget);

        this.searchBox = new TextFieldWidget(this.textRenderer, this.width / 2 - 185, 18, 300, 18,
                this.searchBox, Text.translatable("concerto.screen.search"));
        this.addSelectableChild(this.searchBox);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.search"), button ->
                this.toggleSearch()).position(this.width / 2 + 125, 17).size(50, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.next"), button -> {
            if (!MusicPlayer.INSTANCE.started) MusicPlayer.INSTANCE.start();
            else MusicPlayer.INSTANCE.playNext(1, index -> {
                this.widget.reset();
                this.widget.setSelected(index);
            });
        }).position(this.width / 2 - 185, this.height - 30).size(50, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.play"), button -> {
            ConcertoListWidget<Music>.Entry entry = this.widget.getSelectedOrNull();
            if (entry != null) {
                MusicPlayer.INSTANCE.skipTo(entry.index);
            }
        }).position(this.width / 2 - 135, this.height - 30).size(50, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.delete"), button -> {
            ConcertoListWidget<Music>.Entry entry = this.widget.getSelectedOrNull();
            if (entry != null) {
                MusicPlayer.INSTANCE.remove(entry.index, () -> this.widget.removeEntryWithoutScrolling(entry));
            }
        }).position(this.width / 2 - 85, this.height - 30).size(50, 20).build());

        this.addDrawableChild(CyclingButtonWidget.builder(OrderType::getName).values(OrderType.values())
                .initially(MusicPlayerHandler.INSTANCE.getOrderType()).build(
                        this.width / 2 - 35, this.height - 30, 60, 20, Text.translatable("concerto.screen.order"),
                        (widget, orderType) -> MusicPlayerHandler.INSTANCE.setOrderType(orderType)));

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.pause"), button -> {
            if (MusicPlayer.INSTANCE.started) {
                if (MusicPlayer.INSTANCE.forcePaused) MusicPlayer.INSTANCE.forceResume();
                else MusicPlayer.INSTANCE.forcePause();
            }
        }).position(this.width / 2 + 25, this.height - 30).size(50, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.info"), button -> {
            ConcertoListWidget<Music>.Entry entry = this.widget.getSelectedOrNull();
            if (entry != null) {
                MinecraftClient.getInstance().setScreen(new MusicInfoScreen(entry.item, this));
            }
        }).position(this.width / 2 + 75, this.height - 30).size(50, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.clear"), button -> {
            MusicPlayer.INSTANCE.clear();
            MinecraftClient.getInstance().setScreen(null);
        }).position(this.width / 2 + 125, this.height - 30).size(50, 20).build());
    }

    @Override
    public void render(DrawContext matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        this.searchBox.render(matrices, mouseX, mouseY, delta);
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
}
