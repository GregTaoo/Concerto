package top.gregtao.concerto.screen;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.music.meta.music.MusicMetaData;
import top.gregtao.concerto.network.ClientMusicNetworkHandler;
import top.gregtao.concerto.player.MusicPlayer;
import top.gregtao.concerto.screen.widget.URLImageWidget;

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

        ButtonWidget requestButton = ButtonWidget.builder(
                Text.translatable("concerto.screen.request"),
                button -> ClientMusicNetworkHandler.musicAgentAddMusic(this.music)
        ).position(this.width - 245, this.height - 30).size(50, 20).build();
        this.addDrawableChild(requestButton);
        requestButton.active = ConcertoClient.clientState == ConcertoClient.ClientState.MUSIC_AGENT;

        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("concerto.screen.play"),
                button -> MusicPlayer.INSTANCE.addMusicHere(this.music, true)
        ).position(this.width - 190, this.height - 30).size(50, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("concerto.screen.add"),
                button -> MusicPlayer.INSTANCE.addMusic(this.music)
        ).position(this.width - 135, this.height - 30).size(50, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("concerto.screen.copy_link"),
                button -> {
                    if (this.client != null) {
                        this.client.keyboard.setClipboard(this.music.getLink());
                    }
                }
        ).position(this.width - 80, this.height - 30).size(50, 20).build());
    }

    private void initInfo() {
        MusicMetaData meta = this.music.getMeta();
        if (!meta.headPictureUrl().isEmpty()) {
            this.headPicture.setUrl(meta.headPictureUrl());
            this.headPicture.loadImage();
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
