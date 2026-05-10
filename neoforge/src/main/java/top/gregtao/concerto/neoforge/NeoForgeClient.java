package top.gregtao.concerto.neoforge;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import org.jetbrains.annotations.NotNull;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.bridge.MinecraftClientBridge;
import top.gregtao.concerto.core.Concerto;
import top.gregtao.concerto.network.ConcertoPayload;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

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
        public void registerResourceReloadListener(Identifier id, Consumer<ResourceManager> listener) {
            this.modEventBus.addListener((AddClientReloadListenersEvent event) ->
                    event.addListener(id, (state, e1, barrier, e2) ->
                            CompletableFuture.runAsync(() -> listener.accept(state.resourceManager()), e1)
                                    .thenCompose(barrier::wait)));
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
        public void registerClientPayloadReceiver(CustomPacketPayload.Type<@NotNull ConcertoPayload> type, StreamCodec<@NotNull RegistryFriendlyByteBuf, @NotNull ConcertoPayload> codec, Consumer<ConcertoPayload> handler) {
            IPayloadHandler<@NotNull ConcertoPayload> clientHandler = (payload, context) -> handler.accept(payload);
            this.modEventBus.addListener((RegisterClientPayloadHandlersEvent event) -> {
                event.register(type, clientHandler);
            });
        }

        @Override
        public void sendPayload(ConcertoPayload payload) {
            ClientPacketDistributor.sendToServer(payload);
        }
    }

    public NeoForgeClient(IEventBus modEventBus, ModContainer modContainer) {
        ConcertoClient.initializeClient(new NeoForgeClientBridge(modEventBus));
    }
}

