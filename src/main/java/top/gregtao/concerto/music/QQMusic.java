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

    public String mid, mediaMid, rawPath, rawLyrics, rawSubLyrics;

    public QQMusic(String mid) {
        this.mid = mid;
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
        return this.rawPath = QQMusicApiClient.INSTANCE.getMusicLink(this.mid, this.mediaMid);
    }

    @Override
    public String getLastRawPath() {
        if (this.rawPath == null) return this.getRawPath();
        return this.rawPath;
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
        return "ogg";
    }

    @Override
    public Music getMusic() {
        return this;
    }
}
