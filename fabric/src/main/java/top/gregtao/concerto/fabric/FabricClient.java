package top.gregtao.concerto.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.PackType;
import net.minecraft.resources.ResourceLocation;
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
        public void registerClientPayloadReceiver(CustomPacketPayload.Type<ConcertoPayload> type, StreamCodec<RegistryFriendlyByteBuf, ConcertoPayload> codec, Consumer<ConcertoPayload> handler) {
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
