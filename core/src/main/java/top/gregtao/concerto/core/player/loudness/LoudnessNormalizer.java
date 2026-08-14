package top.gregtao.concerto.core.player.loudness;

import javax.sound.sampled.AudioFormat;

/**
 * Applies the per-track normalization gain from {@link LoudnessAnalyzer} to the
 * engine's PCM stream, in place.
 *
 * <p>The static gain (which may exceed 1.0 when a quiet track is boosted) is
 * followed by a <b>lookahead peak limiter</b>: input is delayed by a few
 * milliseconds while a sliding window tracks the peak of the gained signal; if
 * it would exceed full scale, a shared gain reduction (linked across channels
 * to preserve the stereo image) is applied to the delayed samples. The
 * lookahead lets the reduction start <i>before</i> the transient reaches the
 * output, so the limiter is click-free; the release time constant keeps it from
 * pumping. When no reduction is active the limiter is transparent.
 *
 * <p>When the gain value changes (e.g. the background analysis completes a few
 * seconds into the track), the applied gain ramps linearly over ~50 ms so the
 * level change is inaudible instead of a step discontinuity.
 *
 * <p>Handles the two formats the engine decodes to: 16-bit signed little-endian
 * and 32-bit float little-endian PCM, 1-2 channels. Chunks are processed in
 * whole frames; a partial frame at the end of a chunk is left untouched (the
 * engine feeds frame-aligned chunks, so this is only a safety net). The
 * analyzer, which reads from a different stream, carries partial frames itself.
 * Single-threaded; all calls must happen on the engine thread.
 */
public final class LoudnessNormalizer {

    /** Samples are capped at full scale after the gain. */
    private static final float LIMIT_CEILING = 1.0f;
    /** How far the limiter looks ahead, in seconds. */
    private static final double LOOKAHEAD_SECONDS = 0.005;
    /** Release time constant once the peak falls below the ceiling. */
    private static final double RELEASE_SECONDS = 0.15;
    /** Duration of the linear ramp when the gain value changes. */
    private static final double RAMP_SECONDS = 0.05;

    /** Shared empty result for {@link #flushTail} when there is no tail to render. */
    private static final byte[] EMPTY_TAIL = new byte[0];

    private int sampleRate = -1;
    private int channels = -1;
    private boolean float32 = false;
    private int bytesPerSample = 0;
    private int frameSize = 0;

    // Static gain (from analysis) with a linear ramp
    private float targetGain = 1f;
    private float currentGain = 1f;
    private float rampStep = 0f;
    private int rampFramesRemaining = 0;

    // Lookahead limiter state
    private int lookaheadSamples = 0;
    private float[] delay;          // ring of post-gain samples, one per channel slot
    private int delayIndex = 0;
    /** Number of valid frames currently in the delay ring (<= lookaheadSamples). */
    private int bufferedFrames = 0;
    private float[][] dequeVal;     // sliding-max window per channel (monotonic deque)
    private long[][] dequeIdx;
    private int[] dequeHead;
    private int[] dequeTail;
    private float limiterGain = 1f;
    private float releaseCoeff = 0f;
    private long step = 0;
    /**
     * When true the delay line runs from the first sample, so the lookahead
     * ring is already full of real audio if the gain changes mid-track. Without
     * it, engaging the limiter mid-track would output ~5 ms of silence from the
     * empty ring. Set by the engine at session start when analysis will run.
     */
    private boolean engaged = false;

    private float[] frameSamples = new float[0];

    /** Sets the target gain; the applied gain ramps toward it smoothly. */
    public void setGain(float gain) {
        if (!Float.isFinite(gain)) return;
        this.targetGain = Math.max(0f, gain);
        if (this.sampleRate > 0) {
            this.startRamp();
        } else {
            // No audio processed yet: jump straight to the target.
            this.currentGain = this.targetGain;
            this.rampFramesRemaining = 0;
        }
    }

    /**
     * Engages or disengages the limiter pipeline. Engaged means the delay line
     * runs from the first sample of the track, so the lookahead ring is already
     * full of real audio when the gain changes mid-track; without this, engaging
     * mid-track would output ~5 ms of silence from the empty ring. Call once at
     * session start, before the first chunk is processed.
     */
    public void setEngaged(boolean engaged) {
        this.engaged = engaged;
    }

    private void startRamp() {
        int rampFrames = Math.max(1, (int) Math.round(RAMP_SECONDS * this.sampleRate));
        this.rampStep = (this.targetGain - this.currentGain) / rampFrames;
        this.rampFramesRemaining = rampFrames;
    }

    /** Drops all state (session ended or format changed). */
    public void reset() {
        this.targetGain = 1f;
        this.currentGain = 1f;
        this.rampStep = 0f;
        this.rampFramesRemaining = 0;
        this.limiterGain = 1f;
        this.step = 0;
        this.delayIndex = 0;
        this.bufferedFrames = 0;
        this.sampleRate = -1;
        this.engaged = false;
    }

