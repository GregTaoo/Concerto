package top.gregtao.concerto.http.kugou;

import com.google.gson.*;
import org.apache.commons.lang3.StringUtils;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.config.ClientConfig;
import top.gregtao.concerto.enums.SearchType;
import top.gregtao.concerto.enums.Sources;
import top.gregtao.concerto.http.HttpApiClient;
import top.gregtao.concerto.http.HttpRequestBuilder;
import top.gregtao.concerto.music.KuGouMusic;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.music.list.KuGouMusicPlaylist;
import top.gregtao.concerto.music.meta.music.MusicMetaData;
import top.gregtao.concerto.music.meta.music.UnknownMusicMeta;
import top.gregtao.concerto.music.meta.music.list.PlaylistMetaData;
import top.gregtao.concerto.player.MusicPlayerHandler;
import top.gregtao.concerto.util.*;

import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 酷狗 API 来自 <a href="https://github.com/MakcRe/KuGouMusicApi">MakcRe/KuGouMusicApi</a>
 */
public class KuGouMusicApiClient extends HttpApiClient {

    public static final Gson GSON = new GsonBuilder()
            .enableComplexMapKeySerialization()
            .create();

    public static final String BASE_URL = "https://gateway.kugou.com";

    public static final String LITE_APPID = "3116";

    public static final String APPID = "1005";

    public static final String LITE_CLIENT_VER = "11440";

    public static final String CLIENT_VER = "20489";

    public static final String KEY = "90b8382a1bb4ccdcf063102053fd75b8";

    public static final String IV = "f063102053fd75b8";

    public static final String LITE_KEY = "c24f74ca2820225badc01946dba4fdf7";

    public static final String LITE_IV = "adc01946dba4fdf7";

    public static final String LITE_T2_KEY = "fd14b35e3f81af3817a20ae7adae7020";

    public static final String LITE_T2_IV = "17a20ae7adae7020";

    public static final String LITE_T1_KEY = "5e4ef500e9597fe004bd09a46d8add98";

    public static final String LITE_T1_IV = "04bd09a46d8add98";

    public static Map<String, String> HEADERS = Map.of(
            "User-Agent", "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi",
            "Referer", ""
    );

    public static Map<String, String> COOKIES;

    public static KuGouMusicApiClient INSTANCE = new KuGouMusicApiClient();

    public static KuGouMusicUser LOCAL_USER = new KuGouMusicUser(INSTANCE);

    public static String GUID = HashUtil.md5(RandomUtil.getGuid());

    public static String MID = RandomUtil.calculateMid(GUID);

    public static String SERVER_DEV = RandomUtil.randomString(10).toUpperCase();

    public static String MAC = "02:00:00:00:00:00";

    public KuGouMusicApiClient() {
        // 不直接使用 Cookies
        super(Sources.KUGOU_MUSIC.asString(), HEADERS, Map.of("", List.of()));
        this.setClient(
                HttpClient.newBuilder()
                        // 默认使用 HTTP2, 在获取歌词时可能会出现连接重置问题
                        .version(HttpClient.Version.HTTP_1_1)
                        .build()
        );
    }

    public Map<String, String> parseCookies(String raw) {
        String[] cookies = raw.split(";");
        Map<String, String> result = new HashMap<>();
        for (String cookie : cookies) {
            String[] pair = cookie.split("=");
            if (pair.length == 2) {
                result.put(pair[0].trim(), pair[1].trim());
            }
        }
        return result;
    }

    @Override
    public void clearCookie() {
        // 不清除 KUGOU_API_MID, KUGOU_API_GUID, KUGOU_API_DEV, KUGOU_API_MAC
        HashMap<String, String> cacheMap = new HashMap<>();
        COOKIES.computeIfPresent("KUGOU_API_MID", cacheMap::put);
        COOKIES.computeIfPresent("KUGOU_API_GUID", cacheMap::put);
        COOKIES.computeIfPresent("KUGOU_API_DEV", cacheMap::put);
        COOKIES.computeIfPresent("KUGOU_API_MAC", cacheMap::put);
        COOKIES.clear();
        COOKIES.putAll(cacheMap);
        writeCookie();
    }

    @Override
    public void writeCookie() {
        String cookies = TextUtil.toBase64("https://www.kugou.com") + ":"
                + COOKIES.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .reduce((a, b) -> a + "\n" + b)
                    .map(TextUtil::toBase64)
                    .orElse("");

        getCookieFile().write(cookies);
    }

    @Override
    public void readCookie() {
        COOKIES = parseCookies(getCookieFile().readAsHeader());
        COOKIES.computeIfAbsent("KUGOU_API_MID", k -> MID);
        COOKIES.computeIfAbsent("KUGOU_API_GUID", k -> GUID);
        COOKIES.computeIfAbsent("KUGOU_API_DEV", k -> SERVER_DEV);
        COOKIES.computeIfAbsent("KUGOU_API_MAC", k -> MAC);
    }

    @Override
    public void setCookie(String url, String key, String value) {
        COOKIES.put(key, value);
        writeCookie();
    }

