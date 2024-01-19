package top.gregtao.concerto.screen.netease;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import top.gregtao.concerto.api.WithMetaData;
import top.gregtao.concerto.http.netease.NeteaseCloudApiClient;
import top.gregtao.concerto.music.list.NeteaseCloudPlaylist;
import top.gregtao.concerto.music.list.Playlist;
import top.gregtao.concerto.player.MusicPlayer;
import top.gregtao.concerto.screen.PageScreen;
import top.gregtao.concerto.screen.PlaylistPreviewScreen;
import top.gregtao.concerto.screen.widget.ConcertoListWidget;
import top.gregtao.concerto.screen.widget.MetadataListWidget;

import java.util.concurrent.CompletableFuture;

public class NeteaseCloudUserScreen extends PageScreen {
    private MetadataListWidget<NeteaseCloudPlaylist> playlistList;

    private <T extends WithMetaData> MetadataListWidget<T> initWidget() {
        MetadataListWidget<T> widget = new MetadataListWidget<>(this.width, 0, 15, this.height - 35, 18,
                entry -> MinecraftClient.getInstance().setScreen(new PlaylistPreviewScreen((Playlist) entry.item, this))
        );
        widget.setRenderBackground(false);
        widget.setRenderHorizontalShadows(false);
        return widget;
    }

    public NeteaseCloudUserScreen(Screen parent) {
        super(Text.translatable("concerto.screen.user"), parent);
    }

    private void onPageTurned(int page) {
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
        this.configure(this::onPageTurned, this.width / 2 - 120, this.height - 30);

        super.init();
        if (!this.loggedIn()) {
            MinecraftClient.getInstance().setScreen(new NeteaseCloudLoginScreens(null));
        }
        this.playlistList = this.initWidget();

        this.onPageTurned(0);
        this.addSelectableChild(this.playlistList);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.daily_recommendation"),
                button -> CompletableFuture.supplyAsync(
                        () -> NeteaseCloudApiClient.INSTANCE.getDailyRecommendation()
                ).thenAccept(playlist -> MinecraftClient.getInstance().setScreen(new PlaylistPreviewScreen(playlist, this)))
                ).position(this.width / 2 + 10, this.height - 30).size(50, 20).build()
        );

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.play"), button -> {
            ConcertoListWidget<NeteaseCloudPlaylist>.Entry entry = this.playlistList.getSelectedOrNull();
            if (entry != null) {
                MinecraftClient.getInstance().setScreen(new PlaylistPreviewScreen(entry.item, this));
            }
        }).position(this.width / 2 + 65, this.height - 30).size(50, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.logout"), button -> {
            if (this.loggedIn()) {
                NeteaseCloudApiClient.LOCAL_USER.logout();
            } else {
                MinecraftClient.getInstance().setScreen(new NeteaseCloudLoginScreens(this));
            }
        }).position(this.width / 2 + 120, this.height - 30).size(50, 20).build());
    }

    @Override
    public void render(DrawContext matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        if (this.loggedIn()) {
            this.playlistList.render(matrices, mouseX, mouseY, delta);
        } else {
            matrices.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("concerto.screen.163.not_login"),
                    this.width / 2, this.height / 2, 0xffffffff);
        }
    }

}
