package top.gregtao.concerto.core.http.netease;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import top.gregtao.concerto.core.Concerto;
import top.gregtao.concerto.core.config.ClientConfig;
import top.gregtao.concerto.core.enums.SearchType;
import top.gregtao.concerto.core.enums.Sources;
import top.gregtao.concerto.core.http.HttpApiClient;
import top.gregtao.concerto.core.http.HttpRequestBuilder;
import top.gregtao.concerto.core.music.Music;
import top.gregtao.concerto.core.music.NeteaseCloudMusic;
import top.gregtao.concerto.core.music.list.FixedPlaylist;
import top.gregtao.concerto.core.music.list.NeteaseCloudPlaylist;
import top.gregtao.concerto.core.music.meta.music.TimelessMusicMetaData;
import top.gregtao.concerto.core.music.meta.music.list.PlaylistMetaData;
import top.gregtao.concerto.core.util.HashUtil;
import top.gregtao.concerto.core.util.JsonUtil;
import top.gregtao.concerto.core.util.MathUtil;
import top.gregtao.concerto.core.util.Pair;

import java.net.http.HttpResponse;
import java.util.*;

public class NeteaseCloudApiClient extends HttpApiClient {

    public static String APP_VERSION = "3.1.6";
    public static Map<String, String> HEADERS = Map.of(
            "Referer", "https://music.163.com",
            "User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Safari/537.36 Chrome/91.0.4472.164 NeteaseMusicDesktop/" + APP_VERSION
    );
    public static List<String> INIT_COOKIES = List.of("appver=" + APP_VERSION, "os=pc");

    public static NeteaseCloudApiClient INSTANCE = new NeteaseCloudApiClient();
    public static NeteaseCloudUser LOCAL_USER = new NeteaseCloudUser(INSTANCE);

    public NeteaseCloudApiClient() {
        super(Sources.NETEASE_CLOUD.asString(), HEADERS, Map.of(
                "http://music.163.com", INIT_COOKIES,
                "https://music.163.com", INIT_COOKIES
        ));
    }

    public String getOuterMusicLink(String id) {
        return "http://music.163.com/song/media/outer/url?id=" + id;
    }

    public JsonObject getMusicLink(String id, NeteaseCloudMusic.Level level) {
        String url = "http://music.163.com/api/song/enhance/player/url/v1?encodeType=mp3&ids=[" + id + "]&level=" + level.asString();
        return parseJson(this.open().url(url).get());
    }

    public JsonObject getMusicDetail(String id) {
        String url = "http://music.163.com/api/v3/song/detail?c=%5B%7B%22id%22%3A%20" + id + "%7D%5D";
        return parseJson(this.open().url(url).get());
    }

    public Pair<String, String> getLyrics(String id) {
        String url = "http://music.163.com/api/song/lyric?id=" + id + "&lv=0&tv=0";
        JsonObject object = parseJson(this.open().url(url).get());
        if (object == null) return null;
        return Pair.of(object.getAsJsonObject("lrc").get("lyric").getAsString(),
                object.getAsJsonObject("tlyric").get("lyric").getAsString());
    }

    public Pair<Integer, String> sendPhoneCaptcha(String countryCode, String phoneNumber) {
        String path = "/api/middle/captcha/sent/v1";
        JsonObject object = parseJson(this.open().url("https://interfacepc.music.163.com/eapi/middle/captcha/sent/v1").post(
                HttpResponse.BodyHandlers.ofString(), HttpRequestBuilder.ContentType.FORM,
                NeteaseCloudApiCrypto.eapiForm(path, Map.of(
                        "ctcode", countryCode, "secrete", "music_middleuser_pclogin",
                        "cellphone", phoneNumber, "scene", "0"
                ))
        ));
        if (object == null) return null;
        return getCodeAndMessage(object);
    }

    public Pair<Integer, String> sendPhoneCaptcha(String phoneNumber) {
        return this.sendPhoneCaptcha("86", phoneNumber);
    }

