package top.gregtao.concerto;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.gregtao.concerto.bridge.CoreBridgeImpl;
import top.gregtao.concerto.bridge.LoggerFactoryImpl;
import top.gregtao.concerto.bridge.MinecraftServerBridge;
import top.gregtao.concerto.command.ConcertoServerCommand;
import top.gregtao.concerto.core.Concerto;
import top.gregtao.concerto.core.config.ClientConfig;
import top.gregtao.concerto.config.PresetPlaylistsConfig;
import top.gregtao.concerto.core.config.ServerConfig;
import top.gregtao.concerto.core.http.kugou.KuGouMusicApiClient;
import top.gregtao.concerto.core.http.netease.NeteaseCloudApiClient;
import top.gregtao.concerto.core.http.qq.QQMusicApiClient;
import top.gregtao.concerto.network.ConcertoPayload;
import top.gregtao.concerto.network.ServerMusicNetworkHandler;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ConcertoServer implements ModInitializer {

    public static Logger LOGGER = LoggerFactory.getLogger("ConcertoServer");
    public static final CoreBridgeImpl CORE_BRIDGE = new CoreBridgeImpl();
    public static final LoggerFactoryImpl LOGGER_FACTORY = new LoggerFactoryImpl();

    private static MinecraftServerBridge BRIDGE;

    public static MinecraftServerBridge getBridge() {
        if (BRIDGE == null)
            throw new NullPointerException("Bridge not initialized yet");
        return BRIDGE;
    }

    public static void initializeServer(MinecraftServerBridge bridge) {
        BRIDGE = bridge;

        Concerto.registerCoreBridge(CORE_BRIDGE, LOGGER_FACTORY);

        bridge.registerCommand(ConcertoServerCommand::register);
        ConcertoPayload.register(bridge);
        ServerMusicNetworkHandler.register(bridge);
        
        bridge.registerResourceReloadListener(
                ResourceLocation.fromNamespaceAndPath(Concerto.MOD_ID, "music"),
                manager -> reload()
        );
    }

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
        public <T extends CustomPacketPayload> void registerServerPayloadReceiver(CustomPacketPayload.Type<T> type, BiConsumer<T, NetworkingContext> handler) {
            ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) ->
                    handler.accept(payload, new NetworkingContext(context.player(), context.server())));
        }

        @Override
        public void sendPayload(ServerPlayer player, CustomPacketPayload payload) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    @Override
    public void onInitialize() {
        initializeServer(new FabricServerBridge());
    }

    public static void reload() {
        ServerConfig.INSTANCE.readOptions();
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
            // 只在专用服务器同步配置
            ClientConfig.INSTANCE.options.kuGouMusicLite = ServerConfig.INSTANCE.options.kuGouMusicLite;
        }
        PresetPlaylistsConfig.PRESET_RADIOS.read();
        NeteaseCloudApiClient.INSTANCE.readCookie();
        QQMusicApiClient.INSTANCE.readCookie();
        KuGouMusicApiClient.INSTANCE.readCookie();
    }
}
