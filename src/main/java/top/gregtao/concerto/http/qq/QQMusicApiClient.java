package top.gregtao.concerto.http.qq;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import top.gregtao.concerto.enums.Sources;
import top.gregtao.concerto.http.HttpApiClient;
import top.gregtao.concerto.music.lyric.LRCFormatLyric;
import top.gregtao.concerto.music.lyric.Lyric;
import top.gregtao.concerto.util.HttpUtil;
import top.gregtao.concerto.util.JsonUtil;
import top.gregtao.concerto.util.TextUtil;

import java.net.CookieManager;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QQMusicApiClient extends HttpApiClient {

    public static CookieManager SHARED_COOKIE = new CookieManager();

    public static QQMusicApiClient U = new QQMusicApiClient("u");

    public static QQMusicApiClient C = new QQMusicApiClient("c");

    public QQMusicApiClient(String prefix) {
        super(Sources.QQ_MUSIC.asString(), HttpUtil.createCorrectURL("https://" + (prefix = prefix + ".y.qq.com")), Map.of(
                "Referer", "https://" + prefix, "Host", prefix
        ), List.of(), SHARED_COOKIE);
    }

    public String generateGuid() {
        return String.valueOf((new Random().nextLong(1000000000, 9999999999L)));
    }

    public String getMP3Filename(String mid, String mediaMid) {
        return "M800" + mid + mediaMid + ".mp3";
    }

    public String getMusicLink(String mid, String mediaMid) throws Exception {
        String uin = this.getCookie("uin"), guid = this.generateGuid();
        uin = uin.isEmpty() ? "956581739" : uin;
        JsonObject object = JsonUtil.from(this.get("/cgi-bin/musicu.fcg?-=getplaysongvkey&format=json&loginUin=" + uin + "&hostUin=0&inCharset=utf-8&needNewCode=0&outCharset=utf-8&platform=yqq.json&data=%7B%22req_0%22%3A%7B%22module%22%3A%22vkey.GetVkeyServer%22%2C%22method%22%3A%22CgiGetVkey%22%2C%22param%22%3A%7B%22filename%22%3A%5B%22" + this.getMP3Filename(mid, mediaMid) + "%22%5D%2C%22guid%22%3A%22" + guid + "%22%2C%22songmid%22%3A%5B%22" + mid + "%22%5D%2C%22songtype%22%3A%5B0%5D%2C%22uin%22%3A%22" + uin + "%22%2C%22loginflag%22%3A1%2C%22platform%22%3A%2220%22%7D%7D%2C%22comm%22%3A%7B%22uin%22%3A" + uin + "%2C%22format%22%3A%22json%22%2C%22ct%22%3A24%2C%22cv%22%3A0%7D%7D"));
        JsonObject data = object.getAsJsonObject("req_0").getAsJsonObject("data");
        JsonObject midUrlInfo = data.getAsJsonArray("midurlinfo").get(0).getAsJsonObject();
        String link = midUrlInfo.get("purl").getAsString();
        JsonArray sip = data.getAsJsonArray("sip");
        if (sip.isJsonNull() || sip.size() == 0 || link.isEmpty()) {
            return "";
        } else {
            return sip.get(0).getAsString() + link;
        }
    }

    public JsonObject getMusicDetail(String mid) throws Exception {
        return JsonUtil.from(this.get("/cgi-bin/musicu.fcg?data=%7B%22songinfo%22%3A%7B%22method%22%3A%22get_song_detail_yqq%22,%22param%22%3A%7B%22song_mid%22%3A%22" + mid + "%22%7D,%20%22module%22%3A%22music.pf_song_detail_svr%22%7D%7D"));
    }

    public Lyric getLyric(String mid) throws Exception {
        Pattern pattern = Pattern.compile("MusicJsonCallback_lrc\\(([\\d\\D]+)\\)");
        String result = this.get("/lyric/fcgi-bin/fcg_query_lyric_new.fcg?callback=MusicJsonCallback_lrc&pcachetime=" + TextUtil.getCurrentTime() + "&songmid=" + mid + "&g_tk=5381&jsonpCallback=MusicJsonCallback_lrc&loginUin=0&hostUin=0&format=jsonp&inCharset=utf8&outCharset=utf-8¬ice=0&platform=yqq&needNewCode=0", Map.of("Referer", "https://y.qq.com"));
        Matcher matcher = pattern.matcher(result);
        if (!matcher.find()) return null;
        result = matcher.group(1);
        JsonObject object = JsonUtil.from(result);
        String raw = new String(Base64.getDecoder().decode(object.get("lyric").getAsString()));
        return new LRCFormatLyric().load(raw);
    }

    private String extractVKey(String str) {
        Pattern pattern = Pattern.compile("vkey=([0-9A-Z]+)\\u0026");
        Matcher matcher = pattern.matcher(str);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }
}
