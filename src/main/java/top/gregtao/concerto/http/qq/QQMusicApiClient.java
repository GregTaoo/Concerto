package top.gregtao.concerto.http.qq;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import top.gregtao.concerto.enums.Sources;
import top.gregtao.concerto.http.HttpApiClient;
import top.gregtao.concerto.http.encrypt.QQMusicApiEncrypt;
import top.gregtao.concerto.music.lyric.LRCFormatLyric;
import top.gregtao.concerto.music.lyric.Lyric;
import top.gregtao.concerto.util.HttpUtil;
import top.gregtao.concerto.util.JsonUtil;
import top.gregtao.concerto.util.MathUtil;
import top.gregtao.concerto.util.TextUtil;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URISyntaxException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QQMusicApiClient extends HttpApiClient {

    public static CookieManager SHARED_COOKIE = new CookieManager();

    public static QQMusicApiClient U = new QQMusicApiClient("u");

    public static QQMusicApiClient C = new QQMusicApiClient("c");

    public static QQMusicUser LOCAL_USER = new QQMusicUser(U);

    public QQMusicApiClient(String prefix) {
        super(Sources.QQ_MUSIC.asString(), HttpUtil.createCorrectURL("https://" + (prefix = prefix + ".y.qq.com")), Map.of(
                "Referer", "https://" + prefix, "Host", prefix
        ), List.of(), SHARED_COOKIE);
    }

    public String generateGuid() {
        return String.valueOf((new Random().nextLong(1000000000L, 9999999999L)));
    }

    public String getMP3Filename(String mid, String mediaMid) {
        return "M800" + mid + mediaMid + ".mp3";
    }

    public String getUin() throws IOException, URISyntaxException {
        String uin = this.getCookie("https://u.y.qq.com", "wxuin");
        if (!uin.isEmpty()) return uin;
        uin = this.getCookie("https://u.y.qq.com", "uin");
        return uin.isEmpty() ? "111111111" : uin; // An unknown uin
    }

    public String getQQLoginUi() throws IOException, URISyntaxException {
        String uuid = UUID.randomUUID().toString().toUpperCase();
        this.setCookie("https://graph.qq.com", "ui", uuid);
        return uuid;
    }

    public String getQQLoginGTK() throws IOException, URISyntaxException {
        String sKey = this.getCookie("https://graph.qq.com", "p_skey");
        return QQMusicApiEncrypt.getGTK(sKey);
    }

    public String getMusicLink(String mid, String mediaMid) throws Exception {
        String uin = this.getUin(), guid = this.generateGuid();
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

    private final Pattern LYRIC_PATTERN = Pattern.compile("MusicJsonCallback_lrc\\(([\\d\\D]+)\\)");

    public Lyric getLyric(String mid) throws Exception {
        String result = this.get("/lyric/fcgi-bin/fcg_query_lyric_new.fcg?callback=MusicJsonCallback_lrc&pcachetime=" + TextUtil.getCurrentTime() + "&songmid=" + mid + "&g_tk=5381&jsonpCallback=MusicJsonCallback_lrc&loginUin=0&hostUin=0&format=jsonp&inCharset=utf8&outCharset=utf-8¬ice=0&platform=yqq&needNewCode=0", Map.of("Referer", "https://y.qq.com"));
        Matcher matcher = LYRIC_PATTERN.matcher(result);
        if (!matcher.find()) return null;
        result = matcher.group(1);
        JsonObject object = JsonUtil.from(result);
        String raw = new String(Base64.getDecoder().decode(object.get("lyric").getAsString()));
        return new LRCFormatLyric().load(raw);
    }

    private final Pattern WECHAT_QRKEY_PATTERN = Pattern.compile("\"/connect/qrcode/([a-zA-Z0-9]+)\"");

    public String getWeChatQRKey() throws Exception {
        String result = this.get("https://open.weixin.qq.com/connect/qrconnect?appid=wx48db31d50e334801&redirect_uri=https%3A%2F%2Fy.qq.com%2Fportal%2Fwx_redirect.html%3Flogin_type%3D2%26surl%3Dhttps%3A%2F%2Fy.qq.com%2F&response_type=code&scope=snsapi_login&state=STATE&href=https%3A%2F%2Fy.qq.com%2Fmediastyle%2Fmusic_v17%2Fsrc%2Fcss%2Fpopup_wechat.css%23wechat_redirect", Map.of("Referer", "https://y.qq.com"));
        Matcher matcher = WECHAT_QRKEY_PATTERN.matcher(result);
        if (!matcher.find()) return "";
        return matcher.group(1) + ":" + TextUtil.getCurrentTime();
    }

    public String combineWeChatQRLink(String key) {
        try {
            return "https://open.weixin.qq.com/connect/confirm?uuid=" + key.substring(0, key.indexOf(":"));
        } catch (StringIndexOutOfBoundsException e) {
            return "err";
        }
    }

    private final Pattern WECHAT_QRKEY_UPDATE_PATTERN = Pattern.compile("window.wx_errcode=([0-9]{3});window.wx_code='([0-9a-zA-Z]*)';");

    // 402: expired, 408: waiting, 404: scanning, 405: success
    public Pair<Integer, String> getWeChatQRStatus(String key) throws Exception {
        String[] args = key.split(":");
        if (args.length != 2) return Pair.of(-1, "");
        String result = this.get("https://lp.open.weixin.qq.com/connect/l/qrconnect?uuid=" + args[0] + "&_=" + args[1], Map.of("Host", "https://lp.open.weixin.qq.com"));
        Matcher matcher = WECHAT_QRKEY_UPDATE_PATTERN.matcher(result);
        if (!matcher.find()) return Pair.of(-1, "");
        return Pair.of(MathUtil.parseIntOrElse(matcher.group(1), -1), matcher.group(2));
    }

    public void setWxLoginCookies(String code) throws Exception {
        this.post("/cgi-bin/musicu.fcg", "{\"comm\":{\"tmeAppID\":\"qqmusic\",\"tmeLoginType\":\"1\",\"g_tk\":1515983069,\"platform\":\"yqq\",\"ct\":24,\"cv\":0},\"req\":{\"module\":\"music.login.LoginServer\",\"method\":\"Login\",\"param\":{\"strAppid\":\"wx48db31d50e334801\",\"code\":\"" + code + "\"}}}", ContentType.JSON);
    }

    public String getQQLoginQRLink() {
        return "https://ssl.ptlogin2.qq.com/ptqrshow?appid=716027609&e=2&l=M&s=3&d=72&v=4&daid=383&pt_3rd_aid=100497308&u1=https%3A%2F%2Fgraph.qq.com%2Foauth2.0%2Flogin_jump";
    }

    private final Pattern QQ_QRKEY_UPDATE_PATTERN = Pattern.compile("ptuiCB\\('([0-9]+)','0','([0-9a-zA-Z:/&=?_.%]*)'");

    // 66: waiting, 67: verifying, 0: success
    public Pair<Integer, String> getQQLoginQRStatus() throws Exception {
        String qrSig = this.getCookie("https://ptlogin2.qq.com", "qrsig");
        String result = this.get("https://ssl.ptlogin2.qq.com/ptqrlogin?u1=https%3A%2F%2Fgraph.qq.com%2Foauth2.0%2Flogin_jump&ptqrtoken=" + QQMusicApiEncrypt.hash33(qrSig) + "&ptredirect=0&h=1&t=1&g=1&from_ui=1&ptlang=2052&action=0-1-" + TextUtil.getCurrentTime() + "&js_ver=23052315&js_type=1&login_sig=&pt_uistyle=40&aid=716027609&daid=383&pt_3rd_aid=100497308&&pt_js_version=v1.42.4").replace(" ", "");
        Matcher matcher = QQ_QRKEY_UPDATE_PATTERN.matcher(result);
        if (!matcher.find()) return Pair.of(-1, "");
        System.out.println(result);
        return Pair.of(MathUtil.parseIntOrElse(matcher.group(1), -1), matcher.group(2));
    }

    private final Pattern QQ_REDIRECT_LOCATION_PATTERN = Pattern.compile("&code=([A-Z0-9]+)&");

    public void authorizeQQLogin() throws Exception {
        String link = this.post("https://graph.qq.com/oauth2.0/authorize", "response_type=code&client_id=100497308&redirect_uri=https%3A%2F%2Fy.qq.com%2Fportal%2Fwx_redirect.html%3Flogin_type%3D1%26surl%3Dhttps%3A%2F%2Fy.qq.com%2F&scope=get_user_info%2Cget_app_friends&state=state&switch=&from_ptlogin=1&src=1&update_auth=1&openapi=1010_1030&g_tk=" + this.getQQLoginGTK() + "&auth_time=" + TextUtil.getCurrentTime() + "&ui=" + this.getQQLoginUi(), ContentType.FORM, false);
        Matcher matcher = QQ_REDIRECT_LOCATION_PATTERN.matcher(link);
        if (matcher.find()) {
            this.setQQLoginCookies(matcher.group(1));
        }
    }

    public void setQQLoginCookies(String code) throws Exception {
        this.post("/cgi-bin/musicu.fcg", "{\"comm\":{\"g_tk\":1515983069,\"platform\":\"yqq\",\"ct\":24,\"cv\":0},\"req\":{\"module\":\"QQConnectLogin.LoginServer\",\"method\":\"QQLogin\",\"param\":{\"code\":\"" + code + "\"}}}", ContentType.JSON);
    }

    public JsonObject requestSignedApi(String module, String method, String params) throws Exception {
        String data = "{\"comm\":{\"cv\":4747474,\"ct\":24,\"format\":\"json\",\"inCharset\":\"utf-8\",\"outCharset\":\"utf-8\",\"notice\":0,\"platform\":\"yqq.json\",\"needNewCode\":1,\"uin\":\"" + this.getUin() + "\",\"g_tk_new_20200303\":1325348968,\"g_tk\":1325348968},\"req_1\":{\"module\":\"" + module + "\",\"method\":\"" + method + "\",\"param\":" + params + "}}";
        JsonObject object = JsonUtil.from(this.post("/cgi-bin/musics.fcg?_=" + TextUtil.getCurrentTime() + "&sign=" + QQMusicApiEncrypt.getSign(data), data, ContentType.JSON));
        return object.getAsJsonObject("req_1");
    }

    public JsonObject requestSignedApi(String module, String method, Map<?, ?> params) throws Exception {
        return this.requestSignedApi(module, method, ContentType.toJson(params));
    }
}
