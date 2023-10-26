package top.gregtao.concerto.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.StringReader;

public class JsonUtil {

    public static JsonObject from(String raw) {
        JsonReader reader = new JsonReader(new StringReader(raw));
        reader.setLenient(true);
        return JsonParser.parseReader(reader).getAsJsonObject();
    }

    public static String getStringOrElse(JsonObject object, String name, String def) {
        try {
            return object.get(name).getAsString();
        } catch (Exception e) {
            return def;
        }
    }

    public static int getIntOrElse(JsonObject object, String name, int def) {
        try {
            return object.get(name).getAsInt();
        } catch (Exception e) {
            return def;
        }
    }
}
