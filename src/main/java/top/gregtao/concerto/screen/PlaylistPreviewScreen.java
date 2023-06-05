package top.gregtao.concerto.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.music.list.Playlist;
import top.gregtao.concerto.player.MusicPlayer;
import top.gregtao.concerto.player.MusicPlayerStatus;
import top.gregtao.concerto.screen.widget.ConcertoListWidget;
import top.gregtao.concerto.screen.widget.MetadataListWidget;

public class PlaylistPreviewScreen extends ConcertoScreen {
    private final Playlist playlist;
    private final MetadataListWidget<Music> widget;

    public PlaylistPreviewScreen(Playlist playlist, Screen parent) {
        super(Text.literal(Text.translatable("concerto." + (playlist.isAlbum() ? "album" : "playlist")).getString() +
                ": " + playlist.getMeta().title() + " - " + playlist.getMeta().author()), parent);
        this.playlist = playlist;
        this.widget = new MetadataListWidget<>(this.width, 0, 18, this.height - 35, 18);
        this.widget.setRenderHorizontalShadows(false);
        this.widget.setRenderBackground(false);
    }

    @Override
    protected void init() {
        super.init();
        this.addSelectableChild(this.widget);
        MusicPlayer.executeThread(() -> this.widget.reset(this.playlist.getList(), null));

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.playlist.add"), button ->
            MusicPlayer.INSTANCE.addMusic(this.playlist.getList(), () -> {
                MusicPlayer.INSTANCE.skipTo(MusicPlayerStatus.INSTANCE.getMusicList().size() - this.playlist.getList().size());
                if (!MusicPlayer.INSTANCE.started) MusicPlayer.INSTANCE.start();
            }
        )).position(this.width / 2 - 160, this.height - 30).size(50, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.play"), button -> {
            ConcertoListWidget<Music>.Entry entry = this.widget.getSelectedOrNull();
            if (entry != null) {
                MusicPlayer.INSTANCE.addMusicHere(entry.item, true, () -> {
                    if (!MusicPlayer.INSTANCE.started) MusicPlayer.INSTANCE.start();
                });
            }
        }).position(this.width / 2 - 105, this.height - 30).size(50, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.add"), button -> {
            ConcertoListWidget<Music>.Entry entry = this.widget.getSelectedOrNull();
            if (entry != null) {
                MusicPlayer.INSTANCE.addMusic(entry.item);
            }
        }).position(this.width / 2 - 50, this.height - 30).size(50, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.info"), button -> {
            ConcertoListWidget<Music>.Entry entry = this.widget.getSelectedOrNull();
            if (entry != null) {
                MinecraftClient.getInstance().setScreen(new MusicInfoScreen(entry.item, this));
            }
        }).position(this.width / 2 + 5, this.height - 30).size(50, 20).build());
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        DrawableHelper.drawCenteredTextWithShadow(matrices, this.textRenderer, this.title, this.width / 2, 5, 0xffffffff);
        this.widget.render(matrices, mouseX, mouseY, delta);
    }
}
