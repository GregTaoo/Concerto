package top.gregtao.concerto;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.gregtao.concerto.command.ConcertoServerCommand;
import top.gregtao.concerto.config.ClientConfig;
import top.gregtao.concerto.config.PresetPlaylistsConfig;
import top.gregtao.concerto.config.ServerConfig;
import top.gregtao.concerto.http.kugou.KuGouMusicApiClient;
import top.gregtao.concerto.http.netease.NeteaseCloudApiClient;
import top.gregtao.concerto.http.qq.QQMusicApiClient;
import top.gregtao.concerto.network.ServerMusicNetworkHandler;

public class ConcertoServer implements ModInitializer {

    public static Logger LOGGER = LoggerFactory.getLogger("ConcertoServer");

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(ConcertoServerCommand::register);
        ServerMusicNetworkHandler.register();

        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return new Identifier(ConcertoClient.MOD_ID, "music");
            }

            @Override
            public void reload(ResourceManager manager) {
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
