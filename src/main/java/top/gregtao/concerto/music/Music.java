package top.gregtao.concerto.music;

import com.mojang.datafixers.util.Pair;
import top.gregtao.concerto.api.JsonParsable;
import top.gregtao.concerto.api.LazyLoadable;
import top.gregtao.concerto.api.WithMetaData;
import top.gregtao.concerto.music.lyric.Lyric;
import top.gregtao.concerto.music.meta.music.MusicMetaData;

import java.io.IOException;

public abstract class Music implements JsonParsable<Music>, LazyLoadable, WithMetaData {

    private boolean isMetaLoaded = false;
    private MusicMetaData musicMetaData = null;

    public MusicSource getMusicSourceOrNull() {
        try {
            return this.getMusicSource();
        } catch (MusicSourceNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Pair<Lyric, Lyric> getLyric() throws IOException {
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
