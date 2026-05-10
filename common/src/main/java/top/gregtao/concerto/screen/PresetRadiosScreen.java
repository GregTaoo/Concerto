package top.gregtao.concerto.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.core.api.WithMetaData;
import top.gregtao.concerto.core.music.list.Playlist;
import top.gregtao.concerto.screen.widget.ConcertoListWidget;
import top.gregtao.concerto.screen.widget.MetadataListWidget;

public class PresetRadiosScreen extends ConcertoScreen {

    private MetadataListWidget<Playlist> playlistList;

    private <T extends WithMetaData> MetadataListWidget<T> initWidget() {
        return new MetadataListWidget<>(this.width, this.height - 55, 20, 18) {
            @Override
            public void onDoubleClicked(ConcertoListWidget<T>.Entry entry) {
                Minecraft.getInstance().setScreen(new PlaylistPreviewScreen((Playlist) entry.item, PresetRadiosScreen.this));
            }
        };
    }

    public PresetRadiosScreen(Screen parent) {
        super(Component.translatable("concerto.screen.preset_radios"), parent);
    }

    public void reset() {
        this.playlistList.reset(ConcertoClient.presetRadios, null);
    }

    @Override
    protected void init() {
        super.init();
        this.playlistList = this.initWidget();
        this.reset();
        this.addWidget(this.playlistList);
        this.addRenderableWidget(this.playlistList);

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.play"), button -> {
            ConcertoListWidget<Playlist>.Entry entry = this.playlistList.getSelected();
            if (entry != null) {
                Minecraft.getInstance().setScreen(new PlaylistPreviewScreen(entry.item, this));
            }
        }).pos(20, this.height - 30).size(60, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.refresh"), button -> {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                player.connection.sendCommand("concerto-server fetch-radios");
            }
        }).pos(85, this.height - 30).size(60, 20).build());
    }

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor matrices, int mouseX, int mouseY, float delta) {
        super.extractRenderState(matrices, mouseX, mouseY, delta);
        this.playlistList.extractRenderState(matrices, mouseX, mouseY, delta);
    }
}
