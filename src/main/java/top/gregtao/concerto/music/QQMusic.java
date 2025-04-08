package top.gregtao.concerto.music;

import com.google.gson.JsonObject;
import top.gregtao.concerto.api.*;
import top.gregtao.concerto.enums.Sources;
import top.gregtao.concerto.http.HttpURLInputStream;
import top.gregtao.concerto.http.qq.QQMusicApiClient;
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

public class QQMusic extends Music implements CacheableMusic, DynamicPath {

    public String mid, mediaMid, rawPath, rawLyrics, rawSubLyrics, format;

    public QQMusic(String mid) {
        this.mid = mid;
    }

    public QQMusic(String mid, String mediaMid) {
        this(mid);
        this.mediaMid = mediaMid;
    }

    public QQMusic(JsonObject object, int type) {
        this.mid = object.get(type == 2 ? "songmid" : "mid").getAsString();
        this.setMusicMeta(type == 2 ? this.parseMetaData2(object) : this.parseMetaData(object));
    }

    public MusicMetaData parseMetaData(JsonObject object) {
        String title = object.get("name").getAsString();
        List<String> singers = new ArrayList<>();
        object.getAsJsonArray("singer").forEach(element -> singers.add(element.getAsJsonObject().get("name").getAsString()));
        this.mediaMid = object.getAsJsonObject("file").get("media_mid").getAsString();
        String picUrl = QQMusicApiClient.getAlbumPictureUrl(object.getAsJsonObject("album").get("pmid").getAsString());
        return new BasicMusicMetaData(String.join(", ", singers), title, Sources.QQ_MUSIC.getName().getString(), object.get("interval").getAsLong() * 1000, picUrl);
    }

    public MusicMetaData parseMetaData2(JsonObject object) {
        String title = object.get("songname").getAsString();
        List<String> singers = new ArrayList<>();
        object.getAsJsonArray("singer").forEach(element -> singers.add(element.getAsJsonObject().get("name").getAsString()));
        this.mediaMid = object.get("strMediaMid").getAsString();
        String picUrl = QQMusicApiClient.getAlbumPictureUrl(object.get("albummid").getAsString());
        return new BasicMusicMetaData(String.join(", ", singers), title, Sources.QQ_MUSIC.getName().getString(), object.get("interval").getAsLong() * 1000, picUrl);
    }

    @Override
    public void load() {
        try {
            JsonObject object = QQMusicApiClient.INSTANCE.getMusicDetail(this.mid)
                    .getAsJsonObject("songinfo").getAsJsonObject("data");
            this.setMusicMeta(this.parseMetaData(object.getAsJsonObject("track_info")));
        } catch (Exception e) {
            this.setMusicMeta(new UnknownMusicMeta(Sources.QQ_MUSIC.getName().getString()));
        }
        super.load();
    }

    @Override
    public Pair<Lyrics, Lyrics> getLyrics() {
        try {
            Pair<String, String> pair = QQMusicApiClient.INSTANCE.getLyrics(this.mid);
            this.rawLyrics = pair.getFirst();
            this.rawSubLyrics = pair.getSecond();
            Lyrics lyrics1 = new DefaultFormatLyrics().load(pair.getFirst());
            Lyrics lyrics2 = new DefaultFormatLyrics().load(pair.getSecond());
            return Pair.of(lyrics1.isEmpty() ? null : lyrics1, lyrics2.isEmpty() ? null : lyrics2);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public JsonParser<Music> getJsonParser() {
        return MusicJsonParsers.QQ_MUSIC;
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
        return "https://y.qq.com/n/ryqq/songDetail/" + this.mid;
    }

    public String getRawPath() {
        try {
            Pair<String, String> pair = QQMusicApiClient.INSTANCE.getMusicLink(this.mid, this.mediaMid);
            this.rawPath = pair.getFirst().isEmpty() ? null : pair.getFirst();
            this.format = pair.getSecond().isEmpty() ? null : pair.getSecond();
        } catch (Exception e) {
            this.rawPath = null;
            this.format = null;
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
    public String getLastSuffix() {
        if (this.format == null) return this.getRawPath();
        return this.format;
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
    public String getSuffix() {
        return this.getLastSuffix();
    }

    @Override
    public Music getMusic() {
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof QQMusic music) && music.mid.equals(this.mid);
    }

    public enum Level {
        /*
        MASTER: 臻品母带2.0, 24Bit 192kHz
        ATMOS_2: 臻品全景声2.0, 16Bit 44.1kHz
        ATMOS_51: 臻品音质2.0, 16Bit 44.1kHz
        FLAC: flac 格式, 16Bit 44.1kHz ~ 24Bit 48kHz
        OGG_640: ogg 格式, 640kbps
        OGG_320: ogg 格式, 320kbps
        OGG_192: ogg 格式, 192kbps
        OGG_96: ogg 格式, 96kbps
        MP3_320: mp3 格式, 320kbps
        MP3_128: mp3 格式, 128kbps
        ACC_192: m4a 格式, 192kbps
        ACC_96: m4a 格式, 96kbps
        ACC_48: m4a 格式, 48kbps
        */

        ATMOS_2("Q000", "flac"),
        ATMOS_51("Q001", "flac"),
        OGG_320("O800", "ogg"),
        MP3_320("M800", "mp3"),
        MP3_128("M500", "mp3"),
        OGG_96("O400", "ogg"),
        FLAC("F000", "flac"),
        // 不支持的格式
        // MASTER("AI00", "flac"),
        // OGG_640("O801", "ogg"),
        // ACC_192("C600", "m4a"),
        // ACC_48("C200", "m4a"),
        // ACC_96("C400", "m4a"),

        // 会 404 的格式
        // OGG_192("O600", "ogg"),
        ;

        private final String prefix, suffix;

        Level(String prefix, String suffix) {
            this.prefix = prefix;
            this.suffix = suffix;
        }

        public String getSuffix() {
            return this.suffix;
        }

        public String getFilename(String mid, String mediaMid) {
            return this.prefix + mid + mediaMid + "." + this.suffix;
        }
    }
}
