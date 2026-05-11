package top.gregtao.concerto;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.gregtao.concerto.bridge.ConcertoEventListeners;
import top.gregtao.concerto.bridge.MinecraftClientBridge;
import top.gregtao.concerto.command.MusicCommand;
import top.gregtao.concerto.command.MusicRoomCommand;
import top.gregtao.concerto.command.ShareMusicCommand;
import top.gregtao.concerto.core.Concerto;
import top.gregtao.concerto.core.config.ClientConfig;
import top.gregtao.concerto.core.config.PresetPlaylistsConfig;
import top.gregtao.concerto.core.http.kugou.KuGouMusicApiClient;
import top.gregtao.concerto.core.http.netease.NeteaseCloudApiClient;
import top.gregtao.concerto.core.http.qq.QQMusicApiClient;
import top.gregtao.concerto.core.music.list.Playlist;
import top.gregtao.concerto.core.player.MusicPlayer;
import top.gregtao.concerto.core.player.MusicPlayerHandler;
import top.gregtao.concerto.core.util.ConcertoRunner;
import top.gregtao.concerto.network.ClientMusicNetworkHandler;
import top.gregtao.concerto.util.ConcertoHotkeys;
import top.gregtao.concerto.util.ConcertoOptions;

import java.util.List;

public class ConcertoClient {

    public static final Logger LOGGER = LoggerFactory.getLogger("ConcertoClient");

    public static void syncPlayerVolume() {
        try {
            Minecraft client = Minecraft.getInstance();
            Options options = client.options;
            double volume = options.getSoundSourceVolume(SoundSource.MASTER) * options.getSoundSourceVolume(SoundSource.MUSIC) * 0.5;
            MusicPlayer.INSTANCE.setGain(volume);
        } catch (NullPointerException ignore) {
        }
    }

    public static boolean serverAvailable = false;

    public static List<Playlist> presetRadios = List.of();

    public static boolean isServerAvailable() {
        return serverAvailable || !ClientConfig.INSTANCE.options.handshakeRequired ||
                Minecraft.getInstance().isLocalServer();
    }

    private static MinecraftClientBridge BRIDGE;

    public static MinecraftClientBridge getBridge() {
        if (BRIDGE == null)
            throw new NullPointerException("Bridge not initialized yet");
        return BRIDGE;
    }

    public static void initializeClient(MinecraftClientBridge bridge) {
        BRIDGE = bridge;

        ConcertoEventListeners.registerClientListeners();

        bridge.registerClientCommand(MusicCommand::register);
        bridge.registerClientCommand(ShareMusicCommand::register);
        bridge.registerClientCommand(MusicRoomCommand::register);

        bridge.registerResourceReloadListener(
                ResourceLocation.tryBuild(Concerto.MOD_ID, "music"),
                manager -> ConcertoRunner.run(() -> {
                    ClientConfig.INSTANCE.readOptions();
                    ConcertoOptions.INSTANCE.readOptions();
                    MusicPlayerHandler.reloadConfig(() -> LOGGER.info("Loaded general music playlist"));
                    PresetPlaylistsConfig.LOCAL_PLAYLISTS.read();
                    NeteaseCloudApiClient.LOCAL_USER.updateLoginStatus();
                    QQMusicApiClient.LOCAL_USER.updateLoginStatus();

                    // 酷狗音乐相关
                    KuGouMusicApiClient.LOCAL_USER.updateLoginStatusAndDfid();
                    // 刷新 token, 延长 token 有效时间
                    KuGouMusicApiClient.INSTANCE.refreshToken();
                    // 更新 VIP 状态
                    KuGouMusicApiClient.LOCAL_USER.updateVIPStatus();
                    // 自动获取每日酷狗音乐 VIP
                    if (ClientConfig.INSTANCE.options.kuGouMusicLite &&
                            KuGouMusicApiClient.LOCAL_USER.isLoggedIn() &&
                            ClientConfig.INSTANCE.options.autoGetKuGouDailyVIP) {
                        KuGouMusicApiClient.INSTANCE.receiveVip();
                    }
                })
        );

        ClientMusicNetworkHandler.register(bridge);
        ConcertoHotkeys.register(bridge);
    }
}
