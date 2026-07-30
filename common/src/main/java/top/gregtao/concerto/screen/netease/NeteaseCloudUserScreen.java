package top.gregtao.concerto.screen.netease;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.gregtao.concerto.core.http.netease.NeteaseCloudApiClient;
import top.gregtao.concerto.core.music.list.NeteaseCloudPlaylist;
import top.gregtao.concerto.screen.PlaylistPreviewScreen;
import top.gregtao.concerto.screen.UserPlaylistScreen;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class NeteaseCloudUserScreen extends UserPlaylistScreen<NeteaseCloudPlaylist> {
    public NeteaseCloudUserScreen(Screen parent) {
        super(Component.translatable("concerto.screen.index.163"), parent);
    }

    @Override
    protected boolean isLoggedIn() {
        return NeteaseCloudApiClient.LOCAL_USER.loggedIn;
    }

    @Override
    protected boolean refreshLoginStatus() {
        return NeteaseCloudApiClient.LOCAL_USER.updateLoginStatus();
    }

    @Override
    protected List<NeteaseCloudPlaylist> getUserPlaylists(int page) {
        return NeteaseCloudApiClient.LOCAL_USER.getUserPlaylists(page);
    }

    @Override
    protected Screen createLoginScreen(Screen parent) {
        return new NeteaseCloudLoginScreens(parent);
    }

    @Override
    protected Screen createSearchScreen(Screen parent) {
        return new NeteaseCloudSearchScreen(parent);
    }

    @Override
    protected Component getAccountSummary() {
        return Component.translatable("concerto.screen.163.welcome", NeteaseCloudApiClient.LOCAL_USER.nickname);
    }

    @Override
    protected Component getLoggedOutSummary() {
        return Component.translatable("concerto.screen.163.not_login");
    }

    @Override
    protected String getAvatarUrl() {
        String avatarUrl = NeteaseCloudApiClient.LOCAL_USER.avatarUrl;
        return avatarUrl == null || avatarUrl.isEmpty() ? null : avatarUrl;
    }

    @Override
    protected void logout() {
        if (this.isLoggedIn()) NeteaseCloudApiClient.LOCAL_USER.logout();
    }

    @Override
    protected int serviceActionCount() {
        return 1;
    }

    @Override
    protected void addServiceActions(int x, int y, int width) {
        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.daily_recommendation"), button ->
                CompletableFuture.supplyAsync(NeteaseCloudApiClient.INSTANCE::getDailyRecommendation)
                        .thenAccept(playlist -> Minecraft.getInstance().execute(() ->
                                Minecraft.getInstance().gui.setScreen(new PlaylistPreviewScreen(playlist, this))))
        ).pos(x, y).size(width, 20).build());
    }
}
