package top.gregtao.concerto.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.api.WithMetaData;
import top.gregtao.concerto.music.list.Playlist;
import top.gregtao.concerto.screen.widget.ConcertoListWidget;
import top.gregtao.concerto.screen.widget.MetadataListWidget;

public class PresetRadiosScreen extends ConcertoScreen {

    private MetadataListWidget<Playlist> playlistList;

    private <T extends WithMetaData> MetadataListWidget<T> initWidget() {
        return new MetadataListWidget<>(this.width, 0, 15, this.height - 35, 18) {
            @Override
            public void onDoubleClicked(ConcertoListWidget<T>.Entry entry) {
                MinecraftClient.getInstance().setScreen(new PlaylistPreviewScreen((Playlist) entry.item, PresetRadiosScreen.this));
            }
        };
    }

    public PresetRadiosScreen(Screen parent) {
        super(Text.translatable("concerto.screen.preset_radios"), parent);
    }

    public void reset() {
        this.playlistList.reset(ConcertoClient.presetRadios, null);
    }

    @Override
    protected void init() {
        super.init();
        this.playlistList = this.initWidget();
        this.reset();
        this.addSelectableChild(this.playlistList);
        this.addDrawableChild(this.playlistList);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.play"), button -> {
            ConcertoListWidget<Playlist>.Entry entry = this.playlistList.getSelectedOrNull();
            if (entry != null) {
                MinecraftClient.getInstance().setScreen(new PlaylistPreviewScreen(entry.item, this));
            }
        }).position(20, this.height - 30).size(60, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.refresh"), button -> {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player != null) {
                player.networkHandler.sendChatCommand("concerto-server fetch-radios");
            }
        }).position(85, this.height - 30).size(60, 20).build());
    }

    @Override
    public void render(DrawContext matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        this.playlistList.render(matrices, mouseX, mouseY, delta);
    }
}