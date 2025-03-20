package top.gregtao.concerto.screen;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.TranslatableText;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.music.meta.music.MusicMetaData;
import top.gregtao.concerto.network.ClientMusicNetworkHandler;
import top.gregtao.concerto.player.MusicPlayer;
import top.gregtao.concerto.screen.widget.URLImageWidget;

import java.net.MalformedURLException;
import java.net.URI;

public class MusicInfoScreen extends ConcertoScreen {

    private URLImageWidget headPicture;
    private final Music music;

    public MusicInfoScreen(Music music, Screen parent) {
        super(new TranslatableText("concerto.screen.info"), parent);
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

        this.addDrawableChild(new ButtonWidget(this.width - 245, this.height - 30, 50, 20,
                new TranslatableText("concerto.screen.request"),
                button -> ClientMusicNetworkHandler.musicAgentAddMusic(this.music)
        ));

        this.addDrawableChild(new ButtonWidget(this.width - 190, this.height - 30, 50, 20,
                new TranslatableText("concerto.screen.play"),
                button -> MusicPlayer.INSTANCE.addMusicHere(this.music, true)
        ));

        this.addDrawableChild(new ButtonWidget(this.width - 135, this.height - 30, 50, 20,
                new TranslatableText("concerto.screen.add"),
                button -> MusicPlayer.INSTANCE.addMusic(this.music)
        ));

        this.addDrawableChild(new ButtonWidget(this.width - 80, this.height - 30, 50, 20,
                new TranslatableText("concerto.screen.copy_link"),
                button -> {
                    if (this.client != null) {
                        this.client.keyboard.setClipboard(this.music.getLink());
                    }
                }
        ));
    }

    private void initInfo() {
        MusicMetaData meta = this.music.getMeta();
        try {
            if (!meta.headPictureUrl().isEmpty()) {
                this.headPicture.setUrl(URI.create(meta.headPictureUrl()).toURL());
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
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        this.headPicture.render(matrices, mouseX, mouseY, delta);
        this.textRenderer.draw(matrices, this.music.getMeta().getSource(), (float) this.width / 2 + 5, (float) this.height / 2 - 20, 0xffffffff);
        this.textRenderer.draw(matrices, this.music.getMeta().title(), (float) this.width / 2 + 5, (float) this.height / 2 - 5, 0xffffffff);
        this.textRenderer.draw(matrices, this.music.getMeta().author(), (float) this.width / 2 + 5, (float) this.height / 2 + 10, 0xffffffff);
    }
}
