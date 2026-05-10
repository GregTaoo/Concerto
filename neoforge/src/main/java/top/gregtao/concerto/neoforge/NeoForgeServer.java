package top.gregtao.concerto.neoforge;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.NotNull;
import top.gregtao.concerto.ConcertoServer;
import top.gregtao.concerto.bridge.MinecraftServerBridge;
import top.gregtao.concerto.core.Concerto;
import top.gregtao.concerto.network.ConcertoPayload;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Mod(value = Concerto.MOD_ID)
public class NeoForgeServer {

    public static class NetworkingHandler<T extends CustomPacketPayload> implements IPayloadHandler<T> {

        public IPayloadHandler<T> clientHandler;
        public IPayloadHandler<T> serverHandler;

        public void setClientHandler(IPayloadHandler<T> clientHandler) {
            this.clientHandler = clientHandler;
        }

        public void setServerHandler(IPayloadHandler<T> serverHandler) {
            this.serverHandler = serverHandler;
        }

        @Override
        public void handle(@NotNull T payload, @NotNull IPayloadContext context) {
            if (context.flow().isClientbound()) {
                if (this.clientHandler == null)
                    throw new NullPointerException("Client handler not registered");
                this.clientHandler.handle(payload, context);
            } else if (context.flow().isServerbound()) {
                if (this.serverHandler == null)
                    throw new NullPointerException("Server handler not registered");
                this.serverHandler.handle(payload, context);
            }
        }
    }

    public static final HashMap<CustomPacketPayload.Type<ConcertoPayload>, NetworkingHandler<ConcertoPayload>> NETWORK_HANDLERS = new HashMap<>();

    static class NeoForgeServerBridge implements MinecraftServerBridge {

        IEventBus modEventBus;

        NeoForgeServerBridge(IEventBus modEventBus) {
            this.modEventBus = modEventBus;
        }

        @Override
        public void registerCommand(CommandRegister register) {
            NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                    register.register(event.getDispatcher(), event.getBuildContext(), event.getCommandSelection()));
        }

        @Override
        public void registerResourceReloadListener(ResourceLocation id, Consumer<ResourceManager> listener) {
            NeoForge.EVENT_BUS.addListener((AddServerReloadListenersEvent event) ->
                    event.addListener(id, (barrier, resourceManager, e1, e2) ->
                            CompletableFuture.runAsync(() -> listener.accept(resourceManager))
                                    .thenCompose(barrier::wait)));
        }

        @Override
        public void registerServerPayloadReceiver(CustomPacketPayload.Type<ConcertoPayload> type, StreamCodec<RegistryFriendlyByteBuf, ConcertoPayload> codec, BiConsumer<ConcertoPayload, NetworkingContext> handler) {
            IPayloadHandler<ConcertoPayload> serverHandler = (payload, context) -> {
                if (context.player() instanceof ServerPlayer player) {
                    handler.accept(payload, new NetworkingContext(player, player.getServer()));
                }
            };
            if (NETWORK_HANDLERS.containsKey(type)) {
                NetworkingHandler<ConcertoPayload> handlers = NETWORK_HANDLERS.get(type);
                handlers.setServerHandler(serverHandler);
            } else {
                NetworkingHandler<ConcertoPayload> handlers = new NetworkingHandler<>();
                handlers.setServerHandler(serverHandler);
                this.modEventBus.addListener((RegisterPayloadHandlersEvent event) -> {
                    PayloadRegistrar registrar = event.registrar(ConcertoPayload.VERSION).optional();
                    registrar.playBidirectional(type, codec, handlers);
                });
                NETWORK_HANDLERS.put(type, handlers);
            }
        }

        @Override
        public void sendPayload(ServerPlayer player, ConcertoPayload payload) {
            PacketDistributor.sendToPlayer(player, payload);
        }

        @Override
        public boolean isDedicatedServer() {
            return FMLLoader.getDist() == Dist.DEDICATED_SERVER;
        }
    }

    public NeoForgeServer(IEventBus modEventBus, ModContainer modContainer) {
        ConcertoServer.initializeServer(new NeoForgeServerBridge(modEventBus));
    }
}
