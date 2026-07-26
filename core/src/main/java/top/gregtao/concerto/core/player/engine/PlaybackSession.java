package top.gregtao.concerto.core.player.engine;

import top.gregtao.concerto.core.music.Music;
import top.gregtao.concerto.core.player.seek.ContainerFormat;
import top.gregtao.concerto.core.player.seek.SeekIndex;
import top.gregtao.concerto.core.player.source.AudioByteSource;

import java.io.Closeable;
import java.io.IOException;

/**
 * Everything belonging to one loaded track: the byte source, the detected
 * container format and the (incrementally built) seek index. Created by the
 * facade, owned and mutated by the engine thread after being handed over.
 */
public class PlaybackSession implements Closeable {

    private final Music music;
    private final AudioByteSource byteSource;
    private final String suffixHint;
    private final long generation;
    private final long startMillis;

    private ContainerFormat format;
    private SeekIndex seekIndex;

    public PlaybackSession(Music music, AudioByteSource byteSource, String suffixHint, long generation, long startMillis) {
        this.music = music;
        this.byteSource = byteSource;
        this.suffixHint = suffixHint;
        this.generation = generation;
        this.startMillis = startMillis;
    }

    public Music getMusic() {
        return this.music;
    }

    public AudioByteSource getByteSource() {
        return this.byteSource;
    }

    public String getSuffixHint() {
        return this.suffixHint;
    }

    public long getGeneration() {
        return this.generation;
    }

    public long getStartMillis() {
        return this.startMillis;
    }

    public ContainerFormat getFormat() {
        return this.format;
    }

    void setFormat(ContainerFormat format) {
        this.format = format;
    }

    /** Null when the container format is not seekable. */
    public SeekIndex getSeekIndex() {
        return this.seekIndex;
    }

    void setSeekIndex(SeekIndex seekIndex) {
        this.seekIndex = seekIndex;
    }

    public boolean isSeekable() {
        return this.seekIndex != null;
    }

    @Override
    public void close() {
        try {
            this.byteSource.close();
        } catch (IOException ignored) {
        }
    }
}
