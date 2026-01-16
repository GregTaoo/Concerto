package top.gregtao.concerto.http.kugou;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import top.gregtao.concerto.config.ClientConfig;
import top.gregtao.concerto.music.list.KuGouMusicPlaylist;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class KuGouMusicUser {

    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private long userId;

    private String userName;

    private String avatarUrl;

    private boolean loggedIn = false;

    private boolean isLite = false;

    private VIPLevel vipLevel = VIPLevel.NONE;

    private LocalDateTime vipExpireTime;

    private final KuGouMusicApiClient apiClient;

    public KuGouMusicUser(KuGouMusicApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public void setLoggedIn(boolean loggedIn) {
        this.loggedIn = loggedIn;
    }

    public boolean isLite() {
        return isLite;
    }

    public LocalDateTime getVipExpireTime() {
        return vipExpireTime;
    }

    public VIPLevel getVipLevel() {
        return vipLevel;
    }

    public boolean updateLoginStatus() {
        Optional<JsonObject> optional = apiClient.getUserDetail();

        try {
            JsonObject data = optional.map(json -> json.getAsJsonObject("data"))
                    .orElseThrow();

            Optional<JsonObject> dataOpt = Optional.of(data);
            this.userName = dataOpt
                    .map(json -> json.get("nickname"))
                    .map(JsonElement::getAsString)
                    .orElseThrow();

            this.avatarUrl = dataOpt
                    .map(json -> json.get("pic"))
                    .map(JsonElement::getAsString)
                    .orElseThrow();

            this.loggedIn = true;
            this.isLite = ClientConfig.INSTANCE.options.kuGouMusicLite;
            return true;
        } catch (Exception e) {
            this.logout();
            return false;
        }
    }

    public boolean updateLoginStatusAndDfid() {
        this.apiClient.updateDfid();
        return updateLoginStatus();
    }

    public boolean updateVIPStatus() {
        if (!this.loggedIn) return false;
        Optional<JsonObject> optional = KuGouMusicApiClient.INSTANCE.getVIPStatus();
        if (optional.map(json -> json.get("status")).isEmpty()) {
            this.vipLevel = VIPLevel.NONE;
            this.vipExpireTime = null;
            return false;
        }

        Optional<JsonObject> dataOpt = optional.map(json -> json.getAsJsonObject("data"));

        LocalDateTime maxExpireTime = LocalDateTime.now();
        VIPLevel maxExpireLevel = VIPLevel.NONE;


        String vipEndTime = dataOpt.map(json -> json.get("vip_end_time"))
                .map(JsonElement::getAsString)
                .orElse("");

        if (!vipEndTime.isEmpty()) {
            LocalDateTime expireTime = parseTime(vipEndTime);
            if (expireTime.isAfter(maxExpireTime)) {
                maxExpireLevel = VIPLevel.VIP;
                maxExpireTime = expireTime;
            }
        }

        String suVIPEndTime = dataOpt.map(json -> json.get("su_vip_end_time"))
                .map(JsonElement::getAsString)
                .orElse("");

        if (!suVIPEndTime.isEmpty()) {
            LocalDateTime expireTime = parseTime(suVIPEndTime);
            if (expireTime.isAfter(maxExpireTime)) {
                maxExpireLevel = VIPLevel.S_VIP;
                maxExpireTime = expireTime;
            }
        }

        // 如果是概念版, 检查概念版的 VIP
        if (isLite()) {
            Optional<JsonArray> busiVipArray = dataOpt.map(json -> json.getAsJsonArray("busi_vip"));
            if (busiVipArray.isPresent()) {
                for (JsonElement element : busiVipArray.get()) {
                    try {
                        Optional<JsonObject> objectOpt = Optional.ofNullable(element.getAsJsonObject());
                        String productType = objectOpt.map(obj -> obj.get("product_type"))
                                .map(JsonElement::getAsString)
                                .orElseThrow();

                        String expireTime = objectOpt.map(obj -> obj.get("vip_end_time"))
                                .map(JsonElement::getAsString)
                                .orElseThrow();

                        LocalDateTime time = parseTime(expireTime);
                        if (time.isAfter(maxExpireTime)) {
                            if (productType.equals("svip")) {
                                maxExpireLevel = VIPLevel.BUSI_S_VIP;
                            } else if (productType.equals("tvip")) {
                                maxExpireLevel = VIPLevel.BUSI_T_VIP;
                            } else {
                                continue;
                            }
                            maxExpireTime = time;
                        }
                    } catch (Exception ignore) {
                    }
                }
            }
        }

        this.vipLevel = maxExpireLevel;
        this.vipExpireTime = maxExpireLevel != VIPLevel.NONE ? maxExpireTime : null;
        return true;
    }

    private LocalDateTime parseTime(String time) {
        return LocalDateTime.from(FORMATTER.parse(time));
    }

    public void logout() {
        this.apiClient.clearCookie();
        this.loggedIn = false;
        this.apiClient.updateDfid();
    }

    public List<KuGouMusicPlaylist> getUserPlaylists(int page) {
        if (!this.loggedIn) return new ArrayList<>();
        Optional<JsonObject> optional = apiClient.getUserPlaylists(page);

        return optional.map(json -> json.getAsJsonObject("data"))
                .map(data -> data.getAsJsonArray("info"))
                .map(info -> info.asList().stream()
                        .map(element -> new KuGouMusicPlaylist(element.getAsJsonObject(), false, true))
                        .collect(Collectors.toList())
                )
                .orElse(new ArrayList<>());
    }

    /**
     * 判断用户接口版本和配置版本是否相同
     */
    public boolean isVersionSame() {
        return this.isLite == ClientConfig.INSTANCE.options.kuGouMusicLite;
    }

    public enum VIPLevel {
        NONE,
        // 豪华 VIP
        VIP,
        // 超级 VIP
        S_VIP,
        // 概念版 VIP (仅概念版)
        BUSI_S_VIP,
        // 畅听 VIP (仅概念版)
        BUSI_T_VIP
    }
}
