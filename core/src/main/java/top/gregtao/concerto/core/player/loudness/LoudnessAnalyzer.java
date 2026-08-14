package top.gregtao.concerto.core.player.loudness;

import javax.sound.sampled.AudioFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Integrated loudness measurement per ITU-R BS.1770-4 / EBU R128, following the
 * reference implementation in libebur128 (the engine behind ffmpeg's loudnorm):
 *
 * <ul>
 *   <li><b>K-weighting</b> — each channel passes a 4th-order filter made of the
 *       two standard biquads (a +4 dB high shelf at 1681.97 Hz and the RLB
 *       high-pass at 38.14 Hz), combined into one set of 5 b/5 a coefficients.</li>
 *   <li><b>Mean square</b> — the weighted signal is squared and averaged over
 *       400 ms blocks with 75 % overlap (a new block every 100 ms). Channel
 *       powers are weighted per BS.1770 (surround +1.5 dB, LFE excluded).</li>
 *   <li><b>Gating</b> — blocks below -70 LUFS are dropped at insertion; the
 *       remaining blocks are averaged, the mean is lowered by 10 LU, and only
 *       blocks above that relative gate contribute to the final mean.</li>
 *   <li><b>Loudness</b> — {@code L = 10 log10(meanEnergy) - 0.691} (the
 *       -0.691 dB calibration offset from BS.1770).</li>
 * </ul>
 *
 * <p>The streamed-in loudness is then turned into a static normalization gain
 * {@code gainDb = target - measured}, clamped to {@code [-maxGainDb, maxGainDb]},
 * and returned as a linear multiplier.
 *
 * <p>Input is the engine's decoded PCM: 16-bit signed little-endian or 32-bit
 * float little-endian, 1-2 channels (whatever {@code DecoderFactory} emits).
 * The analyzer is single-threaded; all calls must happen on one thread.
 */
public final class LoudnessAnalyzer {

    /** Measured loudness plus the derived normalization gain. */
    public record Result(double integratedLufs, double gainDb, float linearGain) {
    }

    /** The -0.691 dB offset that calibrates K-weighted energy to LUFS. */
    private static final double LOUDNESS_OFFSET = -0.691;
    /** Relative gate: -10 LU below the gated mean (EBU R128). */
    private static final double RELATIVE_GATE = -10.0;
    /** Absolute gate energy: the -70 LUFS floor, converted with the offset. */
    private static final double ABSOLUTE_GATE_ENERGY = Math.pow(10.0, (-70.0 + 0.691) / 10.0);

    // Stage 1: high-shelf (ITU-R BS.1770-4 Annex 2, as used by libebur128)
    private static final double SHELF_F0 = 1681.974450955533;
    private static final double SHELF_GAIN_DB = 3.999843853973347;
    private static final double SHELF_Q = 0.7071752369554196;
    // Stage 2: RLB high-pass
    private static final double RLB_F0 = 38.13547087602444;
    private static final double RLB_Q = 0.5003270373238773;

    /** Number of 100 ms hops that make one 400 ms gating block. */
    private static final int HOPS_PER_BLOCK = 4;

    private final int sampleRate;
    private final int channels;
    private final int framesPerHop;
    private final int framesPerBlock;
    private final boolean[] usedChannel;
    private final double[] channelWeight;
    private boolean float32 = false;

    // K-weighting filter: b[0..4] / a[0..4] with a[0] == 1, one state per channel
    private final double[] b = new double[5];
    private final double[] a = new double[5];
    private final double[][] state;

    // Per-channel sum of squares inside the current 100 ms hop
    private final double[] hopSum;
    private int framesInHop = 0;
    // Ring of the last HOPS_PER_BLOCK hop sums per channel
    private final double[][] hopRing;
    private int hopRingIndex = 0;
    private int hopsComplete = 0;

    /** Block energies that passed the absolute gate (stored for the relative gate). */
    private final List<Double> gatedBlocks = new ArrayList<>();
    private boolean finished = false;

    // A decoder read can split a PCM frame across chunk boundaries; hold the
    // partial frame here until the rest arrives (<= frameSize - 1 bytes).
    private byte[] carry = new byte[0];
    private int carryLength = 0;

    public LoudnessAnalyzer(int sampleRate, int channels) {
        if (sampleRate <= 0) throw new IllegalArgumentException("Invalid sample rate: " + sampleRate);
        if (channels <= 0) throw new IllegalArgumentException("Invalid channel count: " + channels);
        this.sampleRate = sampleRate;
        this.channels = channels;
        // Round like libebur128: (fs + 5) / 10 samples per 100 ms hop
        this.framesPerHop = (sampleRate + 5) / 10;
        this.framesPerBlock = this.framesPerHop * HOPS_PER_BLOCK;
        this.state = new double[channels][5];
        this.hopSum = new double[channels];
        this.hopRing = new double[HOPS_PER_BLOCK][channels];
        this.usedChannel = new boolean[channels];
        this.channelWeight = new double[channels];
        for (int c = 0; c < this.channels; c++) {
            this.channelWeight[c] = 1.0;
        }
        this.initFilter();
    }

    private void initFilter() {
        // Stage 1: high-shelf, as parameterized in libebur128
        double k = Math.tan(Math.PI * SHELF_F0 / this.sampleRate);
        double vh = Math.pow(10.0, SHELF_GAIN_DB / 20.0);
        double vb = Math.pow(vh, 0.4996667741545416);
        double a0 = 1.0 + k / SHELF_Q + k * k;
        double[] pb = {
                (vh + vb * k / SHELF_Q + k * k) / a0,
                2.0 * (k * k - vh) / a0,
                (vh - vb * k / SHELF_Q + k * k) / a0
        };
        double[] pa = {1.0, 2.0 * (k * k - 1.0) / a0, (1.0 - k / SHELF_Q + k * k) / a0};

        // Stage 2: RLB high-pass
        k = Math.tan(Math.PI * RLB_F0 / this.sampleRate);
        double rlba0 = 1.0 + k / RLB_Q + k * k;
        double[] rb = {1.0, -2.0, 1.0};
        double[] ra = {1.0, 2.0 * (k * k - 1.0) / rlba0, (1.0 - k / RLB_Q + k * k) / rlba0};

        // Cascade: convolve the two biquads into a single 4th-order filter.
        // Direct form II transposed state machine, matching libebur128:
        //   v0 = x - a1 v1 - a2 v2 - a3 v3 - a4 v4
        //   y  = b0 v0 + b1 v1 + b2 v2 + b3 v3 + b4 v4
        this.convolve(pb, rb, this.b);
        this.convolve(pa, ra, this.a);

        // BS.1770-4 channel map: L/R/C = 0 dB, Ls/Rs = +1.5 dB (1.41x power),
        // LFE excluded. Only 1-2 channels ever come out of DecoderFactory, but
        // keep the standard mapping for higher-order sources (4ch: L R Ls Rs,
        // 5ch adds C, 6ch adds an excluded LFE).
        for (int c = 0; c < this.channels; c++) {
            this.usedChannel[c] = !(c == 3 && this.channels == 6);
            if (this.channels >= 4 && c >= this.channels - 2) {
                this.channelWeight[c] = 1.41;
            }
        }
    }

    private void convolve(double[] x, double[] y, double[] out) {
        out[0] = x[0] * y[0];
        out[1] = x[0] * y[1] + x[1] * y[0];
        out[2] = x[0] * y[2] + x[1] * y[1] + x[2] * y[0];
        out[3] = x[1] * y[2] + x[2] * y[1];
        out[4] = x[2] * y[2];
    }

    /** Validates the format against the constructor parameters and the byte layout. */
    private void checkFormat(AudioFormat format) {
        if (format.getSampleRate() != this.sampleRate || format.getChannels() != this.channels) {
            throw new IllegalArgumentException(
                    "Analyzer configured for " + this.sampleRate + " Hz x " + this.channels
                            + " ch, got " + format);
        }
        boolean float32 = AudioFormat.Encoding.PCM_FLOAT.equals(format.getEncoding())
                && format.getSampleSizeInBits() == 32;
        boolean int16 = AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())
                && format.getSampleSizeInBits() == 16;
        if (!float32 && !int16) {
            throw new IllegalArgumentException("Unsupported PCM format: " + format);
        }
        // Byte layout assumptions: little-endian, packed frames.
        if (format.isBigEndian()) {
            throw new IllegalArgumentException("Big-endian PCM is not supported: " + format);
        }
        int bytesPerSample = format.getSampleSizeInBits() / 8;
        if (format.getFrameSize() != format.getChannels() * bytesPerSample) {
            throw new IllegalArgumentException("Unpacked or padded frames are not supported: " + format);
        }
        this.float32 = float32;
    }

    /**
     * Feeds a chunk of interleaved PCM. The chunk does not need to be aligned
     * to the 100 ms hop or even to frame boundaries: a partial frame at the end
     * of a chunk is carried over to the next feed.
     */
    public void feed(byte[] data, int offset, int length, AudioFormat format) {
        if (this.finished) throw new IllegalStateException("Analyzer already finished");
        this.checkFormat(format);
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new IllegalArgumentException("Invalid range: offset=" + offset + ", length=" + length
                    + ", data.length=" + data.length);
        }
        int frameSize = format.getFrameSize();
        if (this.carry.length < frameSize) {
            this.carry = new byte[frameSize];
        }
        int position = offset;
        int remaining = length;
        if (this.carryLength > 0) {
            while (remaining > 0 && this.carryLength < frameSize) {
                this.carry[this.carryLength++] = data[position++];
                remaining--;
            }
            if (this.carryLength == frameSize) {
                this.feedFrame(this.carry, 0);
                this.carryLength = 0;
            }
        }
        while (remaining >= frameSize) {
            this.feedFrame(data, position);
            position += frameSize;
            remaining -= frameSize;
        }
        if (remaining > 0) {
            System.arraycopy(data, position, this.carry, 0, remaining);
            this.carryLength = remaining;
        }
    }

    private void feedFrame(byte[] data, int index) {
        for (int c = 0; c < this.channels; c++) {
            if (!this.usedChannel[c]) continue;
            float sample = readSample(data, index + c * (this.float32 ? 4 : 2), this.float32);
            double y = this.filter(c, sample);
            this.hopSum[c] += y * y;
        }
        if (++this.framesInHop == this.framesPerHop) {
            this.completeHop();
        }
    }

    private static float readSample(byte[] data, int index, boolean float32) {
        if (float32) {
            int bits = (data[index] & 0xFF) | ((data[index + 1] & 0xFF) << 8)
                    | ((data[index + 2] & 0xFF) << 16) | ((data[index + 3] & 0xFF) << 24);
            return Float.intBitsToFloat(bits);
        }
        short sample = (short) ((data[index] & 0xFF) | (data[index + 1] << 8));
        return sample / 32768.0f;
    }

    private double filter(int channel, float input) {
        double[] v = this.state[channel];
        double v0 = input - this.a[1] * v[1] - this.a[2] * v[2] - this.a[3] * v[3] - this.a[4] * v[4];
        double y = this.b[0] * v0 + this.b[1] * v[1] + this.b[2] * v[2] + this.b[3] * v[3] + this.b[4] * v[4];
        v[4] = v[3];
        v[3] = v[2];
        v[2] = v[1];
        v[1] = v0;
        return y;
    }

    private void completeHop() {
        for (int c = 0; c < this.channels; c++) {
            this.hopRing[this.hopRingIndex][c] = this.hopSum[c];
            this.hopSum[c] = 0.0;
        }
        this.hopRingIndex = (this.hopRingIndex + 1) % HOPS_PER_BLOCK;
        this.hopsComplete++;
        this.framesInHop = 0;
        if (this.hopsComplete < HOPS_PER_BLOCK) return;

        double blockEnergy = 0.0;
        for (int i = 0; i < HOPS_PER_BLOCK; i++) {
            for (int c = 0; c < this.channels; c++) {
                blockEnergy += this.hopRing[i][c] * this.channelWeight[c];
            }
        }
        blockEnergy /= (double) this.framesPerBlock;
        if (blockEnergy >= ABSOLUTE_GATE_ENERGY) {
            this.gatedBlocks.add(blockEnergy);
        }
    }

    /**
     * Closes the measurement and derives the normalization gain.
     *
     * @param targetLufs the integrated loudness tracks are normalized to (LUFS)
     * @param maxGainDb  cap on the applied gain in dB (applied in both directions)
     */
    public Result finish(double targetLufs, double maxGainDb) {
        if (this.finished) throw new IllegalStateException("Analyzer already finished");
        this.finished = true;
        if (this.gatedBlocks.isEmpty()) {
            // Nothing above the absolute gate (silence or shorter than one block):
            // treat as extremely quiet and allow the maximum boost.
            double gainDb = Math.min(maxGainDb, targetLufs + 70.0);
            return new Result(Double.NEGATIVE_INFINITY, gainDb, (float) Math.pow(10.0, gainDb / 20.0));
        }
        double mean = 0.0;
        for (double z : this.gatedBlocks) mean += z;
        mean /= this.gatedBlocks.size();
        double threshold = mean * Math.pow(10.0, RELATIVE_GATE / 10.0);

        double gated = 0.0;
        int count = 0;
        for (double z : this.gatedBlocks) {
            if (z >= threshold) {
                gated += z;
                count++;
            }
        }
        if (count == 0) {
            double gainDb = Math.min(maxGainDb, targetLufs + 70.0);
            return new Result(Double.NEGATIVE_INFINITY, gainDb, (float) Math.pow(10.0, gainDb / 20.0));
        }
        double lufs = 10.0 * Math.log10(gated / count) + LOUDNESS_OFFSET;
        double gainDb = Math.max(-maxGainDb, Math.min(maxGainDb, targetLufs - lufs));
        return new Result(lufs, gainDb, (float) Math.pow(10.0, gainDb / 20.0));
    }
}
