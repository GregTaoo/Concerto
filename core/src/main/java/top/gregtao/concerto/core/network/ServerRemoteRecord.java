package top.gregtao.concerto.core.network;

import com.google.gson.JsonObject;
import java.util.concurrent.atomic.AtomicLong;

public abstract class ServerRemoteRecord {

    private final AtomicLong version = new AtomicLong(0);

    public synchronized void receiveFromClient(JsonObject patch) {

        long clientVersion = patch.get("version").getAsLong();
        long currentVersion = version.get();

        if (clientVersion != currentVersion) {
            onVersionMismatch(patch, currentVersion);
            return;
        }

        long newVersion = version.incrementAndGet();

        JsonObject forward = patch.deepCopy();
        forward.addProperty("version", newVersion);

        broadcastPatch(forward);
    }

    protected void onVersionMismatch(JsonObject patch, long serverVersion) {
        // 默认忽略
        // 可以要求客户端重新同步
    }

    protected abstract void broadcastPatch(JsonObject patch);

    public long getVersion() {
        return version.get();
    }
}
