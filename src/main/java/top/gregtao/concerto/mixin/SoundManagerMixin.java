package top.gregtao.concerto.mixin;

import net.minecraft.client.sound.SoundManager;
import net.minecraft.sound.SoundCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.gregtao.concerto.player.MusicPlayer;

@Mixin(SoundManager.class)
public class SoundManagerMixin {

    @Inject(at = @At("HEAD"), method = "pauseAll()V")
    private void pauseAllInject(CallbackInfo ci) {
        MusicPlayer.INSTANCE.pause();
    }

    @Inject(at = @At("HEAD"), method = "resumeAll()V")
    private void resumeAllInject(CallbackInfo ci) {
        MusicPlayer.INSTANCE.resume();
    }

    @Inject(at = @At("TAIL"), method = "updateSoundVolume(Lnet/minecraft/sound/SoundCategory;F)V")
    private void updateSoundVolumeInject(SoundCategory category, float volume, CallbackInfo ci) {
        if (category == SoundCategory.MASTER || category == SoundCategory.MUSIC) {
            MusicPlayer.INSTANCE.syncVolume();
        }
    }
}
