package top.gregtao.concerto.music.list;

import com.mojang.datafixers.util.Pair;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.music.meta.music.list.PlaylistMetaData;

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
