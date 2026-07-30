package top.gregtao.concerto.screen.qq;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.gregtao.concerto.core.http.qq.QQMusicApiClient;
import top.gregtao.concerto.core.music.list.QQMusicPlaylist;
import top.gregtao.concerto.screen.UserPlaylistScreen;

import java.util.List;

public class QQMusicUserScreen extends UserPlaylistScreen<QQMusicPlaylist> {
    public QQMusicUserScreen(Screen parent) {
        super(Component.translatable("concerto.screen.index.qq"), parent);
    }

    @Override
    protected boolean isLoggedIn() {
        return QQMusicApiClient.LOCAL_USER.loggedIn;
    }

    @Override
    protected boolean refreshLoginStatus() {
        return QQMusicApiClient.LOCAL_USER.updateLoginStatus();
    }

    @Override
    protected List<QQMusicPlaylist> getUserPlaylists(int page) {
        return QQMusicApiClient.LOCAL_USER.getUserPlaylists();
    }

    @Override
    protected Screen createLoginScreen(Screen parent) {
        return new QQMusicLoginScreens(parent);
    }

    @Override
    protected Screen createSearchScreen(Screen parent) {
        return new QQMusicSearchScreen(parent);
    }

    @Override
    protected Component getAccountSummary() {
        return Component.translatable("concerto.screen.qq.welcome", QQMusicApiClient.LOCAL_USER.nickname);
    }

    @Override
    protected Component getLoggedOutSummary() {
        return Component.translatable("concerto.screen.qq.not_login");
    }

    @Override
    protected String getAvatarUrl() {
        String avatarUrl = QQMusicApiClient.LOCAL_USER.avatarUrl;
        return avatarUrl == null || avatarUrl.isEmpty() ? null : avatarUrl;
    }

    @Override
    protected void logout() {
        QQMusicApiClient.LOCAL_USER.logout();
    }

    @Override
    protected boolean hasPagination() {
        return false;
    }
}
