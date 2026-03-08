package top.gregtao.concerto.core.music.parser;

import com.google.gson.JsonObject;
import top.gregtao.concerto.core.api.JsonParser;
import top.gregtao.concerto.core.enums.Sources;
import top.gregtao.concerto.core.music.NeteaseCloudMusic;

public class NeteaseCloudMusicJsonParser implements JsonParser<NeteaseCloudMusic> {
    @Override
    public NeteaseCloudMusic fromJson(JsonObject object) {
        return new NeteaseCloudMusic(object.get("id").getAsString(),
                NeteaseCloudMusic.Level.valueOf(object.get("level").getAsString()));
    }

    @Override
    public JsonObject toJson(JsonObject object, NeteaseCloudMusic music) {
        object.addProperty("id", music.getId());
        object.addProperty("level", music.getLevel().toString());
        return object;
    }

    @Override
    public String name() {
        return Sources.NETEASE_CLOUD.asString();
    }
}
