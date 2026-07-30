package top.gregtao.concerto.core.player.engine;

import org.jetbrains.annotations.NotNull;
import org.kc7bfi.jflac.FLACDecoder;
import org.kc7bfi.jflac.PCMProcessor;
import org.kc7bfi.jflac.metadata.StreamInfo;
import org.kc7bfi.jflac.util.ByteData;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.logging.Logger;

/**
 * Bridges jFLAC to an {@link InputStream} of PCM: a decoder thread pumps decoded
 * samples through a pipe. High-resolution integer samples are converted to
 * float32 without losing their effective precision.
 */
public class FlacDecoderStream extends InputStream {

    private final InputStream stream;
    private final PipedInputStream pipedInputStream;
    private final PipedOutputStream pipedOutputStream;
    private final Thread decoderThread;

    public FlacDecoderStream(InputStream inputStream, AudioFormat targetFormat, int bit, Logger logger) throws IOException {
        this.stream = inputStream;
        this.pipedInputStream = new PipedInputStream(64 * 1024);
        this.pipedOutputStream = new PipedOutputStream(this.pipedInputStream);
        FLACDecoder decoder = new FLACDecoder(this.stream);
        decoder.addPCMProcessor(new FlacPcmProcessor(this.pipedOutputStream, bit,
                PcmSampleConverter.isFloat32(targetFormat)));
        this.decoderThread = new Thread(() -> {
            try {
                decoder.decode();
                logger.info(() -> "Decoding FLAC complete.");
            } catch (IOException e) {
                logger.warning(() -> "Error during decoding FLAC: " + e);
            } finally {
                // Close only the write end: the reader must still drain the PCM
                // buffered in the pipe before it sees EOF.
                try {
                    this.pipedOutputStream.close();
                } catch (IOException ignored) {
                }
                try {
                    this.stream.close();
                } catch (IOException ignored) {
                }
            }
        }, "Concerto-FLAC-Decoder");
        this.decoderThread.setDaemon(true);
        this.decoderThread.start();
    }

    @Override
    public int read() throws IOException {
        return this.pipedInputStream.read();
    }

    @Override
    public int read(byte @NotNull [] buffer, int offset, int length) throws IOException {
        return this.pipedInputStream.read(buffer, offset, length);
    }

    @Override
    public int available() throws IOException {
        return this.pipedInputStream.available();
    }

    @Override
    public void close() {
        try {
            this.decoderThread.interrupt();
            this.pipedOutputStream.close();
            this.pipedInputStream.close();
            this.stream.close();
        } catch (IOException ignored) {
        }
    }

    private static class FlacPcmProcessor implements PCMProcessor {
        private final PipedOutputStream outputStream;
        private final int bitsPerSample;
        private final boolean floatOutput;

        public FlacPcmProcessor(PipedOutputStream outputStream, int bitsPerSample, boolean floatOutput) {
            this.outputStream = outputStream;
            this.bitsPerSample = bitsPerSample;
            this.floatOutput = floatOutput;
        }

        @Override
        public void processStreamInfo(StreamInfo streamInfo) {
        }

        @Override
        public void processPCM(ByteData byteData) {
            try {
                if (this.floatOutput) {
                    byte[] pcmData = byteData.getData();
                    int len = byteData.getLen();
                    int bytesPerSample = (this.bitsPerSample + 7) / 8;
                    byte[] bytes = new byte[(len / bytesPerSample) * Float.BYTES];
                    int k = 0;
                    int valueMask = (1 << this.bitsPerSample) - 1;
                    int signBit = 1 << (this.bitsPerSample - 1);
                    float scale = (float) Math.scalb(1.0, this.bitsPerSample - 1);
                    for (int i = 0; i + bytesPerSample <= len; i += bytesPerSample) {
                        int sample = 0;
                        for (int byteIndex = 0; byteIndex < bytesPerSample; byteIndex++) {
                            sample |= (pcmData[i + byteIndex] & 0xFF) << (byteIndex * 8);
                        }
                        sample &= valueMask;
                        if ((sample & signBit) != 0) sample -= 1 << this.bitsPerSample;
                        int floatBits = Float.floatToRawIntBits(sample / scale);
                        bytes[k++] = (byte) floatBits;
                        bytes[k++] = (byte) (floatBits >> 8);
                        bytes[k++] = (byte) (floatBits >> 16);
                        bytes[k++] = (byte) (floatBits >> 24);
                    }
                    this.outputStream.write(bytes, 0, bytes.length);
                } else {
                    this.outputStream.write(byteData.getData(), 0, byteData.getLen());
                }
            } catch (IOException ignored) {
            }
        }
    }
}
