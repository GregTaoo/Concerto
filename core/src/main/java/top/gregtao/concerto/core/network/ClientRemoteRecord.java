package top.gregtao.concerto.core.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import top.gregtao.concerto.core.api.Copyable;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Function;

public abstract class ClientRemoteRecord<T extends Copyable<T>> extends SyncRecord<T> {

    private final Gson gson;

    private final List<Field> fields = new ArrayList<>();
    private final Map<Field, Object> snapshot = new HashMap<>();

    public ClientRemoteRecord(T initialState, Function<GsonBuilder, GsonBuilder> builderFactory) {
        super(initialState, true);
        this.gson = builderFactory.apply(new GsonBuilder().serializeNulls()).create();
        initFields();
        takeSnapshot();
    }

    private void initFields() {
        Class<?> c = state.getClass();
        while (c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                f.setAccessible(true);
                fields.add(f);
            }
            c = c.getSuperclass();
        }
    }

    private void takeSnapshot() {
        try {
            for (Field f : fields) {
                snapshot.put(f, f.get(state));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void applyUpdate(T newState) {
        JsonObject diff = buildDiff(newState);
        JsonObject patch = new JsonObject();
        patch.addProperty("version", version.get());
        patch.add("state", diff);
        sendPackage(patch);
    }

    private JsonObject buildDiff(T newState) {
        JsonObject diff = new JsonObject();
        try {
            for (Field f : fields) {
                Object oldVal = snapshot.get(f);
                Object newVal = f.get(newState);
                if (!Objects.equals(oldVal, newVal)) {
                    diff.add(f.getName(), gson.toJsonTree(newVal));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return diff;
    }

    protected void applyDiff(JsonObject diff) {
        try {
            for (Map.Entry<String, JsonElement> e : diff.entrySet()) {
                for (Field f : fields) {
                    if (f.getName().equals(e.getKey())) {
                        Object val = gson.fromJson(e.getValue(), f.getGenericType());
                        f.set(state, val);
                        snapshot.put(f, val);
                        break;
                    }
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public synchronized void receivePatch(JsonObject patch) {
        long v = patch.get("version").getAsLong();
        if (v <= version.get()) return;
        JsonObject diff = patch.getAsJsonObject("state");
        applyDiff(diff);
        version.set(v);
    }

    protected abstract void sendPackage(JsonObject patch);
}