    @Override
    public void setCookies(String url, Map<String, String> cookies) {
        COOKIES.putAll(cookies);
        writeCookie();
    }

    public HttpResponse<String> request(String url, Map<String, String> params, KuGouRequestConfig config) {
        return request(url, params, config, HttpResponse.BodyHandlers.ofString());
    }

    public <T> HttpResponse<T> request(String url, Map<String, String> params, KuGouRequestConfig config, HttpResponse.BodyHandler<T> bodyHandler) {
        String dfid = getCookie("dfid", "-");
        String mid = getCookie("KUGOU_API_MID");
        String uuid = "-";
        String token = getCookie("token", "");
        String userid = getCookie("userid", "0");
        String clientTime = String.valueOf((long)(Math.floor((double) System.currentTimeMillis() / 1000)));
        Map<String, String> paramsMap = new HashMap<>(params);
        Map<String, String> headers = new HashMap<>(Map.of(
                "dfid", dfid,
                "mid", mid,
                "clienttime", clientTime,
                "kg-rc", "1",
                "kg-thash", "5d816a0",
                "kg-rec", "1",
                "kg-rf", "B9EDA08A64250DEFFBCADDEE00F8F25F"
        ));

        String appid = getUseAppid();
        String clientVer = getUseClientVer();
        Map<String, String> defaultParams = new HashMap<>(Map.of(
                "dfid", dfid,
                "mid", mid,
                "uuid", uuid,
                "appid", appid,
                "clientver", clientVer,
                "clienttime", clientTime
        ));

        if (!StringUtils.isEmpty(token)) {
            defaultParams.put("token", token);
        }

        if (!userid.equals("0")) {
            defaultParams.put("userid", userid);
        }

        if (!config.isClearDefaultParams()) {
            defaultParams.putAll(paramsMap);
            paramsMap = defaultParams;
        }

        // 生成 Key
        if (config.isNeedKey()) {
            String key = KuGouMusicApiCrypto.signKey(
                    paramsMap.getOrDefault("hash", ""),
                    mid,
                    userid,
                    appid
            );
            paramsMap.put("key", key);
        }

        // 处理 data
        Object data = config.getData();
        String dataJson = data instanceof String dataStr ? dataStr : (data == null ? "" : GSON.toJson(data));

        // 签名
        if (!config.isNoSign()) {
            String signature;
            switch (config.getEncryptType()) {
                case WEB -> signature = KuGouMusicApiCrypto.signWebParams(paramsMap);
                case REGISTER -> signature = KuGouMusicApiCrypto.signRegisterParams(paramsMap);
                case null, default -> signature = KuGouMusicApiCrypto.signAndroidParams(paramsMap, dataJson);
            }
            paramsMap.put("signature", signature);
        }

        String baseUrl = config.getBaseUrl();
        String fullUrl = (baseUrl == null ? BASE_URL : baseUrl) + url;
        if (!paramsMap.isEmpty()) {
            fullUrl += paramsMap.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                    .reduce((a, b) -> a + "&" + b)
                    .map(s -> "?" + s)
                    .get();
        }

        headers.putAll(config.getHeaders());
        HttpRequestBuilder builder = open().url(fullUrl).setHeaders(headers);
        if (config.getRequestType().equals(KuGouRequestConfig.RequestType.GET)) {
            return builder.get(bodyHandler);
        } else {
            return builder.post(bodyHandler, HttpRequestBuilder.ContentType.JSON, dataJson);
        }
    }

    public Optional<JsonObject> search(String keyword, Integer page, SearchType searchType) {
        Map<String, String> params = new HashMap<>(Map.of(
                "albumhide", "0",
                "iscorrection", "1",
                "keyword", keyword,
                "nocollect", "0",
                "page", page.toString(),
                "pagesize", "30",
                "platform", "AndroidFilter"
        ));
        String url = "/" + (searchType.equals(SearchType.MUSIC) ? "v3" : "v1")
                + "/search/" + searchType.kuGouKey;

        JsonObject json = parseJson(request(
                url,
                params,
                KuGouRequestConfig.builder()
                        .addHeaders("x-router", "complexsearch.kugou.com")
                        .build()
        ));

        return Optional.ofNullable(json);
    }

    public Optional<JsonObject> getSongUrl(String hash, boolean isFreePart) {
        boolean isLite = ClientConfig.INSTANCE.options.kuGouMusicLite;
        String pageId = isLite ? "967177915" : "151369488";
        String ppageId = isLite ? "356753938,823673182,967485191" : "463467626,350369493,788954147";
        String pid = isLite ? "411" : "2";

        Map<String, String> params = new HashMap<>(){{
            put("album_id", "0");
            put("area_code", "1");
            put("hash", hash);
            put("ssa_flag", "is_fromtrack");
            put("version", "11436");
            put("page_id", pageId);
            put("quality", "128");
            put("album_audio_id", "0");
            put("behavior", "play");
            put("pid", pid);
            put("cmd", "26");
            put("pidversion", "3001");
            put("IsFreePart", isFreePart ? "1" : "0");
            put("ppage_id", ppageId);
            put("cdnBackup", "1");
            put("kcard", "0");
            put("module", "");
        }};

        JsonObject json = parseJson(
                request(
                        "/v5/url",
                        params,
                        KuGouRequestConfig.builder()
                                .needKey(true)
                                .addHeaders("x-router", "trackercdn.kugou.com")
                                .build()
                )
        );

        return Optional.ofNullable(json);
    }

