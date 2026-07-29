package top.gregtao.concerto.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import top.gregtao.concerto.core.music.Music;
import top.gregtao.concerto.core.util.Pair;
import top.gregtao.concerto.network.ClientMusicNetworkHandler;
import top.gregtao.concerto.screen.widget.ConcertoListWidget;
import top.gregtao.concerto.screen.widget.MusicWithUUIDListWidget;

import java.util.UUID;

public class MusicConfirmationScreen extends ConcertoScreen {

    private MusicWithUUIDListWidget widget;

    public MusicConfirmationScreen(Screen parent) {
        super(Component.translatable("concerto.screen.confirmation"), parent);
    }

    public void refresh() {
        this.widget.reset(ClientMusicNetworkHandler.WAIT_CONFIRMATION.entrySet().stream().map(
                entry -> Pair.of(entry.getValue().music, entry.getKey())).toList(), null);
    }

    @Override
    protected void init() {
        super.init();
        this.widget = new MusicWithUUIDListWidget(this.width, this.height - 55, 20, 18);
        this.refresh();
        this.addWidget(this.widget);
        int actionX = ConcertoListWidget.PAGE_MARGIN;
        int actionY = this.height - 30;
        int actionWidth = (this.width - ConcertoListWidget.PAGE_MARGIN * 2) / 4;

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.accept"), button -> {
            LocalPlayer player = Minecraft.getInstance().player;
            ConcertoListWidget<Pair<Music, UUID>>.Entry entry = this.widget.getSelected();
            if (player != null && entry != null) {
                player.connection.sendCommand("sharemusic accept " + entry.item.getSecond());
                this.widget.removeEntryFromTop(entry);
            }
        }).pos(actionX, actionY).size(actionWidth, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.reject"), button -> {
            LocalPlayer player = Minecraft.getInstance().player;
            ConcertoListWidget<Pair<Music, UUID>>.Entry entry = this.widget.getSelected();
            if (player != null && entry != null) {
                player.connection.sendCommand("sharemusic reject " + entry.item.getSecond());
                this.widget.removeEntryFromTop(entry);
            }
        }).pos(actionX + actionWidth, actionY).size(actionWidth, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.reject.all"), button -> {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                player.connection.sendCommand("sharemusic reject all");
                this.widget.clear();
            }
        }).pos(actionX + actionWidth * 2, actionY).size(actionWidth, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.refresh"), button -> this.refresh())
                .pos(actionX + actionWidth * 3, actionY).size(this.width - ConcertoListWidget.PAGE_MARGIN - actionX - actionWidth * 3, 20).build());
    }

    @Override
    public void render(GuiGraphics matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        this.widget.render(matrices, mouseX, mouseY, delta);
    }
}
