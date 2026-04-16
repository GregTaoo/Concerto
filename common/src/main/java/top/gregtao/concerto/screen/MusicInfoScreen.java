package top.gregtao.concerto.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import top.gregtao.concerto.core.music.Music;
import top.gregtao.concerto.core.music.meta.music.MusicMetaData;
import top.gregtao.concerto.core.player.MusicPlayerHandler;
import top.gregtao.concerto.core.player.PlayerPermissions;
import top.gregtao.concerto.core.room.MusicRoom;
import top.gregtao.concerto.network.room.ServerMusicAgentManager;
import top.gregtao.concerto.screen.widget.URLImageWidget;
import top.gregtao.concerto.core.util.ConcertoRunner;

public class MusicInfoScreen extends ConcertoScreen {

    private URLImageWidget headPicture;
    private final Music music;
    private Button requestButton;
    private Button playButton;
    private Button addButton;

    public MusicInfoScreen(Music music, Screen parent) {
        super(Component.translatable("concerto.screen.info"), parent);
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

        this.requestButton = Button.builder(
                Component.translatable("concerto.screen.request"),
                button -> ServerMusicAgentManager.clientAddMusic(this.music)
        ).pos(this.width - 245, this.height - 30).size(50, 20).build();
        this.addRenderableWidget(this.requestButton);

        this.playButton = Button.builder(
                Component.translatable("concerto.screen.play"),
                button -> MusicPlayerHandler.INSTANCE.addMusicHereAsync(this.music, true, () -> {})
        ).pos(this.width - 190, this.height - 30).size(50, 20).build();
        this.addRenderableWidget(this.playButton);

        this.addButton = Button.builder(
                Component.translatable("concerto.screen.add"),
                button -> MusicPlayerHandler.INSTANCE.addMusicAsync(this.music, false, () -> {})
        ).pos(this.width - 135, this.height - 30).size(50, 20).build();
        this.addRenderableWidget(this.addButton);

        this.addRenderableWidget(Button.builder(
                Component.translatable("concerto.screen.copy_link"),
                button -> {
                    if (this.minecraft != null) {
                        this.minecraft.keyboardHandler.setClipboard(this.music.getLink());
                    }
                }
        ).pos(this.width - 80, this.height - 30).size(50, 20).build());

        this.updateButtonStates();
    }

    private void updateButtonStates() {
        this.playButton.active = PlayerPermissions.canModifyMusicList();
        this.addButton.active = PlayerPermissions.canModifyMusicList();
        this.requestButton.active = MusicRoom.clientGetState() == MusicRoom.ClientState.MUSIC_AGENT;
    }

    private void initInfo() {
        MusicMetaData meta = this.music.getMeta();
        if (!meta.headPictureUrl().isEmpty()) {
            this.headPicture.setUrl(meta.headPictureUrl());
            this.headPicture.loadImage();
        }
    }

    @Override
    public void onClose() {
        super.onClose();
        this.headPicture.close();
    }

    @Override
    public void render(GuiGraphics matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        this.updateButtonStates();
        this.headPicture.render(matrices, mouseX, mouseY, delta);
        matrices.drawString(this.font, this.music.getMeta().getSource(), this.width / 2 + 5, this.height / 2 - 20, 0xffffffff, false);
        matrices.drawString(this.font, this.music.getMeta().title(), this.width / 2 + 5, this.height / 2 - 5, 0xffffffff, false);
        matrices.drawString(this.font, this.music.getMeta().author(), this.width / 2 + 5, this.height / 2 + 10, 0xffffffff, false);
    }
}
