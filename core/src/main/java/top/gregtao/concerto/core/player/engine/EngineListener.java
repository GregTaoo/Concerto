package top.gregtao.concerto.core.player.engine;

/**
 * Callbacks from the playback engine. All methods are invoked on the engine
 * thread and must return quickly.
 */
public interface EngineListener {

    /** A new session finished loading and is about to produce audio. */
    void onTrackStarted(PlaybackSession session);

    /** The track played to its end and the sink has drained. */
    void onTrackEnded(PlaybackSession session);

    /** Loading or playback failed; the session has been torn down. */
    void onPlaybackError(PlaybackSession session, Exception exception);

    /** Fresh playback position, roughly every PCM chunk (~20 ms). */
    void onPositionUpdate(long positionMillis);

    /** A seek finished being applied; playback continues from {@code positionMillis}. */
    void onSeekApplied(PlaybackSession session, long positionMillis, boolean publishRoomSync);

    /** Decoded PCM about to be written to the sink (for visualisation taps). */
    void onPcm(byte[] data, int offset, int length);
}
