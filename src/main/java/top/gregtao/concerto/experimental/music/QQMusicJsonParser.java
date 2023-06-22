package top.gregtao.concerto.experimental.music;

import com.google.gson.JsonObject;
import top.gregtao.concerto.api.JsonParser;
import top.gregtao.concerto.enums.Sources;
import top.gregtao.concerto.experimental.music.QQMusic;

public class QQMusicJsonParser implements JsonParser<QQMusic> {
    @Override
    public QQMusic fromJson(JsonObject object) {
        return new QQMusic(object.get("mid").getAsString());
    }

    @Override
    public JsonObject toJson(JsonObject object, QQMusic music) {
        object.addProperty("mid", music.mid);
        return object;
    }

    @Override
    public String name() {
        return Sources.QQ_MUSIC.asString();
    }
}
