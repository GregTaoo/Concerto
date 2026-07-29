package top.gregtao.concerto.core.player.seek;

import top.gregtao.concerto.core.player.source.AudioByteSource;

import java.io.IOException;

/**
 * Scans one container format sequentially and fills a {@link SeekIndex}.
 *
 * Implementations run on a background indexer task, read the media through
 * blocking {@link AudioByteSource} reads (waiting for the download to progress),
 * and hold no locks across I/O. When the source is closed the pending read
 * throws and the scan simply ends.
 */
public interface SeekIndexBuilder {

    /** Roughly one seek point per second of audio. */
    long POINT_INTERVAL_MILLIS = 1000;

    void build(AudioByteSource source, SeekIndex index) throws IOException;
}
