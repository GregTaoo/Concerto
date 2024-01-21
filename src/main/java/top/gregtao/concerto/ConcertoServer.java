package top.gregtao.concerto;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.gregtao.concerto.command.AuditCommand;
import top.gregtao.concerto.config.ServerConfig;
import top.gregtao.concerto.network.ServerMusicNetworkHandler;
import top.gregtao.concerto.player.exp.ConcertoJukeboxBlock;

public class ConcertoServer implements ModInitializer {

    public static Logger LOGGER = LoggerFactory.getLogger("ConcertoServer");
    public static final Identifier MY_SOUND_ID = new Identifier("concerto:my_sound");
    public static SoundEvent MY_SOUND_EVENT = SoundEvent.of(MY_SOUND_ID);
    @Override
    public void onInitialize() {
        ServerConfig.INSTANCE.readOptions();
        CommandRegistrationCallback.EVENT.register(AuditCommand::register);
        ServerMusicNetworkHandler.register();

        Registry.register(Registries.SOUND_EVENT, MY_SOUND_ID, MY_SOUND_EVENT);
        ConcertoJukeboxBlock.register();
    }
}
