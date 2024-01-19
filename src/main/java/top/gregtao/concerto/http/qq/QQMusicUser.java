package top.gregtao.concerto.http.qq;

import com.google.gson.JsonObject;
import top.gregtao.concerto.music.list.QQMusicPlaylist;
import top.gregtao.concerto.player.MusicPlayerHandler;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class QQMusicUser {

    public String nickname;
    public String signature;
    public String avatarUrl;
    public String gtk = "";
    public boolean loggedIn = false;
    public final QQMusicApiClient apiClient;

    public QQMusicUser(QQMusicApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public void updateLoginStatus() {
        try {
            String uin = this.apiClient.getQQUin();
            JsonObject object = this.apiClient.requestSignedApi("userInfo.BaseUserInfoServer", "get_user_baseinfo_v2", "\"vec_uin\":[\"" + uin + "\"]");
            JsonObject data = object.getAsJsonObject("data").getAsJsonObject("map_userinfo").getAsJsonObject(uin);
            this.nickname = data.get("nick").getAsString();
            this.signature = data.get("desc").getAsString();
            this.avatarUrl = data.get("headurl").getAsString();
            this.apiClient.getQQLoginGTK();
            this.loggedIn = true;
        } catch (Exception e) {
            this.loggedIn = false;
        }
    }

    public List<QQMusicPlaylist> getUserPlaylists() {
        try {
            JsonObject object = QQMusicApiClient.parseJson(this.apiClient.openCApi().url("https://c.y.qq.com/rsc/fcgi-bin/fcg_get_profile_homepage.fcg?_=" + QQMusicApiClient.getQQLoginTimestamp() + "&cv=4747474&ct=20&format=json&inCharset=utf-8&outCharset=utf-8&notice=0&platform=yqq.json&needNewCode=1&uin=" + this.apiClient.getQQUin() + "&g_tk_new_20200303=" + this.apiClient.getQQLoginGTK() + "&mesh_devops=DevopsBase&cid=205360838&userid=0&reqfrom=1&reqtype=0")
                    .setFixedReferer("https://y.qq.com/").get());
            List<QQMusicPlaylist> playlists = new ArrayList<>();
            if (object != null) {
                object.getAsJsonObject("data").getAsJsonObject("mydiss").getAsJsonArray("list")
                        .forEach(element -> playlists.add(new QQMusicPlaylist(element.getAsJsonObject().get("dissid").getAsString(), false)));
            }
            MusicPlayerHandler.loadInThreadPool(playlists);
            return playlists;
        } catch (IOException | URISyntaxException e) {
            return List.of();
        }
    }

    public void logout() {
        this.apiClient.clearCookie();
    }
}
