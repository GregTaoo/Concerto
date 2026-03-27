package top.gregtao.concerto.core.network;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import top.gregtao.concerto.core.Concerto;
import top.gregtao.concerto.core.api.Copyable;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Function;

public abstract class ServerRemoteRecord<T extends Copyable<T>> extends ClientRemoteRecord<T> {

    public ServerRemoteRecord(T initialState, Function<GsonBuilder, GsonBuilder> builderFactory) {
        super(initialState, builderFactory);
    }

    @Override
    public boolean receivePatch(JsonObject patch) {
        // System.out.println("Server received Patch: " + patch.toString());
        long clientVersion = patch.get("version").getAsLong();
        synchronized (this.lock) {
            long currentVersion = this.version.get();
            if (clientVersion > currentVersion) {
                Concerto.getLogger().warn("Server: Client version mismatch. Expected: " + clientVersion + " Actual: " + currentVersion);
                return false;
            }
            // new version
            long newVersion = this.version.incrementAndGet();
            // save
            JsonObject diff = patch.getAsJsonObject("state");
            this.applyDiff(diff);
            // forward
            JsonObject forward = patch.deepCopy();
            forward.addProperty("version", newVersion);
            sendPackage(forward);
        }
        return true;
    }

    @Override
    protected void applyUpdate(T newState, List<Field> updatedFields) {
        JsonObject patch = new JsonObject(), diff;
        synchronized (this.lock) {
            // save
            diff = this.buildDiff(newState, updatedFields);
            if (diff.isEmpty()) return;
            this.state = newState;
            // forward
            patch.addProperty("version", this.version.incrementAndGet());
        }
        patch.add("state", diff);
        sendPackage(patch);
    }
}
