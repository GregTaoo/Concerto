package top.gregtao.concerto.http.netease;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.enums.SearchType;
import top.gregtao.concerto.enums.Sources;
import top.gregtao.concerto.http.HttpApiClient;
import top.gregtao.concerto.music.lyric.LRCFormatLyrics;
import top.gregtao.concerto.screen.QRCodeRenderer;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.music.NeteaseCloudMusic;
import top.gregtao.concerto.music.list.NeteaseCloudPlaylist;
import top.gregtao.concerto.music.lyric.Lyrics;
import top.gregtao.concerto.music.meta.music.TimelessMusicMetaData;
import top.gregtao.concerto.music.meta.music.list.PlaylistMetaData;
import top.gregtao.concerto.player.MusicPlayer;
import top.gregtao.concerto.player.MusicPlayerHandler;
import top.gregtao.concerto.util.HashUtil;
import top.gregtao.concerto.util.HttpUtil;
import top.gregtao.concerto.util.JsonUtil;
import top.gregtao.concerto.util.MathUtil;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NeteaseCloudApiClient extends HttpApiClient {
    public static URL DEFAULT_API = HttpUtil.createCorrectURL("http://music.163.com");

    public static String APP_VERSION = "2.10.6.200601";

    public static Map<String, String> HEADERS = Map.of(
            "Referer", "https://music.163.com",
            "Host", "music.163.com",
            "User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Safari/537.36 Chrome/91.0.4472.164 NeteaseMusicDesktop/" + APP_VERSION
    );

    public static List<String> INIT_COOKIES = List.of("appver=" + APP_VERSION, "os=pc");

    public static NeteaseCloudApiClient INSTANCE = new NeteaseCloudApiClient();

    public static NeteaseCloudUser LOCAL_USER = new NeteaseCloudUser(INSTANCE);

    public NeteaseCloudApiClient() {
        super(Sources.NETEASE_CLOUD.asString(), DEFAULT_API, HEADERS, INIT_COOKIES);
    }

    public JsonObject getMusicLink(String id, NeteaseCloudMusic.Level level) throws Exception {
        return JsonUtil.from(this.get("/api/song/enhance/player/url/v1?encodeType=mp3&ids=[" + id + "]&level=" + level.asString()));
    }

    public JsonObject getMusicDetail(String id) throws Exception {
        return JsonUtil.from(this.get("/api/v3/song/detail?c=%5B%7B%22id%22%3A%20" + id + "%7D%5D"));
    }

    public Pair<Lyrics, Lyrics> getLyric(String id) throws Exception {
        JsonObject object = JsonUtil.from(this.get("/api/song/lyric?id=" + id + "&lv=0&tv=0"));
        Lyrics lyrics1 = new LRCFormatLyrics().load(object.getAsJsonObject("lrc").get("lyric").getAsString());
        lyrics1 = lyrics1.isEmpty() ? null : lyrics1;
        Lyrics lyrics2 = new LRCFormatLyrics().load(object.getAsJsonObject("tlyric").get("lyric").getAsString());
        lyrics2 = lyrics2.isEmpty() ? null : lyrics2;
        return Pair.of(lyrics1, lyrics2);
    }

    public Pair<Integer, String> sendPhoneCaptcha(String countryCode, String phoneNumber) throws Exception {
        JsonObject body = JsonUtil.from(this.get("/api/sms/captcha/sent?cellphone=" + phoneNumber + "&ctcode=" + countryCode));
        return getCodeAndMessage(body);
    }

    public Pair<Integer, String> sendPhoneCaptcha(String phoneNumber) throws Exception {
        return this.sendPhoneCaptcha("86", phoneNumber);
    }

    public Pair<Integer, String> cellphoneLogin(String countryCode, String phoneNumber, boolean captcha, String code) throws Exception {
        JsonObject body = JsonUtil.from(this.get("/api/login/cellphone", Map.of(
                "phone", phoneNumber, "countrycode", countryCode, "rememberLogin", true,
                captcha ? "captcha" : "password", captcha ? code : HashUtil.md5(code)
        ), Map.of()));

        Pair<Integer, String> result = getCodeAndMessage(body);
        if (result.getFirst() == 200) LOCAL_USER.updateLoginStatus();
        return result;
    }

    public Pair<Integer, String> cellphoneLogin(String phoneNumber, boolean captcha, String code) throws Exception {
        return cellphoneLogin("86", phoneNumber, captcha, code);
    }

    public Pair<Integer, String> emailPasswordLogin(String email, String password) throws Exception {
        JsonObject body = JsonUtil.from(this.get("/api/login", Map.of(
                "username", email, "password", HashUtil.md5(password), "rememberLogin", true
        ), Map.of()));
        Pair<Integer, String> result = getCodeAndMessage(body);
        if (result.getFirst() == 200) LOCAL_USER.updateLoginStatus();
        return result;
    }

    public String generateQRCodeKey() throws Exception {
        return JsonUtil.from(this.get("/api/login/qrcode/unikey?type=1")).get("unikey").getAsString();
    }

    public String getQRCodeLoginLink(String uniKey) throws MalformedURLException {
        return HttpUtil.getSonOfURL(this.baseApi, "/login?codekey=" + uniKey).toString();
    }

    public Pair<Integer, String> getQRCodeStatus(String uniKey) throws Exception {
        return getCodeAndMessage(JsonUtil.from(this.get("/api/login/qrcode/client/login?type=1&key=" + uniKey)));
    }

    public Pair<ArrayList<Music>, PlaylistMetaData> parsePlayListJson(JsonObject object, NeteaseCloudMusic.Level level, boolean simply) {
        ArrayList<Music> music = new ArrayList<>();
        String createTime = "";
        if (!simply) {
            JsonArray array = object.getAsJsonArray("tracks");
            array.forEach(element -> music.add(new NeteaseCloudMusic(element.getAsJsonObject(), level)));
            createTime = MathUtil.formattedTime(object.get("createTime").getAsString());
        }
        String name = object.get("name").getAsString();
        JsonObject creator = object.getAsJsonObject("creator");
        String creatorName = creator.get("nickname").getAsString();
        String description;
        try {
            description = object.get("description").getAsString();
        } catch (UnsupportedOperationException e) {
            description = "";
        }
        return Pair.of(music, new PlaylistMetaData(creatorName, name, createTime, description));
    }

    public Pair<ArrayList<Music>, PlaylistMetaData> parseAlbumJson(JsonObject object, NeteaseCloudMusic.Level level, boolean simply) {
        ArrayList<Music> music = new ArrayList<>();
        String createTime = "", name, description;
        JsonObject creator;
        if (!simply) {
            JsonArray array = object.getAsJsonArray("songs");
            JsonObject album = object.getAsJsonObject("album");
            String picUrl = album.get("picUrl").getAsString();
            array.forEach(element -> {
                NeteaseCloudMusic music1 = new NeteaseCloudMusic(element.getAsJsonObject(), level);
                ((TimelessMusicMetaData) music1.getMeta()).setHeadPictureUrl(picUrl);
                music.add(music1);
            });
            createTime = MathUtil.formattedTime(album.get("publishTime").getAsString());
            name = album.get("name").getAsString();
            creator = album.getAsJsonObject("artist");
            try {
                description = album.get("description").getAsString();
            } catch (UnsupportedOperationException e) {
                description = "";
            }
        } else {
            name = object.get("name").getAsString();
            creator = object.getAsJsonObject("artist");
            try {
                description = object.get("description").getAsString();
            } catch (UnsupportedOperationException e) {
                description = "";
            }
        }
        String creatorName = creator.get("name").getAsString();
        return Pair.of(music, new PlaylistMetaData(creatorName, name, createTime, description));
    }

    public Pair<ArrayList<Music>, PlaylistMetaData> getPlayList(String id, NeteaseCloudMusic.Level level) {
        try {
            JsonObject object = JsonUtil.from(this.get("/api/v6/playlist/detail?id=" + id + "&n=" + MusicPlayerHandler.MAX_SIZE))
                    .getAsJsonObject("playlist");
            return this.parsePlayListJson(object, level, false);
        } catch (Exception e) {
            return Pair.of(new ArrayList<>(), PlaylistMetaData.EMPTY);
        }
    }

    public Pair<ArrayList<Music>, PlaylistMetaData> getAlbum(String id, NeteaseCloudMusic.Level level) {
        try {
            JsonObject object = JsonUtil.from(this.get("/api/v1/album/" + id));
            return this.parseAlbumJson(object, level, false);
        } catch (Exception e) {
            e.printStackTrace();
            return Pair.of(new ArrayList<>(), PlaylistMetaData.EMPTY);
        }
    }

    private JsonObject search(String keyword, int page, SearchType type) throws Exception {
        return JsonUtil.from(this.post("/api/cloudsearch/pc/", Map.of(
                "s", keyword, "offset", 30 * page, "limit", 30, "type", type.searchKey, "total", true), ContentType.FORM));
    }

    public List<Music> searchMusic(String keyword, int page) {
        try {
            JsonObject object = this.search(keyword, page, SearchType.MUSIC);
            List<Music> musics = new ArrayList<>();
            JsonArray array = object.getAsJsonObject("result").getAsJsonArray("songs");
            array.forEach(element -> musics.add(new NeteaseCloudMusic(element.getAsJsonObject(), NeteaseCloudMusic.Level.STANDARD)));
            MusicPlayerHandler.loadInThreadPool(musics);
            return musics;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public List<NeteaseCloudPlaylist> searchPlaylist(String keyword, int page) {
        try {
            JsonObject object = this.search(keyword, page, SearchType.PLAYLIST);
            List<NeteaseCloudPlaylist> playlists = new ArrayList<>();
            JsonArray array = object.getAsJsonObject("result").getAsJsonArray("playlists");
            array.forEach(element -> playlists.add(new NeteaseCloudPlaylist(element.getAsJsonObject(), false, true)));
            return playlists;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public List<NeteaseCloudPlaylist> searchAlbum(String keyword, int page) {
        try {
            JsonObject object = this.search(keyword, page, SearchType.ALBUM);
            List<NeteaseCloudPlaylist> playlists = new ArrayList<>();
            JsonArray array = object.getAsJsonObject("result").getAsJsonArray("albums");
            array.forEach(element -> playlists.add(new NeteaseCloudPlaylist(element.getAsJsonObject(), true, true)));
            return playlists;
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public static Thread CURRENT_THREAD = null;

    public static void checkQRCodeStatusProgress(PlayerEntity player, String uniKey) {
        if (CURRENT_THREAD != null) {
            CURRENT_THREAD.stop();
            return;
        }
        CURRENT_THREAD = MusicPlayer.run(() -> {
            try {
                long wait = 120000;
                while (wait > 0) {
                    Pair<Integer, String> pair = INSTANCE.getQRCodeStatus(uniKey);
                    int code = pair.getFirst();
                    if (code == 801 || code == 802) {
                        Thread.sleep(1000L);
                        wait -= 1000;
                    } else if (code == 800) {
                        wait = -1;
                        break;
                    } else if (code == 803) {
                        player.sendMessage(Text.translatable("concerto.login.163.qrcode.success"));
                        LOCAL_USER.updateLoginStatus();
                        break;
                    } else {
                        ConcertoClient.LOGGER.error("Unknown code " + code + ", it may caused by networking problems.");
                        break;
                    }
                }
                if (wait <= 0) player.sendMessage(Text.translatable("concerto.login.163.qrcode.expired"));
                QRCodeRenderer.clear();
            } catch (Exception e) {
                player.sendMessage(Text.translatable("concerto.login.163.qrcode.error"));
                ConcertoClient.LOGGER.error("Error occurs while checking QR code scanning status.");
                e.printStackTrace();
                QRCodeRenderer.clear();
            }
        });
    }

    public static int getCode(JsonObject body) {
        return JsonUtil.getIntOrElse(body, "code", 200);
    }

    public static Pair<Integer, String> getCodeAndMessage(JsonObject body) {
        return Pair.of(JsonUtil.getIntOrElse(body, "code", 200), JsonUtil.getStringOrElse(body, "message", "?"));
    }
}
