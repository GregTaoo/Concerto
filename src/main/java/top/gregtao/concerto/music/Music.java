package top.gregtao.concerto.music;

import top.gregtao.concerto.api.*;
import top.gregtao.concerto.music.lyric.BrokenLyricException;
import top.gregtao.concerto.music.lyric.Lyric;
import top.gregtao.concerto.music.meta.music.MusicMeta;

import java.io.IOException;
import java.io.InputStream;

public abstract class Music implements JsonParsable<Music>, LazyLoadable, WithMetaData {
    private boolean isMetaLoaded = false;
    private MusicMeta musicMeta = null;

    public InputStream getMusicStream() {
        try {
            return this.getInputStream();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public Lyric getLyric() throws IOException, BrokenLyricException {
        return null;
    }

    public MusicMeta getMeta() {
        if (!this.isLoaded()) {
            this.load();
            this.isMetaLoaded = true;
        }
        return this.musicMeta;
    }

    public void load() {
        this.isMetaLoaded = true;
    }

    public void setMusicMeta(MusicMeta musicMeta) {
        this.musicMeta = musicMeta;
        this.isMetaLoaded = true;
    }

    public boolean isLoaded() {
        return this.isMetaLoaded;
    }

    public abstract InputStream getInputStream() throws Exception;
}
