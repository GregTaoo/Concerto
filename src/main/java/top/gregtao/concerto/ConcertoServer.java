package top.gregtao.concerto;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.gregtao.concerto.command.ConcertoServerCommand;
import top.gregtao.concerto.config.PresetRadioConfig;
import top.gregtao.concerto.config.ServerConfig;
import top.gregtao.concerto.network.ServerMusicNetworkHandler;

public class ConcertoServer implements ModInitializer {

    public static Logger LOGGER = LoggerFactory.getLogger("ConcertoServer");

    @Override
    public void onInitialize() {
        ServerConfig.INSTANCE.readOptions();
        CommandRegistrationCallback.EVENT.register(ConcertoServerCommand::register);
        ServerMusicNetworkHandler.register();

        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return Identifier.of(ConcertoClient.MOD_ID, "music");
            }

            @Override
            public void reload(ResourceManager manager) {
                ConcertoServer.reload();
            }
        });
    }

    public static void reload() {
        ServerConfig.INSTANCE.readOptions();
        PresetRadioConfig.INSTANCE.read();
    }
}
