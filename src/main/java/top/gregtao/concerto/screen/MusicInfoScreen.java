package top.gregtao.concerto.screen;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
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
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        this.headPicture.render(matrices, mouseX, mouseY, delta);
        this.textRenderer.draw(matrices, this.music.getMeta().getSource(), this.width / 2f + 5, this.height / 2f - 20, 0xffffffff);
        this.textRenderer.draw(matrices, this.music.getMeta().title(), this.width / 2f + 5, this.height / 2f - 5, 0xffffffff);
        this.textRenderer.draw(matrices, this.music.getMeta().author(), this.width / 2f + 5, this.height / 2f + 10, 0xffffffff);
    }
}
