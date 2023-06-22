package top.gregtao.concerto.music;

import top.gregtao.concerto.config.ClientConfig;
import top.gregtao.concerto.config.MusicCacheManager;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

public abstract class CacheableMusic extends Music {
    private final MusicCacheManager cacheManager;

    public CacheableMusic(MusicCacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public MusicSource getMusicSourceOrNull() {
        if (!ClientConfig.INSTANCE.options.cacheBeforePlay) {
            MusicSource source = this.getMusicSource();
            try {
                return source == null ? null : MusicSource.of(new ByteArrayInputStream(source.getAudioStream().readAllBytes()));
            } catch (UnsupportedAudioFileException | IOException e) {
                return null;
            }
        }
        File child = this.cacheManager.getChild(this);
        try {
            if (child == null) {
                this.cacheManager.addMusic(this);
            }
            child = this.cacheManager.getChild(this);
            return child == null ? null : MusicSource.of(child);
        } catch (MusicSourceNotFoundException e) {
            return null;
        } catch (IOException | UnsupportedAudioFileException e) {
            return super.getMusicSourceOrNull();
        }
    }

    public abstract String getSuffix();
}
