package top.gregtao.concerto.music;

import top.gregtao.concerto.api.CacheableMusic;
import top.gregtao.concerto.api.JsonParsable;
import top.gregtao.concerto.api.LazyLoadable;
import top.gregtao.concerto.api.WithMetaData;
import top.gregtao.concerto.config.MusicCacheManager;
import top.gregtao.concerto.music.lyrics.Lyrics;
import top.gregtao.concerto.music.meta.music.MusicMetaData;
import top.gregtao.concerto.util.FileUtil;
import top.gregtao.concerto.util.Pair;

import java.io.*;

public abstract class Music implements JsonParsable<Music>, LazyLoadable, WithMetaData {

    private boolean isMetaLoaded = false;
    private MusicMetaData musicMetaData = null;

    public InputStream getMusicSourceOrNull() {
        if (this instanceof CacheableMusic cacheable) {
            File child = MusicCacheManager.INSTANCE.getChild(cacheable);
            try {
                return child == null ? this.getMusicSource() : FileUtil.createBuffered(new FileInputStream(child));
            } catch (MusicSourceNotFoundException | FileNotFoundException e) {
                return null;
            }
        } else {
            try {
                return this.getMusicSource();
            } catch (MusicSourceNotFoundException e) {
                return null;
            }
        }
    }

    public Pair<Lyrics, Lyrics> getLyrics() throws IOException {
        return null;
    }

    public MusicMetaData getMeta() {
        if (!this.isLoaded()) {
            this.load();
            this.isMetaLoaded = true;
        }
        return this.musicMetaData;
    }

    public void load() {
        this.isMetaLoaded = true;
    }

    public void setMusicMeta(MusicMetaData musicMetaData) {
        this.musicMetaData = musicMetaData;
        this.isMetaLoaded = true;
    }

    public boolean isLoaded() {
        return this.isMetaLoaded;
    }

    public abstract InputStream getMusicSource() throws MusicSourceNotFoundException;

    public abstract String getLink();
}
