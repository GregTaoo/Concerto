package top.gregtao.concerto.core.music.list;

import com.google.gson.JsonObject;
import top.gregtao.concerto.core.config.ClientConfig;
import top.gregtao.concerto.core.http.netease.NeteaseCloudApiClient;
import top.gregtao.concerto.core.music.Music;
import top.gregtao.concerto.core.music.meta.music.list.PlaylistMetaData;
import top.gregtao.concerto.core.util.Pair;

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
        super(isAlbum ? NeteaseCloudApiClient.INSTANCE.parseAlbumJson(object, ClientConfig.INSTANCE.options.neteaseMusicQuality, simply) :
                NeteaseCloudApiClient.INSTANCE.parsePlaylistJson(object, ClientConfig.INSTANCE.options.neteaseMusicQuality, simply), isAlbum);
        this.simply = simply;
        this.id = object.get("id").getAsString();
    }

    @Override
    Pair<ArrayList<Music>, PlaylistMetaData> loadData() {
        return this.isAlbum() ? NeteaseCloudApiClient.INSTANCE.getAlbum(this.id, ClientConfig.INSTANCE.options.neteaseMusicQuality) :
                NeteaseCloudApiClient.INSTANCE.getPlaylist(this.id, ClientConfig.INSTANCE.options.neteaseMusicQuality);
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
