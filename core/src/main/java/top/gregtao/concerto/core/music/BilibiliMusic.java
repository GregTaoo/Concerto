package top.gregtao.concerto.core.music;

import com.google.gson.JsonObject;
import top.gregtao.concerto.core.api.CacheableMusic;
import top.gregtao.concerto.core.api.JsonParser;
import top.gregtao.concerto.core.api.MusicJsonParsers;
import top.gregtao.concerto.core.api.MusicSourceNotFoundException;
import top.gregtao.concerto.core.config.MusicCacheManager;
import top.gregtao.concerto.core.enums.Sources;
import top.gregtao.concerto.core.http.HttpURLInputStream;
import top.gregtao.concerto.core.http.bilibili.BilibiliApiClient;
import top.gregtao.concerto.core.music.lyrics.Lyrics;
import top.gregtao.concerto.core.music.meta.music.BasicMusicMetaData;
import top.gregtao.concerto.core.music.meta.music.UnknownMusicMeta;
import top.gregtao.concerto.core.util.FileUtil;
import top.gregtao.concerto.core.util.Pair;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public class BilibiliMusic extends Music implements CacheableMusic {
    private final String bvid;
    private String aid;
    private String cid;

    public BilibiliMusic(String bvid) {
        this.bvid = bvid;
    }

    public String getBvid() {
        return this.bvid;
    }

    @Override
    public InputStream getMusicSource() throws MusicSourceNotFoundException {
        try {
            return FileUtil.createBuffered(new HttpURLInputStream(URI.create(this.getRawPath()).toURL()));
        } catch (Exception e) {
            throw new MusicSourceNotFoundException(e);
        }
    }

    @Override
    public String getLink() {
        return "https://www.bilibili.com/video/" + this.bvid;
    }

    public String getRawPath() {
        return BilibiliApiClient.INSTANCE.getDirectAudioUrl(this.aid, this.cid);
    }

    @Override
    public Pair<Lyrics, Lyrics> getLyrics() {
        return null;
    }

    public BasicMusicMetaData parseMetaData(JsonObject object) {
        JsonObject data = object.getAsJsonObject("data");
        String title = data.get("title").getAsString(), pic = data.get("pic").getAsString();
        String author = data.getAsJsonObject("owner").get("name").getAsString();
        long duration = data.get("duration").getAsLong() * 1000;
        this.aid = data.get("aid").getAsString();
        this.cid = data.get("cid").getAsString();
        return new BasicMusicMetaData(author, title, Sources.BILIBILI.asString(), duration, pic);
    }

    @Override
    public void load() {
        try {
            JsonObject object = BilibiliApiClient.INSTANCE.getVideoData(this.bvid);
            this.setMusicMeta(parseMetaData(object));
        } catch (Exception e) {
            this.setMusicMeta(new UnknownMusicMeta(Sources.BILIBILI.getName()));
        }
        try {
            // Plain cache write: the m4s (fMP4/AAC) audio is decoded natively now,
            // no transcode step needed.
            MusicCacheManager.INSTANCE.addMusic(this);
        } catch (UnsupportedAudioFileException | IOException e) {
            throw new RuntimeException(e);
        }
        super.load();
    }

    @Override
    public JsonParser<Music> getJsonParser() {
        return MusicJsonParsers.BILIBILI;
    }

    @Override
    public String getSuffix() {
        return "m4s";
    }

    @Override
    public Music getMusic() {
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof BilibiliMusic music) && music.bvid.equals(this.bvid);
    }
}
