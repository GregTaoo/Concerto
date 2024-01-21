package top.gregtao.concerto.mixin;

import net.minecraft.client.sound.*;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import top.gregtao.concerto.player.exp.ConcertoSoundInstance;
import top.gregtao.concerto.player.exp.ConcertoAudioStream;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Mixin(SoundSystem.class)
public class SoundSystemMixin {

    @Shadow @Final private List<TickableSoundInstance> tickingSounds;

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", cancellable = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/sound/Channel$SourceManager;run(Ljava/util/function/Consumer;)V", shift = At.Shift.AFTER),
            locals = LocalCapture.CAPTURE_FAILSOFT)
    public void playInject(SoundInstance sound, CallbackInfo ci, WeightedSoundSet weightedSoundSet, Identifier identifier,
                           Sound sound2, float f, float g, SoundCategory soundCategory, float h, float i,
                           SoundInstance.AttenuationType attenuationType, boolean bl, Vec3d vec3d, boolean bl2, boolean bl3,
                           CompletableFuture<Channel.SourceManager> completableFuture, Channel.SourceManager sourceManager) {
        if (sound.getId().getNamespace().equals("concerto") && (sound instanceof ConcertoSoundInstance concertoSound)) {
            CompletableFuture.supplyAsync(() -> {
                try {
                    return new ConcertoAudioStream(concertoSound.getMusic().getMusicSource().getAudioStream());
                }
                catch (IOException | UnsupportedAudioFileException e) {
                    throw new CompletionException(e);
                }
            }, Util.getMainWorkerExecutor()).thenAccept(stream -> sourceManager.run(source -> {
                source.setStream(stream);
                source.play();
            }));
            this.tickingSounds.add(concertoSound);
            ci.cancel();
        }
    }
}
