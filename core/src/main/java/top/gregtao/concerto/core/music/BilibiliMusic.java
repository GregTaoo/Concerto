package top.gregtao.concerto.core.music;

import com.google.gson.JsonObject;
import top.gregtao.concerto.core.api.CacheableMusic;
import top.gregtao.concerto.core.api.DynamicPath;
import top.gregtao.concerto.core.api.JsonParser;
import top.gregtao.concerto.core.api.MusicJsonParsers;
import top.gregtao.concerto.core.api.MusicSourceNotFoundException;
import top.gregtao.concerto.core.enums.Sources;
import top.gregtao.concerto.core.http.HttpURLInputStream;
import top.gregtao.concerto.core.http.bilibili.BilibiliApiClient;
import top.gregtao.concerto.core.music.lyrics.Lyrics;
import top.gregtao.concerto.core.music.meta.music.BasicMusicMetaData;
import top.gregtao.concerto.core.music.meta.music.UnknownMusicMeta;
import top.gregtao.concerto.core.util.FileUtil;
import top.gregtao.concerto.core.util.Pair;

import java.io.InputStream;
import java.net.URI;
import java.util.Map;

public class BilibiliMusic extends Music implements CacheableMusic, DynamicPath {
    private final String bvid;
    private String aid;
    private String cid;
    private String rawPath;

    public BilibiliMusic(String bvid) {
        this(bvid, null, null);
    }

    public BilibiliMusic(String bvid, String aid, String cid) {
        this.bvid = bvid;
        this.aid = aid;
        this.cid = cid;
    }

    public String getBvid() {
        return this.bvid;
    }
    public String getAid() {
        return this.aid;
    }

    public String getCid() {
        return this.cid;
    }

    @Override
    public InputStream getMusicSource() throws MusicSourceNotFoundException {
        try {
            return FileUtil.createBuffered(new HttpURLInputStream(
                    URI.create(this.getRawPath()).toURL(), 0, this::updateRawPath, BilibiliApiClient.REQUEST_HEADERS
            ));
        } catch (Exception e) {
            throw new MusicSourceNotFoundException(e);
        }
    }

    @Override
    public String getLink() {
        return "https://www.bilibili.com/video/" + this.bvid;
    }

    public String getRawPath() {
        return this.updateRawPath();
    }

    @Override
    public String getLastRawPath() {
        return this.rawPath;
    }

    @Override
    public String updateRawPath() {
        if (this.aid == null || this.cid == null) throw new NullPointerException("aid or cid is null");
        this.rawPath = BilibiliApiClient.INSTANCE.getDirectAudioUrl(this.aid, this.cid);
        return this.rawPath;
    }

    @Override
    public String getLastSuffix() {
        return "m4s";
    }

    @Override
    public String getLastLyrics() {
        return null;
    }

    @Override
    public String getLastSubLyrics() {
        return null;
    }

    @Override
    public Map<String, String> getCustomHeaders() {
        return BilibiliApiClient.REQUEST_HEADERS;
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
