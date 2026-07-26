package top.gregtao.concerto.fabric;

import io.netty.buffer.Unpooled;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.bridge.MinecraftClientBridge;
import top.gregtao.concerto.network.ConcertoPayload;

import java.util.function.Consumer;

public class FabricClient implements ClientModInitializer {
    static class FabricClientBridge implements MinecraftClientBridge {

        @Override
        public void registerClientCommand(ClientCommandRegister register) {
            ClientCommandRegistrationCallback.EVENT.register(register::register);
        }

        @Override
        public void registerResourceReloadListener(ResourceLocation id, Consumer<ResourceManager> listener) {
            ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
                @Override
                public ResourceLocation getFabricId() {
                    return id;
                }

                @Override
                public void onResourceManagerReload(ResourceManager manager) {
                    listener.accept(manager);
                }
            });
        }

        @Override
        public KeyMapping registerKeyMapping(KeyMapping keyMapping) {
            return KeyBindingHelper.registerKeyBinding(keyMapping);
        }

        @Override
        public void registerEndOfTickListener(Consumer<Minecraft> listener) {
            ClientTickEvents.END_CLIENT_TICK.register(listener::accept);
        }

        @Override
        public void registerClientPayloadReceiver(ResourceLocation id, Consumer<ConcertoPayload> handler) {
            // 1.20.1 receivers run on the netty thread: decode before the buf is
            // released, then hop to the client thread like the 1.20.5+ payload API
            ClientPlayNetworking.registerGlobalReceiver(id, (client, listener, buf, sender) -> {
                ConcertoPayload payload = ConcertoPayload.decode(buf);
                client.execute(() -> handler.accept(payload));
            });
        }

        @Override
        public void sendPayload(ConcertoPayload payload) {
            Minecraft client = Minecraft.getInstance();
            if (client.getConnection() == null || !ClientPlayNetworking.canSend(ConcertoPayload.ID)) {
                return;
            }
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            payload.encode(buf);
            try {
                ClientPlayNetworking.send(ConcertoPayload.ID, buf);
            } catch (IllegalStateException ignored) {
                // The client can disconnect between the connection check and the send call.
            }
        }
    }

    @Override
    public void onInitializeClient() {
        ConcertoClient.initializeClient(new FabricClientBridge());
    }
}