    public Optional<String> getMusicLink(String hash, boolean isFreePart) {
        if (StringUtils.isEmpty(hash)) return Optional.empty();
        return getSongUrl(hash, isFreePart)
                .map(songUrlResponse -> songUrlResponse.getAsJsonArray("url"))
                .map(url -> !url.isEmpty() ? url.get(0).getAsString() : null)
                .map(link -> !link.isEmpty() ? link : null );
    }

    public Optional<JsonObject> getMusicHash(String hash) {
        List<Map<String, String>> data = List.of(
            Map.of(
                    "type", "audio",
                    "page_id", "0",
                    "hash", hash,
                    "album_id", "0"
            )
        );
        String dfId = getCookie("dfid", "-");
        String userId = getCookie("userid", "0");
        String token = getCookie("token", "0");
        String clientTime = getClientTime();

        Map<String, Object> dataMap = Map.of(
                "appid", getUseAppid(),
                "clienttime", clientTime,
                "clientver", getUseClientVer(),
                "data", data,
                "dfid", dfId,
                "key", KuGouMusicApiCrypto.signParamsKey(clientTime),
                "mid", getCookie("KUGOU_API_MID"),
                "token", token,
                "userid", userId
        );

        JsonObject json = parseJson(
                request(
                        "/v1/audio/audio",
                        Map.of(),
                        KuGouRequestConfig.builder()
                                .baseUrl("http://kmr.service.kugou.com")
                                .requestType(KuGouRequestConfig.RequestType.POST)
                                .addHeaders("x-router", "media.store.kugou.com")
                                .data(dataMap)
                                .build()
                )
        );

        return Optional.ofNullable(json);
    }

    public Optional<JsonObject> getMusicDetail(String albumAudioId, String field) {
        if (StringUtils.isEmpty(albumAudioId)) return Optional.empty();
        List<Object> data = List.of(
                Map.of(
                        "entity_id", Long.parseLong(albumAudioId)
                )
        );
        Map<String, Object> dataMap = Map.of(
                "data", data,
                "fields", field
        );

        JsonObject json = parseJson(
                request(
                        "/kmr/v2/audio",
                        Map.of(),
                        KuGouRequestConfig.builder()
                                .requestType(KuGouRequestConfig.RequestType.POST)
                                .addHeaders("x-router", "openapi.kugou.com")
                                .addHeaders("KG-TID", "238")
                                .data(dataMap)
                                .build()
                )
        );

        if (json != null) {
            JsonArray dataArray = json.getAsJsonArray("data");
            if (dataArray != null && !dataArray.isEmpty()) {
                return Optional.ofNullable(dataArray.get(0).getAsJsonObject());
            }
        }

        return Optional.empty();
    }

