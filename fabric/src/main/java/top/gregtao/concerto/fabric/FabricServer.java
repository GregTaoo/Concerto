package top.gregtao.concerto.fabric;

import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import top.gregtao.concerto.ConcertoServer;
import top.gregtao.concerto.bridge.MinecraftServerBridge;
import top.gregtao.concerto.network.ConcertoPayload;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class FabricServer implements ModInitializer {
    static class FabricServerBridge implements MinecraftServerBridge {

        @Override
        public void registerCommand(CommandRegister register) {
            CommandRegistrationCallback.EVENT.register(register::register);
        }

        @Override
        public void registerResourceReloadListener(ResourceLocation id, Consumer<ResourceManager> listener) {
            ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
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
        public void registerServerPayloadReceiver(ResourceLocation id, BiConsumer<ConcertoPayload, NetworkingContext> handler) {
            // 1.20.1 receivers run on the netty thread: decode before the buf is
            // released, then hop to the server thread like the 1.20.5+ payload API
            ServerPlayNetworking.registerGlobalReceiver(id, (server, player, listener, buf, sender) -> {
                ConcertoPayload payload = ConcertoPayload.decode(buf);
                server.execute(() -> handler.accept(payload, new NetworkingContext(player, server)));
            });
        }

        @Override
        public void sendPayload(ServerPlayer player, ConcertoPayload payload) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            payload.encode(buf);
            ServerPlayNetworking.send(player, ConcertoPayload.ID, buf);
        }

        @Override
        public boolean isDedicatedServer() {
            return FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;
        }
    }

    @Override
    public void onInitialize() {
        ConcertoServer.initializeServer(new FabricServerBridge());
    }
}
