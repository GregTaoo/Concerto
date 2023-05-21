package top.gregtao.concerto.music.list;

import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import top.gregtao.concerto.http.netease.NeteaseCloudApiClient;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.music.NeteaseCloudMusic;
import top.gregtao.concerto.music.meta.music.list.PlaylistMeta;

import java.util.ArrayList;

public class NeteaseCloudPlaylist extends Playlist {

    public final String id;

    public boolean simply = false;

    public NeteaseCloudPlaylist(String id, boolean isAlbum) {
        super(isAlbum);
        this.id = id;
    }

    /**
     * @param simply TRUE if there isn't music data in the Json or else FALSE
     */
    public NeteaseCloudPlaylist(JsonObject object, boolean isAlbum, boolean simply) {
        super(isAlbum ? NeteaseCloudApiClient.INSTANCE.parseAlbumJson(object, NeteaseCloudMusic.Level.STANDARD, simply) :
                NeteaseCloudApiClient.INSTANCE.parsePlayListJson(object, NeteaseCloudMusic.Level.STANDARD, simply), isAlbum);
        this.simply = simply;
        this.id = object.get("id").getAsString();
    }

    @Override
    Pair<ArrayList<Music>, PlaylistMeta> loadData() {
        return this.isAlbum() ? NeteaseCloudApiClient.INSTANCE.getAlbum(this.id, NeteaseCloudMusic.Level.STANDARD) :
                NeteaseCloudApiClient.INSTANCE.getPlayList(this.id, NeteaseCloudMusic.Level.STANDARD);
    }

    // It is suggested to call this in an independent thread
    @Override
    public ArrayList<Music> getList() {
        if (this.simply && this.isLoaded()) {
            this.list = this.loadData().getFirst();
        }
        return super.getList();
    }
}
