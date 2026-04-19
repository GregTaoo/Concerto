package top.gregtao.concerto.core.music.parser;

import com.google.gson.JsonObject;
import top.gregtao.concerto.core.api.JsonParser;
import top.gregtao.concerto.core.enums.Sources;
import top.gregtao.concerto.core.music.BilibiliMusic;

public class BilibiliMusicJsonParser implements JsonParser<BilibiliMusic> {
    @Override
    public BilibiliMusic fromJson(JsonObject object) {
        return new BilibiliMusic(object.get("bvid").getAsString());
    }

    @Override
    public JsonObject toJson(JsonObject object, BilibiliMusic music) {
        object.addProperty("bvid", music.getBvid());
        return object;
    }

    @Override
    public String name() {
        return Sources.BILIBILI.asString();
    }
}
