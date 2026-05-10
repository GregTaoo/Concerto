package top.gregtao.concerto.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
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

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.accept"), button -> {
            LocalPlayer player = Minecraft.getInstance().player;
            ConcertoListWidget<Pair<Music, UUID>>.Entry entry = this.widget.getSelected();
            if (player != null && entry != null) {
                player.connection.sendCommand("sharemusic accept " + entry.item.getSecond());
                this.widget.removeEntryFromTop(entry);
            }
        }).pos(20, this.height - 30).size(60, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.reject"), button -> {
            LocalPlayer player = Minecraft.getInstance().player;
            ConcertoListWidget<Pair<Music, UUID>>.Entry entry = this.widget.getSelected();
            if (player != null && entry != null) {
                player.connection.sendCommand("sharemusic reject " + entry.item.getSecond());
                this.widget.removeEntryFromTop(entry);
            }
        }).pos(85, this.height - 30).size(60, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.reject.all"), button -> {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                player.connection.sendCommand("sharemusic reject all");
                this.widget.clear();
            }
        }).pos(150, this.height - 30).size(60, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.refresh"), button -> this.refresh())
                .pos(215, this.height - 30).size(60, 20).build());
    }

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor matrices, int mouseX, int mouseY, float delta) {
        super.extractRenderState(matrices, mouseX, mouseY, delta);
        this.widget.extractRenderState(matrices, mouseX, mouseY, delta);
    }
}
