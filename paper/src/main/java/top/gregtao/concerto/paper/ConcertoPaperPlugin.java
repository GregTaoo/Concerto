package top.gregtao.concerto.paper;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.gregtao.concerto.core.Concerto;
import top.gregtao.concerto.core.config.ClientConfig;
import top.gregtao.concerto.core.config.PresetPlaylistsConfig;
import top.gregtao.concerto.core.config.ServerConfig;
import top.gregtao.concerto.core.http.kugou.KuGouMusicApiClient;
import top.gregtao.concerto.core.http.netease.NeteaseCloudApiClient;
import top.gregtao.concerto.core.http.qq.QQMusicApiClient;
import top.gregtao.concerto.core.util.ConcertoRunner;
import top.gregtao.concerto.paper.bridge.CoreBridgeImpl;
import top.gregtao.concerto.paper.bridge.LoggerImpl;
import top.gregtao.concerto.paper.command.ConcertoServerCommand;
import top.gregtao.concerto.paper.network.ConcertoPayload;
import top.gregtao.concerto.paper.network.ServerMusicNetworkHandler;
import top.gregtao.concerto.paper.network.room.ServerMusicAgentManager;

public class ConcertoPaperPlugin extends JavaPlugin implements Listener, PluginMessageListener {

    public static Logger LOGGER = LoggerFactory.getLogger("Concerto");
    public static final CoreBridgeImpl CORE_BRIDGE = new CoreBridgeImpl();
    public static final LoggerImpl.Factory LOGGER_FACTORY = new LoggerImpl.Factory();
    public static ConcertoPaperPlugin INSTANCE;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        INSTANCE = this;
        Concerto.registerCoreBridge(CORE_BRIDGE, LOGGER_FACTORY);
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                commands -> commands.registrar().register(ConcertoServerCommand.build()));
        Messenger messenger = getServer().getMessenger();
        messenger.registerOutgoingPluginChannel(this, ConcertoPayload.ID);
        messenger.registerIncomingPluginChannel(this, ConcertoPayload.ID, this);
        ServerMusicAgentManager.init();
        ConcertoRunner.run(ConcertoPaperPlugin::reload);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        Bukkit.getScheduler().runTask(INSTANCE, () -> ServerMusicNetworkHandler.playerJoinHandshake(player));
    }

    @Override
    public void onPluginMessageReceived(String channel, @NotNull Player player, byte[] message) {
        if (!channel.equals(ConcertoPayload.ID)) return;
        ConcertoPayload payload = ConcertoPayload.decode(message);
        ServerMusicNetworkHandler.generalReceiver(payload, player);
    }

    public static void reload() {
        ServerConfig.INSTANCE.readOptions();
        ClientConfig.INSTANCE.options.kuGouMusicLite = ServerConfig.INSTANCE.options.kuGouMusicLite;
        PresetPlaylistsConfig.PRESET_RADIOS.read();
        NeteaseCloudApiClient.INSTANCE.readCookie();
        QQMusicApiClient.INSTANCE.readCookie();
        KuGouMusicApiClient.INSTANCE.readCookie();
    }
}
