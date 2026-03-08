package top.gregtao.concerto.core.api;

public interface JsonParsable<T extends JsonParsable<?>> {

    JsonParser<T> getJsonParser();
}
