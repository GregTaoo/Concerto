package top.gregtao.concerto.music.parser.meta;

import com.google.gson.JsonObject;
import top.gregtao.concerto.music.meta.music.BasicMusicMetaData;
import top.gregtao.concerto.music.meta.music.TimelessMusicMetaData;

public class BasicMusicMetaJsonParser extends TimelessMusicMetaJsonParser {

    @Override
    public BasicMusicMetaData fromJson(JsonObject object) {
        return new BasicMusicMetaData(super.fromJson(object), object.get("dr").getAsLong());
    }

    @Override
    public JsonObject toJson(JsonObject object, TimelessMusicMetaData meta) {
        BasicMusicMetaData basic = (BasicMusicMetaData) meta;
        object.addProperty("dr", basic.getDuration().asMilliseconds());
        return super.toJson(object, meta);
    }

    @Override
    public String name() {
        return "basic";
    }
}
