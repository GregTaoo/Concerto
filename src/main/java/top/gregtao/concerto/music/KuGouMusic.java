package top.gregtao.concerto.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import top.gregtao.concerto.api.*;
import top.gregtao.concerto.enums.Sources;
import top.gregtao.concerto.http.HttpURLInputStream;
import top.gregtao.concerto.http.kugou.KuGouMusicApiClient;
import top.gregtao.concerto.http.kugou.KuGouMusicApiCrypto;
import top.gregtao.concerto.music.lyrics.DefaultFormatLyrics;
import top.gregtao.concerto.music.lyrics.Lyrics;
import top.gregtao.concerto.music.meta.music.BasicMusicMetaData;
import top.gregtao.concerto.music.meta.music.MusicMetaData;
import top.gregtao.concerto.music.meta.music.UnknownMusicMeta;
import top.gregtao.concerto.util.FileUtil;
import top.gregtao.concerto.http.kugou.KuGouLyricsUtil;
import top.gregtao.concerto.util.Optionals;
import top.gregtao.concerto.util.Pair;

import java.io.InputStream;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

public class KuGouMusic extends Music implements CacheableMusic, DynamicPath {

    private final String albumAudioId;

    private String hash;

    private String rawPath, rawLyrics, rawSubLyrics, format;

    private final Map<Level, String> hashMap = new HashMap<>();

    public KuGouMusic(String id, String hash, boolean isEncodeAlbumAudioId) {
        this.albumAudioId = isEncodeAlbumAudioId ? toAlbumAudioId(id) : id;
        this.hash = hash;
    }

    public KuGouMusic(String id, boolean isEncodeAlbumAudioId) {
        this(id, "", isEncodeAlbumAudioId);
    }

    /**
     * 构造函数, 通过 JsonObject 初始化 KuGouMusic 对象
     * <p>
     * 只接受 searchMusic 和 getPlayListAllTrack 返回的 Json 对象
     * @param object Json 对象, 来自搜索或歌单等接口
     */
    public KuGouMusic(JsonObject object) {
        Optional<JsonObject> optional = Optional.ofNullable(object);

        this.albumAudioId = Optionals
                .firstOf(
                        optional,
                        json -> json.get("MixSongID"),
                        json -> json.get("mixsongid")
                )
                .map(JsonElement::getAsString)
                .orElse("");

        this.hash = Optionals
                .firstOf(
                        optional,
                        json -> json.get("FileHash"),
                        json -> json.get("hash")
                )
                .map(JsonElement::getAsString)
                .orElse("");

        this.setMusicMeta(parseMetaDataForSong(object));
    }

    public KuGouMusic(MusicMetaData metaData, String albumAudioId, String hash) {
        this.setMusicMeta(metaData);
        this.albumAudioId = albumAudioId;
        this.hash = hash;
    }

    public String getAlbumAudioId() {
        return albumAudioId;
    }

    public String getHash() {
        return hash;
    }

    public Map<Level, String> getHashMap() {
        if (hashMap.isEmpty()) return updateHashMap();
        return hashMap;
    }

    public Map<Level, String> updateHashMap() {
        Optional<JsonObject> optional = KuGouMusicApiClient.INSTANCE.getMusicHash(this.hash);
        optional.map(json -> json.getAsJsonArray("data"))
                .map(dataArray -> !dataArray.isEmpty() ? dataArray.get(0).getAsJsonObject() : null)
                .ifPresent(data -> {
                    for (Level level : Level.values()) {
                        JsonElement jsonElement = data.get(level.getKey());
                        // 有些音质的音乐可能没有
                        if (jsonElement != null && !jsonElement.getAsString().isEmpty()) {
                            hashMap.put(level, jsonElement.getAsString());
                        }
                    }
                });
        return hashMap;
    }

    @Override
    public String getSuffix() {
        return this.getLastSuffix();
    }

    @Override
    public Music getMusic() {
        return this;
    }

