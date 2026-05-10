package top.gregtao.concerto.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.NotNull;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.bridge.MinecraftClientBridge;
import top.gregtao.concerto.network.ConcertoPayload;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class FabricClient implements ClientModInitializer {
    static class FabricClientBridge implements MinecraftClientBridge {

        @Override
        public void registerClientCommand(ClientCommandRegister register) {
            ClientCommandRegistrationCallback.EVENT.register(register::register);
        }

        @Override
        public void registerResourceReloadListener(Identifier id, Consumer<ResourceManager> listener) {
            ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(id,
                    (state, executor, barrier, executor2) ->
                            CompletableFuture.runAsync(() -> listener.accept(state.resourceManager()))
                                    .thenCompose(barrier::wait));
        }

        @Override
        public KeyMapping registerKeyMapping(KeyMapping keyMapping) {
            return KeyMappingHelper.registerKeyMapping(keyMapping);
        }

        @Override
        public void registerEndOfTickListener(Consumer<Minecraft> listener) {
            ClientTickEvents.END_CLIENT_TICK.register(listener::accept);
        }

        @Override
        public void registerClientPayloadReceiver(CustomPacketPayload.Type<@NotNull ConcertoPayload> type, StreamCodec<@NotNull RegistryFriendlyByteBuf, @NotNull ConcertoPayload> codec, Consumer<ConcertoPayload> handler) {
            ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> handler.accept(payload));
        }

        @Override
        public void sendPayload(ConcertoPayload payload) {
            ClientPlayNetworking.send(payload);
        }
    }

    @Override
    public void onInitializeClient() {
        ConcertoClient.initializeClient(new FabricClientBridge());
    }
}
