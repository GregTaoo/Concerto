package top.gregtao.concerto.music.list;

import com.mojang.datafixers.util.Pair;
import top.gregtao.concerto.api.LazyLoadable;
import top.gregtao.concerto.api.WithMetaData;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.music.meta.music.list.PlaylistMeta;

import java.util.ArrayList;

public abstract class Playlist implements LazyLoadable, WithMetaData {

    protected ArrayList<Music> list = new ArrayList<>();

    protected PlaylistMeta meta;

    protected boolean loaded = false, isAlbum = false;

    public Playlist(boolean isAlbum) {
        this.isAlbum = isAlbum;
    }

    public Playlist(ArrayList<Music> list, PlaylistMeta meta, boolean isAlbum) {
        this(isAlbum);
        this.list = list;
        this.meta = meta;
        this.loaded = true;
    }

    public Playlist(Pair<ArrayList<Music>, PlaylistMeta> pair, boolean isAlbum) {
        this(pair.getFirst(), pair.getSecond(), isAlbum);
    }

    public boolean isLoaded() {
        return this.loaded;
    }

    public boolean isAlbum() {
        return this.isAlbum;
    }

    abstract Pair<ArrayList<Music>, PlaylistMeta> loadData();

    public void load() {
        if (this.loaded) return;
        Pair<ArrayList<Music>, PlaylistMeta> data = this.loadData();
        this.list = data.getFirst();
        this.meta = data.getSecond();
        this.loaded = true;
    }

    public ArrayList<Music> getList() {
        if (this.isLoaded()) {
            return this.list;
        } else {
            throw new RuntimeException("Calling before being loaded.");
        }
    }

    public PlaylistMeta getMeta() {
        if (this.isLoaded()) {
            return this.meta;
        } else {
            throw new RuntimeException("Calling before being loaded.");
        }
    }
}
