package top.gregtao.concerto.core.api;

import com.google.gson.JsonObject;

public interface JsonParser<T extends JsonParsable<?>> {

    T fromJson(JsonObject object);

    JsonObject toJson(JsonObject object, T t);

    String name();
}
