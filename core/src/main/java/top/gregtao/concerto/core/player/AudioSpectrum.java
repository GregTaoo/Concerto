package top.gregtao.concerto.core.player;

public class AudioSpectrum {

    public static class FFT {
        public static void compute(float[] real, float[] imag) {
            int n = real.length;
            int half = n >> 1;
            int j = 0;
            for (int i = 0; i < n - 1; i++) {
                if (i < j) {
                    float tr = real[i];
                    real[i] = real[j];
                    real[j] = tr;
                    float ti = imag[i];
                    imag[i] = imag[j];
                    imag[j] = ti;
                }
                int k = half;
                while (k <= j) {
                    j -= k;
                    k >>= 1;
                }
                j += k;
            }
            for (int l = 2; l <= n; l <<= 1) {
                int lHalf = l >> 1;
                for (int k = 0; k < lHalf; k++) {
                    float wReal = (float) Math.cos(-2.0 * Math.PI * k / l);
                    float wImag = (float) Math.sin(-2.0 * Math.PI * k / l);
                    for (int i = 0; i < n; i += l) {
                        int even = i + k;
                        int odd = i + k + lHalf;
                        float tr = wReal * real[odd] - wImag * imag[odd];
                        float ti = wReal * imag[odd] + wImag * real[odd];
                        real[odd] = real[even] - tr;
                        imag[odd] = imag[even] - ti;
                        real[even] += tr;
                        imag[even] += ti;
                    }
                }
            }
        }
    }

    private static final int FFT_SIZE = 1024;
    private static final int BINS = FFT_SIZE / 2;

    private static final float SMOOTH_ATTACK = 0.20f;  // 弹起极快
    private static final float SMOOTH_DECAY = 0.03f;  // 优雅回落

    private final float[] ringBuffer = new float[FFT_SIZE];
    private int ringIndex = 0;
    private int newSamples = 0; // 记录是否有新音频数据

    private final float[] fftReal = new float[FFT_SIZE];
    private final float[] fftImag = new float[FFT_SIZE];

    private final float[] smooth = new float[BINS];
    private long lastUpdate = 0;

    public void onAudioFrame(byte[] pcm) {
        for (int i = 0; i < pcm.length - 1; i += 2) {
            short sample = (short) ((pcm[i + 1] << 8) | (pcm[i] & 0xff));
            ringBuffer[ringIndex] = sample / 32768f;
            ringIndex = (ringIndex + 1) % FFT_SIZE;
            newSamples++; // 增加采样计数
        }
    }

    public void update() {
        long now = System.currentTimeMillis();
        if (now - lastUpdate < 8) return;
        lastUpdate = now;

        if (newSamples == 0) {
            for (int i = 0; i < BINS; i++) smooth[i] *= 0.95f;
            for (int i = 0; i < FFT_SIZE; i++) ringBuffer[i] *= 0.5f;
            return;
        }
        newSamples = 0;

        for (int i = 0; i < FFT_SIZE; i++) {
            int actualIndex = (ringIndex + i) % FFT_SIZE;
            float raw = ringBuffer[actualIndex];
            float window = (float) (0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / (FFT_SIZE - 1))));
            fftReal[i] = raw * window;
            fftImag[i] = 0;
        }

        FFT.compute(fftReal, fftImag);

        for (int i = 0; i < BINS; i++) {
            float magnitude = (float) Math.sqrt(fftReal[i] * fftReal[i] + fftImag[i] * fftImag[i]) / BINS;

            magnitude *= (1.0f + i * 0.05f);

            if (Float.isNaN(magnitude)) magnitude = 0f;

            if (magnitude > smooth[i]) smooth[i] += (magnitude - smooth[i]) * SMOOTH_ATTACK;
            else smooth[i] += (magnitude - smooth[i]) * SMOOTH_DECAY;
        }
    }

    public float[] getSpectrum(int n) {
        float[] bars = new float[n];
        int minBin = 1;
        int maxBin = (int) (BINS * 0.6f);

        for (int i = 0; i < n; i++) {
            float r1 = (float) i / n;
            float r2 = (float) (i + 1) / n;

            // 指数分配：低频分得很细，高频压缩在一起，完美还原音乐感
            float curve1 = (float) Math.pow(r1, 1.4);
            float curve2 = (float) Math.pow(r2, 1.4);

            int from = minBin + (int) (curve1 * (maxBin - minBin));
            int to = minBin + (int) (curve2 * (maxBin - minBin));

            if (to <= from) to = from + 1;
            if (to > maxBin) to = maxBin;

            float maxVal = 0f;
            // 使用“取峰值”而不是“取平均值”，让鼓点重音极其清脆锐利
            for (int j = from; j < to; j++) {
                if (smooth[j] > maxVal) maxVal = smooth[j];
            }
            bars[i] = maxVal;
        }
        return bars;
    }
}