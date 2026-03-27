package top.gregtao.concerto.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import top.gregtao.concerto.config.PresetPlaylistsConfig;
import top.gregtao.concerto.core.music.Music;
import top.gregtao.concerto.core.music.list.Playlist;
import top.gregtao.concerto.core.player.MusicPlayerHandler;
import top.gregtao.concerto.screen.widget.ConcertoListWidget;
import top.gregtao.concerto.screen.widget.MetadataListWidget;
import top.gregtao.concerto.core.util.ConcertoRunner;

public class PlaylistPreviewScreen extends ConcertoScreen {
    private final Playlist playlist;
    private MetadataListWidget<Music> widget;

    public PlaylistPreviewScreen(Playlist playlist, Screen parent) {
        super(Text.literal(Text.translatable("concerto." + (playlist.isAlbum() ? "album" : "playlist")).getString() +
                ": " + playlist.getMeta().title() + " - " + playlist.getMeta().author()), parent);
        this.playlist = playlist;
    }

    @Override
    protected void init() {
        super.init();
        this.widget = new MetadataListWidget<>(this.width, this.height - 55, 20, 18) {
            @Override
            public void onDoubleClicked(ConcertoListWidget<Music>.Entry entry) {
                MusicPlayerHandler.INSTANCE.addMusicHereAsync(entry.item, true);
            }
        };
        this.addSelectableChild(this.widget);
        ConcertoRunner.run(() -> this.widget.reset(this.playlist.getList(), null));

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.playlist.add"), button ->
            MusicPlayerHandler.INSTANCE.addMusicAsync(this.playlist.getList(), true))
                .position(20, this.height - 30).size(60, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.play"), button -> {
            ConcertoListWidget<Music>.Entry entry = this.widget.getSelectedOrNull();
            if (entry != null) {
                MusicPlayerHandler.INSTANCE.addMusicHereAsync(entry.item, true);
            }
        }).position(85, this.height - 30).size(60, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.add"), button -> {
            ConcertoListWidget<Music>.Entry entry = this.widget.getSelectedOrNull();
            if (entry != null) {
                MusicPlayerHandler.INSTANCE.addMusicAsync(entry.item, false);
            }
        }).position(150, this.height - 30).size(60, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.info"), button -> {
            ConcertoListWidget<Music>.Entry entry = this.widget.getSelectedOrNull();
            if (entry != null) {
                MinecraftClient.getInstance().setScreen(new MusicInfoScreen(entry.item, this));
            }
        }).position(215, this.height - 30).size(60, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.playlist.export"), button -> {
            Text text = PresetPlaylistsConfig.saveToLocalPlaylists(this.playlist) ? Text.translatable("concerto.playlist.export.success") :
                    Text.translatable("concerto.playlist.export.fail");
            this.displayAlert(text);
        }).position(280, this.height - 30).size(60, 20).build());
    }

    @Override
    public void render(DrawContext matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        matrices.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 5, 0xffffffff);
        this.widget.render(matrices, mouseX, mouseY, delta);
    }
}
