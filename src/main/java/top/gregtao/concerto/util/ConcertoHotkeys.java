package top.gregtao.concerto.util;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import top.gregtao.concerto.player.MusicPlayer;
import top.gregtao.concerto.screen.ConcertoIndexScreen;
import top.gregtao.concerto.screen.GeneralPlaylistScreen;

public class ConcertoHotkeys {

    public static String CATEGORY = "concerto.hotkey";

    public static KeyBinding GENERAL_PLAYLIST, INDEX_SCREEN, NEXT;

    public static void register() {
        GENERAL_PLAYLIST = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "concerto.hotkey.general_music_list",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_U,
                CATEGORY
        ));
        INDEX_SCREEN = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "concerto.hotkey.index",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_I,
                CATEGORY
        ));
        NEXT = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "concerto.screen.next",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                CATEGORY
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (GENERAL_PLAYLIST.wasPressed()) {
                client.setScreen(new GeneralPlaylistScreen(null));
            } else if (INDEX_SCREEN.wasPressed()) {
                client.setScreen(new ConcertoIndexScreen(null));
            } else if (NEXT.wasPressed()) {
                if (!MusicPlayer.INSTANCE.started) MusicPlayer.INSTANCE.start();
                else MusicPlayer.INSTANCE.playNext(1);
            }
        });
    }
}
