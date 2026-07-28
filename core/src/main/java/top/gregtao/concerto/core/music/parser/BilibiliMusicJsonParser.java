package top.gregtao.concerto.core.music.parser;

import com.google.gson.JsonObject;
import top.gregtao.concerto.core.api.JsonParser;
import top.gregtao.concerto.core.enums.Sources;
import top.gregtao.concerto.core.music.BilibiliMusic;

public class BilibiliMusicJsonParser implements JsonParser<BilibiliMusic> {
    @Override
    public BilibiliMusic fromJson(JsonObject object) {
        String aid = object.has("aid") ? object.get("aid").getAsString() : null;
        String cid = object.has("cid") ? object.get("cid").getAsString() : null;
        return new BilibiliMusic(object.get("bvid").getAsString(), aid, cid);
    }

    @Override
    public JsonObject toJson(JsonObject object, BilibiliMusic music) {
        object.addProperty("bvid", music.getBvid());
        if (music.getAid() != null && music.getCid() != null) {
            object.addProperty("aid", music.getAid());
            object.addProperty("cid", music.getCid());
        }
        return object;
    }

    @Override
    public String name() {
        return Sources.BILIBILI.asString();
    }
}
