package top.gregtao.concerto.screen;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import top.gregtao.concerto.enums.OrderType;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.player.MusicPlayer;
import top.gregtao.concerto.player.MusicPlayerStatus;
import top.gregtao.concerto.screen.widget.ConcertoListWidget;
import top.gregtao.concerto.screen.widget.GeneralPlaylistWidget;

public class GeneralPlaylistScreen extends ConcertoScreen {
    private final GeneralPlaylistWidget widget;

    public GeneralPlaylistScreen(Screen parent) {
        super(Text.translatable("concerto.screen.general_list"), parent);
        this.widget = new GeneralPlaylistWidget(this.width, 0, 18, this.height - 35, 18);
        this.widget.setRenderHorizontalShadows(false);
        this.widget.setRenderBackground(false);
    }

    @Override
    protected void init() {
        super.init();
        this.addSelectableChild(this.widget);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.next"), button -> {
            MusicPlayer.INSTANCE.playNext(1, this.widget::reset);
            if (!MusicPlayer.INSTANCE.started) MusicPlayer.INSTANCE.start();
        }).position(this.width / 2 - 160, this.height - 30).size(64, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.play"), button -> {
            ConcertoListWidget<Music>.Entry entry = this.widget.getSelectedOrNull();
            if (entry != null) {
                MusicPlayer.INSTANCE.skipTo(entry.index);
                if (!MusicPlayer.INSTANCE.started) MusicPlayer.INSTANCE.start();
            }
        }).position(this.width / 2 - 96, this.height - 30).size(64, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.delete"), button -> {
            ConcertoListWidget<Music>.Entry entry = this.widget.getSelectedOrNull();
            if (entry != null) {
                MusicPlayer.INSTANCE.remove(entry.index, this.widget::reset);
            }
        }).position(this.width / 2 - 32, this.height - 30).size(64, 20).build());

        this.addDrawableChild(CyclingButtonWidget.builder(OrderType::getName).values(OrderType.values())
                .initially(MusicPlayerStatus.INSTANCE.getOrderType()).build(
                        this.width / 2 + 32, this.height - 30, 64, 20, Text.translatable("concerto.screen.order"),
                        (widget, orderType) -> MusicPlayerStatus.INSTANCE.setOrderType(orderType)));

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.pause"), button -> {
            if (MusicPlayer.INSTANCE.started) {
                if (MusicPlayer.INSTANCE.forcePaused) MusicPlayer.INSTANCE.forceResume();
                else MusicPlayer.INSTANCE.forcePause();
            }
        }).position(this.width / 2 + 96, this.height - 30).size(64, 20).build());
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        this.widget.render(matrices, mouseX, mouseY, delta);
    }
}
