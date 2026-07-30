package top.gregtao.concerto.core.player.engine;

public enum PlaybackState {
    /** No track loaded. */
    IDLE,
    /** Actively decoding and writing audio. */
    PLAYING,
    /** Paused by the user (a track is still loaded). */
    PAUSED,
    /** Playing, but waiting for data to download (load, seek, or underrun). */
    BUFFERING
}