    public Pair<Integer, String> cellphoneLogin(String countryCode, String phoneNumber, boolean captcha, String code) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", "1");
        data.put("https", "true");
        data.put("phone", phoneNumber);
        data.put("countrycode", countryCode);
        data.put("remember", "true");
        data.put("secureCaptcha", "");
        data.put(captcha ? "captcha" : "password", captcha ? code : HashUtil.md5(code));
        JsonObject object = parseJson(this.open().url("https://music.163.com/weapi/w/login/cellphone").post(
                HttpResponse.BodyHandlers.ofString(), HttpRequestBuilder.ContentType.FORM,
                NeteaseCloudApiCrypto.weapiForm(data)
        ));
        if (object == null) return null;
        Pair<Integer, String> result = getCodeAndMessage(object);
        if (result.getFirst() == 200) LOCAL_USER.updateLoginStatus();
        return result;
    }

    public Pair<Integer, String> cellphoneLogin(String phoneNumber, boolean captcha, String code) {
        return cellphoneLogin("86", phoneNumber, captcha, code);
    }

    public Pair<Integer, String> emailPasswordLogin(String email, String password) {
        JsonObject object = parseJson(this.open().url("https://music.163.com/weapi/w/login").post(
                HttpResponse.BodyHandlers.ofString(), HttpRequestBuilder.ContentType.FORM,
                NeteaseCloudApiCrypto.weapiForm(Map.of(
                        "type", "0", "https", "true", "username", email,
                        "password", HashUtil.md5(password), "rememberLogin", "true"
                ))
        ));
        if (object == null) return null;
        Pair<Integer, String> result = getCodeAndMessage(object);
        if (result.getFirst() == 200) LOCAL_USER.updateLoginStatus();
        return result;
    }

    public String generateQRCodeKey() {
        String url = "http://music.163.com/api/login/qrcode/unikey?type=1";
        JsonObject object = parseJson(this.open().url(url).get());
        if (object == null) return null;
        return object.get("unikey").getAsString();
    }

    public String getQRCodeLoginLink(String uniKey) {
        return "http://music.163.com/login?codekey=" + uniKey;
    }

    public Pair<Integer, String> getQRCodeStatus(String uniKey) {
        return getCodeAndMessage(parseJson(this.open()
                .url("http://music.163.com/api/login/qrcode/client/login?type=1&key=" + uniKey)
                .get()));
    }

    public Pair<ArrayList<Music>, PlaylistMetaData> parsePlaylistJson(JsonObject object, NeteaseCloudMusic.Level level, boolean simply) {
        ArrayList<Music> music = new ArrayList<>();
        String createTime = "";
        if (!simply) {
            HashSet<String> ids = new HashSet<>();
            JsonArray array = object.getAsJsonArray("tracks");
            array.forEach(element -> {
                NeteaseCloudMusic nm = new NeteaseCloudMusic(element.getAsJsonObject(), level);
                ids.add(nm.getId());
                music.add(nm);
            });
            JsonArray array1 = object.getAsJsonArray("trackIds");
            array1.forEach(element -> {
                String id = element.getAsJsonObject().get("id").getAsString();
                if (!ids.contains(id)) music.add(new NeteaseCloudMusic(id, level));
            });
            createTime = MathUtil.formattedTime(object.get("createTime").getAsString());
            // MusicPlayerHandler.loadInThreadPool(music);
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

    public Pair<ArrayList<Music>, PlaylistMetaData> getPlaylist(String id, NeteaseCloudMusic.Level level) {
        try {
            String url = "http://music.163.com/api/v6/playlist/detail?id=" + id + "&n=1000000";
            JsonObject object = Objects.requireNonNull(parseJson(this.open().url(url).get()))
                    .getAsJsonObject("playlist");
            return this.parsePlaylistJson(object, level, false);
        } catch (Exception e) {
            Concerto.getLogger().warn("Error while getting playlist {}: {}", id, e.getMessage());
            return Pair.of(new ArrayList<>(), PlaylistMetaData.EMPTY);
        }
    }

    public Pair<ArrayList<Music>, PlaylistMetaData> getAlbum(String id, NeteaseCloudMusic.Level level) {
        try {
            JsonObject object = parseJson(this.open().url("http://music.163.com/api/v1/album/" + id)
                    .get());
            return this.parseAlbumJson(object, level, false);
        } catch (Exception e) {
            Concerto.getLogger().warn("Error while getting album {}: {}", id, e.getMessage());
            return Pair.of(new ArrayList<>(), PlaylistMetaData.EMPTY);
        }
    }

    private JsonObject search(String keyword, int page, SearchType type) {
        return parseJson(this.open().url("http://music.163.com/api/cloudsearch/pc/").post(
                HttpResponse.BodyHandlers.ofString(), HttpRequestBuilder.ContentType.FORM,
                Map.of("s", keyword, "offset", 30 * page, "limit", 30, "type", type.neteaseKey, "total", true)
        ));
    }

    public List<Music> searchMusic(String keyword, int page) {
        try {
            JsonObject object = this.search(keyword, page, SearchType.MUSIC);
            List<Music> musics = new ArrayList<>();
            JsonArray array = object.getAsJsonObject("result").getAsJsonArray("songs");
            array.forEach(element -> musics.add(new NeteaseCloudMusic(element.getAsJsonObject(), ClientConfig.INSTANCE.options.neteaseMusicQuality)));
            // MusicPlayerHandler.loadInThreadPool(musics);
            return musics;
        } catch (Exception e) {
            Concerto.getLogger().warn("Error while searching for music '{}': {}", keyword, e.getMessage());
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
            Concerto.getLogger().warn("Error while searching for playlist '{}': {}", keyword, e.getMessage());
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
            Concerto.getLogger().warn("Error while searching for album '{}': {}", keyword, e.getMessage());
            return new ArrayList<>();
        }
    }

    public FixedPlaylist getDailyRecommendation() {
        JsonObject object = parseJson(this.open().url("http://music.163.com/api/v3/discovery/recommend/songs").post());
        if (object == null) return null;
        JsonArray songs = object.getAsJsonObject("data").getAsJsonArray("dailySongs");
        ArrayList<Music> musics = new ArrayList<>();
        songs.forEach(element -> musics.add(new NeteaseCloudMusic(element.getAsJsonObject(), ClientConfig.INSTANCE.options.neteaseMusicQuality)));
        return new FixedPlaylist(musics, new PlaylistMetaData(Concerto.getCoreBridge().getTranslatable("concerto.source.netease_cloud"),
                Concerto.getCoreBridge().getTranslatable("concerto.screen.daily_recommendation"), "", ""), false);
    }

    public static Pair<Integer, String> getCodeAndMessage(JsonObject body) {
        return Pair.of(JsonUtil.getIntOrElse(body, "code", 200), JsonUtil.getStringOrElse(body, "message", "?"));
    }
}