    /**
     * Clears the limiter's delay line without touching the gain. Call after a
     * seek: samples buffered before the seek must not leak into the new
     * position.
     */
    public void clearDelay() {
        if (this.delay == null) return;
        java.util.Arrays.fill(this.delay, 0f);
        this.delayIndex = 0;
        this.step = 0;
        this.bufferedFrames = 0;
        this.limiterGain = 1f;
        for (int c = 0; c < this.channels; c++) {
            this.dequeHead[c] = 0;
            this.dequeTail[c] = 0;
        }
    }

    /**
     * Renders the samples still sitting in the limiter's delay line and returns
     * them as a byte array. The engine calls this when the track reaches EOF,
     * otherwise the final ~5 ms of audio (the lookahead) would be dropped.
     * Returns an empty array when no delayed samples exist or the format no
     * longer matches the buffered stream.
     */
    public byte[] flushTail(AudioFormat format) {
        if (this.delay == null || this.bufferedFrames == 0) return EMPTY_TAIL;
        // The tail belongs to the stream that filled the delay line; if the
        // format changed (decoder reopened differently), drop it instead of
        // reconfiguring and losing it anyway.
        if (this.sampleRate != (int) format.getSampleRate()
                || this.channels != format.getChannels()
                || this.float32 != AudioFormat.Encoding.PCM_FLOAT.equals(format.getEncoding())
                || this.frameSize != format.getFrameSize()) {
            return EMPTY_TAIL;
        }
        int frames = this.bufferedFrames;
        byte[] tail = new byte[frames * this.frameSize];
        // The ring is contiguous: from index 0 while partially filled, from
        // delayIndex once full.
        int index = (this.delayIndex - frames + this.lookaheadSamples) % this.lookaheadSamples;
        // The limiter gain is held at its last value: no new peaks can arrive.
        for (int frame = 0; frame < frames; frame++) {
            int base = frame * this.frameSize;
            for (int c = 0; c < this.channels; c++) {
                float out = this.delay[index * this.channels + c] * this.limiterGain;
                this.writeSample(tail, base + c * this.bytesPerSample, out);
                this.delay[index * this.channels + c] = 0f;
            }
            index = (index + 1) % this.lookaheadSamples;
        }
        this.bufferedFrames = 0;
        this.step = 0;
        return tail;
    }

