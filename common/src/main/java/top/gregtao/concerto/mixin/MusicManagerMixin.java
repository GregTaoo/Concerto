package top.gregtao.concerto.mixin;

import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.gregtao.concerto.core.player.MusicPlayer;

@Mixin(MusicManager.class)
public class MusicManagerMixin {
    @Inject(at = @At("HEAD"), method = "tick()V", cancellable = true)
    private void tickInject(CallbackInfo ci) {
        if (MusicPlayer.INSTANCE.shouldBlockVanillaMusic()) {
            ci.cancel();
        }
    }
}
