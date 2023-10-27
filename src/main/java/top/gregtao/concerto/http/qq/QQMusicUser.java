package top.gregtao.concerto.http.qq;

import com.google.gson.JsonObject;

public class QQMusicUser {

    public String nickname;
    public String gtk = "";
    public boolean loggedIn = false;
    public final QQMusicApiClient apiClient;

    public QQMusicUser(QQMusicApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public void updateLoginStatus() {
        try {
            String uin = this.apiClient.getUin();
            JsonObject object = this.apiClient.requestSignedApi("userInfo.BaseUserInfoServer", "get_user_baseinfo_v2", "{\"vec_uin\":[\"" + uin + "\"]}");
            JsonObject data = object.getAsJsonObject("data").getAsJsonObject("map_userinfo").getAsJsonObject(uin);
            this.nickname = data.get("nick").getAsString();
            this.loggedIn = true;
            this.apiClient.searchMusic("fontaine");
        } catch (Exception e) {
            this.loggedIn = false;
        }
    }

    public void logout() {
        this.apiClient.clearCookie();
    }
}
