package top.gregtao.concerto.screen.kugou;

import com.google.gson.JsonElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import top.gregtao.concerto.api.WithMetaData;
import top.gregtao.concerto.config.ClientConfig;
import top.gregtao.concerto.http.kugou.KuGouMusicApiClient;
import top.gregtao.concerto.music.list.KuGouMusicPlaylist;
import top.gregtao.concerto.music.list.Playlist;
import top.gregtao.concerto.screen.PageScreen;
import top.gregtao.concerto.screen.PlaylistPreviewScreen;
import top.gregtao.concerto.screen.widget.ConcertoListWidget;
import top.gregtao.concerto.screen.widget.MetadataListWidget;
import top.gregtao.concerto.util.ConcertoRunner;

import java.util.concurrent.CompletableFuture;

public class KuGouMusicUserScreen extends PageScreen {

    private MetadataListWidget<KuGouMusicPlaylist> playlistList;

    private <T extends WithMetaData> MetadataListWidget<T> initWidget() {
        return new MetadataListWidget<>(this.width, this.height - 55, 20, 18) {
            @Override
            public void onDoubleClicked(ConcertoListWidget<T>.Entry entry) {
                MinecraftClient.getInstance().setScreen(new PlaylistPreviewScreen((Playlist) entry.item, KuGouMusicUserScreen.this));
            }
        };
    }

    public KuGouMusicUserScreen(Screen parent) {
        super(Text.translatable("concerto.screen.user"), parent);
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
        if (!this.loggedIn()) {
            MinecraftClient.getInstance().setScreen(new KuGouMusicLoginScreen(null));
        }
        this.playlistList = this.initWidget();

        this.onPageTurned(0);
        this.addDrawableChild(this.playlistList);
        this.addSelectableChild(this.playlistList);

        if (ClientConfig.INSTANCE.options.kuGouMusicLite) {
            this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.daily_vip"),
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

                        displayAlert(Text.translatable("concerto.screen.daily_vip." + text));
                    })).position(this.width / 2 - 10, this.height - 30).size(70, 20).build()
            );
        }

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.play"), button -> {
            ConcertoListWidget<KuGouMusicPlaylist>.Entry entry = this.playlistList.getSelectedOrNull();
            if (entry != null) {
                MinecraftClient.getInstance().setScreen(new PlaylistPreviewScreen(entry.item, this));
            }
        }).position(this.width / 2 + 65, this.height - 30).size(50, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.logout"), button -> {
            if (this.loggedIn()) {
                KuGouMusicApiClient.LOCAL_USER.logout();
            }
            MinecraftClient.getInstance().setScreen(new KuGouMusicLoginScreen(this));
        }).position(this.width / 2 + 120, this.height - 30).size(50, 20).build());
    }

    @Override
    public void render(DrawContext matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        if (!this.loggedIn()) {
            matrices.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("concerto.screen.kugou.not_login"),
                    this.width / 2, this.height / 2, 0xffffffff);
        }
    }
}
