package top.gregtao.concerto.screen.qq;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.gregtao.concerto.core.api.WithMetaData;
import top.gregtao.concerto.core.http.qq.QQMusicApiClient;
import top.gregtao.concerto.core.music.list.Playlist;
import top.gregtao.concerto.core.music.list.QQMusicPlaylist;
import top.gregtao.concerto.core.util.ConcertoRunner;
import top.gregtao.concerto.screen.PageScreen;
import top.gregtao.concerto.screen.PlaylistPreviewScreen;
import top.gregtao.concerto.screen.widget.ConcertoListWidget;
import top.gregtao.concerto.screen.widget.MetadataListWidget;

public class QQMusicUserScreen extends PageScreen {
    private MetadataListWidget<QQMusicPlaylist> playlistList;

    private <T extends WithMetaData> MetadataListWidget<T> initWidget() {
        return new MetadataListWidget<>(this.width, this.height - 55, 20, 18) {
            @Override
            public void onDoubleClicked(ConcertoListWidget<T>.Entry entry) {
                Minecraft.getInstance().setScreen(new PlaylistPreviewScreen((Playlist) entry.item, QQMusicUserScreen.this));
            }
        };
    }

    public QQMusicUserScreen(Screen parent) {
        super(Component.translatable("concerto.screen.user"), parent);
    }

    @Override
    public void onPageTurned(int page) {
        ConcertoRunner.run(() -> {
            QQMusicApiClient.LOCAL_USER.updateLoginStatus();
            this.playlistList.reset(QQMusicApiClient.LOCAL_USER.getUserPlaylists(), null);
        });
    }

    private boolean loggedIn() {
        return QQMusicApiClient.LOCAL_USER.loggedIn;
    }

    @Override
    protected void init() {
        super.init();
        if (!this.loggedIn()) {
            Minecraft.getInstance().setScreen(new QQMusicLoginScreens(null));
        }
        this.playlistList = this.initWidget();

        this.onPageTurned(0);
        this.addRenderableWidget(this.playlistList);
        this.addWidget(this.playlistList);

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.play"), button -> {
            ConcertoListWidget<QQMusicPlaylist>.Entry entry = this.playlistList.getSelected();
            if (entry != null) {
                Minecraft.getInstance().setScreen(new PlaylistPreviewScreen(entry.item, this));
            }
        }).pos(this.width / 2 + 65, this.height - 30).size(50, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.logout"), button -> {
            QQMusicApiClient.LOCAL_USER.logout();
            Minecraft.getInstance().setScreen(new QQMusicLoginScreens(this));
        }).pos(this.width / 2 + 120, this.height - 30).size(50, 20).build());
    }

    @Override
    public void render(GuiGraphics matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        if (!this.loggedIn()) {
            matrices.drawCenteredString(this.font, Component.translatable("concerto.screen.qq.not_login"),
                    this.width / 2, this.height / 2, 0xffffffff);
        }
    }

}
