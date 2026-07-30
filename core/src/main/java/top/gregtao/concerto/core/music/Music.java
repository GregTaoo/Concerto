package top.gregtao.concerto.core.music;

import top.gregtao.concerto.core.api.*;
import top.gregtao.concerto.core.config.MusicCacheManager;
import top.gregtao.concerto.core.music.lyrics.Lyrics;
import top.gregtao.concerto.core.music.meta.music.MusicMetaData;
import top.gregtao.concerto.core.Concerto;
import top.gregtao.concerto.core.player.source.AudioByteSource;
import top.gregtao.concerto.core.player.source.BufferedHttpByteSource;
import top.gregtao.concerto.core.player.source.FileByteSource;
import top.gregtao.concerto.core.util.FileUtil;
import top.gregtao.concerto.core.util.Pair;

import java.io.*;
import java.net.URI;
import java.util.Map;
import java.util.function.Supplier;

public abstract class Music implements JsonParsable<Music>, LazyLoadable, WithMetaData {

    private boolean isMetaLoaded = false;
    private MusicMetaData musicMetaData = null;

    public InputStream getMusicSourceOrNull() {
        if (this instanceof CacheableMusic cacheable) {
            File child = MusicCacheManager.INSTANCE.getChild(cacheable);
            try {
                return child == null ? this.getMusicSource() : FileUtil.createBuffered(new FileInputStream(child));
            } catch (MusicSourceNotFoundException | FileNotFoundException e) {
                return null;
            }
        } else {
            try {
                return this.getMusicSource();
            } catch (MusicSourceNotFoundException e) {
                return null;
            }
        }
    }

    /**
     * Creates the random-access byte source the playback engine consumes, or
     * null when the media cannot be reached.
     */
    public AudioByteSource createByteSource() {
        try {
            if (this instanceof CacheableMusic cacheable) {
                File child = MusicCacheManager.INSTANCE.getChild(cacheable);
                if (child != null) {
                    return new FileByteSource(child);
                }
            }
            if (this instanceof LocalFileMusic localFileMusic) {
                return new FileByteSource(new File(localFileMusic.getRawPath()));
            }
            if (this instanceof DynamicPath dynamicPath) {
                String rawPath = dynamicPath.getLastRawPath();
                if (rawPath == null) {
                    rawPath = dynamicPath.updateRawPath();
                }
                return createUrlByteSource(rawPath, dynamicPath::updateRawPath, dynamicPath.getCustomHeaders());
            }
            if (this instanceof PathFileMusic pathFileMusic) {
                String rawPath = pathFileMusic.getRawPath();
                if (rawPath != null && rawPath.startsWith("http")) {
                    return createUrlByteSource(rawPath, null, Map.of());
                }
            }
            return createUrlByteSource(this.getLink(), null, Map.of());
        } catch (Exception e) {
            Concerto.getLogger().error("Cannot open music source: {}", e.getMessage());
            return null;
        }
    }

    private static AudioByteSource createUrlByteSource(String rawPath, Supplier<String> urlRefresher,
                                                        Map<String, String> customHeaders) throws IOException {
        URI.create(rawPath);
        return new BufferedHttpByteSource(rawPath, urlRefresher, customHeaders);
    }

    public Pair<Lyrics, Lyrics> getLyrics() throws IOException {
        return null;
    }

    public MusicMetaData getMeta() {
        if (!this.isLoaded()) {
            this.load();
            this.isMetaLoaded = true;
        }
        return this.musicMetaData;
    }

    public void load() {
        this.isMetaLoaded = true;
    }

    public void setMusicMeta(MusicMetaData musicMetaData) {
        this.musicMetaData = musicMetaData;
        this.isMetaLoaded = true;
    }

    public boolean isLoaded() {
        return this.isMetaLoaded;
    }

    public boolean isMetaLoaded() {
        return this.isMetaLoaded;
    }

    public abstract InputStream getMusicSource() throws MusicSourceNotFoundException;

    public abstract String getLink();
}
