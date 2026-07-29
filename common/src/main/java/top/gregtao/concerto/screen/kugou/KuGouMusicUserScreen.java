package top.gregtao.concerto.screen.kugou;

import com.google.gson.JsonElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.gregtao.concerto.core.api.WithMetaData;
import top.gregtao.concerto.core.config.ClientConfig;
import top.gregtao.concerto.core.http.kugou.KuGouMusicApiClient;
import top.gregtao.concerto.core.music.list.KuGouMusicPlaylist;
import top.gregtao.concerto.core.music.list.Playlist;
import top.gregtao.concerto.core.util.ConcertoRunner;
import top.gregtao.concerto.screen.PageScreen;
import top.gregtao.concerto.screen.PlaylistPreviewScreen;
import top.gregtao.concerto.screen.widget.ConcertoListWidget;
import top.gregtao.concerto.screen.widget.MetadataListWidget;

import java.util.concurrent.CompletableFuture;

public class KuGouMusicUserScreen extends PageScreen {

    private MetadataListWidget<KuGouMusicPlaylist> playlistList;

    private <T extends WithMetaData> MetadataListWidget<T> initWidget() {
        return new MetadataListWidget<>(this.width, this.height - 55, 20, 18) {
            @Override
            public void onDoubleClicked(ConcertoListWidget<T>.Entry entry) {
                Minecraft.getInstance().setScreen(new PlaylistPreviewScreen((Playlist) entry.item, KuGouMusicUserScreen.this));
            }
        };
    }

    public KuGouMusicUserScreen(Screen parent) {
        super(Component.translatable("concerto.screen.user"), parent);
    }

    @Override
    public void onPageTurned(int page) {
        ConcertoRunner.run(() -> {
            if (KuGouMusicApiClient.LOCAL_USER.updateLoginStatus()) {
                this.playlistList.reset(KuGouMusicApiClient.LOCAL_USER.getUserPlaylists(page + 1), null);
            }
        });
    }

    private boolean loggedIn() {
        return KuGouMusicApiClient.LOCAL_USER.isLoggedIn();
    }

    @Override
    protected void init() {
        super.init();
        int actionX = this.actionBarX();
        int actionCount = ClientConfig.INSTANCE.options.kuGouMusicLite ? 3 : 2;
        int actionWidth = this.actionBarWidth() / actionCount;
        if (!this.loggedIn()) {
            Minecraft.getInstance().setScreen(new KuGouMusicLoginScreen(null));
        }
        this.playlistList = this.initWidget();

        this.onPageTurned(0);
        this.addRenderableWidget(this.playlistList);
        this.addWidget(this.playlistList);

        if (ClientConfig.INSTANCE.options.kuGouMusicLite) {
            this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.daily_vip"),
                    button -> CompletableFuture.supplyAsync(
                            () -> KuGouMusicApiClient.INSTANCE.receiveVip()
                    ).thenAccept(jsonObject -> {
                        String text = jsonObject.map(object -> object.get("error_code"))
                                .map(JsonElement::getAsInt)
                                .map(code -> {
                                    switch (code) {
                                        case 0 -> {
                                            return "success";
                                        }
                                        case 131001 -> {
                                            return "duplicate";
                                        }
                                        default -> {
                                            return "failed";
                                        }
                                    }
                                })
                                .orElse("failed");

                        Minecraft.getInstance().submit(() -> displayAlert(Component.translatable("concerto.screen.daily_vip." + text)));
                    })).pos(actionX, this.bottomBarY()).size(actionWidth, 20).build()
            );
        }

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.play"), button -> {
            ConcertoListWidget<KuGouMusicPlaylist>.Entry entry = this.playlistList.getSelected();
            if (entry != null) {
                Minecraft.getInstance().setScreen(new PlaylistPreviewScreen(entry.item, this));
            }
        }).pos(actionX + (actionCount - 2) * actionWidth, this.bottomBarY()).size(actionWidth, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.logout"), button -> {
            if (this.loggedIn()) {
                KuGouMusicApiClient.LOCAL_USER.logout();
            }
            Minecraft.getInstance().setScreen(new KuGouMusicLoginScreen(this));
        }).pos(actionX + (actionCount - 1) * actionWidth, this.bottomBarY())
                .size(this.actionBarWidth() - (actionCount - 1) * actionWidth, 20).build());
    }

    @Override
    public void render(GuiGraphics matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        if (!this.loggedIn()) {
            matrices.drawCenteredString(this.font, Component.translatable("concerto.screen.kugou.not_login"),
                    this.width / 2, this.height / 2, 0xffffffff);
        }
    }
}
