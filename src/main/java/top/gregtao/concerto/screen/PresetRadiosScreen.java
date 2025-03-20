package top.gregtao.concerto.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.TranslatableText;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.api.WithMetaData;
import top.gregtao.concerto.music.list.Playlist;
import top.gregtao.concerto.screen.widget.ConcertoListWidget;
import top.gregtao.concerto.screen.widget.MetadataListWidget;

public class PresetRadiosScreen extends ConcertoScreen {

    private MetadataListWidget<Playlist> playlistList;

    private <T extends WithMetaData> MetadataListWidget<T> initWidget() {
        MetadataListWidget<T> widget = new MetadataListWidget<>(this.width, 0, 25, this.height - 45, 18) {
            @Override
            public void onDoubleClicked(ConcertoListWidget<T>.Entry entry) {
                MinecraftClient.getInstance().setScreen(new PlaylistPreviewScreen((Playlist) entry.item, PresetRadiosScreen.this));
            }
        };
        widget.setRenderBackground(false);
        widget.setRenderHorizontalShadows(false);
        return widget;
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
        this.playlistList = this.initWidget();
        this.reset();
        this.addSelectableChild(this.playlistList);
        this.addDrawableChild(this.playlistList);

        this.addDrawableChild(new ButtonWidget(20, this.height - 30, 60, 20,
                new TranslatableText("concerto.screen.play"), button -> {
            ConcertoListWidget<Playlist>.Entry entry = this.playlistList.getSelectedOrNull();
            if (entry != null) {
                MinecraftClient.getInstance().setScreen(new PlaylistPreviewScreen(entry.item, this));
            }
        }));

        this.addDrawableChild(new ButtonWidget(85, this.height - 30, 60, 20,
                new TranslatableText("concerto.refresh"), button -> {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player != null) {
                player.sendChatMessage("concerto-server fetch-radios");
            }
        }));
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        this.playlistList.render(matrices, mouseX, mouseY, delta);
    }
}
