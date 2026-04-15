package top.gregtao.concerto.util;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import top.gregtao.concerto.core.player.MusicPlayer;
import top.gregtao.concerto.core.player.MusicPlayerHandler;
import top.gregtao.concerto.screen.ConcertoIndexScreen;
import top.gregtao.concerto.screen.MusicPlayerScreen;

public class ConcertoHotkeys {

    public static String CATEGORY = "concerto.hotkey";

    public static KeyMapping GENERAL_PLAYLIST, INDEX_SCREEN, NEXT_MUSIC, PAUSE_RESUME;

    public static void register() {
        GENERAL_PLAYLIST = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "concerto.hotkey.general_music_list",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_U,
                CATEGORY
        ));
        INDEX_SCREEN = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "concerto.hotkey.index",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_I,
                CATEGORY
        ));
        NEXT_MUSIC = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "concerto.screen.next",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                CATEGORY
        ));
        PAUSE_RESUME = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "concerto.screen.pause_resume",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                CATEGORY
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
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
