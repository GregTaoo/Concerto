package top.gregtao.concerto.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.core.music.Music;
import top.gregtao.concerto.core.music.meta.music.MusicMetaData;
import top.gregtao.concerto.core.player.MusicPlayerHandler;
import top.gregtao.concerto.network.ClientMusicNetworkHandler;
import top.gregtao.concerto.screen.widget.URLImageWidget;
import top.gregtao.concerto.core.util.ConcertoRunner;

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

        ConcertoRunner.run(() -> {
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
                button -> MusicPlayerHandler.INSTANCE.addMusicHereAsync(this.music, true, () -> {})
        ).position(this.width - 190, this.height - 30).size(50, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("concerto.screen.add"),
                button -> MusicPlayerHandler.INSTANCE.addMusicAsync(this.music, false, () -> {})
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
    public void render(DrawContext matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        this.headPicture.render(matrices, mouseX, mouseY, delta);
        matrices.drawText(this.textRenderer, this.music.getMeta().getSource(), this.width / 2 + 5, this.height / 2 - 20, 0xffffffff, false);
        matrices.drawText(this.textRenderer, this.music.getMeta().title(), this.width / 2 + 5, this.height / 2 - 5, 0xffffffff, false);
        matrices.drawText(this.textRenderer, this.music.getMeta().author(), this.width / 2 + 5, this.height / 2 + 10, 0xffffffff, false);
    }
}
