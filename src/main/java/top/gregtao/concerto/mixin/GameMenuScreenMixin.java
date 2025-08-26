package top.gregtao.concerto.mixin;

import net.minecraft.client.gui.screen.GameMenuScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.gregtao.concerto.player.MusicPlayer;
import top.gregtao.concerto.util.ConcertoRunner;

@Mixin(GameMenuScreen.class)
public class GameMenuScreenMixin {

    @Inject(at = @At("HEAD"), method = "disconnect()V")
    private void disconnectInject(CallbackInfo ci) {
        ConcertoRunner.run(MusicPlayer.INSTANCE::pause);
    }
}
