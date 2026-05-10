package top.gregtao.concerto.fabric;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.NotNull;
import top.gregtao.concerto.ConcertoServer;
import top.gregtao.concerto.bridge.MinecraftServerBridge;
import top.gregtao.concerto.network.ConcertoPayload;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class FabricServer implements ModInitializer {
    static class FabricServerBridge implements MinecraftServerBridge {

        @Override
        public void registerCommand(CommandRegister register) {
            CommandRegistrationCallback.EVENT.register(register::register);
        }

        @Override
        public void registerResourceReloadListener(Identifier id, Consumer<ResourceManager> listener) {
            ResourceLoader.get(PackType.SERVER_DATA).registerReloader(id,
                    (state, executor, barrier, executor2) ->
                            CompletableFuture.runAsync(() -> listener.accept(state.resourceManager()))
                                    .thenCompose(barrier::wait));
        }

        @Override
        public void registerServerPayloadReceiver(CustomPacketPayload.Type<@NotNull ConcertoPayload> type, StreamCodec<@NotNull RegistryFriendlyByteBuf, @NotNull ConcertoPayload> codec, BiConsumer<ConcertoPayload, NetworkingContext> handler) {
            PayloadTypeRegistry.playS2C().register(type, codec);
            PayloadTypeRegistry.playC2S().register(type, codec);
            ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) ->
                    handler.accept(payload, new NetworkingContext(context.player(), context.server())));
        }

        @Override
        public void sendPayload(ServerPlayer player, ConcertoPayload payload) {
            ServerPlayNetworking.send(player, payload);
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
