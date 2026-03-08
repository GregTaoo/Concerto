package top.gregtao.concerto.core.api;

public interface SimpleStringIdentifiable {

    default String asString() {
        return this.toString().toLowerCase();
    }
}
