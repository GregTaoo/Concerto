package top.gregtao.concerto.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.music.meta.music.MusicMetaData;
import top.gregtao.concerto.player.MusicPlayer;
import top.gregtao.concerto.screen.widget.URLImageWidget;

import java.net.MalformedURLException;
import java.net.URL;

public class MusicInfoScreen extends ConcertoScreen {

    private URLImageWidget headPicture;
    private final Music music;

    public MusicInfoScreen(Music music, Screen parent) {
        super(Text.translatable("concerto.screen.info"), parent);
        this.music = music;
    }

    @Override
    protected void init() {
        super.init();
        this.headPicture = new URLImageWidget(140, 140, this.width / 2 - 145, this.height / 2 - 70, null);

        MusicPlayer.run(() -> {
            this.music.getMeta();
            this.initInfo();
        });

        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("concerto.screen.play"),
                button -> MusicPlayer.INSTANCE.addMusicHere(this.music, true)
        ).position(this.width / 2 + 65, this.height - 30).size(50, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("concerto.screen.add"),
                button -> MusicPlayer.INSTANCE.addMusic(this.music)
        ).position(this.width / 2 + 120, this.height - 30).size(50, 20).build());
    }

    private void initInfo() {
        MusicMetaData meta = this.music.getMeta();
        try {
            if (!meta.headPictureUrl().isEmpty()) {
                this.headPicture.setUrl(new URL(meta.headPictureUrl()));
                this.headPicture.loadImage();
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        super.close();
        this.headPicture.close();
    }

    @Override
    public void render(DrawContext matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        this.headPicture.render(matrices, mouseX, mouseY, delta);
        matrices.drawText(this.textRenderer, this.music.getMeta().getSource(), this.width / 2 + 5, this.height / 2 - 20, 0xffffffff, false);
        matrices.drawText(this.textRenderer, this.music.getMeta().title(), this.width / 2 + 5, this.height / 2 - 5, 0xffffffff, false);
        matrices.drawText(this.textRenderer, this.music.getMeta().author(), this.width / 2 + 5, this.height / 2 + 10, 0xffffffff, false);
    }
}