    @Override
    public String getLastRawPath() {
        if (this.rawPath == null) this.getRawPath();
        return this.rawPath;
    }

    @Override
    public String updateRawPath() {
        return this.getRawPath();
    }

    @Override
    public String getLastSuffix() {
        if (this.format == null) this.getRawPath();
        return this.format;
    }

    @Override
    public String getLastLyrics() {
        if (this.rawLyrics == null) this.getLyrics();
        return this.rawLyrics;
    }

    @Override
    public Pair<Lyrics, Lyrics> getLyrics() {
        try {
            Optional<JsonObject> optional = KuGouMusicApiClient.INSTANCE.lyricSearch(this.hash);
            JsonArray jsonArray = optional.map(obj -> obj.getAsJsonArray("candidates"))
                    .orElse(new JsonArray());
            Pair<String, String> bestLyric = getBestLyric(jsonArray);

            Pair<String, String> pair = KuGouMusicApiClient.INSTANCE.getLyric(bestLyric.getFirst(), bestLyric.getSecond())
                    .map(KuGouMusicApiCrypto::decodeLyrics)
                    .map(KuGouLyricsUtil::krcToLrc)
                    .orElseThrow();

            this.rawLyrics = pair.getFirst();
            this.rawSubLyrics = pair.getSecond();

            return Pair.of(
                    new DefaultFormatLyrics().load(pair.getFirst()),
                    new DefaultFormatLyrics().load(pair.getSecond())
            );
        } catch (Exception e) {
            return null;
        }
    }

    public Pair<String, String> getBestLyric(JsonArray jsonArray) {
        if (jsonArray.isEmpty()) return null;
        List<JsonElement> list = jsonArray.asList();
        for (JsonElement jsonElement : list) {
            Optional<JsonObject> object = Optional.of(jsonElement.getAsJsonObject());
            Optional<Integer> contentFormat = object.map(obj -> obj.get("content_format"))
                    .map(JsonElement::getAsInt);

            if (contentFormat.isPresent() && contentFormat.get() > 1) {
                String id = object.map(obj -> obj.get("id"))
                        .map(JsonElement::getAsString)
                        .orElseThrow();

                String accessKey = object.map(obj -> obj.get("accesskey"))
                        .map(JsonElement::getAsString)
                        .orElseThrow();

                return Pair.of(id, accessKey);
            }
        }

        Optional<JsonObject> object = Optional.ofNullable(list.getFirst().getAsJsonObject());
        String id = object.map(obj -> obj.get("id"))
                .map(JsonElement::getAsString)
                .orElseThrow();

        String accessKey = object.map(obj -> obj.get("accesskey"))
                .map(JsonElement::getAsString)
                .orElseThrow();

        return Pair.of(id, accessKey);
    }

    @Override
    public String getLastSubLyrics() {
        return this.rawSubLyrics;
    }

    @Override
    public JsonParser<Music> getJsonParser() {
        return MusicJsonParsers.KUGOU_MUSIC;
    }

    @Override
    public InputStream getMusicSource() throws MusicSourceNotFoundException {
        try {
            return FileUtil.createBuffered(new HttpURLInputStream(URI.create(this.getRawPath()).toURL(), this::getRawPath));
        } catch (Exception e) {
            throw new MusicSourceNotFoundException(e);
        }
    }

    @Override
    public String getLink() {
        // album_audio_id 暂时无法转换为 encode_album_audio_id, 无法生成链接
        return "https://www.kugou.com/";
    }

    @Override
    public void load() {
        Optional<JsonObject> musicDetail = KuGouMusicApiClient.INSTANCE.getMusicDetail(albumAudioId, "album_info,base,authors.base,audio_info");

        try {
            musicDetail.map(json -> json.getAsJsonObject("audio_info"))
                    .map(audioInfo -> audioInfo.get("hash"))
                    .map(JsonElement::getAsString)
                    .ifPresent(hash -> this.hash = hash);
        } catch (Exception ignore) {
        }

        this.setMusicMeta(
                musicDetail
                        .map(KuGouMusic::parseMetaData)
                        .orElseGet(() -> new UnknownMusicMeta(Sources.KUGOU_MUSIC.getName().getString()))
        );
        super.load();
    }

