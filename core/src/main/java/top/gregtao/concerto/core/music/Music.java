package top.gregtao.concerto.core.music;

import top.gregtao.concerto.core.api.*;
import top.gregtao.concerto.core.config.MusicCacheManager;
import top.gregtao.concerto.core.music.lyrics.Lyrics;
import top.gregtao.concerto.core.music.meta.music.MusicMetaData;
import top.gregtao.concerto.core.player.seek.ProgressiveMediaDataSource;
import top.gregtao.concerto.core.util.FileUtil;
import top.gregtao.concerto.core.util.Pair;

import java.io.*;
import java.net.URI;

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

    public ProgressiveMediaDataSource createProgressiveMediaDataSource() throws MusicSourceNotFoundException {
        if (this instanceof CacheableMusic cacheable) {
            File child = MusicCacheManager.INSTANCE.getChild(cacheable);
            if (child != null) {
                try {
                    return ProgressiveMediaDataSource.forFile(child);
                } catch (IOException e) {
                    throw new MusicSourceNotFoundException(e);
                }
            }
        }
        if (this instanceof LocalFileMusic localFileMusic) {
            try {
                return ProgressiveMediaDataSource.forFile(new File(localFileMusic.getRawPath()));
            } catch (IOException e) {
                throw new MusicSourceNotFoundException(e);
            }
        }
        if (this instanceof DynamicPath dynamicPath) {
            String rawPath = dynamicPath.getLastRawPath();
            if (rawPath == null) {
                rawPath = dynamicPath.updateRawPath();
            }
            return createUrlDataSource(rawPath, dynamicPath::updateRawPath);
        }
        if (this instanceof BilibiliMusic bilibiliMusic) {
            return createUrlDataSource(bilibiliMusic.getRawPath(), null);
        }
        if (this instanceof PathFileMusic pathFileMusic) {
            String rawPath = pathFileMusic.getRawPath();
            if (rawPath != null && rawPath.startsWith("http")) {
                return createUrlDataSource(rawPath, null);
            }
        }
        return createUrlDataSource(this.getLink(), null);
    }

    private static ProgressiveMediaDataSource createUrlDataSource(String rawPath, java.util.function.Supplier<String> supplier)
            throws MusicSourceNotFoundException {
        try {
            URI.create(rawPath);
            return ProgressiveMediaDataSource.forUrl(rawPath, supplier);
        } catch (Exception e) {
            throw new MusicSourceNotFoundException(e);
        }
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
