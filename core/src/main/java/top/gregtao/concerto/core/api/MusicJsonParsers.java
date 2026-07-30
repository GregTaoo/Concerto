package top.gregtao.concerto.core.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import top.gregtao.concerto.core.Concerto;
import top.gregtao.concerto.core.enums.OrderType;
import top.gregtao.concerto.core.enums.Sources;
import top.gregtao.concerto.core.music.HttpFileMusic;
import top.gregtao.concerto.core.music.LocalFileMusic;
import top.gregtao.concerto.core.music.Music;
import top.gregtao.concerto.core.music.list.FixedPlaylist;
import top.gregtao.concerto.core.music.list.Playlist;
import top.gregtao.concerto.core.music.meta.music.MusicMetaData;
import top.gregtao.concerto.core.music.meta.music.list.PlaylistMetaData;
import top.gregtao.concerto.core.music.parser.*;
import top.gregtao.concerto.core.music.parser.meta.BasicMusicMetaJsonParser;
import top.gregtao.concerto.core.music.parser.meta.TimelessMusicMetaJsonParser;
import top.gregtao.concerto.core.player.ConcertoPlayerList;
import top.gregtao.concerto.core.player.MusicPlayerHandler;
import top.gregtao.concerto.core.util.JsonUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class MusicJsonParsers {
    private static final HashMap<String, JsonParser<Music>> MUSIC_PARSERS = new HashMap<>();
    private static final HashMap<String, JsonParser<MusicMetaData>> META_PARSERS = new HashMap<>();

    // =================================================================================================================
    // Main parsers

    public static final JsonParser<Music> LOCAL_FILE = registerMusicParser(new PathFileMusicJsonParser<LocalFileMusic>(Sources.LOCAL_FILE.asString()) {
        @Override
        public LocalFileMusic fromJson(JsonObject object) {
            return new LocalFileMusic(object.get("path").getAsString());
        }
    });

    public static final JsonParser<Music> HTTP_FILE = registerMusicParser(new PathFileMusicJsonParser<HttpFileMusic>(Sources.INTERNET.asString()) {
        @Override
        public HttpFileMusic fromJson(JsonObject object) {
            return new HttpFileMusic(object.get("path").getAsString());
        }
    });

    public static final JsonParser<Music> NETEASE_CLOUD = registerMusicParser(new NeteaseCloudMusicJsonParser());

    public static final JsonParser<Music> QQ_MUSIC = registerMusicParser(new QQMusicJsonParser());

    public static final JsonParser<Music> KUGOU_MUSIC = registerMusicParser(new KuGouMusicJsonParser());

    public static final JsonParser<Music> BILIBILI = registerMusicParser(new BilibiliMusicJsonParser());

    public static final JsonParser<Music> SHARED = registerMusicParser(new SharedMusicJsonParser());

    // =================================================================================================================
    // Meta parsers

    public static final JsonParser<MusicMetaData> TIMELESS_META = registerMetaParser(new TimelessMusicMetaJsonParser());

    public static final JsonParser<MusicMetaData> BASIC_META = registerMetaParser(new BasicMusicMetaJsonParser());

    // =================================================================================================================

    public static void registerMusicParser(String name, JsonParser<Music> parser) {
        MUSIC_PARSERS.put(name, parser);
    }

    public static void registerMetaParser(String name, JsonParser<MusicMetaData> parser) {
        META_PARSERS.put(name, parser);
    }

    @SuppressWarnings("unchecked")
    public static JsonParser<Music> registerMusicParser(JsonParser<? extends Music> parser) {
        JsonParser<Music> parser1 = (JsonParser<Music>) parser;
        registerMusicParser(parser.name(), parser1);
        return parser1;
    }

    @SuppressWarnings("unchecked")
    public static JsonParser<MusicMetaData> registerMetaParser(JsonParser<? extends MusicMetaData> parser) {
        JsonParser<MusicMetaData> parser1 = (JsonParser<MusicMetaData>) parser;
        registerMetaParser(parser.name(), parser1);
        return parser1;
    }

    public static Music from(String str) {
        return from(JsonUtil.from(str));
    }

    public static Music from(String str, boolean withMeta) {
        return from(JsonUtil.from(str), withMeta);
    }

    public static Music from(JsonObject jsonObject) {
        return from(jsonObject, true);
    }

    public static Music from(JsonObject jsonObject, boolean withMeta) {
        try {
            JsonParser<Music> parser = MUSIC_PARSERS.get(jsonObject.get("name").getAsString());
            Music music = parser.fromJson(jsonObject);
            if (withMeta) {
                JsonObject metaObject = jsonObject.getAsJsonObject("meta");
                music.setMusicMeta(META_PARSERS.get(metaObject.get("name").getAsString()).fromJson(metaObject));
            }
            return music;
        } catch (Exception e) {
            Concerto.getLogger().warn("Error occurred when converting from JSON to music, {}: {}", e, jsonObject.toString());
            return null;
        }
    }

    public static JsonObject to(Music music) {
        return to(music, true);
    }

    public static JsonObject to(Music music, boolean withMeta) {
        try {
            JsonObject object = new JsonObject(), metaObject = new JsonObject();
            object.addProperty("name", music.getJsonParser().name());
            object = music.getJsonParser().toJson(object, music);
            if (withMeta) {
                MusicMetaData meta = music.getMeta();
                metaObject.addProperty("name", meta.getJsonParser().name());
                object.add("meta", meta.getJsonParser().toJson(metaObject, meta));
            }
            return object;
        } catch (Exception e) {
            Concerto.getLogger().warn("Error occurred when converting from music to JSON, {}: {}", e, music.getMeta());
            return null;
        }
    }

    public static Playlist fromPlaylist(String str) {
        return fromPlaylist(JsonUtil.from(str), false);
    }

    public static Playlist fromPlaylist(String str, boolean withMeta) {
        return fromPlaylist(JsonUtil.from(str), withMeta);
    }

    public static Playlist fromPlaylist(JsonObject object) {
        return fromPlaylist(object, false);
    }

    public static Playlist fromPlaylist(JsonObject object, boolean withMeta) {
        try {
            String title = object.get("title").getAsString(), author = object.get("author").getAsString(),
                    createTime = object.get("createTime").getAsString(), description = object.get("description").getAsString();
            boolean isAlbum = object.get("isAlbum").getAsBoolean();
            ArrayList<Music> musicList = new ArrayList<>();
            for (JsonElement element : object.getAsJsonArray("list")) {
                Music music = from(element.getAsJsonObject(), withMeta);
                if (music != null) musicList.add(music);
            }
            return new FixedPlaylist(musicList, new PlaylistMetaData(author, title, createTime, description), isAlbum);
        } catch (Exception e) {
            Concerto.getLogger().warn("Error occurred when converting from JSON to playlist, {}: {}", e, object.toString());
            return null;
        }
    }

    public static JsonObject toPlaylist(Playlist playlist) {
        return toPlaylist(playlist, false);
    }

    public static JsonObject toPlaylist(Playlist playlist, boolean withMeta) {
        try {
            playlist.load();
            JsonObject object = new JsonObject();
            PlaylistMetaData meta = playlist.getMeta();
            object.addProperty("title", meta.title());
            object.addProperty("author", meta.author());
            object.addProperty("createTime", meta.createTime());
            object.addProperty("description", meta.description());
            object.addProperty("isAlbum", playlist.isAlbum());
            JsonArray array = new JsonArray();
            for (Music music : playlist.getList()) {
                JsonObject musicObject = to(music, withMeta);
                if (musicObject != null) array.add(musicObject);
            }
            object.add("list", array);
            return object;
        } catch (Exception e) {
            Concerto.getLogger().warn("Error occurred when converting from playlist to JSON, {}: {}", e, playlist.getMeta());
            return null;
        }
    }

    public static MusicPlayerHandler fromRaw(String json) {
        try {
            if (json.isEmpty()) return new MusicPlayerHandler();
            JsonObject object = JsonUtil.from(json);
            ConcertoPlayerList.GsonAdapter adapter = new ConcertoPlayerList.GsonAdapter();
            ConcertoPlayerList list = adapter.deserialize(object.get("data"), null, null);
            return new MusicPlayerHandler(list,
                    JsonUtil.getUUIDOrElse(object, "cur", null),
                    OrderType.valueOf(JsonUtil.getStringOrElse(object, "ord", OrderType.NORMAL.toString())),
                    parsePlaybackHistory(object.getAsJsonArray("history")));
        } catch (Exception e) {
            Concerto.getLogger().warn("Error parsing JSON from music.json: {}", e.toString());
            return new MusicPlayerHandler();
        }
    }

    public static String toRaw(MusicPlayerHandler status) {
        ConcertoPlayerList.GsonAdapter adapter = new ConcertoPlayerList.GsonAdapter();
        JsonElement data = adapter.serialize(status.getMusicList(), null, null);
        JsonObject object = new JsonObject();
        object.add("data", data);
        UUID currentIndex = status.getCurrentIndex();
        if (currentIndex != null) {
            object.addProperty("cur", status.getCurrentIndex().toString());
        }
        object.addProperty("ord", status.getOrderType().toString());
        JsonArray history = new JsonArray();
        status.getState().get().playbackHistory.forEach(uuid -> history.add(uuid.toString()));
        object.add("history", history);
        return object.toString();
    }

    private static List<UUID> parsePlaybackHistory(JsonArray history) {
        List<UUID> parsed = new ArrayList<>();
        if (history == null) return parsed;
        history.forEach(element -> {
            try {
                parsed.add(UUID.fromString(element.getAsString()));
            } catch (Exception ignored) {
            }
        });
        return parsed;
    }
}
