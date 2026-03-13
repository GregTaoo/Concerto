package top.gregtao.concerto.core.network;

import top.gregtao.concerto.core.api.Copyable;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public abstract class SyncRecord<T extends Copyable<T>> {

    protected T state;
    protected final AtomicLong version = new AtomicLong(0);
    private final boolean alwaysCopy;

    public SyncRecord(T initialState, boolean alwaysCopy) {
        this.state = initialState;
        this.alwaysCopy = alwaysCopy;
    }

    public synchronized T get() {
        return this.state;
    }

    public synchronized void set(Function<T, T> updater) {
        T newState = updater.apply(this.alwaysCopy ? this.get().copy() : this.get());
        applyUpdate(newState);
    }

    protected abstract void applyUpdate(T newState);

    public long getVersion() {
        return this.version.get();
    }
}
