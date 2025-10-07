package top.gregtao.concerto;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.gregtao.concerto.command.MusicCommand;
import top.gregtao.concerto.command.MusicRoomCommand;
import top.gregtao.concerto.command.ShareMusicCommand;
import top.gregtao.concerto.config.ClientConfig;
import top.gregtao.concerto.config.ConfigFile;
import top.gregtao.concerto.config.PresetPlaylistsConfig;
import top.gregtao.concerto.http.kugou.KuGouMusicApiClient;
import top.gregtao.concerto.http.netease.NeteaseCloudApiClient;
import top.gregtao.concerto.http.qq.QQMusicApiClient;
import top.gregtao.concerto.music.list.Playlist;
import top.gregtao.concerto.network.ClientMusicNetworkHandler;
import top.gregtao.concerto.player.MusicPlayer;
import top.gregtao.concerto.util.ConcertoHotkeys;
import top.gregtao.concerto.util.ConcertoOptions;
import top.gregtao.concerto.util.ConcertoRunner;

import java.util.List;

public class ConcertoClient implements ClientModInitializer {

	public static final String MOD_ID = "concerto";

	public static final Logger LOGGER = LoggerFactory.getLogger("ConcertoClient");

	public static final ConfigFile MUSIC_CONFIG = new ConfigFile("Concerto/musics.json");

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
		MusicCommand.register();
		ShareMusicCommand.register();
		MusicRoomCommand.register();

		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return new Identifier(ConcertoClient.MOD_ID, "music");
			}

			@Override
			public void reload(ResourceManager manager) {
				ConcertoRunner.run(() -> {
					ClientConfig.INSTANCE.readOptions();
					MusicPlayer.INSTANCE.reloadConfig(() -> LOGGER.info("Loaded general music playlist"));
                    PresetPlaylistsConfig.LOCAL_PLAYLISTS.read();
					NeteaseCloudApiClient.LOCAL_USER.updateLoginStatus();
					QQMusicApiClient.LOCAL_USER.updateLoginStatus();
					KuGouMusicApiClient.LOCAL_USER.updateLoginStatus();
					// 刷新 token, 延长 token 有效时间
					KuGouMusicApiClient.INSTANCE.refreshToken();
					// 更新 VIP 状态
					KuGouMusicApiClient.LOCAL_USER.updateVIPStatus();
					// 自动获取每日酷狗音乐 VIP
					ClientConfig.ClientConfigOptions options = ClientConfig.INSTANCE.options;
					if (
							options.kuGouMusicLite
							&& KuGouMusicApiClient.LOCAL_USER.isLoggedIn()
							&& options.autoGetKuGouDailyVIP
					) {
						KuGouMusicApiClient.INSTANCE.receiveVip();
					}
				});
			}
		});

		ClientMusicNetworkHandler.register();
		ConcertoHotkeys.register();
	}
}
