package top.gregtao.concerto.core.player.engine;

import javax.sound.sampled.AudioFormat;

final class PcmSampleConverter {

    private PcmSampleConverter() {
    }

    static boolean isFloat32(AudioFormat format) {
        return AudioFormat.Encoding.PCM_FLOAT.equals(format.getEncoding())
                && format.getSampleSizeInBits() == 32 && !format.isBigEndian();
    }

    static int float32ToSignedPcm(byte[] input, int offset, int length, byte[] output, int targetBits) {
        if (targetBits != 16 && targetBits != 24) {
            throw new IllegalArgumentException("Only 16-bit and 24-bit PCM targets are supported");
        }
        int bytesPerOutputSample = targetBits / 8;
        int samples = length / Float.BYTES;
        int outputIndex = 0;
        double scale = Math.scalb(1.0, targetBits - 1);
        int min = -(1 << (targetBits - 1));
        int max = (1 << (targetBits - 1)) - 1;
        for (int i = 0; i < samples; i++) {
            int inputIndex = offset + i * Float.BYTES;
            int bits = (input[inputIndex] & 0xFF)
                    | ((input[inputIndex + 1] & 0xFF) << 8)
                    | ((input[inputIndex + 2] & 0xFF) << 16)
                    | ((input[inputIndex + 3] & 0xFF) << 24);
            float sample = Float.intBitsToFloat(bits);
            int quantized;
            if (!Float.isFinite(sample)) {
                quantized = 0;
            } else if (sample <= -1.0f) {
                quantized = min;
            } else if (sample >= 1.0f) {
                quantized = max;
            } else {
                quantized = (int) Math.round(sample * scale);
                quantized = Math.max(min, Math.min(max, quantized));
            }
            for (int byteIndex = 0; byteIndex < bytesPerOutputSample; byteIndex++) {
                output[outputIndex++] = (byte) (quantized >> (byteIndex * 8));
            }
        }
        return outputIndex;
    }
}
