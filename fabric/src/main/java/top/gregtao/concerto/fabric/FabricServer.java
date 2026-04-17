package top.gregtao.concerto.fabric;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.PackType;
import net.minecraft.resources.ResourceLocation;
import top.gregtao.concerto.ConcertoServer;
import top.gregtao.concerto.bridge.MinecraftServerBridge;

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
        public <T extends CustomPacketPayload> void registerS2CPayload(CustomPacketPayload.Type<T> id, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
            PayloadTypeRegistry.playS2C().register(id, codec);

        }

        @Override
        public <T extends CustomPacketPayload> void registerC2SPayload(CustomPacketPayload.Type<T> id, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
            PayloadTypeRegistry.playC2S().register(id, codec);
        }

        @Override
        public <T extends CustomPacketPayload> void registerServerPayloadReceiver(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, BiConsumer<T, NetworkingContext> handler) {
            ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) ->
                    handler.accept(payload, new NetworkingContext(context.player(), context.server())));
        }

        @Override
        public void sendPayload(ServerPlayer player, CustomPacketPayload payload) {
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
