package top.gregtao.concerto.core.api;

import com.google.gson.*;
import top.gregtao.concerto.core.music.Music;

import java.lang.reflect.Type;

public class MusicSerializer implements JsonSerializer<Music>, JsonDeserializer<Music> {

    @Override
    public Music deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        return MusicJsonParsers.from(jsonElement.getAsJsonObject());
    }

    @Override
    public JsonElement serialize(Music music, Type type, JsonSerializationContext jsonSerializationContext) {
        return MusicJsonParsers.to(music);
    }
}
