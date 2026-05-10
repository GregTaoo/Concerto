package top.gregtao.concerto.mixin;

import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.core.player.MusicPlayerHandler;

@Mixin(SoundManager.class)
public class SoundManagerMixin {

    @Inject(at = @At("HEAD"), method = "stop()V")
    private void pauseAllInject(CallbackInfo ci) {
        MusicPlayerHandler.INSTANCE.setPaused(true);
    }

    @Inject(at = @At("HEAD"), method = "resume()V")
    private void resumeAllInject(CallbackInfo ci) {
        MusicPlayerHandler.INSTANCE.setPaused(false);
    }

    @Inject(at = @At("TAIL"), method = "updateCategoryVolume(Lnet/minecraft/sounds/SoundSource;F)V")
    private void updateSoundVolumeInject(SoundSource category, float volume, CallbackInfo ci) {
        if (category == SoundSource.MASTER || category == SoundSource.MUSIC) {
            ConcertoClient.syncPlayerVolume();
        }
    }
}