    /**
     * Applies the normalization gain and limiter to {@code data[offset, offset+length)}.
     * The buffer is modified in place; trailing partial frames are untouched.
     */
    public void process(byte[] data, int offset, int length, AudioFormat format) {
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new IllegalArgumentException("Invalid range: offset=" + offset + ", length=" + length
                    + ", data.length=" + data.length);
        }
        // Prime the per-format state on every chunk (cheap once configured) so
        // a gain that arrives between chunks always finds a working ramp.
        this.ensureFormat(format);
        // Fast path: engaged means the delay line runs from the first sample
        // (ring warm for a possible mid-track gain change); otherwise skip
        // everything while the gain is unity.
        if (!this.engaged && this.targetGain == 1f && this.currentGain == 1f) return;
        int frames = length / this.frameSize;
        for (int frame = 0; frame < frames; frame++) {
            int base = offset + frame * this.frameSize;
            this.applyRamp();
            float peak = 0f;
            for (int c = 0; c < this.channels; c++) {
                float sample = this.readSample(data, base + c * this.bytesPerSample);
                float gained = sample * this.currentGain;
                this.frameSamples[c] = gained;
                this.pushWindowMax(c, Math.abs(gained));
                float windowMax = this.dequeVal[c][this.dequeHead[c]];
                float delayed = this.delay[this.delayIndex * this.channels + c];
                // |delayed| is the oldest sample leaving the window; include it
                // so the peak covers exactly [step - lookahead, step].
                peak = Math.max(peak, Math.max(windowMax, Math.abs(delayed)));
            }
            // Linked gain reduction across channels, instant attack (the lookahead
            // gives the reduction time to arrive before the transient plays) and
            // slow release to avoid pumping.
            float target = peak > 0f ? Math.min(1f, LIMIT_CEILING / peak) : 1f;
            if (target < this.limiterGain) {
                this.limiterGain = target;
            } else if (target > this.limiterGain) {
                this.limiterGain += (target - this.limiterGain) * this.releaseCoeff;
            }
            for (int c = 0; c < this.channels; c++) {
                float out = this.delay[this.delayIndex * this.channels + c] * this.limiterGain;
                // Overwrite the slot with the current (gained, not yet limited) sample
                this.delay[this.delayIndex * this.channels + c] = this.frameSamples[c];
                this.writeSample(data, base + c * this.bytesPerSample, out);
            }
            this.delayIndex = (this.delayIndex + 1) % this.lookaheadSamples;
            this.step++;
        }
        this.bufferedFrames = Math.min(this.lookaheadSamples, this.bufferedFrames + frames);
    }

    private void applyRamp() {
        if (this.rampFramesRemaining <= 0) return;
        this.currentGain += this.rampStep;
        if (--this.rampFramesRemaining == 0) {
            this.currentGain = this.targetGain;
        }
    }

    private void ensureFormat(AudioFormat format) {
        boolean float32 = AudioFormat.Encoding.PCM_FLOAT.equals(format.getEncoding())
                && format.getSampleSizeInBits() == 32;
        boolean int16 = AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())
                && format.getSampleSizeInBits() == 16;
        if (!float32 && !int16) {
            throw new IllegalArgumentException("Unsupported PCM format: " + format);
        }
        if (format.isBigEndian()) {
            throw new IllegalArgumentException("Big-endian PCM is not supported: " + format);
        }
        int sampleRate = (int) format.getSampleRate();
        int frameSize = format.getFrameSize();
        if (frameSize != format.getChannels() * (format.getSampleSizeInBits() / 8)) {
            throw new IllegalArgumentException("Unpacked or padded frames are not supported: " + format);
        }
        if (this.sampleRate == sampleRate
                && this.channels == format.getChannels()
                && this.float32 == float32) {
            return;
        }
        this.sampleRate = sampleRate;
        this.channels = format.getChannels();
        this.float32 = float32;
        this.bytesPerSample = format.getSampleSizeInBits() / 8;
        this.frameSize = frameSize;
        this.lookaheadSamples = Math.max(1, (int) Math.round(LOOKAHEAD_SECONDS * this.sampleRate));
        this.delay = new float[this.lookaheadSamples * this.channels];
        this.delayIndex = 0;
        this.step = 0;
        this.bufferedFrames = 0;
        this.limiterGain = 1f;
        this.frameSamples = new float[this.channels];
        this.dequeVal = new float[this.channels][this.lookaheadSamples + 1];
        this.dequeIdx = new long[this.channels][this.lookaheadSamples + 1];
        this.dequeHead = new int[this.channels];
        this.dequeTail = new int[this.channels];
        this.releaseCoeff = (float) (1.0 - Math.exp(-1.0 / (RELEASE_SECONDS * this.sampleRate)));
        // A ramp already in flight targets the new format's frame count.
        if (this.rampFramesRemaining > 0 || this.currentGain != this.targetGain) {
            this.startRamp();
        }
    }

    /** Monotonic sliding-max window over the last {@code lookaheadSamples} inputs. */
    private void pushWindowMax(int channel, float value) {
        float[] val = this.dequeVal[channel];
        long[] idx = this.dequeIdx[channel];
        int capacity = this.lookaheadSamples + 1;
        int head = this.dequeHead[channel];
        int tail = this.dequeTail[channel];
        // Expire before inserting: at step s the deque then holds at most
        // lookaheadSamples - 1 entries, so the push can never fill the ring.
        // The sample leaving the window (step - lookaheadSamples) is not
        // tracked here; the caller adds it to the peak explicitly.
        while (head != tail && idx[head] <= this.step - this.lookaheadSamples) {
            head = (head + 1) % capacity;
        }
        while (tail != head && val[(tail - 1 + capacity) % capacity] <= value) {
            tail = (tail - 1 + capacity) % capacity;
        }
        idx[tail] = this.step;
        val[tail] = value;
        tail = (tail + 1) % capacity;
        this.dequeHead[channel] = head;
        this.dequeTail[channel] = tail;
    }

    private float readSample(byte[] data, int index) {
        if (this.float32) {
            int bits = (data[index] & 0xFF) | ((data[index + 1] & 0xFF) << 8)
                    | ((data[index + 2] & 0xFF) << 16) | ((data[index + 3] & 0xFF) << 24);
            return Float.intBitsToFloat(bits);
        }
        short sample = (short) ((data[index] & 0xFF) | (data[index + 1] << 8));
        return sample / 32768.0f;
    }

    private void writeSample(byte[] data, int index, float sample) {
        if (this.float32) {
            int bits = Float.floatToRawIntBits(sample);
            data[index] = (byte) bits;
            data[index + 1] = (byte) (bits >>> 8);
            data[index + 2] = (byte) (bits >>> 16);
            data[index + 3] = (byte) (bits >>> 24);
            return;
        }
        // The limiter caps at 1.0; clamp as a safety net against float rounding.
        int value = sample >= 1.0f ? 32767 : sample <= -1.0f ? -32768
                : (int) Math.round(sample * 32768.0f);
        data[index] = (byte) value;
        data[index + 1] = (byte) (value >>> 8);
    }
}
