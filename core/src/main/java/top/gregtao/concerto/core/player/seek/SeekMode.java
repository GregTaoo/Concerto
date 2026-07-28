package top.gregtao.concerto.core.player.seek;

/**
 * The decoder reopening contract required to seek a container format.
 */
public enum SeekMode {
    /** Seek through an incrementally built time-to-byte index. */
    INDEXED,
    /** Reopen at byte zero and discard decoded PCM through the target time. */
    RESTART_FROM_START,
    /** No safe seek contract is available. */
    UNSUPPORTED
}
