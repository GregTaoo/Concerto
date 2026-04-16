package top.gregtao.concerto.mixin;

import net.minecraft.client.gui.screens.PauseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.gregtao.concerto.core.player.MusicPlayerHandler;
import top.gregtao.concerto.core.util.ConcertoRunner;

@Mixin(PauseScreen.class)
public class PauseScreenMixin {

    @Inject(at = @At("HEAD"), method = "onDisconnect()V")
    private void disconnectInject(CallbackInfo ci) {
        ConcertoRunner.run(() -> MusicPlayerHandler.INSTANCE.setPaused(true));
    }
}
