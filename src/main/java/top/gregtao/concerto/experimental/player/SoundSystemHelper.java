package top.gregtao.concerto.experimental.player;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.*;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.mixin.SoundManagerAccessor;
import top.gregtao.concerto.mixin.SoundSystemAccessor;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.music.MusicSource;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class SoundSystemHelper {

    public static void play(SoundInstance soundInstance, Music music) {
        WeightedSoundSet soundSet = soundInstance.getSoundSet(MinecraftClient.getInstance().getSoundManager());
        SoundSystem soundSystem = getSoundSystem();
        SoundSystemAccessor soundSystemAccessor = (SoundSystemAccessor) soundSystem;
        Identifier identifier = soundInstance.getId();
        Sound sound = soundInstance.getSound();
        float volume = soundInstance.getVolume();
        float attenuation = Math.max(volume, 1.0f) * (float) sound.getAttenuation();
        SoundCategory soundCategory = soundInstance.getCategory();
        float adjustedVolume = getAdjustedVolume(volume, soundCategory);
        float pitch = getAdjustedPitch(soundInstance);
        SoundInstance.AttenuationType attenuationType = soundInstance.getAttenuationType();
        boolean relative = soundInstance.isRelative();
        if (adjustedVolume == 0.0f && !soundInstance.shouldAlwaysPlay()) {
            ConcertoClient.LOGGER.debug("Skipped playing sound {}, volume was zero.", sound.getIdentifier());
            return;
        }
        Vec3d vec3d = new Vec3d(soundInstance.getX(), soundInstance.getY(), soundInstance.getZ());
        List<SoundInstanceListener> listeners = soundSystemAccessor.getListeners();
        if (!listeners.isEmpty()) {
            boolean bl = relative || attenuationType == SoundInstance.AttenuationType.NONE ||
                    soundSystemAccessor.getListener().getPos().squaredDistanceTo(vec3d) < (double)(attenuation * attenuation);
            if (bl) {
                for (SoundInstanceListener soundInstanceListener : listeners) {
                    soundInstanceListener.onSoundPlayed(soundInstance, soundSet);
                }
            } else {
                ConcertoClient.LOGGER.debug("Did not notify listeners of soundEvent: {}, it is too far away to hear", identifier);
            }
        }
        if (soundSystemAccessor.getListener().getVolume() <= 0.0f) {
            ConcertoClient.LOGGER.debug("Skipped playing soundEvent: {}, master volume was zero", identifier);
            return;
        }
        CompletableFuture<Channel.SourceManager> completableFuture = soundSystemAccessor.getChannel().createSource(SoundEngine.RunMode.STREAMING);
        Channel.SourceManager sourceManager = completableFuture.join();
        soundSystemAccessor.getSoundEndTicks().put(soundInstance, soundSystemAccessor.getTicks() + 20);
        soundSystemAccessor.getSources().put(soundInstance, sourceManager);
        soundSystemAccessor.getSounds().put(soundCategory, soundInstance);
        sourceManager.run(source -> {
            source.setPitch(pitch);
            source.setVolume(adjustedVolume);
            source.setAttenuation(attenuation);
            source.setLooping(false);
            source.setPosition(vec3d);
            source.setRelative(relative);
        });
        CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println(1);
                MusicSource musicSource = music.getMusicSourceOrNull();
                if (musicSource == null) return null;
                return new ConcertoAudioStream(musicSource.getAudioStream());
            } catch (UnsupportedAudioFileException | IOException e) {
                return null;
            }
        }).thenAccept(stream -> {
            if (stream == null) return;
            sourceManager.run((source) -> {
                source.setStream(stream);
                source.play();
            });
        });
        if (soundInstance instanceof TickableSoundInstance) {
            soundSystemAccessor.getTickingSounds().add((TickableSoundInstance) soundInstance);
        }
    }

    private static float getAdjustedVolume(float volume, SoundCategory category) {
        return MathHelper.clamp(volume * getSoundVolume(category), 0.0f, 1.0f);
    }

    private static float getSoundVolume(@Nullable SoundCategory category) {
        if (category == null || category == SoundCategory.MASTER) {
            return 1.0f;
        }
        return MinecraftClient.getInstance().options.getSoundVolume(category);
    }

    private static float getAdjustedPitch(SoundInstance sound) {
        return MathHelper.clamp(sound.getPitch(), 0.5f, 2.0f);
    }

    private static SoundSystem getSoundSystem() {
        return ((SoundManagerAccessor) MinecraftClient.getInstance().getSoundManager()).getSoundSystem();
    }
}
