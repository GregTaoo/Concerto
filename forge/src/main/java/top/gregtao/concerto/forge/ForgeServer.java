package top.gregtao.concerto.forge;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkProtocol;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.payload.PayloadConnection;
import org.jetbrains.annotations.NotNull;
import top.gregtao.concerto.ConcertoServer;
import top.gregtao.concerto.bridge.MinecraftServerBridge;
import top.gregtao.concerto.core.Concerto;
import top.gregtao.concerto.network.ConcertoPayload;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Mod(value = Concerto.MOD_ID)
public class ForgeServer {

    public static class NetworkingHandler<T extends CustomPacketPayload> {

        public BiConsumer<T, CustomPayloadEvent.Context> clientHandler;
        public BiConsumer<T, CustomPayloadEvent.Context> serverHandler;

        public void setClientHandler(BiConsumer<T, CustomPayloadEvent.Context> clientHandler) {
            this.clientHandler = clientHandler;
        }

        public void setServerHandler(BiConsumer<T, CustomPayloadEvent.Context> serverHandler) {
            this.serverHandler = serverHandler;
        }

        public void handle(@NotNull T payload, @NotNull CustomPayloadEvent.Context context) {
            if (context.isClientSide()) {
                if (this.clientHandler == null)
                    throw new NullPointerException("Client handler not registered");
                this.clientHandler.accept(payload, context);
            } else if (context.isServerSide()) {
                if (this.serverHandler == null)
                    throw new NullPointerException("Server handler not registered");
                this.serverHandler.accept(payload, context);
            }
        }
    }

    public static Channel<CustomPacketPayload> CHANNEL;
    public static NetworkingHandler<ConcertoPayload> NETWORK_HANDLER = new NetworkingHandler<>();

    static class ForgeServerBridge implements MinecraftServerBridge {

        IEventBus modEventBus;
        
        ForgeServerBridge(IEventBus modEventBus) {
            this.modEventBus = modEventBus;
        }

        @Override
        public void registerCommand(CommandRegister register) {
            MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                    register.register(event.getDispatcher(), event.getBuildContext(), event.getCommandSelection()));
        }

        @Override
        public void registerResourceReloadListener(ResourceLocation id, Consumer<ResourceManager> listener) {
            MinecraftForge.EVENT_BUS.addListener((AddReloadListenerEvent event) ->
                    event.addListener((barrier, resourceManager, e1, e2) ->
                            CompletableFuture.runAsync(() -> listener.accept(resourceManager))
                                    .thenCompose(barrier::wait)));
        }

        @Override
        public void registerServerPayloadReceiver(CustomPacketPayload.Type<ConcertoPayload> type, StreamCodec<RegistryFriendlyByteBuf, ConcertoPayload> codec, BiConsumer<ConcertoPayload, NetworkingContext> handler) {
            PayloadConnection<CustomPacketPayload> connection = ChannelBuilder.named(type.id())
                    .acceptedVersions(Channel.VersionTest.exact(1))
                    .optionalServer()
                    .networkProtocolVersion(1)
                    .payloadChannel();

            NETWORK_HANDLER.setServerHandler((payload, ctx) ->
                    handler.accept(payload, new NetworkingContext(ctx.getSender(), Objects.requireNonNull(ctx.getSender()).getServer())));

            CHANNEL = connection.play().bidirectional().add(type, codec, NETWORK_HANDLER::handle).build();
        }

        @Override
        public void sendPayload(ServerPlayer player, ConcertoPayload payload) {
            Packet<?> packet = NetworkProtocol.PLAY.buildPacket(
                    PacketFlow.CLIENTBOUND,
                    payload.type().id(),
                    buf -> ConcertoPayload.CODEC.encode(buf, payload)
            );
            PacketDistributor.PLAYER.with(player).send(packet);
        }

        @Override
        public boolean isDedicatedServer() {
            return FMLLoader.getDist() == Dist.DEDICATED_SERVER;
        }
    }

    public ForgeServer(IEventBus modEventBus) {
        ConcertoServer.initializeServer(new ForgeServerBridge(modEventBus));
    }
}
