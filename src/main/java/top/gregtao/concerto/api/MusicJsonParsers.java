package top.gregtao.concerto.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import top.gregtao.concerto.enums.OrderType;
import top.gregtao.concerto.enums.Sources;
import top.gregtao.concerto.music.HttpFileMusic;
import top.gregtao.concerto.music.LocalFileMusic;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.music.meta.music.MusicMeta;
import top.gregtao.concerto.music.parser.NeteaseCloudMusicJsonParser;
import top.gregtao.concerto.music.parser.PathFileMusicJsonParser;
import top.gregtao.concerto.music.parser.QQMusicJsonParser;
import top.gregtao.concerto.music.parser.meta.BasicMusicMetaJsonParser;
import top.gregtao.concerto.music.parser.meta.TimelessMusicMetaJsonParser;
import top.gregtao.concerto.player.MusicPlayerStatus;
import top.gregtao.concerto.util.JsonUtil;

import java.util.ArrayList;
import java.util.HashMap;

public class MusicJsonParsers {
    private static final HashMap<String, JsonParser<Music>> MUSIC_PARSERS = new HashMap<>();
    private static final HashMap<String, JsonParser<MusicMeta>> META_PARSERS = new HashMap<>();

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

    // =================================================================================================================
    // Meta parsers

    public static final JsonParser<MusicMeta> TIMELESS_META = registerMetaParser(new TimelessMusicMetaJsonParser());

    public static final JsonParser<MusicMeta> BASIC_META = registerMetaParser(new BasicMusicMetaJsonParser());

    // =================================================================================================================

    public static void registerMusicParser(String name, JsonParser<Music> parser) {
        MUSIC_PARSERS.put(name, parser);
    }

    public static void registerMetaParser(String name, JsonParser<MusicMeta> parser) {
        META_PARSERS.put(name, parser);
    }

    @SuppressWarnings("unchecked")
    public static JsonParser<Music> registerMusicParser(JsonParser<? extends Music> parser) {
        JsonParser<Music> parser1 = (JsonParser<Music>) parser;
        registerMusicParser(parser.name(), parser1);
        return parser1;
    }

    @SuppressWarnings("unchecked")
    public static JsonParser<MusicMeta> registerMetaParser(JsonParser<? extends MusicMeta> parser) {
        JsonParser<MusicMeta> parser1 = (JsonParser<MusicMeta>) parser;
        registerMetaParser(parser.name(), parser1);
        return parser1;
    }

    public static Music from(JsonObject jsonObject) {
        try {
            JsonParser<Music> parser = MUSIC_PARSERS.get(jsonObject.get("name").getAsString());
            Music music = parser.fromJson(jsonObject);
            JsonObject metaObject = jsonObject.getAsJsonObject("meta");
            music.setMusicMeta(META_PARSERS.get(metaObject.get("name").getAsString()).fromJson(metaObject));
            return music;
        } catch (Exception e) {
            return null;
        }
    }

    public static JsonObject to(Music music) {
        try {
            JsonObject object = new JsonObject(), metaObject = new JsonObject();
            object.addProperty("name", music.getJsonParser().name());
            object = music.getJsonParser().toJson(object, music);
            MusicMeta meta = music.getMeta();
            metaObject.addProperty("name", meta.getJsonParser().name());
            object.add("meta", meta.getJsonParser().toJson(metaObject, meta));
            return object;
        } catch (Exception e) {
            return null;
        }
    }

    public static MusicPlayerStatus fromRaw(String json) {
        try {
            ArrayList<Music> list = new ArrayList<>();
            JsonObject object = JsonUtil.from(json);
            JsonArray array = object.get("data").getAsJsonArray();
            array.forEach(element -> {
                Music music = from(element.getAsJsonObject());
                if (music != null) list.add(music);
            });
            return new MusicPlayerStatus(list, Math.min(JsonUtil.getIntOrElse(object, "cur", -1), array.size() - 1),
                    OrderType.valueOf(JsonUtil.getStringOrElse(object, "ord", OrderType.NORMAL.toString())));
        } catch (Exception e) {
            return new MusicPlayerStatus();
        }
    }

    public static String toRaw(MusicPlayerStatus status) {
        JsonArray array = new JsonArray();
        status.getMusicList().forEach(music -> {
            JsonObject object = to(music);
            if (object != null) array.add(object);
        });
        JsonObject object = new JsonObject();
        object.add("data", array);
        object.addProperty("cur", status.getCurrentIndex());
        object.addProperty("ord", status.getOrderType().toString());
        return object.toString();
    }
}
