package top.gregtao.concerto.core.music.list;

import top.gregtao.concerto.core.music.Music;
import top.gregtao.concerto.core.music.meta.music.list.PlaylistMetaData;
import top.gregtao.concerto.core.util.Pair;

import java.util.ArrayList;

public class FixedPlaylist extends Playlist {

    public FixedPlaylist(ArrayList<Music> list, PlaylistMetaData meta, boolean isAlbum) {
        super(list, meta, isAlbum);
    }

    @Override
    public boolean isLoaded() {
        this.loaded = true;
        return true;
    }

    @Override
    public void load() {
        this.loaded = true;
    }

    @Override
    Pair<ArrayList<Music>, PlaylistMetaData> loadData() {
        return null;
    }
}
