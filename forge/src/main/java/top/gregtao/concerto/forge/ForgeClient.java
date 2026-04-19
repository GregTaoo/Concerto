package top.gregtao.concerto.forge;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkProtocol;
import net.minecraftforge.network.PacketDistributor;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.bridge.MinecraftClientBridge;
import top.gregtao.concerto.core.Concerto;
import top.gregtao.concerto.network.ConcertoPayload;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static top.gregtao.concerto.forge.ForgeServer.NETWORK_HANDLER;

@Mod(value = Concerto.MOD_ID)
public class ForgeClient {

    static class ForgeClientBridge implements MinecraftClientBridge {

        IEventBus modEventBus;

        ForgeClientBridge(IEventBus modEventBus) {
            this.modEventBus = modEventBus;
        }

        @Override
        public void registerClientCommand(ClientCommandRegister register) {
            MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) ->
                    register.register(event.getDispatcher(), event.getBuildContext()));
        }

        @Override
        public void registerResourceReloadListener(ResourceLocation id, Consumer<ResourceManager> listener) {
            this.modEventBus.addListener((RegisterClientReloadListenersEvent event) ->
                    event.registerReloadListener((barrier, resourceManager, e1, e2) ->
                            CompletableFuture.runAsync(() -> listener.accept(resourceManager), e1)
                                    .thenCompose(barrier::wait)));
        }

        @Override
        public KeyMapping registerKeyMapping(KeyMapping keyMapping) {
            this.modEventBus.addListener((RegisterKeyMappingsEvent event) -> event.register(keyMapping));
            return keyMapping;
        }

        @Override
        public void registerEndOfTickListener(Consumer<Minecraft> listener) {
            MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent.Post event) -> listener.accept(Minecraft.getInstance()));
        }

        @Override
        public void registerClientPayloadReceiver(CustomPacketPayload.Type<ConcertoPayload> type, StreamCodec<RegistryFriendlyByteBuf, ConcertoPayload> codec, Consumer<ConcertoPayload> handler) {
            NETWORK_HANDLER.setClientHandler((payload, ctx) -> handler.accept(payload));
        }

        @Override
        public void sendPayload(ConcertoPayload payload) {
            Packet<?> packet = NetworkProtocol.PLAY.buildPacket(
                    PacketFlow.SERVERBOUND,
                    payload.type().id(),
                    buf -> ConcertoPayload.CODEC.encode(buf, payload)
            );
            PacketDistributor.SERVER.noArg().send(packet);
        }
    }

    public ForgeClient(IEventBus modEventBus) {
        ConcertoClient.initializeClient(new ForgeClientBridge(modEventBus));
    }
}

