package top.gregtao.concerto.core.network;

import top.gregtao.concerto.core.api.Copyable;
import top.gregtao.concerto.core.util.Pair;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LocalRecord<T extends Copyable<T>> extends SyncRecord<T> {

    public LocalRecord(T initialState) {
        super(initialState, false);
    }

    @Override
    protected synchronized void applyUpdate(T newState, List<Field> updatedFields) {
        try {
            Map<Field, Pair<Object, Object>> updatedFieldValues = new HashMap<>();
            if (updatedFields != null) {
                for (Field f : updatedFields) {
                    Object newVal = f.get(newState);
                    Object oldVal = f.get(this.state);
                    updatedFieldValues.put(f, Pair.of(oldVal, newVal));
                }
            }
            this.state = newState;
            this.version.incrementAndGet();
            updatedFieldValues.forEach((field, pair) ->
                    this.onFieldUpdate(field, pair.getFirst(), pair.getSecond()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
