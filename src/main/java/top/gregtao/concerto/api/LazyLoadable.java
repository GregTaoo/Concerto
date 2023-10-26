package top.gregtao.concerto.api;

public interface LazyLoadable {

    void load();

    boolean isLoaded();

    default void load(Runnable callback) {
        this.load();
        callback.run();
    }
}
