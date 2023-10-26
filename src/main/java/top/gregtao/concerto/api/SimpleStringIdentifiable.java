package top.gregtao.concerto.api;

import net.minecraft.util.StringIdentifiable;

public interface SimpleStringIdentifiable extends StringIdentifiable {

    @Override
    default String asString() {
        return this.toString().toLowerCase();
    }
}
