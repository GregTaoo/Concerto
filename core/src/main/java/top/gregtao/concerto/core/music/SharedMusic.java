package top.gregtao.concerto.core.music;

import top.gregtao.concerto.core.api.JsonParser;
import top.gregtao.concerto.core.api.MusicJsonParsers;
import top.gregtao.concerto.core.api.MusicSourceNotFoundException;
import top.gregtao.concerto.core.http.HttpURLInputStream;
import top.gregtao.concerto.core.music.lyrics.DefaultFormatLyrics;
import top.gregtao.concerto.core.music.lyrics.Lyrics;
import top.gregtao.concerto.core.music.meta.music.MusicMetaData;
import top.gregtao.concerto.core.util.FileUtil;
import top.gregtao.concerto.core.util.Pair;

import java.io.InputStream;
import java.net.URI;

public class SharedMusic extends PathFileMusic {
    private final String rawLyrics, rawSubLyrics;
    public long startTime = 0, startByte = 0;

    public SharedMusic(String rawPath, MusicMetaData metaData, String lyrics, String subLyrics) {
        super(rawPath);
        this.rawLyrics = lyrics;
        this.rawSubLyrics = subLyrics;
        this.setMusicMeta(metaData);
    }

    public SharedMusic(String rawPath, String lyrics, String subLyrics, long startTime, long startByte) {
        super(rawPath);
        this.rawLyrics = lyrics;
        this.rawSubLyrics = subLyrics;
        this.startTime = startTime;
        this.startByte = startByte;
    }

    public String getRawLyrics() {
        return this.rawLyrics == null ? "" : this.rawLyrics;
    }

    public String getRawSubLyrics() {
        return this.rawSubLyrics == null ? "" : this.rawSubLyrics;
    }

    public long getStartTime() {
        return this.startTime;
    }

    public long getStartByte() {
        return this.startByte;
    }

    @Override
    public InputStream getMusicSource() throws MusicSourceNotFoundException {
        try {
            return FileUtil.createBuffered(new HttpURLInputStream(URI.create(this.getRawPath()).toURL(),
                    (int) this.startByte, null));
        } catch (Exception e) {
            throw new MusicSourceNotFoundException(e);
        }
    }

    @Override
    public Pair<Lyrics, Lyrics> getLyrics() {
        try {
            Lyrics lyrics1 = new DefaultFormatLyrics().load(this.rawLyrics);
            Lyrics lyrics2 = new DefaultFormatLyrics().load(this.rawSubLyrics);
            return Pair.of(lyrics1.isEmpty() ? null : lyrics1, lyrics2.isEmpty() ? null : lyrics2);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public JsonParser<Music> getJsonParser() {
        return MusicJsonParsers.SHARED;
    }
}
