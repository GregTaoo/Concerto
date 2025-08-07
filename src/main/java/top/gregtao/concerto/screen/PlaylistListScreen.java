package top.gregtao.concerto.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import top.gregtao.concerto.music.list.Playlist;
import top.gregtao.concerto.screen.widget.ConcertoListWidget;
import top.gregtao.concerto.screen.widget.MetadataListWidget;

import java.util.List;

public class PlaylistListScreen extends ConcertoScreen {
    private final List<Playlist> playlists;
    private MetadataListWidget<Playlist> playlistList;

    public PlaylistListScreen(Text title, Screen parent, List<Playlist> playlists) {
        super(title, parent);
        this.playlists = playlists;
    }

    @Override
    protected void init() {
        super.init();
        this.playlistList = new MetadataListWidget<>(this.width, this.height, 18, this.height - 35, 18) {
            @Override
            public void onDoubleClicked(ConcertoListWidget<Playlist>.Entry entry) {
                MinecraftClient.getInstance().openScreen(new PlaylistPreviewScreen(entry.item, PlaylistListScreen.this));
            }
        };
        this.playlistList.reset(this.playlists, null, "");

        this.addButton(new ButtonWidget(20, this.height - 30, 60, 20,
                new TranslatableText("concerto.screen.play"), button -> {
            ConcertoListWidget<Playlist>.Entry entry = this.playlistList.getSelected();
            if (entry != null) {
                MinecraftClient.getInstance().openScreen(new PlaylistPreviewScreen(entry.item, this));
            }
        }));
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.playlistList.render(matrices, mouseX, mouseY, delta);
        super.render(matrices, mouseX, mouseY, delta);
    }

}
