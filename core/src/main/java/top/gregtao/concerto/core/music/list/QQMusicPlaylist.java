package top.gregtao.concerto.core.music.list;

import com.google.gson.JsonObject;
import top.gregtao.concerto.core.http.qq.QQMusicApiClient;
import top.gregtao.concerto.core.music.Music;
import top.gregtao.concerto.core.music.meta.music.list.PlaylistMetaData;
import top.gregtao.concerto.core.util.Pair;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;

public class QQMusicPlaylist extends Playlist {

    public final String id;

    public boolean simply = false;

    public QQMusicPlaylist(String id, boolean isAlbum) {
        super(isAlbum);
        this.id = id;
    }

    /**
     * @param simply TRUE if there isn't music data in the Json or else FALSE
     */
    public QQMusicPlaylist(JsonObject object, boolean isAlbum, boolean simply) {
        super(isAlbum ? QQMusicApiClient.INSTANCE.parseAlbumJson(object, simply) :
                QQMusicApiClient.INSTANCE.parsePlaylistJson(object, simply), isAlbum);
        this.simply = simply;
        this.id = object.get(isAlbum ? "albumMID" : "dissid").getAsString();
    }

    @Override
    Pair<ArrayList<Music>, PlaylistMetaData> loadData() {
        try {
            return this.isAlbum ? QQMusicApiClient.INSTANCE.getAlbum(this.id) : QQMusicApiClient.INSTANCE.getPlaylist(this.id);
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
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
