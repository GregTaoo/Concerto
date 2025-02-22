package top.gregtao.concerto.music;

import top.gregtao.concerto.api.JsonParser;
import top.gregtao.concerto.api.MusicJsonParsers;
import top.gregtao.concerto.http.HttpURLInputStream;
import top.gregtao.concerto.music.lyrics.DefaultFormatLyrics;
import top.gregtao.concerto.music.lyrics.Lyrics;
import top.gregtao.concerto.music.meta.music.MusicMetaData;
import top.gregtao.concerto.util.FileUtil;
import top.gregtao.concerto.util.Pair;

import java.io.InputStream;
import java.net.URI;

public class SharedMusic extends PathFileMusic {
    private final String rawLyrics, rawSubLyrics;

    public SharedMusic(String rawPath, MusicMetaData metaData, String lyrics, String subLyrics) {
        super(rawPath);
        this.rawLyrics = lyrics;
        this.rawSubLyrics = subLyrics;
        this.setMusicMeta(metaData);
    }

    public SharedMusic(String rawPath, String lyrics, String subLyrics) {
        super(rawPath);
        this.rawLyrics = lyrics;
        this.rawSubLyrics = subLyrics;
    }

    public String getRawLyrics() {
        return this.rawLyrics == null ? "" : this.rawLyrics;
    }

    public String getRawSubLyrics() {
        return this.rawSubLyrics == null ? "" : this.rawSubLyrics;
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
