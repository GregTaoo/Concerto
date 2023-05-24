package top.gregtao.concerto;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.gregtao.concerto.command.MusicCommand;
import top.gregtao.concerto.command.NeteaseCloudMusicCommand;
import top.gregtao.concerto.command.ShareMusicCommand;
import top.gregtao.concerto.config.ClientConfig;
import top.gregtao.concerto.config.ConfigFile;
import top.gregtao.concerto.http.netease.NeteaseCloudApiClient;
import top.gregtao.concerto.network.ClientMusicNetworkHandler;
import top.gregtao.concerto.player.MusicPlayer;
import top.gregtao.concerto.screen.InGameHudRenderer;
import top.gregtao.concerto.util.ConcertoHotkeys;

public class ConcertoClient implements ClientModInitializer {

	public static final String MOD_ID = "concerto";

	public static final Logger LOGGER = LoggerFactory.getLogger("ConcertoClient");

	public static final ConfigFile MUSIC_CONFIG = new ConfigFile("Concerto/musics.json");

	public static boolean serverAvailable = false;

	public static boolean isServerAvailable() {
		return serverAvailable || MinecraftClient.getInstance().isInSingleplayer();
//		return serverAvailable; // DEBUG
	}

	@Override
	public void onInitializeClient() {
		ClientCommandRegistrationCallback.EVENT.register(MusicCommand::register);
		ClientCommandRegistrationCallback.EVENT.register(ShareMusicCommand::register);
		ClientCommandRegistrationCallback.EVENT.register(NeteaseCloudMusicCommand::register);

		HudRenderCallback.EVENT.register(InGameHudRenderer::render);

		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return new Identifier(ConcertoClient.MOD_ID, "music");
			}

			@Override
			public void reload(ResourceManager manager) {
				MusicPlayer.INSTANCE.reloadConfig(() -> LOGGER.info("Loaded general music playlist"));
				NeteaseCloudApiClient.LOCAL_USER.updateLoginStatus();
				ClientConfig.INSTANCE.readOptions();
			}
		});

		ClientMusicNetworkHandler.register();
		ConcertoHotkeys.register();
	}
}
