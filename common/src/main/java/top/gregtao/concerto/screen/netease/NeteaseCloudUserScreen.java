package top.gregtao.concerto.screen.netease;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.gregtao.concerto.core.api.WithMetaData;
import top.gregtao.concerto.core.http.netease.NeteaseCloudApiClient;
import top.gregtao.concerto.core.music.list.NeteaseCloudPlaylist;
import top.gregtao.concerto.core.music.list.Playlist;
import top.gregtao.concerto.core.util.ConcertoRunner;
import top.gregtao.concerto.screen.PageScreen;
import top.gregtao.concerto.screen.PlaylistPreviewScreen;
import top.gregtao.concerto.screen.widget.ConcertoListWidget;
import top.gregtao.concerto.screen.widget.MetadataListWidget;

import java.util.concurrent.CompletableFuture;

public class NeteaseCloudUserScreen extends PageScreen {
    private MetadataListWidget<NeteaseCloudPlaylist> playlistList;

    private <T extends WithMetaData> MetadataListWidget<T> initWidget() {
        return new MetadataListWidget<>(this.width, this.height - 55, 20, 18) {
            @Override
            public void onDoubleClicked(ConcertoListWidget<T>.Entry entry) {
                Minecraft.getInstance().setScreen(new PlaylistPreviewScreen((Playlist) entry.item, NeteaseCloudUserScreen.this));
            }
        };
    }

    public NeteaseCloudUserScreen(Screen parent) {
        super(Component.translatable("concerto.screen.user"), parent);
    }

    @Override
    public void onPageTurned(int page) {
        ConcertoRunner.run(() -> {
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
            Minecraft.getInstance().setScreen(new NeteaseCloudLoginScreens(null));
        }
        this.playlistList = this.initWidget();

        this.onPageTurned(0);
        this.addRenderableWidget(this.playlistList);
        this.addWidget(this.playlistList);

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.daily_recommendation"),
                button -> CompletableFuture.supplyAsync(
                        () -> NeteaseCloudApiClient.INSTANCE.getDailyRecommendation()
                ).thenAccept(playlist -> Minecraft.getInstance().executeBlocking(
                        () -> Minecraft.getInstance().setScreen(new PlaylistPreviewScreen(playlist, this)))
                )).pos(this.width / 2 + 10, this.height - 30).size(50, 20).build()
        );

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.play"), button -> {
            ConcertoListWidget<NeteaseCloudPlaylist>.Entry entry = this.playlistList.getSelected();
            if (entry != null) {
                Minecraft.getInstance().setScreen(new PlaylistPreviewScreen(entry.item, this));
            }
        }).pos(this.width / 2 + 65, this.height - 30).size(50, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.logout"), button -> {
            if (this.loggedIn()) {
                NeteaseCloudApiClient.LOCAL_USER.logout();
            }
            Minecraft.getInstance().setScreen(new NeteaseCloudLoginScreens(this));
        }).pos(this.width / 2 + 120, this.height - 30).size(50, 20).build());
    }

    @Override
    public void render(GuiGraphics matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        if (!this.loggedIn()) {
            matrices.drawCenteredString(this.font, Component.translatable("concerto.screen.163.not_login"),
                    this.width / 2, this.height / 2, 0xffffffff);
        }
    }

}
