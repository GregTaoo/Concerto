package top.gregtao.concerto.music.parser.meta;

import com.google.gson.JsonObject;
import top.gregtao.concerto.music.meta.music.BasicMusicMeta;
import top.gregtao.concerto.music.meta.music.TimelessMusicMeta;

public class BasicMusicMetaJsonParser extends TimelessMusicMetaJsonParser {

    @Override
    public BasicMusicMeta fromJson(JsonObject object) {
        return new BasicMusicMeta(super.fromJson(object), object.get("dr").getAsLong());
    }

    @Override
    public JsonObject toJson(JsonObject object, TimelessMusicMeta meta) {
        BasicMusicMeta basic = (BasicMusicMeta) meta;
        object.addProperty("dr", basic.getDuration().asMilliseconds());
        return super.toJson(object, meta);
    }

    @Override
    public String name() {
        return "basic";
    }
}
