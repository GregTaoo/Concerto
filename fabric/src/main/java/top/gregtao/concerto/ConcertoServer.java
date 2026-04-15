package top.gregtao.concerto;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.PackType;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.gregtao.concerto.bridge.CoreBridgeImpl;
import top.gregtao.concerto.bridge.LoggerFactoryImpl;
import top.gregtao.concerto.command.ConcertoServerCommand;
import top.gregtao.concerto.core.Concerto;
import top.gregtao.concerto.core.config.ClientConfig;
import top.gregtao.concerto.config.PresetPlaylistsConfig;
import top.gregtao.concerto.core.config.ServerConfig;
import top.gregtao.concerto.core.http.kugou.KuGouMusicApiClient;
import top.gregtao.concerto.core.http.netease.NeteaseCloudApiClient;
import top.gregtao.concerto.core.http.qq.QQMusicApiClient;
import top.gregtao.concerto.network.ConcertoNetworking;
import top.gregtao.concerto.network.ServerMusicNetworkHandler;

public class ConcertoServer implements ModInitializer {

    public static Logger LOGGER = LoggerFactory.getLogger("ConcertoServer");
    public static final CoreBridgeImpl CORE_BRIDGE = new CoreBridgeImpl();
    public static final LoggerFactoryImpl LOGGER_FACTORY = new LoggerFactoryImpl();

    @Override
    public void onInitialize() {
        Concerto.registerCoreBridge(CORE_BRIDGE, LOGGER_FACTORY);

        CommandRegistrationCallback.EVENT.register(ConcertoServerCommand::register);
        ConcertoNetworking.register();
        ServerMusicNetworkHandler.register();

        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return ResourceLocation.fromNamespaceAndPath(Concerto.MOD_ID, "music");
            }

            @Override
            public void onResourceManagerReload(ResourceManager manager) {
                ConcertoServer.reload();
            }
        });
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
