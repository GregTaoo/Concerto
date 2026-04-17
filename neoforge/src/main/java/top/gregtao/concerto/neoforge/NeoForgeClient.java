package top.gregtao.concerto.neoforge;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.bridge.MinecraftClientBridge;
import top.gregtao.concerto.core.Concerto;
import top.gregtao.concerto.network.ConcertoPayload;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static top.gregtao.concerto.neoforge.NeoForgeServer.NETWORK_HANDLERS;

@Mod(value = Concerto.MOD_ID, dist = Dist.CLIENT)
public class NeoForgeClient {

    static class NeoForgeClientBridge implements MinecraftClientBridge {

        IEventBus modEventBus;

        NeoForgeClientBridge(IEventBus modEventBus) {
            this.modEventBus = modEventBus;
        }

        @Override
        public void registerClientCommand(ClientCommandRegister register) {
            NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) ->
                    register.register(event.getDispatcher(), event.getBuildContext()));
        }

        @Override
        public void registerResourceReloadListener(ResourceLocation id, Consumer<ResourceManager> listener) {
            this.modEventBus.addListener((AddClientReloadListenersEvent event) ->
                    event.addListener(id, (barrier, resourceManager, e1, e2) ->
                            CompletableFuture.runAsync(() -> listener.accept(resourceManager), e1)
                                    .thenCompose(barrier::wait)));
        }

        @Override
        public <T extends CustomPacketPayload> void registerClientPayloadReceiver(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Consumer<T> handler) {
            IPayloadHandler<T> clientHandler = (payload, context) -> handler.accept(payload);
            if (NETWORK_HANDLERS.containsKey(type)) {
                @SuppressWarnings("unchecked")
                NeoForgeServer.NetworkingHandler<T> handlers = (NeoForgeServer.NetworkingHandler<T>) NETWORK_HANDLERS.get(type);
                handlers.setClientHandler(clientHandler);
            } else {
                NeoForgeServer.NetworkingHandler<T> handlers = new NeoForgeServer.NetworkingHandler<>();
                handlers.setClientHandler(clientHandler);
                this.modEventBus.addListener((RegisterPayloadHandlersEvent event) -> {
                    PayloadRegistrar registrar = event.registrar(ConcertoPayload.VERSION).optional();
                    registrar.playBidirectional(type, codec, handlers);
                });
                NETWORK_HANDLERS.put(type, handlers);
            }
        }

        @Override
        public KeyMapping registerKeyMapping(KeyMapping keyMapping) {
            this.modEventBus.addListener((RegisterKeyMappingsEvent event) -> event.register(keyMapping));
            return keyMapping;
        }

        @Override
        public void registerEndOfTickListener(Consumer<Minecraft> listener) {
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> listener.accept(Minecraft.getInstance()));
        }

        @Override
        public void sendPayload(CustomPacketPayload payload) {
            PacketDistributor.sendToServer(payload);
        }
    }

    public NeoForgeClient(IEventBus modEventBus, ModContainer modContainer) {
        ConcertoClient.initializeClient(new NeoForgeClientBridge(modEventBus));
    }
}

