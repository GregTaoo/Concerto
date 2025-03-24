package top.gregtao.concerto.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import net.minecraft.util.StringIdentifiable;
import top.gregtao.concerto.api.*;
import top.gregtao.concerto.enums.Sources;
import top.gregtao.concerto.http.HttpURLInputStream;
import top.gregtao.concerto.http.netease.NeteaseCloudApiClient;
import top.gregtao.concerto.music.lyrics.DefaultFormatLyrics;
import top.gregtao.concerto.music.lyrics.Lyrics;
import top.gregtao.concerto.music.meta.music.BasicMusicMetaData;
import top.gregtao.concerto.music.meta.music.MusicMetaData;
import top.gregtao.concerto.music.meta.music.UnknownMusicMeta;
import top.gregtao.concerto.util.FileUtil;
import top.gregtao.concerto.util.Pair;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class NeteaseCloudMusic extends Music implements CacheableMusic, DynamicPath, Likeable {
    private final String id;
    private final Level level;
    private String rawPath, rawLyrics, rawSubLyrics;

    public NeteaseCloudMusic(String id, Level level) {
        this.id = id;
        this.level = level;
    }

    public NeteaseCloudMusic(JsonObject object, Level level) {
        this.id = object.get("id").getAsString();
        this.level = level;
        this.setMusicMeta(parseMetaData(object));
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
        return "https://music.163.com/song?id=" + this.getId();
    }

    public String getRawPath() {
        try {
            JsonObject object = NeteaseCloudApiClient.INSTANCE.getMusicLink(this.id, this.level)
                    .getAsJsonArray("data").get(0).getAsJsonObject();
            this.rawPath = object.get("url").getAsString();
            this.rawPath = this.rawPath.isEmpty() ? null : this.rawPath;
        } catch (Exception e) {
            this.rawPath = null;
        }
        return this.rawPath;
    }

    @Override
    public String getLastRawPath() {
        if (this.rawPath == null) return this.getRawPath();
        return this.rawPath;
    }

    @Override
    public String updateRawPath() {
        return this.getRawPath();
    }

    @Override
    public String getLastLyrics() {
        if (this.rawLyrics == null) this.getLyrics();
        return this.rawLyrics;
    }

    @Override
    public String getLastSubLyrics() {
        return this.rawSubLyrics;
    }

    @Override
    public Pair<Lyrics, Lyrics> getLyrics() {
        try {
            Pair<String, String> pair = NeteaseCloudApiClient.INSTANCE.getLyrics(this.id);
            this.rawLyrics = pair.getFirst();
            this.rawSubLyrics = pair.getSecond();
            Lyrics lyrics1 = new DefaultFormatLyrics().load(pair.getFirst());
            Lyrics lyrics2 = new DefaultFormatLyrics().load(pair.getSecond());
            return Pair.of(lyrics1.isEmpty() ? null : lyrics1, lyrics2.isEmpty() ? null : lyrics2);
        } catch (Exception e) {
            return null;
        }
    }

    public static MusicMetaData parseMetaData(JsonObject object) {
        String name = object.get("name").getAsString();
        long duration = object.get("dt").getAsLong();
        JsonArray authors = object.getAsJsonArray("ar");
        List<String> authorList = new ArrayList<>();
        authors.forEach(element -> authorList.add(element.getAsJsonObject().get("name").getAsString()));
        JsonObject album = object.getAsJsonObject("al");
        String headPic = "";
        if (!album.isJsonNull()) {
            JsonElement element = album.get("picUrl");
            if (element != null && !element.isJsonNull()) headPic = element.getAsString();
        }
        return new BasicMusicMetaData(String.join(", ", authorList), name,
                Sources.NETEASE_CLOUD.getName().getString(), duration, headPic);
    }

    @Override
    public void load() {
        try {
            JsonObject object = NeteaseCloudApiClient.INSTANCE.getMusicDetail(this.id)
                    .getAsJsonArray("songs").get(0).getAsJsonObject();
            this.setMusicMeta(parseMetaData(object));
        } catch (Exception e) {
            this.setMusicMeta(new UnknownMusicMeta(Sources.NETEASE_CLOUD.getName().getString()));
        }
        super.load();
    }

    @Override
    public JsonParser<Music> getJsonParser() {
        return MusicJsonParsers.NETEASE_CLOUD;
    }

    public String getId() {
        return this.id;
    }

    public Level getLevel() {
        return this.level;
    }

    @Override
    public String getSuffix() {
        return "mp3";
    }

    @Override
    public Music getMusic() {
        return this;
    }

    @Override
    public boolean likeIt() {
        return NeteaseCloudApiClient.LOCAL_USER.likeMusic(this);
    }

    @Override
    public boolean dislikeIt() {
        return NeteaseCloudApiClient.LOCAL_USER.dislikeMusic(this);
    }

    public enum Level implements SimpleStringIdentifiable {
        STANDARD,
        HIGHER,
        EXHIGH,
        LOSSLESS,
        HIRES;

        public static final Codec<Level> CODEC = StringIdentifiable.createCodec(Level::values,
                s -> SimpleStringIdentifiable.fromString(Level.class, s));
    }
}
