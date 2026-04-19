package top.gregtao.concerto.core.music.list;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import top.gregtao.concerto.core.music.Music;
import top.gregtao.concerto.core.music.meta.music.list.PlaylistMetaData;
import top.gregtao.concerto.core.util.MathUtil;
import top.gregtao.concerto.core.util.Optionals;
import top.gregtao.concerto.core.util.Pair;
import top.gregtao.concerto.core.http.kugou.KuGouMusicApiClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class KuGouMusicPlaylist extends Playlist {

    private final String id;

    public KuGouMusicPlaylist(String id, boolean isAlbum) {
        super(isAlbum);
        this.id = id;
    }

    public KuGouMusicPlaylist(JsonObject object, boolean isAlbum, boolean isFromDetail) {
        super(isAlbum);

        if (!isAlbum) {
            this.id = Optional.ofNullable(object)
                    .map(json -> json.get(isFromDetail ? "global_collection_id" : "gid"))
                    .map(JsonElement::getAsString)
                    .orElseThrow();
            this.meta = parsePlaylistInfo(object, isFromDetail);
        } else {
            this.id = Optional.ofNullable(object)
                    .map(json -> json.get("albumid"))
                    .map(JsonElement::getAsString)
                    .orElseThrow();
            this.meta = parseAlbumInfo(object);
        }

        this.loaded = true;
    }

    public String getId() {
        return id;
    }

    public static PlaylistMetaData parsePlaylistInfo(JsonObject jsonObject, boolean isDetail) {
        String authorKey = isDetail ? "list_create_username" : "nickname" ;
        String titleKey = isDetail ? "name" : "specialname";

        Optional<JsonObject> optional = Optional.ofNullable(jsonObject);
        String author = optional.map(json -> json.get(authorKey))
                .map(JsonElement::getAsString)
                .orElse("");

        String title = optional.map(json -> json.get(titleKey))
                .map(JsonElement::getAsString)
                .orElse("");

        String desc = optional.map(json -> json.get("intro"))
                .map(JsonElement::getAsString)
                .orElse("");

        String createTime = optional.map(json -> json.get("create_time"))
                .map(JsonElement::getAsString)
                .map(Integer::parseInt)
                .map(i -> i * 1000L)
                .map(MathUtil::formattedTime)
                .orElse("");

        return new PlaylistMetaData(author, title, createTime, desc);
    }

    /**
     * 解析专辑元数据, 处理 searchAlbum 和 getAlbumDetail 返回的数据
     * @param jsonObject 专辑的 JsonObject
     * @return 专辑元数据
     */
    public static PlaylistMetaData parseAlbumInfo(JsonObject jsonObject) {
        Optional<JsonObject> optional = Optional.ofNullable(jsonObject);

        String title = Optionals
                .firstOf(
                        optional,
                        // searchAlbum 接口返回的专辑数据
                        json -> json.get("albumname"),
                        // getAlbumDetail 接口返回的专辑数据
                        json -> json.get("album_name")
                )
                .map(JsonElement::getAsString)
                .orElse("");

        List<String> authorsList = Optionals
                .firstOf(
                        optional,
                        // searchAlbum 接口
                        json -> json.getAsJsonArray("singers"),
                        // getAlbumDetail 接口
                        json -> json.getAsJsonArray("authors")
                )
                .map(arr -> StreamSupport.stream(arr.spliterator(), false))
                // 作者/歌手列表中字段不一样, 需要分别处理
                .map(stream -> stream
                        .map(JsonElement::getAsJsonObject)
                        .map(object -> Optionals
                                .firstOf(
                                        Optional.of(object),
                                        // searchAlbum 接口
                                        json -> json.get("name"),
                                        // getAlbumDetail 接口
                                        json -> json.get("author_name")
                                )
                                .map(JsonElement::getAsString)
                                .orElse("")
                        ).collect(Collectors.toList())
                )
                .orElse(new ArrayList<>());

        String desc = optional.map(json -> json.get("intro"))
                .map(JsonElement::getAsString)
                .orElse("");

        String createTime = Optionals
                .firstOf(
                        optional,
                        json -> json.get("publish_time"),
                        json -> json.get("publish_date")
                )
                .map(JsonElement::getAsString)
                .orElse("");

        return new PlaylistMetaData(
                String.join(", ", authorsList),
                title,
                createTime,
                desc
        );
    }

    @Override
    Pair<ArrayList<Music>, PlaylistMetaData> loadData() {
        return isAlbum ?
                KuGouMusicApiClient.INSTANCE.getAlbum(this.id) :
                KuGouMusicApiClient.INSTANCE.getPlaylist(this.id);
    }

    @Override
    public ArrayList<Music> getList() {
        Pair<ArrayList<Music>, PlaylistMetaData> pair = this.loadData();
        this.list = pair.getFirst();
        this.meta = pair.getSecond();
        this.loaded = true;
        return this.list;
    }
}