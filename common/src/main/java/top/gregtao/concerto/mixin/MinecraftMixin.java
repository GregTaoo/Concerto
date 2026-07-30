package top.gregtao.concerto.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.core.player.MusicPlayer;
import top.gregtao.concerto.core.player.MusicPlayerHandler;
import top.gregtao.concerto.core.room.MusicRoom;
import top.gregtao.concerto.network.ClientMusicNetworkHandler;
import top.gregtao.concerto.screen.MusicAuditionScreen;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Inject(at = @At("TAIL"), method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V")
    public void disconnectInject(Screen disconnectionScreen, boolean transferring, CallbackInfo ci) {
        ConcertoClient.serverAvailable = false;
        ClientMusicNetworkHandler.WAIT_CONFIRMATION.clear();
        MusicAuditionScreen.WAIT_AUDITION.clear();
        MusicRoom.CLIENT_ROOM = null;
        if (MusicPlayerHandler.INSTANCE != null) {
            MusicPlayerHandler.INSTANCE.setPaused(true);
        }
        ConcertoClient.LOGGER.info("Exited from server. Functions of server side are unavailable now.");
    }

    /**
     * Runs once on the quit path: destroy() calls close() to release the
     * game's resources (and only exits the JVM afterwards). Release Concerto's
     * audio device, thread pools and log file here so quitting never leaves
     * audio playing or a pinned javaw.exe behind. shutdownAll() is idempotent
     * and never throws.
     */
    @Inject(at = @At("HEAD"), method = "close()V")
    public void closeInject(CallbackInfo ci) {
        MusicPlayer.shutdownAll();
        ConcertoClient.LOGGER.info("Client is closing, Concerto resources released.");
    }
}
