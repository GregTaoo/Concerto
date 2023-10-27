package top.gregtao.concerto.http.qq;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import org.apache.commons.lang3.RandomStringUtils;
import top.gregtao.concerto.enums.Sources;
import top.gregtao.concerto.http.HttpApiClient;
import top.gregtao.concerto.http.HttpRequestBuilder;
import top.gregtao.concerto.music.lyric.LRCFormatLyrics;
import top.gregtao.concerto.music.lyric.Lyrics;
import top.gregtao.concerto.util.JsonUtil;
import top.gregtao.concerto.util.MathUtil;
import top.gregtao.concerto.util.TextUtil;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QQMusicApiClient extends HttpApiClient {

    public static final QQMusicApiClient INSTANCE = new QQMusicApiClient();

    public static final QQMusicUser LOCAL_USER = new QQMusicUser(INSTANCE);

    public QQMusicApiClient() {
        super(Sources.QQ_MUSIC.name(), Map.of("Referer", "http://y.qq.com", "Host", "y.qq.com"), Map.of());
    }

    public HttpRequestBuilder openUApi() {
        return this.open().addFixedHeaders(Map.of("Referer", "http://u.y.qq.com", "Host", "u.y.qq.com"));
    }

    public HttpRequestBuilder openCApi() {
        return this.open().addFixedHeaders(Map.of("Referer", "http://c.y.qq.com", "Host", "c.y.qq.com"));
    }

    public HttpRequestBuilder openQQLoginApi() {
        return this.open().addFixedHeaders(Map.of("Referer", "https://ssl.ptlogin2.qq.com", "Host", "ssl.ptlogin2.qq.com"));
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
        return uin.isEmpty() ? "0" : uin;
    }

    public String getQQLoginUi() throws IOException, URISyntaxException {
        Random random = new Random();
        char[] format = "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".toCharArray();
        for (int i = 0; i < format.length; ++i) {
            char ch = format[i];
            if (ch == 'x' || ch == 'y') {
                char r = (char) ((int) ((random.nextDouble() % 1d) * 16));
                char v = ch == 'x' ? r : (char) (r & 0x3 | 0x8);
                format[i] = Integer.toHexString(v).charAt(0);
            }
        }
        String uuid = String.copyValueOf(format).toUpperCase();
        this.setCookie("https://graph.qq.com", "ui", uuid);
        return uuid;
    }

    public String getQQLoginGTK() throws IOException, URISyntaxException {
        if (!LOCAL_USER.gtk.isEmpty() && !LOCAL_USER.gtk.equals("5381")) return LOCAL_USER.gtk;
        String sKey = this.getCookie("https://graph.qq.com", "p_skey");
        String gtk = String.valueOf(calculateGTK(sKey));
        LOCAL_USER.gtk = gtk;
        return gtk;
    }

    public String getMusicLink(String mid, String mediaMid) {
        try {
            String uin = this.getUin(), guid = this.generateGuid();
            String url = "https://u.y.qq.com/cgi-bin/musicu.fcg?-=getplaysongvkey&format=json&loginUin=" + uin + "&hostUin=0&inCharset=utf-8&needNewCode=0&outCharset=utf-8&platform=yqq.json&data=%7B%22req_0%22%3A%7B%22module%22%3A%22vkey.GetVkeyServer%22%2C%22method%22%3A%22CgiGetVkey%22%2C%22param%22%3A%7B%22filename%22%3A%5B%22" + this.getMP3Filename(mid, mediaMid) + "%22%5D%2C%22guid%22%3A%22" + guid + "%22%2C%22songmid%22%3A%5B%22" + mid + "%22%5D%2C%22songtype%22%3A%5B0%5D%2C%22uin%22%3A%22" + uin + "%22%2C%22loginflag%22%3A1%2C%22platform%22%3A%2220%22%7D%7D%2C%22comm%22%3A%7B%22uin%22%3A" + uin + "%2C%22format%22%3A%22json%22%2C%22ct%22%3A24%2C%22cv%22%3A0%7D%7D";
            JsonObject object = parseJson(this.openUApi().url(url).get());
            if (object == null) return "";
            JsonObject data = object.getAsJsonObject("req_0").getAsJsonObject("data");
            JsonObject midUrlInfo = data.getAsJsonArray("midurlinfo").get(0).getAsJsonObject();
            String link = midUrlInfo.get("purl").getAsString();
            JsonArray sip = data.getAsJsonArray("sip");
            if (sip.isJsonNull() || sip.isEmpty() || link.isEmpty()) {
                return "";
            } else {
                return sip.get(0).getAsString() + link;
            }
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public JsonObject getMusicDetail(String mid) {
        String url = "https://u.y.qq.com/cgi-bin/musicu.fcg?data=%7B%22songinfo%22%3A%7B%22method%22%3A%22get_song_detail_yqq%22,%22param%22%3A%7B%22song_mid%22%3A%22" + mid + "%22%7D,%20%22module%22%3A%22music.pf_song_detail_svr%22%7D%7D";
        return parseJson(this.openUApi().url(url).get());
    }

    private final Pattern LYRIC_PATTERN = Pattern.compile("MusicJsonCallback_lrc\\(([\\d\\D]+)\\)");

    public Lyrics getLyric(String mid) {
        String url = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?callback=MusicJsonCallback_lrc&pcachetime=" + TextUtil.getCurrentTime() + "&songmid=" + mid + "&g_tk=5381&jsonpCallback=MusicJsonCallback_lrc&loginUin=0&hostUin=0&format=jsonp&inCharset=utf8&outCharset=utf-8¬ice=0&platform=yqq&needNewCode=0";
        String result = this.openCApi().setFixedReferer("https://y.qq.com").url(url).get().body();
        Matcher matcher = LYRIC_PATTERN.matcher(result);
        if (!matcher.find()) return null;
        result = matcher.group(1);
        JsonObject object = JsonUtil.from(result);
        String raw = new String(Base64.getDecoder().decode(object.get("lyric").getAsString()));
        return new LRCFormatLyrics().load(raw);
    }

    private final Pattern WECHAT_QRKEY_PATTERN = Pattern.compile("\"/connect/qrcode/([a-zA-Z0-9]+)\"");

    public String getWeChatQRKey() {
        String url = "https://open.weixin.qq.com/connect/qrconnect?appid=wx48db31d50e334801&redirect_uri=https%3A%2F%2Fy.qq.com%2Fportal%2Fwx_redirect.html%3Flogin_type%3D2%26surl%3Dhttps%3A%2F%2Fy.qq.com%2F&response_type=code&scope=snsapi_login&state=STATE&href=https%3A%2F%2Fy.qq.com%2Fmediastyle%2Fmusic_v17%2Fsrc%2Fcss%2Fpopup_wechat.css%23wechat_redirect";
        String result = this.openUApi().setFixedReferer("https://y.qq.com").url(url).get().body();
        Matcher matcher = WECHAT_QRKEY_PATTERN.matcher(result);
        if (!matcher.find()) return "";
        return matcher.group(1) + ":" + TextUtil.getCurrentTime();
    }

    public String combineWeChatQRLink(String key) {
        try {
            return "https://open.weixin.qq.com/connect/confirm?uuid=" + key.substring(0, key.indexOf(":"));
        } catch (StringIndexOutOfBoundsException e) {
            return "error";
        }
    }

    private final Pattern WECHAT_QRKEY_UPDATE_PATTERN = Pattern.compile("window.wx_errcode=([0-9]{3});window.wx_code='([0-9a-zA-Z]*)';");

    // 402: expired, 408: waiting, 404: scanning, 405: success
    public Pair<Integer, String> getWeChatQRStatus(String key) throws Exception {
        String[] args = key.split(":");
        if (args.length != 2) return Pair.of(-1, "");
        String url = "https://lp.open.weixin.qq.com/connect/l/qrconnect?uuid=" + args[0] + "&_=" + args[1];
        String result = this.openUApi().addFixedHeader("Host", "https://lp.open.weixin.qq.com").url(url).get().body();
        Matcher matcher = WECHAT_QRKEY_UPDATE_PATTERN.matcher(result);
        if (!matcher.find()) return Pair.of(-1, "");
        return Pair.of(MathUtil.parseIntOrElse(matcher.group(1), -1), matcher.group(2));
    }

    public void setWxLoginCookies(String code) throws IOException, URISyntaxException {
        this.openUApi().url("https://u.y.qq.com/cgi-bin/musicu.fcg").post(
                HttpResponse.BodyHandlers.ofString(),
                HttpRequestBuilder.ContentType.JSON,
                "{\"comm\":{\"tmeAppID\":\"qqmusic\",\"tmeLoginType\":\"1\",\"g_tk\":" + this.getQQLoginGTK() + ",\"platform\":\"yqq\",\"ct\":24,\"cv\":0},\"req\":{\"module\":\"music.login.LoginServer\",\"method\":\"Login\",\"param\":{\"strAppid\":\"wx48db31d50e334801\",\"code\":\"" + code + "\"}}}"
        );
    }

    public String getQQLoginQRLink() {
        return "https://ssl.ptlogin2.qq.com/ptqrshow?appid=716027609&e=2&l=M&s=3&d=72&v=4&daid=383&pt_3rd_aid=100497308&u1=https%3A%2F%2Fgraph.qq.com%2Foauth2.0%2Flogin_jump";
    }

    public static int decryptQRSig(String qrSig) {
        int e = 0;
        for (int i = 0; i < qrSig.length(); ++i) {
            e += (e << 5) + qrSig.charAt(i);
        }
        return e & 2147483647;
    }

    public static int calculateGTK(String pSKey) {
        int hash = 5381;
        for (int i = 0; i < pSKey.length(); ++i) {
            hash += (hash << 5) + pSKey.charAt(i);
        }
        return hash & 2147483647;
    }

    public static String getQQLoginTimestamp() {
        return String.valueOf(Math.floor(System.currentTimeMillis() * 1000));
    }

    private final Pattern QQ_QRKEY_UPDATE_PATTERN = Pattern.compile("ptuiCB\\('([0-9]+)','0','([0-9a-zA-Z:/&=?_.%]*)'");

    // 66: waiting, 67: verifying, 0: success
    public Pair<Integer, String> getQQLoginQRStatus() throws IOException, URISyntaxException {
        String qrSig = this.getCookie("https://ptlogin2.qq.com", "qrsig");
        String url = "https://ssl.ptlogin2.qq.com/ptqrlogin?u1=https%3A%2F%2Fgraph.qq.com%2Foauth2.0%2Flogin_jump&ptqrtoken=" + decryptQRSig(qrSig) + "&ptredirect=0&h=1&t=1&g=1&from_ui=1&ptlang=2052&action=0-1-" + getQQLoginTimestamp() + "&js_ver=23052315&js_type=1&login_sig=&pt_uistyle=40&aid=716027609&daid=383&pt_3rd_aid=100497308&&pt_js_version=v1.42.4";
        String result = this.openQQLoginApi().url(url).get().body().replace(" ", "");
        Matcher matcher = QQ_QRKEY_UPDATE_PATTERN.matcher(result);
        if (!matcher.find()) return Pair.of(-1, "");
        return Pair.of(MathUtil.parseIntOrElse(matcher.group(1), -1), matcher.group(2));
    }

    private final Pattern QQ_REDIRECT_LOCATION_PATTERN = Pattern.compile("&code=([A-Z0-9]+)&");

    public void authorizeQQLogin() throws IOException, URISyntaxException {
        String url = "https://graph.qq.com/oauth2.0/authorize";
        HttpResponse<String> response = this.open().addFixedHeader("Host", "graph.qq.com").setFixedReferer("https://graph.qq.com").url(url).post(
                HttpResponse.BodyHandlers.ofString(),
                HttpRequestBuilder.ContentType.FORM,
                "response_type=code&client_id=100497308&redirect_uri=https%3A%2F%2Fy.qq.com%2Fportal%2Fwx_redirect.html%3Flogin_type%3D1%26surl%3Dhttps%3A%2F%2Fy.qq.com%2F&scope=get_user_info%2Cget_app_friends&state=state&switch=&from_ptlogin=1&src=1&update_auth=1&openapi=80901010&g_tk=" + this.getQQLoginGTK() + "&auth_time=" + TextUtil.getCurrentTime() + "&ui=" + this.getQQLoginUi()
        );
        Optional<String> locationOptional = response.headers().firstValue("Location");
        if (locationOptional.isPresent()) {
            Matcher matcher = QQ_REDIRECT_LOCATION_PATTERN.matcher(locationOptional.get());
            if (matcher.find()) {
                this.setQQLoginCookies(matcher.group(1));
            }
        }
    }

    public void setQQLoginCookies(String code) throws IOException, URISyntaxException {
        this.openUApi().url("https://u.y.qq.com/cgi-bin/musicu.fcg").post(
                HttpResponse.BodyHandlers.ofString(),
                HttpRequestBuilder.ContentType.JSON,
                "{\"comm\":{\"g_tk\":" + this.getQQLoginGTK() + ",\"platform\":\"yqq\",\"ct\":24,\"cv\":0},\"req\":{\"module\":\"QQConnectLogin.LoginServer\",\"method\":\"QQLogin\",\"param\":{\"code\":\"" + code + "\"}}}"
        );
    }

    public static String randomNumber(int length, boolean with0) {
        return with0 ? RandomStringUtils.random(length, '0', '1', '2', '3', '4', '5', '6', '7', '8', '9') : RandomStringUtils.random(length, '1', '2', '3', '4', '5', '6', '7', '8', '9');
    }

    public void searchMusic(String keyword) throws IOException, URISyntaxException {
        String id = randomNumber(1, false) + randomNumber(16, true);
        this.requestSignedApi("music.search.SearchCgiService", "DoSearchForQQMusicDesktop", "\"remoteplace\":\"txt.yqq.top\",\"searchid\":\"" + id + "\",\"search_type\":0,\"query\":\"" + keyword + "\",\"page_num\":1,\"num_per_page\":10");
    }

    public JsonObject requestSignedApi(String module, String method, String params) throws IOException, URISyntaxException {
        String data = "{\"comm\":{\"cv\":4747474,\"ct\":24,\"format\":\"json\",\"inCharset\":\"utf-8\",\"outCharset\":\"utf-8\",\"notice\":0,\"platform\":\"yqq.json\",\"needNewCode\":1,\"uin\":\"" + this.getUin() + "\",\"g_tk_new_20200303\":" + this.getQQLoginGTK() + ",\"g_tk\":" + this.getQQLoginGTK() + "},\"req_1\":{\"module\":\"" + module + "\",\"method\":\"" + method + "\",\"param\":" + params + "}}";
        String url = "https://u.y.qq.com/cgi-bin/musics.fcg?_=" + TextUtil.getCurrentTime() + "&sign=" + QQMusicApiEncrypt.Sign.getSign(data);
        JsonObject object = parseJson(this.openUApi().setFixedReferer("https://y.qq.com/").url(url).post(
                HttpResponse.BodyHandlers.ofString(),
                HttpRequestBuilder.ContentType.FORM,
                data
        ));
        return object == null ? null : object.getAsJsonObject("req_1");
    }

    public JsonObject requestSignedApi(String module, String method, Map<?, ?> params) throws IOException, URISyntaxException {
        return this.requestSignedApi(module, method, HttpRequestBuilder.ContentType.toJson(params));
    }
}