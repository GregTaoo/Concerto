package top.gregtao.concerto.util;

import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import top.gregtao.concerto.bridge.MinecraftClientBridge;
import top.gregtao.concerto.core.player.MusicPlayer;
import top.gregtao.concerto.core.player.MusicPlayerHandler;
import top.gregtao.concerto.screen.ConcertoIndexScreen;
import top.gregtao.concerto.screen.MusicPlayerScreen;

public class ConcertoHotkeys {

    public static String CATEGORY = "concerto.hotkey";

    public static KeyMapping GENERAL_PLAYLIST, INDEX_SCREEN, NEXT_MUSIC, PAUSE_RESUME;

    public static void register(MinecraftClientBridge bridge) {
        GENERAL_PLAYLIST = bridge.registerKeyMapping(new KeyMapping(
                "concerto.hotkey.general_music_list",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_U,
                CATEGORY
        ));
        INDEX_SCREEN = bridge.registerKeyMapping(new KeyMapping(
                "concerto.hotkey.index",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_I,
                CATEGORY
        ));
        NEXT_MUSIC = bridge.registerKeyMapping(new KeyMapping(
                "concerto.screen.next",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                CATEGORY
        ));
        PAUSE_RESUME = bridge.registerKeyMapping(new KeyMapping(
                "concerto.screen.pause_resume",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                CATEGORY
        ));
        bridge.registerEndOfTickListener(client -> {
            if (GENERAL_PLAYLIST.consumeClick()) {
                client.setScreen(new MusicPlayerScreen(null));
            } else if (INDEX_SCREEN.consumeClick()) {
                client.setScreen(new ConcertoIndexScreen(null));
            } else if (NEXT_MUSIC.consumeClick()) {
                MusicPlayerHandler.INSTANCE.playNextAsync(1);
            } else if (PAUSE_RESUME.consumeClick()) {
                if (MusicPlayer.INSTANCE.started) {
                    boolean paused = MusicPlayerHandler.INSTANCE.isPaused();
                    MusicPlayerHandler.INSTANCE.tryForcePause(!paused);
                }
            }
        });
    }
}
