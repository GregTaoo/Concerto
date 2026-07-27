package top.gregtao.concerto.core.player.source;

import java.io.Closeable;
import java.io.IOException;

/**
 * Random-access view over the bytes of a piece of media.
 *
 * Implementations must support concurrent readers (the playback engine and the
 * seek indexer read from different threads). {@link #read} blocks until at least
 * one byte at {@code position} is available, the end of the media is reached, or
 * the source is closed/failed.
 */
public interface AudioByteSource extends Closeable {

    /**
     * Reads up to {@code length} bytes starting at the absolute {@code position}.
     *
     * @return number of bytes read, or -1 if {@code position} is at or past the end
     * @throws IOException if the source is closed or the underlying transfer failed
     */
    int read(long position, byte[] buffer, int offset, int length) throws IOException;

    /** Total size in bytes, or -1 while still unknown. */
    long length();

    /** Number of contiguous bytes available from the start of the media. */
    long availableTo();

    /** True once every byte of the media is locally available. */
    boolean isComplete();

    /**
     * Waits until a read at {@code position} would not block: either
     * {@code count} bytes are buffered, the media ends before that point, or the
     * source is closed/failed (in which case the read reports it promptly).
     *
     * @return true if a read at {@code position} would not block
     */
    boolean awaitAvailable(long position, int count, long timeoutMillis) throws InterruptedException;

    /**
     * Updates the playback window that a streaming source should keep available.
     * Local sources already have every byte and therefore do not need to act on it.
     */
    default void setPlaybackWindow(long bytePosition, long positionMillis, long durationMillis) {
    }
}
