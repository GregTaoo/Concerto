package top.gregtao.concerto.mixin;

import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.gregtao.concerto.experimental.player.ConcertoMusicSound;
import top.gregtao.concerto.experimental.player.SoundSystemHelper;

@Mixin(SoundSystem.class)
public class SoundSystemMixin {

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", cancellable = true,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sound/SoundInstance;getSoundSet(Lnet/minecraft/client/sound/SoundManager;)Lnet/minecraft/client/sound/WeightedSoundSet;"))
    private void playInject(SoundInstance sound, CallbackInfo ci) {
        if (sound instanceof ConcertoMusicSound musicSound) {
            SoundSystemHelper.play(sound, musicSound.getMusic());
            ci.cancel();
        }
    }
}
