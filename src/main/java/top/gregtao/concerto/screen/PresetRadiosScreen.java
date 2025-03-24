package top.gregtao.concerto.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.TranslatableText;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.api.WithMetaData;
import top.gregtao.concerto.music.list.Playlist;
import top.gregtao.concerto.screen.widget.ConcertoListWidget;
import top.gregtao.concerto.screen.widget.MetadataListWidget;

public class PresetRadiosScreen extends ConcertoScreen {

    private MetadataListWidget<Playlist> playlistList;

    private <T extends WithMetaData> MetadataListWidget<T> initWidget() {
        return new MetadataListWidget<>(PresetRadiosScreen.this.width, this.height, 18, PresetRadiosScreen.this.height - 35, 18) {
            @Override
            public void onDoubleClicked(ConcertoListWidget<T>.Entry entry) {
                MinecraftClient.getInstance().openScreen(new PlaylistPreviewScreen((Playlist) entry.item, PresetRadiosScreen.this));
            }
        };
    }

    public PresetRadiosScreen(Screen parent) {
        super(new TranslatableText("concerto.screen.preset_radios"), parent);
    }

    public void reset() {
        this.playlistList.reset(ConcertoClient.presetRadios, null);
    }

    @Override
    protected void init() {
        super.init();
        this.setRenderBg(false);

        this.playlistList = this.initWidget();
        this.reset();
        this.addChild(this.playlistList);

        this.addButton(new ButtonWidget(20, this.height - 30, 60, 20,
                new TranslatableText("concerto.screen.play"), button -> {
            ConcertoListWidget<Playlist>.Entry entry = this.playlistList.getSelected();
            if (entry != null) {
                MinecraftClient.getInstance().openScreen(new PlaylistPreviewScreen(entry.item, this));
            }
        }));

        this.addButton(new ButtonWidget(85, this.height - 30, 60, 20,
                new TranslatableText("concerto.refresh"), button -> {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player != null) {
                player.sendChatMessage("concerto-server fetch-radios");
            }
        }));
    }
}
