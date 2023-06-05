package top.gregtao.concerto.music.parser.meta;

import com.google.gson.JsonObject;
import top.gregtao.concerto.api.JsonParser;
import top.gregtao.concerto.music.meta.music.TimelessMusicMeta;

public class TimelessMusicMetaJsonParser implements JsonParser<TimelessMusicMeta> {

    @Override
    public TimelessMusicMeta fromJson(JsonObject object) {
        return new TimelessMusicMeta(
                object.get("author").getAsString(),
                object.get("title").getAsString(),
                object.get("src").getAsString(),
                object.get("pic").getAsString()
        );
    }

    @Override
    public JsonObject toJson(JsonObject object, TimelessMusicMeta meta) {
        object.addProperty("title", meta.title());
        object.addProperty("src", meta.getSource());
        object.addProperty("author", meta.author());
        object.addProperty("pic", meta.headPictureUrl());
        return object;
    }

    @Override
    public String name() {
        return "timeless";
    }

}
