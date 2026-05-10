package top.gregtao.concerto.core.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import top.gregtao.concerto.core.Concerto;
import top.gregtao.concerto.core.api.Copyable;
import top.gregtao.concerto.core.util.Pair;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public abstract class ClientRemoteRecord<T extends Copyable<T>> extends SyncRecord<T> {

    private final Gson gson;

    protected final Map<String, Field> fields = new HashMap<>();

    protected final Object lock = new Object();

    public ClientRemoteRecord(T initialState, Function<GsonBuilder, GsonBuilder> builderFactory) {
        super(initialState, true);
        this.gson = builderFactory.apply(new GsonBuilder().serializeNulls()).create();
        initFields();
    }

    private void initFields() {
        Class<?> c = this.state.getClass();
        while (c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                int modifiers = f.getModifiers();
                if (!Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers)) {
                    f.setAccessible(true);
                    this.fields.put(f.getName(), f);
                }
            }
            c = c.getSuperclass();
        }
    }

    @Override
    protected void applyUpdate(T newState, List<Field> updatedFields) {
        JsonObject patch = new JsonObject(), diff;
        synchronized (this.lock) {
            diff = this.buildDiff(newState, updatedFields);
            if (diff.isEmpty()) return;
            patch.addProperty("version", this.version.get());
        }
        patch.add("state", diff);
        this.sendPackage(patch);
    }

    public JsonObject buildFull() {
        synchronized (this.lock) {
            JsonObject object = new JsonObject();
            try {
                for (Field f : this.fields.values()) {
                    object.add(f.getName(), this.gson.toJsonTree(f.get(this.state)));
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            JsonObject full = new JsonObject();
            full.add("state", object);
            full.addProperty("version", this.version.get());
            return full;
        }
    }

    // 请确保该函数不在 Critical Section!
    protected JsonObject buildDiff(T newState, List<Field> updatedFields) {
        JsonObject diff = new JsonObject();
        try {
            if (updatedFields != null) {
                for (Field f : updatedFields) {
                    Object newVal = f.get(newState);
                    diff.add(f.getName(), this.gson.toJsonTree(newVal));
                }
            } else {
                for (Field f : this.fields.values()) {
                    Object oldVal = f.get(this.state);
                    Object newVal = f.get(newState);
                    if (!Objects.equals(oldVal, newVal)) {
                        diff.add(f.getName(), this.gson.toJsonTree(newVal));
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // System.out.println("Built diff: " + diff);
        return diff;
    }

    protected void applyDiff(JsonObject diff) {
        try {
            Map<Field, Pair<Object, Object>> updatedFields = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : diff.entrySet()) {
                Field f = this.fields.get(e.getKey());
                if (f != null) {
                    Object newVal = this.gson.fromJson(e.getValue(), f.getGenericType());
                    Object oldVal = f.get(this.state);
                    f.set(this.state, newVal);
                    updatedFields.put(f, Pair.of(oldVal, newVal));
                }
            }
            for (Map.Entry<Field, Pair<Object, Object>> e : updatedFields.entrySet()) {
                this.onFieldUpdate(e.getKey(), e.getValue().getFirst(), e.getValue().getSecond());
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public boolean receivePatch(JsonObject patch) {
        // System.out.println("Client received Patch: " + patch.toString());
        synchronized (this.lock) {
            long newVersion = patch.get("version").getAsLong();
            long version = this.version.get();
            if (newVersion < version) {
                Concerto.getLogger().warn("Client: Patch version not available. Expected: " + version + " Actual: " + newVersion);
                return false;
            }
            JsonObject diff = patch.getAsJsonObject("state");
            this.applyDiff(diff);
            this.version.set(newVersion);
        }
        return true;
    }

    public void sendFullPackage() {
        this.sendPackage(this.buildFull());
    }

    protected abstract void sendPackage(JsonObject patch);
}
