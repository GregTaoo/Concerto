package top.gregtao.concerto.experimental.player;

import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import top.gregtao.concerto.music.MusicSource;

public class ConcertoMusicSound extends MovingSoundInstance {
    private final MusicSource musicSource;

    public ConcertoMusicSound(SoundEvent soundEvent, SoundCategory soundCategory, MusicSource musicSource) {
        super(soundEvent, soundCategory, SoundInstance.createRandom());
        this.musicSource = musicSource;
    }

    @Override
    public void tick() {

    }

    public MusicSource getMusicSource() {
        return this.musicSource;
    }
}
