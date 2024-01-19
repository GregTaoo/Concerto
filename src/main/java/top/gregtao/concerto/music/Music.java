package top.gregtao.concerto.music;

import com.mojang.datafixers.util.Pair;
import top.gregtao.concerto.api.CacheableMusic;
import top.gregtao.concerto.api.JsonParsable;
import top.gregtao.concerto.api.LazyLoadable;
import top.gregtao.concerto.api.WithMetaData;
import top.gregtao.concerto.config.MusicCacheManager;
import top.gregtao.concerto.music.lyrics.Lyrics;
import top.gregtao.concerto.music.meta.music.MusicMetaData;

import java.io.File;
import java.io.IOException;

public abstract class Music implements JsonParsable<Music>, LazyLoadable, WithMetaData {

    private boolean isMetaLoaded = false;
    private MusicMetaData musicMetaData = null;

    public MusicSource getMusicSourceOrNull() {
        if (this instanceof CacheableMusic cacheable) {
            File child = MusicCacheManager.INSTANCE.getChild(cacheable);
            try {
                return child == null ? this.getMusicSource() : MusicSource.of(child);
            } catch (MusicSourceNotFoundException e) {
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

    public abstract MusicSource getMusicSource() throws MusicSourceNotFoundException;
}
