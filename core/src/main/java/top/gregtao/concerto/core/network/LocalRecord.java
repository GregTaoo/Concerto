package top.gregtao.concerto.core.network;

import top.gregtao.concerto.core.api.Copyable;

public class LocalRecord<T extends Copyable<T>> extends SyncRecord<T> {

    public LocalRecord(T initialState) {
        super(initialState, false);
    }

    @Override
    protected void applyUpdate(T newState) {
        state = newState;
        version.incrementAndGet();
    }
}
