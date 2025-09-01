package top.gregtao.concerto.screen.qq;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.TranslatableText;
import top.gregtao.concerto.api.WithMetaData;
import top.gregtao.concerto.http.qq.QQMusicApiClient;
import top.gregtao.concerto.music.list.Playlist;
import top.gregtao.concerto.music.list.QQMusicPlaylist;
import top.gregtao.concerto.screen.ConcertoScreen;
import top.gregtao.concerto.screen.PageScreen;
import top.gregtao.concerto.screen.PlaylistPreviewScreen;
import top.gregtao.concerto.screen.widget.ConcertoListWidget;
import top.gregtao.concerto.screen.widget.MetadataListWidget;
import top.gregtao.concerto.util.ConcertoRunner;

public class QQMusicUserScreen extends PageScreen {
    private MetadataListWidget<QQMusicPlaylist> playlistList;

    private <T extends WithMetaData> MetadataListWidget<T> initWidget() {
        return new MetadataListWidget<>(QQMusicUserScreen.this.width, QQMusicUserScreen.this.height, 18, QQMusicUserScreen.this.height - 35, 18) {
            @Override
            public void onDoubleClicked(ConcertoListWidget<T>.Entry entry) {
                MinecraftClient.getInstance().openScreen(new PlaylistPreviewScreen((Playlist) entry.item, QQMusicUserScreen.this));
            }
        };
    }

    public QQMusicUserScreen(Screen parent) {
        super(new TranslatableText("concerto.screen.user"), parent);
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
        this.setRenderBg(false);

        if (!this.loggedIn()) {
            MinecraftClient.getInstance().openScreen(new QQMusicLoginScreens(null));
        }
        this.playlistList = this.initWidget();

        this.onPageTurned(0);
        this.addChild(this.playlistList);

        this.addButton(new ButtonWidget(this.width / 2 + 65, this.height - 30, 50, 20,
                new TranslatableText("concerto.screen.play"), button -> {
            ConcertoListWidget<QQMusicPlaylist>.Entry entry = this.playlistList.getSelected();
            if (entry != null) {
                MinecraftClient.getInstance().openScreen(new PlaylistPreviewScreen(entry.item, this));
            }
        }));

        this.addButton(new ButtonWidget(this.width / 2 + 120, this.height - 30, 50, 20,
                new TranslatableText("concerto.screen.logout"), button -> {
            QQMusicApiClient.LOCAL_USER.logout();
            MinecraftClient.getInstance().openScreen(new QQMusicLoginScreens(this));
        }));
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        if (!this.loggedIn()) {
            ConcertoScreen.drawCenteredTextWithShadow(matrices, this.textRenderer,
                    new TranslatableText("concerto.screen.qq.not_login").asOrderedText(),
                    this.width / 2, this.height / 2, 0xffffffff);
        } else {
            this.playlistList.render(matrices, mouseX, mouseY, delta);
        }
        super.render(matrices, mouseX, mouseY, delta);
    }

}
