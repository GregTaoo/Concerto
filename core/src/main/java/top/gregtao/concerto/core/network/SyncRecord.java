package top.gregtao.concerto.core.network;

import com.google.gson.*;
import top.gregtao.concerto.core.api.Copyable;
import top.gregtao.concerto.core.player.ConcertoPlayerList;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class SyncRecord<T extends Copyable<T>> {

    public interface Listener<T extends Copyable<T>, V> {
        void update(SyncRecord<T> newRecord, T state, V oldVal, V newVal);
    }

    protected volatile T state;
    protected final AtomicLong version = new AtomicLong(0);
    private final boolean alwaysCopy;

    private final Map<Field, List<Listener<T, Object>>> listeners = new HashMap<>();

    public SyncRecord(T initialState, boolean alwaysCopy) {
        this.state = initialState;
        this.alwaysCopy = alwaysCopy;
    }

    public void addListener(Field field, Listener<T, Object> listener) {
        this.listeners.computeIfAbsent(field, k -> new ArrayList<>()).add(listener);
    }

    public void onFieldUpdate(Field field, Object oldVal, Object newVal) {
        this.listeners.computeIfAbsent(field, k -> new ArrayList<>()).forEach(
                l -> l.update(this, this.state, oldVal, newVal));
    }

    public synchronized T get() {
        return this.state;
    }

    public synchronized void set(Function<T, T> updater, List<Field> updatedFields) {
        T newState = updater.apply(this.alwaysCopy ? this.get().copy() : this.get());
        this.applyUpdate(newState, updatedFields);
    }

    public synchronized void set(Function<T, T> updater, Function<T, List<Field>> updatedFieldsSupplier) {
        List<Field> updatedFields = updatedFieldsSupplier.apply(this.get());
        T newState = updater.apply(this.alwaysCopy ? this.get().copy() : this.get());
        this.applyUpdate(newState, updatedFields);
    }

    protected abstract void applyUpdate(T newState, List<Field> updatedFields);

    public long getVersion() {
        return this.version.get();
    }

    public static <T extends Copyable<T>> ClientRemoteRecord<T> createClientRecord(T init, Consumer<JsonObject> packageSender) {
        return new ClientRemoteRecord<>(init,
                (gson) -> gson.registerTypeHierarchyAdapter(
                        ConcertoPlayerList.class, new ConcertoPlayerList.GsonAdapter()
                )) {
            @Override
            protected void sendPackage(JsonObject patch) {
                // System.out.println("Client sending Patch: " + patch.toString());
                packageSender.accept(patch);
            }
        };
    }

    public static <T extends Copyable<T>, S> ServerRemoteRecord<T> createServerRecord(T init, BiConsumer<JsonObject, S> packageSender, S server) {
        return new ServerRemoteRecord<>(init,
                (gson) -> gson.registerTypeHierarchyAdapter(
                        ConcertoPlayerList.class, new ConcertoPlayerList.GsonAdapter()
                )) {
            @Override
            protected void sendPackage(JsonObject patch) {
                // System.out.println("Server sending Patch: " + patch.toString());
                packageSender.accept(patch, server);
            }
        };
    }

    public static <T extends Copyable<T>> LocalRecord<T> createLocalRecord(T init) {
        return new LocalRecord<>(init);
    }

}
