package top.gregtao.concerto.core.music.parser;

import com.google.gson.JsonObject;
import top.gregtao.concerto.core.api.JsonParser;
import top.gregtao.concerto.core.enums.Sources;
import top.gregtao.concerto.core.music.KuGouMusic;

public class KuGouMusicJsonParser implements JsonParser<KuGouMusic> {
    @Override
    public KuGouMusic fromJson(JsonObject object) {
        return new KuGouMusic(object.get("id").getAsString(),
                object.get("hash").getAsString(), false);
    }

    @Override
    public JsonObject toJson(JsonObject object, KuGouMusic kuGouMusic) {
        object.addProperty("id", kuGouMusic.getAlbumAudioId());
        object.addProperty("hash", kuGouMusic.getHash());
        return object;
    }

    @Override
    public String name() {
        return Sources.KUGOU_MUSIC.asString();
    }
}
