package top.gregtao.concerto.core.player.engine;

import javax.sound.sampled.AudioFormat;
import java.io.Closeable;

/**
 * A PCM output device. All methods are called from the engine thread only, so
 * implementations need no internal synchronization.
 *
 * Position contract: {@link #playedFrames()} counts frames actually rendered
 * since {@link #open} or the last {@link #flush()}, whichever came later.
 */
public interface AudioSink extends Closeable {

    /** PCM_SIGNED, 16-bit little-endian, 1-2 channels. */
    void open(AudioFormat format) throws Exception;

    boolean isOpen();

    /** Human-readable description of the output selected by this sink. */
    default String getOutputDescription() {
        return "unknown output";
    }

    /** Blocks until all bytes are accepted (backpressure paces the decode loop). */
    void write(byte[] data, int offset, int length);

    void pause();

    void resume();

    /** Discards buffered/queued audio and resets {@link #playedFrames()} to 0. */
    void flush();

    /** Blocks until everything written has been rendered (end of track). */
    void drain();

    long playedFrames();

    /** Linear volume 0..1. Must apply immediately and persist across writes. */
    void setGain(float gain);

    @Override
    void close();
}
