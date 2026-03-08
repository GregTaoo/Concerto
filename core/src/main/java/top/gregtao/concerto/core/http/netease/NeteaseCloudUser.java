package top.gregtao.concerto.core.http.netease;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import top.gregtao.concerto.core.http.HttpApiClient;
import top.gregtao.concerto.core.http.HttpRequestBuilder;
import top.gregtao.concerto.core.music.NeteaseCloudMusic;
import top.gregtao.concerto.core.music.list.NeteaseCloudPlaylist;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NeteaseCloudUser {

    public long uid;
    public String nickname;
    public String signature;
    public String avatarUrl;
    public boolean loggedIn = false;

    public final NeteaseCloudApiClient apiClient;

    public NeteaseCloudUser(NeteaseCloudApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public boolean updateLoginStatus() {
        String url = "https://music.163.com/api/w/nuser/account/get";
        JsonObject object = HttpApiClient.parseJson(this.apiClient.open().url(url).post());
        if (object == null || object.get("account").isJsonNull()) {
            this.loggedIn = false;
        } else {
            this.uid = object.getAsJsonObject("profile").get("userId").getAsLong();
            String url1 = "https://music.163.com/api/v1/user/detail/" + this.uid;
            JsonObject detail = HttpApiClient.parseJson(this.apiClient.open().url(url1).post());
            if (detail == null) return false;
            JsonObject profile = detail.getAsJsonObject("profile");
            this.nickname = profile.get("nickname").getAsString();
            this.signature = profile.get("signature").getAsString();
            this.avatarUrl = profile.get("defaultAvatar").getAsBoolean() ? "" : profile.get("avatarUrl").getAsString();
            return this.loggedIn = true;
        }
        return false;
    }

    public void logout() {
        this.apiClient.open().url("https://music.163.com/api/logout").get();
        this.apiClient.clearCookie();
        this.loggedIn = false;
    }

    public List<NeteaseCloudPlaylist> getUserPlaylists(int page) {
        List<NeteaseCloudPlaylist> lists = new ArrayList<>();
        String url = "https://music.163.com/api/user/playlist";
        JsonObject object = HttpApiClient.parseJson(this.apiClient.open().url(url).post(
                HttpResponse.BodyHandlers.ofString(),
                HttpRequestBuilder.ContentType.FORM,
                Map.of("uid", this.uid, "limit", 30, "offset", 30 * page, "includeVideo", true)
        ));
        if (object != null) {
            JsonArray array = object.getAsJsonArray("playlist");
            array.forEach(element -> lists.add(new NeteaseCloudPlaylist(element.getAsJsonObject(), false, true)));
        }
        return lists;
    }

    public boolean likeMusic(String id, boolean like) {
        JsonObject object = HttpApiClient.parseJson(this.apiClient.open().url("https://music.163.com/api/radio/like", Map.of(
                "alg", "itembased", "trackId", id, "like", like
        )).post());
        return object != null && object.get("code").getAsInt() == 200;
    }

    public boolean likeMusic(NeteaseCloudMusic music) {
        return this.likeMusic(music.getId(), true);
    }

    public boolean dislikeMusic(NeteaseCloudMusic music) {
        return this.likeMusic(music.getId(), false);
    }

}
