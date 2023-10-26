package top.gregtao.concerto.music.parser;

import com.google.gson.JsonObject;
import top.gregtao.concerto.api.JsonParser;
import top.gregtao.concerto.music.PathFileMusic;

public abstract class PathFileMusicJsonParser<T extends PathFileMusic> implements JsonParser<T> {
    private final String name;

    public PathFileMusicJsonParser(String name) {
        this.name = name;
    }

    @Override
    public JsonObject toJson(JsonObject object, T music) {
        object.addProperty("path", music.getRawPath());
        return object;
    }

    @Override
    public String name() {
        return this.name;
    }
}
