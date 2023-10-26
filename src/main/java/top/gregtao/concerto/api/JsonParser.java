package top.gregtao.concerto.api;

import com.google.gson.JsonObject;

public interface JsonParser<T extends JsonParsable<?>> {

    T fromJson(JsonObject object);

    JsonObject toJson(JsonObject object, T t);

    String name();
}
