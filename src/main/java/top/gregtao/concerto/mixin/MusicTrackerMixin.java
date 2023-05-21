package top.gregtao.concerto.mixin;

import com.goxr3plus.streamplayer.enums.Status;
import net.minecraft.client.sound.MusicTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.gregtao.concerto.player.MusicPlayer;

@Mixin(MusicTracker.class)
public class MusicTrackerMixin {
    @Inject(at = @At("HEAD"), method = "tick()V", cancellable = true)
    private void tickInject(CallbackInfo ci) {
        Status status = MusicPlayer.INSTANCE.getStatus();
        if (status != Status.NOT_SPECIFIED && status != Status.STOPPED && status != Status.PAUSED) {
            ci.cancel();
        }
    }
}
