package top.gregtao.concerto.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import top.gregtao.concerto.config.PresetPlaylistsConfig;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.music.list.Playlist;
import top.gregtao.concerto.player.MusicPlayer;
import top.gregtao.concerto.player.MusicPlayerHandler;
import top.gregtao.concerto.screen.widget.ConcertoListWidget;
import top.gregtao.concerto.screen.widget.MetadataListWidget;
import top.gregtao.concerto.util.ConcertoRunner;

public class PlaylistPreviewScreen extends ConcertoScreen {
    private final Playlist playlist;
    private MetadataListWidget<Music> widget;

    public PlaylistPreviewScreen(Playlist playlist, Screen parent) {
        super(new LiteralText(new TranslatableText("concerto." + (playlist.isAlbum() ? "album" : "playlist")).getString() +
                ": " + playlist.getMeta().title() + " - " + playlist.getMeta().author()), parent);
        this.playlist = playlist;
    }

    @Override
    protected void init() {
        super.init();
        this.widget = new MetadataListWidget<>(this.width, this.height, 20, this.height - 35, 18) {
            @Override
            public void onDoubleClicked(ConcertoListWidget<Music>.Entry entry) {
                MusicPlayer.INSTANCE.addMusicHere(entry.item, true);
            }
        };
        this.addDrawableChild(this.widget);
        this.addSelectableChild(this.widget);
        ConcertoRunner.run(() -> this.widget.reset(this.playlist.getList(), null));

        this.addDrawableChild(new ButtonWidget(20, this.height - 30, 60, 20,
                new TranslatableText("concerto.screen.playlist.add"), button ->
            MusicPlayer.INSTANCE.addMusic(this.playlist.getList(), () ->
                    MusicPlayer.INSTANCE.skipTo(MusicPlayerHandler.INSTANCE.getMusicList().size() - this.playlist.getList().size())
        )));

        this.addDrawableChild(new ButtonWidget(85, this.height - 30, 60, 20,
                new TranslatableText("concerto.screen.play"), button -> {
            ConcertoListWidget<Music>.Entry entry = this.widget.getSelectedOrNull();
            if (entry != null) {
                MusicPlayer.INSTANCE.addMusicHere(entry.item, true);
            }
        }));

        this.addDrawableChild(new ButtonWidget(150, this.height - 30, 60, 20,
                new TranslatableText("concerto.screen.add"), button -> {
            ConcertoListWidget<Music>.Entry entry = this.widget.getSelectedOrNull();
            if (entry != null) {
                MusicPlayer.INSTANCE.addMusic(entry.item);
            }
        }));

        this.addDrawableChild(new ButtonWidget(215, this.height - 30, 60, 20,
                new TranslatableText("concerto.screen.info"), button -> {
            ConcertoListWidget<Music>.Entry entry = this.widget.getSelectedOrNull();
            if (entry != null) {
                MinecraftClient.getInstance().setScreen(new MusicInfoScreen(entry.item, this));
            }
        }));

        this.addDrawableChild(new ButtonWidget(280, this.height - 30, 60, 20,
                new TranslatableText("concerto.playlist.export"), button -> {
            Text text = PresetPlaylistsConfig.saveToLocalPlaylists(this.playlist) ? new TranslatableText("concerto.playlist.export.success") :
                    new TranslatableText("concerto.playlist.export.fail");
            this.displayAlert(text);
        }));
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        DrawableHelper.drawCenteredTextWithShadow(matrices, this.textRenderer, this.title.asOrderedText(), this.width / 2, 5, 0xffffffff);
    }
}
