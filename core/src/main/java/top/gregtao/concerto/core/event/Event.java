package top.gregtao.concerto.core.event;

import java.util.ArrayList;
import java.util.List;

public class Event {

    private final List<Runnable> listeners = new ArrayList<>();

    public void register(Runnable listener) {
        this.listeners.add(listener);
    }

    public void emit() {
        for (Runnable listener : this.listeners) {
            listener.run();
        }
    }
}
