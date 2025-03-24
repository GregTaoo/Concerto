package top.gregtao.concerto.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.TranslatableText;
import org.lwjgl.glfw.GLFW;
import top.gregtao.concerto.enums.OrderType;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.player.MusicPlayer;
import top.gregtao.concerto.player.MusicPlayerHandler;
import top.gregtao.concerto.screen.widget.ConcertoListWidget;
import top.gregtao.concerto.screen.widget.CyclingButtonWidget;
import top.gregtao.concerto.screen.widget.GeneralPlaylistWidget;

public class GeneralPlaylistScreen extends ApplyDraggedFileScreen {
    private GeneralPlaylistWidget widget;
    protected TextFieldWidget searchBox;

    public GeneralPlaylistScreen(Screen parent) {
        super(new TranslatableText("concerto.screen.general_list"), parent);
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
        this.setRenderBg(false);

        this.widget = new GeneralPlaylistWidget(this.width, this.height, 40, this.height - 35, 18);
        this.addChild(this.widget);

        this.searchBox = new TextFieldWidget(this.textRenderer, this.width / 2 - 185, 18, 300, 18,
                this.searchBox, new TranslatableText("concerto.screen.search"));
        this.addButton(this.searchBox);

        this.addButton(new ButtonWidget(this.width / 2 + 125, 17, 50, 20,
                new TranslatableText("concerto.screen.search"), button -> this.toggleSearch()));

        this.addButton(new ButtonWidget(this.width / 2 - 185, this.height - 30, 50, 20,
                new TranslatableText("concerto.screen.next"), button -> {
            if (!MusicPlayer.INSTANCE.started) MusicPlayer.INSTANCE.start();
            else if (!MusicPlayer.INSTANCE.playNextLock) MusicPlayer.INSTANCE.playNext(1, index -> {
                this.widget.reset();
                this.widget.setSelected(index);
            });
        }));

        this.addButton(new ButtonWidget(this.width / 2 - 135, this.height - 30, 50, 20,
                new TranslatableText("concerto.screen.play"), button -> {
            ConcertoListWidget<Music>.Entry entry = this.widget.getSelected();
            if (entry != null) {
                MusicPlayer.INSTANCE.skipTo(entry.index);
            } else if (!MusicPlayer.INSTANCE.started) {
                MusicPlayer.INSTANCE.start();
            }
        }));

        this.addButton(new ButtonWidget(this.width / 2 - 85, this.height - 30, 50, 20,
                new TranslatableText("concerto.screen.delete"), button -> {
            ConcertoListWidget<Music>.Entry entry = this.widget.getSelected();
            if (entry != null) {
                MusicPlayer.INSTANCE.remove(entry.index, () -> this.widget.removeEntryWithoutScrolling(entry));
            }
        }));

        this.addButton(CyclingButtonWidget.builder(OrderType::getName).values(OrderType.values())
                .initially(MusicPlayerHandler.INSTANCE.getOrderType()).build(
                        this.width / 2 - 35, this.height - 30, 60, 20, new TranslatableText("concerto.screen.order"),
                        (widget, orderType) -> MusicPlayerHandler.INSTANCE.setOrderType(orderType)));

        this.addButton(new ButtonWidget(this.width / 2 + 25, this.height - 30, 50, 20,
                new TranslatableText("concerto.screen.pause"), button -> {
            if (MusicPlayer.INSTANCE.started) {
                if (MusicPlayer.INSTANCE.forcePaused) MusicPlayer.INSTANCE.forceResume();
                else MusicPlayer.INSTANCE.forcePause();
            }
        }));

        this.addButton(new ButtonWidget(this.width / 2 + 75, this.height - 30, 50, 20,
                new TranslatableText("concerto.screen.info"), button -> {
            ConcertoListWidget<Music>.Entry entry = this.widget.getSelected();
            if (entry != null) {
                MinecraftClient.getInstance().openScreen(new MusicInfoScreen(entry.item, this));
            }
        }));

        this.addButton(new ButtonWidget(this.width / 2 + 125, this.height - 30, 50, 20,
                new TranslatableText("concerto.screen.clear"), button -> {
            MusicPlayer.INSTANCE.clear();
            MinecraftClient.getInstance().openScreen(null);
        }));
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.widget.render(matrices, mouseX, mouseY, delta);
        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER && this.searchBox.isActive()) {
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
