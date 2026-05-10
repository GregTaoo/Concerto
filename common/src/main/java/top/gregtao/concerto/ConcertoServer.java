package top.gregtao.concerto;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.gregtao.concerto.bridge.CoreBridgeImpl;
import top.gregtao.concerto.bridge.LoggerImpl;
import top.gregtao.concerto.bridge.MinecraftServerBridge;
import top.gregtao.concerto.command.ConcertoServerCommand;
import top.gregtao.concerto.core.Concerto;
import top.gregtao.concerto.core.config.ClientConfig;
import top.gregtao.concerto.core.config.PresetPlaylistsConfig;
import top.gregtao.concerto.core.config.ServerConfig;
import top.gregtao.concerto.core.http.kugou.KuGouMusicApiClient;
import top.gregtao.concerto.core.http.netease.NeteaseCloudApiClient;
import top.gregtao.concerto.core.http.qq.QQMusicApiClient;
import top.gregtao.concerto.core.util.ConcertoRunner;
import top.gregtao.concerto.network.ServerMusicNetworkHandler;

public class ConcertoServer {

    public static Logger LOGGER = LoggerFactory.getLogger("ConcertoServer");
    public static final CoreBridgeImpl CORE_BRIDGE = new CoreBridgeImpl();
    public static final LoggerImpl.Factory LOGGER_FACTORY = new LoggerImpl.Factory();

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
        ServerMusicNetworkHandler.register(bridge);

        bridge.registerResourceReloadListener(
                ResourceLocation.fromNamespaceAndPath(Concerto.MOD_ID, "music"),
                manager -> ConcertoRunner.run(ConcertoServer::reload)
        );
    }

    public static void reload() {
        ServerConfig.INSTANCE.readOptions();
        if (getBridge().isDedicatedServer()) {
            // 只在专用服务器同步配置
            ClientConfig.INSTANCE.options.kuGouMusicLite = ServerConfig.INSTANCE.options.kuGouMusicLite;
        }
        PresetPlaylistsConfig.PRESET_RADIOS.read();
        NeteaseCloudApiClient.INSTANCE.readCookie();
        QQMusicApiClient.INSTANCE.readCookie();
        KuGouMusicApiClient.INSTANCE.readCookie();
    }
}
