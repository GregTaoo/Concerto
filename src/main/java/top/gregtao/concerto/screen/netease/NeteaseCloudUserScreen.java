package top.gregtao.concerto.screen.netease;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.TranslatableText;
import top.gregtao.concerto.api.WithMetaData;
import top.gregtao.concerto.http.netease.NeteaseCloudApiClient;
import top.gregtao.concerto.music.list.NeteaseCloudPlaylist;
import top.gregtao.concerto.music.list.Playlist;
import top.gregtao.concerto.player.MusicPlayer;
import top.gregtao.concerto.screen.ConcertoScreen;
import top.gregtao.concerto.screen.PageScreen;
import top.gregtao.concerto.screen.PlaylistPreviewScreen;
import top.gregtao.concerto.screen.widget.ConcertoListWidget;
import top.gregtao.concerto.screen.widget.MetadataListWidget;

import java.util.concurrent.CompletableFuture;

public class NeteaseCloudUserScreen extends PageScreen {
    private MetadataListWidget<NeteaseCloudPlaylist> playlistList;

    private <T extends WithMetaData> MetadataListWidget<T> initWidget() {
        return new MetadataListWidget<>(NeteaseCloudUserScreen.this.width, NeteaseCloudUserScreen.this.height, 18, NeteaseCloudUserScreen.this.height - 35, 18) {
            @Override
            public void onDoubleClicked(ConcertoListWidget<T>.Entry entry) {
                MinecraftClient.getInstance().openScreen(new PlaylistPreviewScreen((Playlist) entry.item, NeteaseCloudUserScreen.this));
            }
        };
    }

    public NeteaseCloudUserScreen(Screen parent) {
        super(new TranslatableText("concerto.screen.user"), parent);
    }

    @Override
    public void onPageTurned(int page) {
        MusicPlayer.run(() -> {
            if (NeteaseCloudApiClient.LOCAL_USER.updateLoginStatus()) {
                this.playlistList.reset(NeteaseCloudApiClient.LOCAL_USER.getUserPlaylists(page), null);
            }
        });
    }

    private boolean loggedIn() {
        return NeteaseCloudApiClient.LOCAL_USER.loggedIn;
    }

    @Override
    protected void init() {
        super.init();
        if (!this.loggedIn()) {
            MinecraftClient.getInstance().openScreen(new NeteaseCloudLoginScreens(null));
        }
        this.playlistList = this.initWidget();

        this.onPageTurned(0);
        this.addChild(this.playlistList);

        this.addButton(new ButtonWidget(this.width / 2 + 10, this.height - 30, 50, 20,
                new TranslatableText("concerto.screen.daily_recommendation"),
                button -> CompletableFuture.supplyAsync(
                        () -> NeteaseCloudApiClient.INSTANCE.getDailyRecommendation()
                ).thenAccept(playlist -> MinecraftClient.getInstance().submitAndJoin(
                        () -> MinecraftClient.getInstance().openScreen(new PlaylistPreviewScreen(playlist, this)))
                ))
        );

        this.addButton(new ButtonWidget(this.width / 2 + 65, this.height - 30, 50, 20,
                new TranslatableText("concerto.screen.play"), button -> {
            ConcertoListWidget<NeteaseCloudPlaylist>.Entry entry = this.playlistList.getSelected();
            if (entry != null) {
                MinecraftClient.getInstance().openScreen(new PlaylistPreviewScreen(entry.item, this));
            }
        }));

        this.addButton(new ButtonWidget(this.width / 2 + 120, this.height - 30, 50, 20,
                new TranslatableText("concerto.screen.logout"), button -> {
            if (this.loggedIn()) {
                NeteaseCloudApiClient.LOCAL_USER.logout();
            } else {
                MinecraftClient.getInstance().openScreen(new NeteaseCloudLoginScreens(this));
            }
        }));
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        if (!this.loggedIn()) {
            ConcertoScreen.drawCenteredTextWithShadow(matrices, this.textRenderer,
                    new TranslatableText("concerto.screen.163.not_login").asOrderedText(),
                    this.width / 2, this.height / 2, 0xffffffff);
        } else {
            this.playlistList.render(matrices, mouseX, mouseY, delta);
        }
        super.render(matrices, mouseX, mouseY, delta);
    }

}
