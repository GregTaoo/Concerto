package top.gregtao.concerto;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.gregtao.concerto.bridge.ConcertoEventListeners;
import top.gregtao.concerto.command.MusicCommand;
import top.gregtao.concerto.command.MusicRoomCommand;
import top.gregtao.concerto.command.ShareMusicCommand;
import top.gregtao.concerto.core.Concerto;
import top.gregtao.concerto.core.config.ClientConfig;
import top.gregtao.concerto.config.PresetPlaylistsConfig;
import top.gregtao.concerto.core.http.kugou.KuGouMusicApiClient;
import top.gregtao.concerto.core.http.netease.NeteaseCloudApiClient;
import top.gregtao.concerto.core.http.qq.QQMusicApiClient;
import top.gregtao.concerto.core.music.list.Playlist;
import top.gregtao.concerto.network.ClientMusicNetworkHandler;
import top.gregtao.concerto.core.player.MusicPlayer;
import top.gregtao.concerto.screen.widget.URLImageWidget;
import top.gregtao.concerto.util.ConcertoHotkeys;
import top.gregtao.concerto.util.ConcertoOptions;
import top.gregtao.concerto.core.util.ConcertoRunner;

import java.util.List;

public class ConcertoClient implements ClientModInitializer {

	public static final Logger LOGGER = LoggerFactory.getLogger("ConcertoClient");

    public static URLImageWidget COVER_IMAGE = new URLImageWidget(20, 20, 0, 0, null, false);

    public static void syncPlayerVolume() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            GameOptions options = client.options;
            double volume = options.getSoundVolume(SoundCategory.MASTER) * options.getSoundVolume(SoundCategory.MUSIC) * 0.5;
            MusicPlayer.INSTANCE.setGain(volume);
        } catch (NullPointerException ignore) {}
    }

	// ======================================================
	// Server States

	public static ClientState clientState = ClientState.LOCAL;

	public static boolean serverAvailable = false;

	public static List<Playlist> presetRadios = List.of();

	public static boolean isServerAvailable() {
		return serverAvailable || !ClientConfig.INSTANCE.options.handshakeRequired ||
				MinecraftClient.getInstance().isInSingleplayer();
	}

	public enum ClientState {
		LOCAL,
		MUSIC_ROOM,
		MUSIC_AGENT
	}

	// ======================================================

	@Override
	public void onInitializeClient() {
        ConcertoEventListeners.registerClientListeners();

		ClientCommandRegistrationCallback.EVENT.register(MusicCommand::register);
		ClientCommandRegistrationCallback.EVENT.register(ShareMusicCommand::register);
		ClientCommandRegistrationCallback.EVENT.register(MusicRoomCommand::register);

		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return Identifier.of(Concerto.MOD_ID, "music");
			}

			@Override
			public void reload(ResourceManager manager) {
				ConcertoRunner.run(() -> {
					ClientConfig.INSTANCE.readOptions();
					ConcertoOptions.INSTANCE.readOptions();
					MusicPlayer.INSTANCE.reloadConfig(() -> LOGGER.info("Loaded general music playlist"));
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
				});
			}
		});

		ClientMusicNetworkHandler.register();
		ConcertoHotkeys.register();
	}
}
