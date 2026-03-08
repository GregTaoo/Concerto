package top.gregtao.concerto.core.event;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class PayloadEvent<T> {

    private final List<Consumer<T>> listeners = new ArrayList<>();

    public void register(Consumer<T> listener) {
        this.listeners.add(listener);
    }

    public void emit(T payload) {
        for (Consumer<T> listener : this.listeners) {
            listener.accept(payload);
        }
    }
}
