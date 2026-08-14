package top.gregtao.concerto.core.player.engine;

/**
 * Loudness normalization policy for one playback session, read from the client
 * config when the session is prepared. A disabled policy leaves the stream
 * untouched (unity gain).
 *
 * <p>The constructor sanitizes its inputs: non-finite or out-of-range values
 * (possible from hand-edited config files, where {@code Math.clamp} would let
 * NaN through) are replaced with the defaults, so a NaN can never reach the
 * sample path.
 *
 * @param enabled    whether the track is measured and normalized
 * @param targetLufs integrated loudness tracks are normalized to (LUFS)
 * @param maxGainDb  cap on the applied gain in dB, both boosting and cutting
 */
public record LoudnessSettings(boolean enabled, float targetLufs, float maxGainDb) {

    private static final float DEFAULT_TARGET_LUFS = -14.0f;
    private static final float DEFAULT_MAX_GAIN_DB = 15.0f;
    private static final float MIN_TARGET_LUFS = -23.0f;
    private static final float MAX_TARGET_LUFS = -11.0f;
    private static final float MAX_GAIN_DB = 20.0f;

    public LoudnessSettings {
        if (!Float.isFinite(targetLufs)) targetLufs = DEFAULT_TARGET_LUFS;
        if (!Float.isFinite(maxGainDb)) maxGainDb = DEFAULT_MAX_GAIN_DB;
        targetLufs = Math.max(MIN_TARGET_LUFS, Math.min(MAX_TARGET_LUFS, targetLufs));
        maxGainDb = Math.max(0.0f, Math.min(MAX_GAIN_DB, maxGainDb));
    }

    public static LoudnessSettings disabled() {
        return new LoudnessSettings(false, DEFAULT_TARGET_LUFS, DEFAULT_MAX_GAIN_DB);
    }
}
