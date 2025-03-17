package top.gregtao.concerto.music.parser;

import com.google.gson.JsonObject;
import top.gregtao.concerto.api.JsonParser;
import top.gregtao.concerto.enums.Sources;
import top.gregtao.concerto.music.SharedMusic;
import top.gregtao.concerto.util.TextUtil;

public class SharedMusicJsonParser implements JsonParser<SharedMusic> {

    @Override
    public SharedMusic fromJson(JsonObject object) {
        return new SharedMusic(object.get("path").getAsString(), TextUtil.fromBase64(object.get("lyrics").getAsString()),
                TextUtil.fromBase64(object.get("sub_lyrics").getAsString()), object.get("start_time").getAsLong(),
                object.get("start_byte").getAsLong());
    }

    @Override
    public JsonObject toJson(JsonObject object, SharedMusic music) {
        object.addProperty("lyrics", TextUtil.toBase64(music.getRawLyrics()));
        object.addProperty("sub_lyrics", TextUtil.toBase64(music.getRawSubLyrics()));
        object.addProperty("path", music.getRawPath());
        object.addProperty("start_time", music.getStartTime());
        object.addProperty("start_byte", music.getStartByte());
        return object;
    }

    @Override
    public String name() {
        return Sources.SHARED.asString();
    }
}
