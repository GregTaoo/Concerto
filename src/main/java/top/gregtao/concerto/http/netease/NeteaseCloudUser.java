package top.gregtao.concerto.http.netease;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import top.gregtao.concerto.http.HttpApiClient;
import top.gregtao.concerto.music.list.NeteaseCloudPlaylist;
import top.gregtao.concerto.util.JsonUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NeteaseCloudUser {

    public long uid;
    public String nickname;
    public String signature;
    public boolean loggedIn = false;

    public final NeteaseCloudApiClient apiClient;

    public NeteaseCloudUser(NeteaseCloudApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public boolean updateLoginStatus() {
        try {
            JsonObject object = JsonUtil.from(this.apiClient.post("/api/w/nuser/account/get"));
            if (object.get("account").isJsonNull()) {
                this.loggedIn = false;
            } else {
                this.uid = object.getAsJsonObject("profile").get("userId").getAsLong();
                JsonObject detail = JsonUtil.from(this.apiClient.post("/api/v1/user/detail/" + this.uid));
                JsonObject profile = detail.getAsJsonObject("profile");
                this.nickname = profile.get("nickname").getAsString();
                this.signature = profile.get("signature").getAsString();
                return this.loggedIn = true;
            }
        } catch (Exception e) {
            this.loggedIn = false;
            throw new RuntimeException(e);
        }
        return false;
    }

    public void logout() {
        try {
            this.apiClient.get("/api/logout");
            this.apiClient.clearCookie();
        } catch (Exception ignored) {}
        this.loggedIn = false;
    }

    public List<NeteaseCloudPlaylist> userPlaylists(int page) {
        List<NeteaseCloudPlaylist> lists = new ArrayList<>();
        try {
            JsonArray array = JsonUtil.from(this.apiClient.post("/api/user/playlist", Map.of(
                    "uid", this.uid, "limit", 30, "offset", 30 * page, "includeVideo", true
            ), HttpApiClient.ContentType.FORM)).getAsJsonArray("playlist");
            array.forEach(element -> lists.add(new NeteaseCloudPlaylist(element.getAsJsonObject(), false, true)));
            return lists;
        } catch (Exception e) {
            return lists;
        }
    }

}
