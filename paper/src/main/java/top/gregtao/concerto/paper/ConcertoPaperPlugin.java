package top.gregtao.concerto.paper;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
import top.gregtao.concerto.core.room.MusicRoom;
import top.gregtao.concerto.core.util.ConcertoRunner;
import top.gregtao.concerto.paper.network.room.MusicRoomManager;
import top.gregtao.concerto.paper.bridge.CoreBridgeImpl;
import top.gregtao.concerto.paper.bridge.LoggerImpl;
import top.gregtao.concerto.paper.command.ConcertoServerCommand;
import top.gregtao.concerto.paper.network.ConcertoPayload;
import top.gregtao.concerto.paper.network.ServerMusicNetworkHandler;
import top.gregtao.concerto.paper.network.room.ServerMusicAgentManager;
import top.gregtao.concerto.paper.util.I18n;

import java.util.Locale;

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
        // Paper's brigadier lifecycle registrar is 1.20.6+; register the classic
        // Bukkit executor declared in plugin.yml instead
        PluginCommand serverCommand = this.getCommand("concerto-server");
        if (serverCommand != null) {
            ConcertoServerCommand executor = new ConcertoServerCommand();
            serverCommand.setExecutor(executor);
            serverCommand.setTabCompleter(executor);
        }
        Messenger messenger = getServer().getMessenger();
        messenger.registerOutgoingPluginChannel(this, ConcertoPayload.ID);
        messenger.registerIncomingPluginChannel(this, ConcertoPayload.ID, this);
        ServerMusicAgentManager.init();
        ConcertoRunner.run(ConcertoPaperPlugin::reload);
        I18n.INSTANCE.loadFile(Locale.ENGLISH, this.getResource("assets/concerto/lang/en_us.json"));
        I18n.INSTANCE.loadFile(Locale.SIMPLIFIED_CHINESE, this.getResource("assets/concerto/lang/zh_cn.json"));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 3s delay
        Bukkit.getScheduler().runTaskLater(INSTANCE, () -> ServerMusicNetworkHandler.playerJoinHandshake(player), 60L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Mirrors the vanilla PlayerListMixin cleanup: without it, rooms and
        // members of disconnected players lingered forever on Paper
        MusicRoom.serverOnPlayerDisconnect(event.getPlayer().getName(), MusicRoomManager.createServerBridge());
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
