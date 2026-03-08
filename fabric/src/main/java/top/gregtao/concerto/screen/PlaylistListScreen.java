package top.gregtao.concerto.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import top.gregtao.concerto.core.music.list.Playlist;
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
        this.playlistList = new MetadataListWidget<>(this.width, this.height - 55, 20, 18) {
            @Override
            public void onDoubleClicked(ConcertoListWidget<Playlist>.Entry entry) {
                MinecraftClient.getInstance().setScreen(new PlaylistPreviewScreen(entry.item, PlaylistListScreen.this));
            }
        };
        this.playlistList.reset(this.playlists, null, "");

        this.addDrawableChild(this.playlistList);
        this.addSelectableChild(this.playlistList);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.play"), button -> {
            ConcertoListWidget<Playlist>.Entry entry = this.playlistList.getSelectedOrNull();
            if (entry != null) {
                MinecraftClient.getInstance().setScreen(new PlaylistPreviewScreen(entry.item, this));
            }
        }).position(20, this.height - 30).size(60, 20).build());
    }

}
