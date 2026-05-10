package top.gregtao.concerto.neoforge;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
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
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.NotNull;
import top.gregtao.concerto.ConcertoServer;
import top.gregtao.concerto.bridge.MinecraftServerBridge;
import top.gregtao.concerto.core.Concerto;
import top.gregtao.concerto.network.ConcertoPayload;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Mod(value = Concerto.MOD_ID)
public class NeoForgeServer {

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
        public void registerResourceReloadListener(Identifier id, Consumer<ResourceManager> listener) {
            NeoForge.EVENT_BUS.addListener((AddServerReloadListenersEvent event) ->
                    event.addListener(id, (state, e1, barrier, e2) ->
                            CompletableFuture.runAsync(() -> listener.accept(state.resourceManager()))
                                    .thenCompose(barrier::wait)));
        }

        @Override
        public void registerServerPayloadReceiver(CustomPacketPayload.Type<@NotNull ConcertoPayload> type, StreamCodec<@NotNull RegistryFriendlyByteBuf, @NotNull ConcertoPayload> codec, BiConsumer<ConcertoPayload, NetworkingContext> handler) {
            IPayloadHandler<@NotNull ConcertoPayload> serverHandler = (payload, context) -> {
                if (context.player() instanceof ServerPlayer player) {
                    handler.accept(payload, new NetworkingContext(player, player.level().getServer()));
                }
            };
            this.modEventBus.addListener((RegisterPayloadHandlersEvent event) -> {
                PayloadRegistrar registrar = event.registrar(ConcertoPayload.VERSION).optional();
                registrar.playBidirectional(type, codec, serverHandler);
            });
        }

        @Override
        public void sendPayload(ServerPlayer player, ConcertoPayload payload) {
            PacketDistributor.sendToPlayer(player, payload);
        }

        @Override
        public boolean isDedicatedServer() {
            return FMLLoader.getCurrent().getDist() == Dist.DEDICATED_SERVER;
        }
    }

    public NeoForgeServer(IEventBus modEventBus, ModContainer modContainer) {
        ConcertoServer.initializeServer(new NeoForgeServerBridge(modEventBus));
    }
}
