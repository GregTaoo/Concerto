package top.gregtao.concerto.api;

public interface LazyLoadable {

    void load();

    boolean isLoaded();
}
