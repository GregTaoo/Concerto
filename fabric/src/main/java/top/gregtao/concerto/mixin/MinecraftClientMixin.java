package top.gregtao.concerto.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.core.player.MusicPlayerHandler;
import top.gregtao.concerto.network.ClientMusicNetworkHandler;
import top.gregtao.concerto.network.room.MusicRoom;
import top.gregtao.concerto.screen.MusicAuditionScreen;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Inject(at = @At("TAIL"), method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;Z)V")
    public void disconnectInject(Screen disconnectionScreen, boolean transferring, CallbackInfo ci) {
        ConcertoClient.serverAvailable = false;
        ClientMusicNetworkHandler.WAIT_CONFIRMATION.clear();
        MusicAuditionScreen.WAIT_AUDITION.clear();
        MusicRoom.CLIENT_ROOM = null;
        ConcertoClient.clientState = ConcertoClient.ClientState.LOCAL;
        if (MusicPlayerHandler.INSTANCE != null) {
            MusicPlayerHandler.INSTANCE.setPaused(true);
        }
        ConcertoClient.LOGGER.info("Exited from server. Functions of server side are unavailable now.");
    }
}