    public List<Music> searchMusic(String keyword, Integer page) {
        JsonArray jsonArray = search(keyword, page, SearchType.MUSIC)
                .map(json -> json.getAsJsonObject("data"))
                .map(data -> data.getAsJsonArray("lists"))
                .orElse(new JsonArray());
        try {
            return jsonArray.asList().stream()
                    .map(element -> new KuGouMusic(element.getAsJsonObject()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            ConcertoClient.LOGGER.warn("Error while searching for music '{}': {}", keyword, e.getMessage());
            return List.of();
        }
    }

    public List<KuGouMusicPlaylist> searchPlaylist(String keyword, int page) {
        JsonArray jsonArray = search(keyword, page, SearchType.PLAYLIST)
                .map(json -> json.getAsJsonObject("data"))
                .map(data -> data.getAsJsonArray("lists"))
                .orElse(new JsonArray());

        try {
            return jsonArray.asList().stream()
                    .map(element -> new KuGouMusicPlaylist(element.getAsJsonObject(), false, false))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            ConcertoClient.LOGGER.warn("Error while searching for playlist '{}': {}", keyword, e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<KuGouMusicPlaylist> searchAlbum(String keyword, int page) {
        JsonArray jsonArray = search(keyword, page, SearchType.ALBUM)
                .map(json -> json.getAsJsonObject("data"))
                .map(data -> data.getAsJsonArray("lists"))
                .orElse(new JsonArray());

        try {
            return jsonArray.asList().stream()
                    .map(element -> new KuGouMusicPlaylist(element.getAsJsonObject(), true, false))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            ConcertoClient.LOGGER.warn("Error while searching for album '{}': {}", keyword, e.getMessage());
            return new ArrayList<>();
        }
    }

    public Optional<JsonObject> getPlayListAllTrack(String id) {
        if (StringUtils.isEmpty(id)) return Optional.empty();
        Map<String, String> paramsMap = Map.of(
                "area_code", "1",
                "begin_idx", "0",
                "plat", "1",
                "type", "1",
                "mode", "1",
                "personal_switch", "1",
                "extend_fields", "",
                "pagesize", String.valueOf(MusicPlayerHandler.MAX_SIZE),
                "global_collection_id", id
        );

        JsonObject json = parseJson(
                request(
                        "/pubsongs/v2/get_other_list_file_nofilt",
                        paramsMap,
                        KuGouRequestConfig.builder()
                                .build()
                )
        );

        return Optional.ofNullable(json);
    }

    public Pair<ArrayList<Music>, PlaylistMetaData> getPlaylist(String id) {
        Optional<JsonObject> optional = getPlayListAllTrack(id);

        Optional<JsonObject> data = optional.map(json -> json.getAsJsonObject("data"));
        PlaylistMetaData playlistMetaData = data.map(json -> json.getAsJsonObject("list_info"))
                .map(listInfoJson -> KuGouMusicPlaylist.parsePlaylistInfo(listInfoJson, true))
                .orElse(PlaylistMetaData.EMPTY);

        ArrayList<Music> songs = data.map(json -> json.getAsJsonArray("songs"))
                .map(jsonElements -> jsonElements.asList().stream())
                .map(stream -> stream.map(element -> (Music) new KuGouMusic(element.getAsJsonObject()))
                        // 由于未知原因, 歌单里会有部分歌曲被 "保护", 无法被搜索和显示
                        // 这里过滤掉这些歌曲
                        .filter(music -> !(music.getMeta() instanceof UnknownMusicMeta))
                        .collect(Collectors.toCollection(ArrayList::new))
                )
                .orElse(new ArrayList<>());

        return Pair.of(songs, playlistMetaData);
    }

    public Optional<JsonObject> getAlbumDetail(String id) {
        if (StringUtils.isEmpty(id)) return Optional.empty();
        Map<String, Object> dataMap = Map.of(
                "data", List.of(
                        Map.of(
                                "album_id", Long.parseLong(id)
                        )
                ),
                "is_buy", 0,
                "fields", "album_id,album_name,publish_date,sizable_cover,intro,authors"
        );

        JsonObject json = parseJson(
                request(
                        "/kmr/v2/albums",
                        Map.of(),
                        KuGouRequestConfig.builder()
                                .requestType(KuGouRequestConfig.RequestType.POST)
                                .data(dataMap)
                                .addHeaders("x-router", "openapi.kugou.com")
                                .addHeaders("kg-tid", "255")
                                .build()
                )
        );

        return Optional.ofNullable(json);
    }

    public Optional<JsonObject> getAlbumSongs(String id) {
        if (StringUtils.isEmpty(id)) return Optional.empty();
        Map<String, Object> dataMap = Map.of(
                "album_id", id,
                "is_buy", "",
                "page", 1,
                // 经测试, 最大值只能为 50
                "pagesize", 50
        );

        JsonObject json = parseJson(
                request(
                        "/v1/album_audio/lite",
                        Map.of(),
                        KuGouRequestConfig.builder()
                                .requestType(KuGouRequestConfig.RequestType.POST)
                                .data(dataMap)
                                .addHeaders("x-router", "openapi.kugou.com")
                                .addHeaders("kg-tid", "255")
                                .build()
                )
        );

        return Optional.ofNullable(json);
    }

    public Pair<ArrayList<Music>, PlaylistMetaData> getAlbum(String id) {
        Optional<JsonObject> albumDetail = getAlbumDetail(id);
        Optional<JsonObject> albumSongs = getAlbumSongs(id);

        PlaylistMetaData playlistMetaData = albumDetail.map(json -> json.getAsJsonArray("data"))
                .map(arr -> !arr.isEmpty() ? arr.get(0).getAsJsonObject() : null)
                .map(KuGouMusicPlaylist::parseAlbumInfo)
                .orElse(PlaylistMetaData.EMPTY);

        ArrayList<Music> songs = albumSongs.map(json -> json.getAsJsonObject("data"))
                .map(data -> data.getAsJsonArray("songs"))
                .map(arr -> arr.asList().stream()
                        .map(element -> {
                            Optional<JsonObject> songOpt = Optional.of(element.getAsJsonObject());

                            String albumAudioId = songOpt.map(song -> song.getAsJsonObject("base"))
                                    .map(base -> base.get("album_audio_id"))
                                    .map(JsonElement::getAsString)
                                    .orElse("");

                            String hash = songOpt.map(song -> song.getAsJsonObject("audio_info"))
                                    .map(audioInfo -> audioInfo.get("hash"))
                                    .map(JsonElement::getAsString)
                                    .orElse("");

                            MusicMetaData musicMetaData = songOpt.map(KuGouMusic::parseMetaData)
                                    .orElse(new UnknownMusicMeta(Sources.KUGOU_MUSIC.getName().getString()));

                            return (Music) new KuGouMusic(musicMetaData, albumAudioId, hash);
                        })
                        .collect(Collectors.toCollection(ArrayList::new))
                )
                .orElse(new ArrayList<>());

        return Pair.of(songs, playlistMetaData);
    }

    public Optional<JsonObject> getUserPlaylists(int page) {
        String userId = getCookie("userid", "0");
        String token = getCookie("token");
        Map<String, Object> dataMap = Map.of(
                "userid", userId,
                "token", token,
                "total_ver", 979,
                "type", 2,
                "page", page,
                "pagesize", 30
        );

        Map<String, String> paramsMap = Map.of(
                "plat", "1",
                "userid", userId,
                "token", token
        );

        return Optional.ofNullable(parseJson(
                request(
                        "/v7/get_all_list",
                        paramsMap,
                        KuGouRequestConfig.builder()
                                .requestType(KuGouRequestConfig.RequestType.POST)
                                .data(dataMap)
                                .addHeaders("x-router", "cloudlist.service.kugou.com")
                                .build()
                )
        ));
    }

    public Optional<JsonObject> getUserDetail() {
        String userId = getCookie("userid", "0");
        String token = getCookie("token");
        long clientTime = Long.parseLong(getClientTime());
        Map<String, Object> pMap = Map.of(
                "token", token,
                "clienttime", clientTime
        );
        String pk = KuGouMusicApiCrypto.cryptoRSAEncrypt(GSON.toJson(pMap)).toUpperCase();

        Map<String, Object> dataMap = Map.of(
                "visit_time", clientTime,
                "usertype", 1,
                "p", pk,
                "userid", userId
        );

        JsonObject json = parseJson(
                request(
                        "/v3/get_my_info",
                        Map.of(
                                "plat", "1"
                        ),
                        KuGouRequestConfig.builder()
                                .requestType(KuGouRequestConfig.RequestType.POST)
                                .data(dataMap)
                                .addHeaders("x-router", "usercenter.kugou.com")
                                .build()
                )
        );

        return Optional.ofNullable(json);
    }

    public String generateQRCodeKey() {
        String qrUrl = "https://h5.kugou.com/apps/loginQRCode/html/index.html?appid=" + getUseAppid() + "&";
        Map<String, String> paramsMap = Map.of(
                "appid", "1001",
                "type", "1",
                "plat", "4",
                "qrcode_txt", qrUrl,
                "srcappid", "2919"
        );

        JsonObject json = parseJson(
                request(
                        "/v2/qrcode",
                        paramsMap,
                        KuGouRequestConfig.builder()
                                .baseUrl("https://login-user.kugou.com")
                                .requestType(KuGouRequestConfig.RequestType.GET)
                                .encryptType(KuGouRequestConfig.EncryptType.WEB)
                                .build()
                )
        );

        Optional<JsonObject> optional = Optional.ofNullable(json);

        return optional.map(object -> object.getAsJsonObject("data"))
                .map(data -> data.get("qrcode"))
                .map(JsonElement::getAsString)
                .orElse("");
    }

    public Optional<JsonObject> getQRCodeStatus(String key) {
        Map<String, String> paramsMap = Map.of(
                "plat", "4",
                "appid", getUseAppid(),
                "srcappid", "2919",
                "qrcode", key
        );

        JsonObject json = parseJson(
                request(
                        "/v2/get_userinfo_qrcode",
                        paramsMap,
                        KuGouRequestConfig.builder()
                                .baseUrl("https://login-user.kugou.com")
                                .requestType(KuGouRequestConfig.RequestType.GET)
                                .encryptType(KuGouRequestConfig.EncryptType.WEB)
                                .build()
                )
        );

        return Optional.ofNullable(json);
    }

    public boolean sendPhoneCaptcha(String phone) {
        if (StringUtils.isEmpty(phone)) return false;
        Map<String, Object> dataMap = Map.of(
                "businessid", 5,
                "mobile", phone,
                "plat", 3
        );

        JsonObject json = parseJson(
                request(
                        "/v7/send_mobile_code",
                        Map.of(),
                        KuGouRequestConfig.builder()
                                .baseUrl("http://login.user.kugou.com")
                                .requestType(KuGouRequestConfig.RequestType.POST)
                                .data(dataMap)
                                .build()
                )
        );

        return Optional.ofNullable(json)
                .map(obj -> obj.get("error_code"))
                .map(JsonElement::getAsInt)
                .map(code -> code == 0)
                .orElse(false);
    }

    public Optional<Pair<Long, String>> cellphoneLogin(String mobile, String code) {
        if (StringUtils.isEmpty(mobile) || StringUtils.isEmpty(code) || mobile.length() < 11) return Optional.empty();
        long dateTime = System.currentTimeMillis();
        Pair<String, String> encrypt = KuGouMusicApiCrypto.cryptoAesEncrypt(GSON.toJson(Map.of(
                "mobile", mobile,
                "code", code
        )), null, null);
        String maskMobile = mobile.substring(0, 2) + "*****" + mobile.substring(10);
        String dfid = getCookie("dfid", RandomUtil.randomString(24));
        boolean isLite = ClientConfig.INSTANCE.options.kuGouMusicLite;

        String guid = getCookie("KUGOU_API_GUID");
        String mac = getCookie("KUGOU_API_MAC");
        String dev = getCookie("KUGOU_API_DEV");
        String t2 = KuGouMusicApiCrypto.cryptoAesEncrypt(
                guid + "|" +
                "0f607264fc6318a92b9e13c65db7cd3c" + "|" +
                mac + "|" +
                dev + "|" +
                dateTime,
                LITE_T2_KEY,
                LITE_T2_IV
        ).getFirst();

        String t1 = KuGouMusicApiCrypto.cryptoAesEncrypt(
                "|" + dateTime,
                LITE_T1_KEY,
                LITE_T1_IV
        ).getFirst();

        Map<String, Object> dataMap = new HashMap<>(Map.of(
                "plat", 1,
                "support_multi", 1,
                "t1", isLite ? t1 : 0,
                "t2", isLite ? t2 : 0,
                "clienttime_ms", dateTime,
                "mobile", maskMobile,
                "key", KuGouMusicApiCrypto.signParamsKey(String.valueOf(dateTime)),
                "pk", KuGouMusicApiCrypto.cryptoRSAEncrypt(GSON.toJson(Map.of(
                        "clienttime_ms", dateTime,
                        "key", encrypt.getSecond()
                ))).toUpperCase(),
                "params", encrypt.getFirst()
        ));

        if (isLite) {
            dataMap.put("dfid", dfid);
            dataMap.put("dev", getCookie("KUGOU_API_DEV"));
            dataMap.put("gitversion", "5f0b7c4");
        } else {
            dataMap.put("t3", "MCwwLDAsMCwwLDAsMCwwLDA=");
        }

        JsonObject json = parseJson(
                request(
                         "/v7/login_by_verifycode",
                        Map.of(),
                        KuGouRequestConfig.builder()
                                .requestType(KuGouRequestConfig.RequestType.POST)
                                .data(dataMap)
                                .baseUrl("https://loginserviceretry.kugou.com")
                                .addHeaders("SUPPORT-CALM", "1")
                                .addHeaders("User-Agent", "Android16-1070-11440-130-0-LOGIN-wifi")
                                .build()
                )
        );

        return getLoginData(json, encrypt.getSecond());
    }

    public Optional<Pair<Long, String>> login(String userName, String password) {
        if (StringUtils.isEmpty(userName) || StringUtils.isEmpty(password)) return Optional.empty();
        long dateNow = System.currentTimeMillis();
        Pair<String, String> encrypt = KuGouMusicApiCrypto.cryptoAesEncrypt(GSON.toJson(Map.of(
                "pwd", password,
                "code", "",
                "clienttime_ms", dateNow
        )), null, null);

        Map<String, Object> dataMap = new HashMap<>(Map.of(
                "plat", 1,
                "support_multi", 1,
                "clienttime_ms", dateNow,
                "t1", "562a6f12a6e803453647d16a08f5f0c2ff7eee692cba2ab74cc4" +
                              "c8ab47fc467561a7c6b586ce7dc46a63613b246737c03a1dc8f8" +
                              "d162d8ce1d2c71893d19f1d4b797685a4c6d3d81341cbde65e48" +
                              "8c4829a9b4d42ef2df470eb102979fa5adcdd9b4eecfea8b909f" +
                              "f7599abeb49867640f10c3c70fc444effca9d15db44a9a6c9077" +
                              "31e2bb0f22cd9b3536380169995693e5f0e2424e3378097d3813" +
                              "186e3fe96bbe7023808a0981b4e2b6135a76faac",
                "t2", "31c4daf4cf480169ccea1cb7d4a209295865a9d2b78851030169" +
                              "4db229b87807469ea0d41b4d4b9173c2151da7294aeebfc9738d" +
                              "f154bbdf11a4e117bb5dff6a3af8ce5ce333e681c1f29a44038f" +
                              "27567d58992eb81283e080778ac77db1400fdf49b7cf7e26be2e" +
                              "5af4da7830cc3be4",
                "t3", "MCwwLDAsMCwwLDAsMCwwLDA=",
                "username", userName,
                "params", encrypt.getFirst(),
                "pk", KuGouMusicApiCrypto.cryptoRSAEncrypt(GSON.toJson(Map.of(
                        "clienttime_ms", dateNow,
                        "key", encrypt.getSecond()
                ))).toUpperCase()
        ));

        JsonObject json = parseJson(
                request(
                        "/v9/login_by_pwd",
                        Map.of(),
                        KuGouRequestConfig.builder()
                                .requestType(KuGouRequestConfig.RequestType.POST)
                                .data(dataMap)
                                .addHeaders("x-router", "login.user.kugou.com")
                                .build()
                )
        );

        return getLoginData(json, encrypt.getSecond());
    }

    public Optional<Pair<Long, String>> refreshToken() {
        String userId = getCookie("userid", "0");
        String token = getCookie("token", "");

        if (userId.equals("0") || token.isEmpty()) return Optional.empty();
        long dateNow = System.currentTimeMillis();
        Pair<String, String> encrypt = KuGouMusicApiCrypto.cryptoAesEncrypt(GSON.toJson(Map.of(
                "clienttime", Math.floor((double) dateNow / 1000),
                "token", token
        )), getKey(), getIv());
        Pair<String, String> encryptParams = KuGouMusicApiCrypto.cryptoAesEncrypt(GSON.toJson(Map.of()), null, null);
        String pk = KuGouMusicApiCrypto.cryptoRSAEncrypt(GSON.toJson(Map.of(
                "clienttime_ms", dateNow,
                "key", encryptParams.getSecond()
        )));
        boolean isLite = ClientConfig.INSTANCE.options.kuGouMusicLite;

        String guid = getCookie("KUGOU_API_GUID");
        String mac = getCookie("KUGOU_API_MAC");
        String dev = getCookie("KUGOU_API_DEV");
        String t2 = KuGouMusicApiCrypto.cryptoAesEncrypt(
                guid + "|" +
                        "0f607264fc6318a92b9e13c65db7cd3c" + "|" +
                        mac + "|" +
                        dev + "|" +
                        dateNow,
                LITE_T2_KEY,
                LITE_T2_IV
        ).getFirst();

        String t1 = KuGouMusicApiCrypto.cryptoAesEncrypt(
                "|" + dateNow,
                LITE_T1_KEY,
                LITE_T1_IV
        ).getFirst();

        Map<String, Object> dataMap = new HashMap<>(Map.of(
                "dfid", getCookie("dfid", "-"),
                "p3", encrypt.getFirst(),
                "plat", 1,
                "t1", isLite ? t1 : 0,
                "t2", isLite ? t2 : 0,
                "t3", "MCwwLDAsMCwwLDAsMCwwLDA=",
                "pk", pk,
                "params", encryptParams.getFirst(),
                "userid", userId,
                "clienttime_ms", dateNow
        ));

        if (isLite) {
            dataMap.put("dev", getCookie("KUGOU_API_DEV"));
        }

        JsonObject json = parseJson(
                request(
                        "/v5/login_by_token",
                        Map.of(),
                        KuGouRequestConfig.builder()
                                .baseUrl("http://login.user.kugou.com")
                                .requestType(KuGouRequestConfig.RequestType.POST)
                                .data(dataMap)
                                .build()
                )
        );

        return getLoginData(json, encryptParams.getSecond());
    }

    public Optional<Pair<Long, String>> getLoginData(JsonObject json, String encryptKey) {
        Optional<JsonObject> optional = Optional.ofNullable(json);

        Optional<Integer> status = optional.map(object -> object.get("status"))
                .map(JsonElement::getAsInt);

        if (status.isPresent()) {
            if (status.get() != 1) return Optional.empty();
        } else {
            return Optional.empty();
        }

        Optional<JsonObject> dataOpt = optional.map(object -> object.getAsJsonObject("data"));
        try {
            Long userId = dataOpt.map(data -> data.get("userid"))
                    .map(JsonElement::getAsLong)
                    .orElseThrow();

            String token = Optionals
                    .flatFirstOf(
                            dataOpt,
                            data -> Optional
                                    .ofNullable(data.get("secu_params"))
                                    .map(JsonElement::getAsString)
                                    .map(s -> KuGouMusicApiCrypto.cryptoAesDecrypt(s, encryptKey, null)),
                            data -> Optional.of(data.get("token"))
                                    .map(JsonElement::getAsString)
                    )
                    .orElseThrow();

            try {
                // 尝试以 json 格式解析
                JsonObject jsonObject = JsonUtil.from(escapeChars(token));
                JsonElement element = jsonObject.get("token");
                if (element != null) {
                    token = element.getAsString();
                } else {
                    return Optional.empty();
                }
            } catch (JsonSyntaxException ignored) {
            }

            return Optional.of(Pair.of(userId, token));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * 领取酷狗音乐概念版一天畅听 vip, 仅限概念版用户使用
     * @return 领取结果
     */
    public Optional<JsonObject> receiveVip() {
        // 概念版和普通版都能获取, 但只能在概念版使用, 为避免歧义, 只允许在概念版获取
        if (!ClientConfig.INSTANCE.options.kuGouMusicLite) return Optional.empty();

        Map<String, String> params = Map.of(
                "source_id", "90139",
                "receive_day", LocalDate.now().toString()
        );
        JsonObject json = parseJson(
                request(
                        "/youth/v1/recharge/receive_vip_listen_song",
                        params,
                        KuGouRequestConfig.builder()
                                .requestType(KuGouRequestConfig.RequestType.POST)
                                .build()
                )
        );

        return Optional.ofNullable(json);
    }

    public Optional<JsonObject> lyricSearch(String hash) {
        if (StringUtils.isEmpty(hash)) return Optional.empty();
        Map<String, String> paramsMap = Map.of(
                "album_audio_id", "0",
                "appid", getUseAppid(),
                "clientver", getUseClientVer(),
                "duration", "0",
                "hash", hash,
                "keyword", "",
                "lrctxt", "1",
                "man", "yes"
        );

        JsonObject json = parseJson(
                request(
                        "/v1/search",
                        paramsMap,
                        KuGouRequestConfig.builder()
                                .baseUrl("https://lyrics.kugou.com")
                                .clearDefaultParams(true)
                                .build()
                )
        );

        return Optional.ofNullable(json);
    }

    public Optional<String> getLyric(String id, String accessKey) {
        Map<String, String> paramsMap = Map.of(
                "ver", "1",
                "client", "android",
                "id", id,
                "accesskey", accessKey,
                "fmt", "krc",
                "charset", "utf8"
        );

        JsonObject json = parseJson(
                request(
                        "/download",
                        paramsMap,
                        KuGouRequestConfig.builder()
                                .baseUrl("https://lyrics.kugou.com")
                                .build()
                )
        );

        return Optional.ofNullable(json)
                .map(object -> object.get("content"))
                .map(JsonElement::getAsString);
    }

    public Optional<JsonObject> getVIPStatus() {
        Map<String, String> paramsMap = Map.of(
                "busi_type", "concept",
                "opt_product_types", "dvip,qvip",
                "product_type", "svip"
        );

        JsonObject json = parseJson(
                request(
                        "/v1/get_union_vip",
                        paramsMap,
                        KuGouRequestConfig.builder()
                                .baseUrl("https://kugouvip.kugou.com")
                                .build()
                )
        );

        return Optional.ofNullable(json);
    }

    public Optional<JsonObject> getDfid() {
        String userid = getCookie("userid", "0");
        String token = getCookie("token");
        String guid = getCookie("KUGOU_API_GUID");

        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("availableRamSize", 4983533568L);
        dataMap.put("availableRomSize", 48114719);
        dataMap.put("availableSDSize", 48114717);
        dataMap.put("basebandVer", "");
        dataMap.put("batteryLevel", 100);
        dataMap.put("batteryStatus", 3);
        dataMap.put("brand", "Redmi");
        dataMap.put("buildSerial", "unknown");
        dataMap.put("device", "marble");
        dataMap.put("imei", guid);
        dataMap.put("imsi", "");
        dataMap.put("manufacturer", "Xiaomi");
        dataMap.put("uuid", guid);
        dataMap.put("accelerometer", false);
        dataMap.put("accelerometerValue", "");
        dataMap.put("gravity", false);
        dataMap.put("gravityValue", "");
        dataMap.put("gyroscope", false);
        dataMap.put("gyroscopeValue", "");
        dataMap.put("light", false);
        dataMap.put("lightValue", "");
        dataMap.put("magnetic", false);
        dataMap.put("magneticValue", "");
        dataMap.put("orientation", false);
        dataMap.put("orientationValue", "");
        dataMap.put("pressure", false);
        dataMap.put("pressureValue", "");
        dataMap.put("step_counter", false);
        dataMap.put("step_counterValue", "");
        dataMap.put("temperature", false);
        dataMap.put("temperatureValue", "");

        Pair<String, String> aesEncrypt = KuGouMusicApiCrypto.playlistAesEncrypt(GSON.toJson(dataMap));

        String p = KuGouMusicApiCrypto.rsaEncrypt2(GSON.toJson(Map.of(
                "aes", aesEncrypt.getFirst(),
                "uid", userid,
                "token", token)));

        Map<String, String> paramsMap = new HashMap<>();
        paramsMap.put("part", "1");
        paramsMap.put("platid", "1");
        paramsMap.put("p", p);

        HttpResponse<byte[]> response = request(
                "/risk/v2/r_register_dev",
                paramsMap,
                KuGouRequestConfig.builder()
                        .baseUrl("https://userservice.kugou.com")
                        .requestType(KuGouRequestConfig.RequestType.POST)
                        .data(aesEncrypt.getSecond())
                        .encryptType(KuGouRequestConfig.EncryptType.ANDROID)
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );
        String bodyBase64 = Base64.getEncoder().encodeToString(response.body());
        String body = KuGouMusicApiCrypto.playlistAesDecrypt(bodyBase64, aesEncrypt.getFirst());
        JsonObject json = response.statusCode() == 200 ? JsonUtil.from(escapeChars(body)) : null;

        return Optional.ofNullable(json);
    }

    public void updateDfid() {
        Optional<JsonObject> optional = getDfid();
        optional.map(json -> json.getAsJsonObject("data"))
                .map(data -> data.get("dfid"))
                .map(JsonElement::getAsString)
                .ifPresent(dfid -> setCookie("https://www.kugou.com", "dfid", dfid));
    }

    public String getQRCodeLoginLink(String key) {
        return "https://h5.kugou.com/apps/loginQRCode/html/index.html?appid=" + getUseAppid() + "&qrcode=" + key;
    }

    public String getUseAppid() {
        return ClientConfig.INSTANCE.options.kuGouMusicLite ? LITE_APPID : APPID;
    }

    public String getUseClientVer() {
        return ClientConfig.INSTANCE.options.kuGouMusicLite ? LITE_CLIENT_VER : CLIENT_VER;
    }

    public String getClientTime() {
        return String.valueOf((long)(Math.floor((double) System.currentTimeMillis() / 1000)));
    }

    public String getKey() {
        return ClientConfig.INSTANCE.options.kuGouMusicLite ? LITE_KEY : KEY;
    }

    public String getIv() {
        return ClientConfig.INSTANCE.options.kuGouMusicLite ? LITE_IV : IV;
    }

    public String getCookie(String key) {
        return getCookie(key, "");
    }

    public String getCookie(String key, String defaultValue) {
        return COOKIES.getOrDefault(key, defaultValue);
    }
}
