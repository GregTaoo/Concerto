package top.gregtao.concerto.core.enums;

import top.gregtao.concerto.core.Concerto;
import top.gregtao.concerto.core.api.SimpleStringIdentifiable;

public enum OrderType implements SimpleStringIdentifiable {
    NORMAL,
    RANDOM,
    REVERSED,
    LOOP;

    public String getName() {
        return Concerto.getMinecraft().getTranslatableText("concerto.order." + this.asString());
    }
}
