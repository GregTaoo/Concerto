package top.gregtao.concerto.core.event;

import java.util.ArrayList;
import java.util.List;

public class Event {

    private final List<Runnable> listeners = new ArrayList<>();

    public Subscription subscribe(Runnable listener) {
        this.listeners.add(listener);
        return () -> this.listeners.remove(listener);
    }

    public void emit() {
        for (Runnable listener : this.listeners) {
            listener.run();
        }
    }

    public interface Subscription {
        void unsubscribe();
    }
}
