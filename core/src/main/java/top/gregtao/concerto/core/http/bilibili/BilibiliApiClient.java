package top.gregtao.concerto.core.http.bilibili;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import top.gregtao.concerto.core.enums.Sources;
import top.gregtao.concerto.core.http.HttpApiClient;

import java.util.Map;

public class BilibiliApiClient extends HttpApiClient {

    public static final Map<String, String> REQUEST_HEADERS = Map.of(
            "Referer", "https://www.bilibili.com/",
            "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"
    );
    public static BilibiliApiClient INSTANCE = new BilibiliApiClient();

    public BilibiliApiClient() {
        super(Sources.BILIBILI.asString(), REQUEST_HEADERS, Map.of());
    }

    public JsonObject getVideoData(String bvid) {
        return parseJson(this.open().url("https://api.bilibili.com/x/web-interface/view?bvid=" + bvid).get());
    }

    public JsonObject getAudioUrl(String aid, String cid) {
        return parseJson(this.open().url("https://api.bilibili.com/x/player/playurl?fnval=80&avid=" + aid + "&cid=" + cid).get());
    }

    public String getDirectAudioUrl(String aid, String cid) {
        JsonObject object = this.getAudioUrl(aid, cid).getAsJsonObject("data").getAsJsonObject("dash");
        JsonArray streams = object.getAsJsonArray("audio");
        JsonObject selected = null;
        int selectedBandwidth = -1;
        for (int index = 0; index < streams.size(); index++) {
            JsonObject stream = streams.get(index).getAsJsonObject();
            String codecs = stream.has("codecs") ? stream.get("codecs").getAsString() : "";
            int bandwidth = stream.has("bandwidth") ? stream.get("bandwidth").getAsInt() : 0;
            // The bundled JAAD decoder cannot reliably decode Bilibili's HE-AAC
            // stream (mp4a.40.5); prefer the highest-bitrate AAC-LC alternative.
            if ("mp4a.40.2".equals(codecs) && bandwidth > selectedBandwidth) {
                selected = stream;
                selectedBandwidth = bandwidth;
            }
        }
        if (selected == null && !streams.isEmpty()) {
            selected = streams.get(0).getAsJsonObject();
        }
        if (selected == null || !selected.has("baseUrl")) {
            throw new IllegalStateException("Bilibili playurl response contains no audio stream");
        }
        return selected.get("baseUrl").getAsString();
    }
}
