package top.gregtao.concerto.core.music.parser;

import com.google.gson.JsonObject;
import top.gregtao.concerto.core.api.JsonParser;
import top.gregtao.concerto.core.enums.Sources;
import top.gregtao.concerto.core.music.QQMusic;

public class QQMusicJsonParser implements JsonParser<QQMusic> {
    @Override
    public QQMusic fromJson(JsonObject object) {
        return object.has("media") ?
                new QQMusic(object.get("mid").getAsString(), object.get("media").getAsString()) :
                new QQMusic(object.get("mid").getAsString());
    }

    @Override
    public JsonObject toJson(JsonObject object, QQMusic music) {
        object.addProperty("mid", music.mid);
        object.addProperty("media", music.mediaMid);
        return object;
    }

    @Override
    public String name() {
        return Sources.QQ_MUSIC.asString();
    }
}
