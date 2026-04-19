package top.gregtao.concerto.core.http.bilibili;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import top.gregtao.concerto.core.enums.Sources;
import top.gregtao.concerto.core.http.HttpApiClient;

import java.util.Map;

public class BilibiliApiClient extends HttpApiClient {

    public static BilibiliApiClient INSTANCE = new BilibiliApiClient();

    public BilibiliApiClient() {
        super(Sources.BILIBILI.asString(), Map.of(), Map.of());
    }

    public JsonObject getVideoData(String bvid) {
        return parseJson(this.open().url("https://api.bilibili.com/x/web-interface/view?bvid=" + bvid).get());
    }

    public JsonObject getAudioUrl(String aid, String cid) {
        return parseJson(this.open().url("https://api.bilibili.com/x/player/playurl?fnval=80&avid=" + aid + "&cid=" + cid).get());
    }

    public String getDirectAudioUrl(String aid, String cid) {
        JsonObject object = this.getAudioUrl(aid, cid).getAsJsonObject("data").getAsJsonObject("dash");
        JsonArray array = object.getAsJsonArray("audio");
        return array.get(0).getAsJsonObject().get("baseUrl").getAsString();
    }
}
