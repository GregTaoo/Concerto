package top.gregtao.concerto;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.gregtao.concerto.command.AuditCommand;
import top.gregtao.concerto.config.ServerConfig;
import top.gregtao.concerto.network.ServerMusicNetworkHandler;

public class ConcertoServer implements ModInitializer {

    public static Logger LOGGER = LoggerFactory.getLogger("ConcertoServer");

    @Override
    public void onInitialize() {
        ServerConfig.INSTANCE.readOptions();
        CommandRegistrationCallback.EVENT.register(AuditCommand::register);
        ServerMusicNetworkHandler.register();
    }
}