    /**
     * 解析音乐元数据, 处理 getDetail 和 getAlbumSongs 返回的 Json 对象
     * @param object 音乐详情 Json 对象
     * @return 音乐元数据
     */
    public static MusicMetaData parseMetaData(JsonObject object) {

        Optional<JsonObject> optional = Optional.of(object);

        try {
            String name = Optionals
                    .firstOf(
                            optional.map(json -> json.getAsJsonObject("base")),
                            // getDetail 接口
                            json -> json.get("songname"),
                            // getAlbumSongs 接口
                            json -> json.get("audio_name")
                    )
                    .map(JsonElement::getAsString)
                    .orElseThrow();

            Long duration = Optionals
                    .firstOf(
                            optional.map(json -> json.getAsJsonObject("audio_info")),
                            // getDetail 接口
                            json -> json.get("timelength"),
                            // getAlbumSongs 接口
                            json -> json.get("duration")
                    )
                    .map(JsonElement::getAsLong)
                    .orElseThrow();

            List<String> authorList = Optionals
                    .flatFirstOf(
                            optional,
                            // 先尝试从 authors 中提取
                            KuGouMusic::getAuthorsList,
                            // 再尝试从 base 中提取
                            KuGouMusic::getAuthorsListFromBase
                    ).orElseThrow();

            String headPic = optional.map(json -> json.getAsJsonObject("album_info"))
                    .map(albumInfo -> albumInfo.get("cover"))
                    .map(coverElement -> {
                        if (coverElement.isJsonNull()) return "";
                        String cover = coverElement.getAsString();
                        return cover.replace("{size}", "512");
                    })
                    .orElseThrow();
            return new BasicMusicMetaData(String.join(", ", authorList), name,
                    Sources.KUGOU_MUSIC.getName().getString(), duration, headPic);
        } catch (Exception e) {
            return new UnknownMusicMeta(Sources.KUGOU_MUSIC.getName().getString());
        }
    }

    /**
     * 尝试从 authors 中提取作者列表, 可能有 authors 不存在的情况 (作者信息未上传)
     */
    private static Optional<List<String>> getAuthorsList(JsonObject jsonObject) {
        return Optional.ofNullable(jsonObject)
                .map(json -> json.getAsJsonArray("authors"))
                .map(arr -> arr.asList().stream()
                        .map(JsonElement::getAsJsonObject)
                        .map(Optional::of)
                        .map(authorOpt -> Optionals
                                .flatFirstOf(
                                        authorOpt,
                                        // getDetail 接口会再套一层 base 对象
                                        json -> Optional.ofNullable(json.getAsJsonObject("base")),
                                        Optional::ofNullable
                                )
                                // 获取到 author_name 所在对象
                                .map(base -> base.get("author_name"))
                        )
                        .filter(nameElementOpt -> nameElementOpt.isPresent() && !nameElementOpt.get().isJsonNull())
                        .map(nameElementOpt -> nameElementOpt.get().getAsString())
                        .collect(Collectors.toList())
                );
    }

    public static Optional<List<String>> getAuthorsListFromBase(JsonObject jsonObject) {
        return Optional.ofNullable(jsonObject)
                .map(json -> json.getAsJsonObject("base"))
                .map(base -> base.get("author_name"))
                .map(JsonElement::getAsString)
                .map(names -> Arrays.asList(names.split("、")));
    }

