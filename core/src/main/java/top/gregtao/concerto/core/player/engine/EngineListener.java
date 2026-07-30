package top.gregtao.concerto.core.player.engine;

import javax.sound.sampled.AudioFormat;

/**
 * Callbacks from the playback engine. All methods are invoked on the engine
 * thread and must return quickly.
 */
public interface EngineListener {

    /** A new session finished loading and is about to produce audio. */
    void onTrackStarted(PlaybackSession session);

    /** The PCM format is decoded and the output sink has opened successfully. */
    default void onAudioOutputOpened(PlaybackSession session, AudioSink sink, AudioFormat format) {
    }

    /** The track played to its end and the sink has drained. */
    void onTrackEnded(PlaybackSession session);

    /** Loading or playback failed; the session has been torn down. */
    void onPlaybackError(PlaybackSession session, Exception exception);

    /** Fresh playback position, roughly every PCM chunk (~20 ms). */
    void onPositionUpdate(long positionMillis);

    /** A seek finished being applied; playback continues from {@code positionMillis}. */
    void onSeekApplied(PlaybackSession session, long positionMillis, boolean publishRoomSync);

    /** Decoded PCM about to be written to the sink (for visualisation taps). */
    void onPcm(byte[] data, int offset, int length, AudioFormat format);

    /**
     * A freshly created sink failed to open. Return a replacement sink to retry
     * the same session with (e.g. an OpenAL sink when JavaSound has no line),
     * or {@code null} to let the failure propagate. The failed sink is closed
     * by the engine before the replacement is opened.
     */
    default AudioSink onSinkOpenFailed(AudioSink failedSink, Exception failure) {
        return null;
    }
}
