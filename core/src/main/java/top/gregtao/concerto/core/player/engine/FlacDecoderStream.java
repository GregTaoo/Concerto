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
 * samples through a pipe, collapsing 24-bit samples to 16-bit on the way.
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
        decoder.addPCMProcessor(new FlacBitCollapser(this.pipedOutputStream, bit));
        this.decoderThread = new Thread(() -> {
            try {
                decoder.decode();
                logger.info(() -> "Decoding FLAC complete.");
            } catch (IOException e) {
                logger.warning(() -> "Error during decoding FLAC: " + e.getMessage());
            } finally {
                this.close();
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

    private static class FlacBitCollapser implements PCMProcessor {
        private final PipedOutputStream outputStream;
        private final int bit;

        public FlacBitCollapser(PipedOutputStream outputStream, int bit) {
            this.outputStream = outputStream;
            this.bit = bit;
        }

        @Override
        public void processStreamInfo(StreamInfo streamInfo) {
        }

        @Override
        public void processPCM(ByteData byteData) {
            try {
                if (this.bit == 24) {
                    byte[] pcmData = byteData.getData();
                    int len = byteData.getLen();
                    // Convert 24-bit little-endian PCM to 16-bit by dropping the low byte
                    byte[] bytes = new byte[(len / 3) * 2];
                    int k = 0;
                    for (int i = 0; i + 2 < len; i += 3) {
                        int sample = ((pcmData[i + 2] & 0xFF) << 16) | ((pcmData[i + 1] & 0xFF) << 8) | (pcmData[i] & 0xFF);
                        short sample16 = (short) (sample >> 8);
                        bytes[k++] = (byte) (sample16 & 0xFF);
                        bytes[k++] = (byte) ((sample16 >> 8) & 0xFF);
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