    /**
     * 解析音乐元数据, 处理 searchMusic 和 getPlayListAllTrack 返回的 Json 对象
     * @param object 音乐详情 Json 对象
     * @return 音乐元数据
     */
    public static MusicMetaData parseMetaDataForSong(JsonObject object) {
        Optional<JsonObject> optional = Optional.ofNullable(object);

        try {
            // 两个接口的 Json 字段名不相同, 需要分别处理
            long duration = Optionals
                    .flatFirstOf(
                            optional,
                            // searchMusic 接口
                            json -> Optional.ofNullable(json.get("Duration"))
                                    .map(JsonElement::getAsLong)
                                    .map(l -> l * 1000),
                            // getPlaylist 接口
                            json -> Optional.ofNullable(json.get("timelen"))
                                    .map(JsonElement::getAsLong)
                    ).orElseThrow();

            List<String> authorsList = Optionals
                    .firstOf(
                            optional,
                            // searchMusic 接口
                            json -> json.get("Singers"),
                            // getPlaylist 接口
                            json -> json.get("singerinfo")
                    )
                    .map(JsonElement::getAsJsonArray)
                    .map(arr ->
                            // 提取歌手名称
                            arr.asList().stream()
                                    .map(JsonElement::getAsJsonObject)
                                    .map(json -> json.get("name"))
                                    .filter(Objects::nonNull)
                                    .map(JsonElement::getAsString)
                                    .collect(Collectors.toList())
                    ).orElseThrow();

            String headPic = Optionals
                    .firstOf(
                            optional,
                            // searchMusic 接口
                            json -> json.get("Image"),
                            // getPlaylist 接口
                            json -> json.get("cover")
                    )
                    .map(JsonElement::getAsString)
                    .map(pic -> pic.replace("{size}", "512"))
                    .orElseThrow();

            String title = Optionals
                    .firstOf(
                            optional,
                            // searchMusic 接口
                            json -> json.get("FileName"),
                            // getPlaylist 接口
                            json -> json.get("name")
                    )
                    .map(JsonElement::getAsString)
                    .map(fullName -> {
                        // 名称格式为 "歌手一、歌手二 - 歌曲"
                        // 注意分隔符为中文顿号
                        return fullName.replace(String.join("、", authorsList) + " - ", "");
                    })
                    .orElseThrow();

            return new BasicMusicMetaData(String.join(", ", authorsList), title,
                    Sources.KUGOU_MUSIC.getName().getString(), duration, headPic);
        } catch (Exception e) {
            return new UnknownMusicMeta(Sources.KUGOU_MUSIC.getName().getString());
        }
    }


    public String getRawPath() {
        String topHash = getTopHash();
        Optional<String> topMusicLink = KuGouMusicApiClient.INSTANCE.getMusicLink(topHash, true);
        if (topMusicLink.isPresent()) {
            this.rawPath = topMusicLink.get();
            this.format = FileUtil.getSuffix(URI.create(this.rawPath).getPath());
        } else {
            this.rawPath = null;
            this.format = null;
        }
        return this.rawPath;
    }

    public String toAlbumAudioId(String encodeAlbumAudioId) {
        if (encodeAlbumAudioId == null || encodeAlbumAudioId.length() < 3) throw new IllegalArgumentException("Invalid encodeAlbumAudioId");
        // 先去除后两位
        encodeAlbumAudioId = encodeAlbumAudioId.substring(0, encodeAlbumAudioId.length() - 2);
        // 再将剩下的部分从36进制转换为10进制
        long num = Long.parseLong(encodeAlbumAudioId, 36);
        return String.valueOf(num);
    }

    /**
     * 获取最高音质的 Hash
     * @return Hash
     */
    public String getTopHash() {
        return this.getHashMap().entrySet().stream()
                .min(Map.Entry.comparingByKey((a, b) -> Integer.compare(b.getRank(), a.getRank())))
                .orElseGet(() -> Map.entry(Level.STANDARD, this.hash))
                .getValue();
    }

    public enum Level {
        STANDARD(0, "hash_128"),
        HIGH(1, "hash_320"),
        LOSSLESS(2, "hash_flac"),
        HIRES(3, "hash_high");

        private final int rank;
        private final String key;

        Level(int rank, String key) {
            this.rank = rank;
            this.key = key;
        }

        public int getRank() {
            return rank;
        }

        public String getKey() {
            return key;
        }
    }
}
