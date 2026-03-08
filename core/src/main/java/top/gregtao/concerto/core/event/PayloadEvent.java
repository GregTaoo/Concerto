package top.gregtao.concerto.core.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class PayloadEvent<T> {

    private final List<Consumer<T>> listeners = new CopyOnWriteArrayList<>();

    public Event.Subscription subscribe(Consumer<T> listener) {
        this.listeners.add(listener);
        return () -> this.listeners.remove(listener);
    }

    public void emit(T payload) {
        for (Consumer<T> listener : this.listeners) {
            listener.accept(payload);
        }
    }
}
