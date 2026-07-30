package top.gregtao.concerto.screen.kugou;

import com.google.gson.JsonElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.gregtao.concerto.core.config.ClientConfig;
import top.gregtao.concerto.core.http.kugou.KuGouMusicApiClient;
import top.gregtao.concerto.core.music.list.KuGouMusicPlaylist;
import top.gregtao.concerto.screen.UserPlaylistScreen;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class KuGouMusicUserScreen extends UserPlaylistScreen<KuGouMusicPlaylist> {
    public KuGouMusicUserScreen(Screen parent) {
        super(Component.translatable("concerto.screen.index.kugou"), parent);
    }

    @Override
    protected boolean isLoggedIn() {
        return KuGouMusicApiClient.LOCAL_USER.isLoggedIn();
    }

    @Override
    protected boolean refreshLoginStatus() {
        return KuGouMusicApiClient.LOCAL_USER.updateLoginStatus();
    }

    @Override
    protected List<KuGouMusicPlaylist> getUserPlaylists(int page) {
        return KuGouMusicApiClient.LOCAL_USER.getUserPlaylists(page + 1);
    }

    @Override
    protected Screen createLoginScreen(Screen parent) {
        return new KuGouMusicLoginScreen(parent);
    }

    @Override
    protected Screen createSearchScreen(Screen parent) {
        return new KuGouMusicSearchScreen(parent);
    }

    @Override
    protected Component getAccountSummary() {
        return Component.translatable("concerto.screen.kugou.welcome", KuGouMusicApiClient.LOCAL_USER.getUserName());
    }

    @Override
    protected Component getLoggedOutSummary() {
        return Component.translatable("concerto.screen.kugou.not_login");
    }

    @Override
    protected String getAvatarUrl() {
        String avatarUrl = KuGouMusicApiClient.LOCAL_USER.getAvatarUrl();
        return avatarUrl == null || avatarUrl.isEmpty() ? null : avatarUrl;
    }

    @Override
    protected void logout() {
        if (this.isLoggedIn()) KuGouMusicApiClient.LOCAL_USER.logout();
    }

    @Override
    protected int serviceActionCount() {
        return ClientConfig.INSTANCE.options.kuGouMusicLite ? 1 : 0;
    }

    @Override
    protected void addServiceActions(int x, int y, int width) {
        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.daily_vip"), button ->
                CompletableFuture.supplyAsync(KuGouMusicApiClient.INSTANCE::receiveVip).thenAccept(jsonObject -> {
                    String text = jsonObject.map(object -> object.get("error_code"))
                            .map(JsonElement::getAsInt)
                            .map(code -> switch (code) {
                                case 0 -> "success";
                                case 131001 -> "duplicate";
                                default -> "failed";
                            }).orElse("failed");
                    Minecraft.getInstance().execute(() -> displayAlert(Component.translatable("concerto.screen.daily_vip." + text)));
                })
        ).pos(x, y).size(width, 20).build());
    }
}
