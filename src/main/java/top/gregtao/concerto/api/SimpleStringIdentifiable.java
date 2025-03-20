package top.gregtao.concerto.api;

import net.minecraft.util.StringIdentifiable;

import java.util.Arrays;

public interface SimpleStringIdentifiable extends StringIdentifiable {

    @Override
    default String asString() {
        return this.toString().toLowerCase();
    }

    static <T extends Enum<T> & SimpleStringIdentifiable> T fromString(Class<T> enumClass, String value) {
        return Arrays.stream(enumClass.getEnumConstants())
                .filter(e -> e.asString().equalsIgnoreCase(value))
                .findFirst()
                .orElse(enumClass.getEnumConstants()[0]);
    }
}
