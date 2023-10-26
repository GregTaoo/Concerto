package top.gregtao.concerto.api;

public interface JsonParsable<T extends JsonParsable<?>> {

    JsonParser<T> getJsonParser();
}
