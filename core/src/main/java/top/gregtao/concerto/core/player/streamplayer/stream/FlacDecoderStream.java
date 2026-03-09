package top.gregtao.concerto.core.player.streamplayer.stream;

import org.jetbrains.annotations.NotNull;
import org.kc7bfi.jflac.FLACDecoder;
import org.kc7bfi.jflac.PCMProcessor;
import org.kc7bfi.jflac.metadata.StreamInfo;
import org.kc7bfi.jflac.util.ByteData;

import javax.sound.sampled.AudioFormat;
import java.io.*;
import java.util.logging.Logger;

public class FlacDecoderStream extends InputStream {
    public final InputStream stream;
    public final AudioFormat format;
    public final int bit;

    private final PipedInputStream pipedInputStream;
    private final PipedOutputStream pipedOutputStream;

    private final FLACDecoder decoder;
    private final Thread decoderThread;

    public FlacDecoderStream(InputStream inputStream, AudioFormat targetFormat, int bit, Logger logger) throws IOException {
        this.stream = inputStream;
        this.format = targetFormat;
        this.bit = bit;
        this.pipedInputStream = new PipedInputStream();
        this.pipedOutputStream = new PipedOutputStream(this.pipedInputStream);
        this.decoder = new FLACDecoder(this.stream);
        PCMProcessor pcmProcessor = new FlacBitCollapser(this.pipedOutputStream, this.bit);
        this.decoder.addPCMProcessor(pcmProcessor);
        this.decoderThread = new Thread(() -> {
            try {
                this.decoder.decode();  // 执行解码操作
                logger.info(() -> "Decoding FLAC complete.");
            } catch (IOException e) {
                logger.warning(() -> "Error during decoding FLAC: " + e.getMessage());
            } finally {
                this.close();
            }
        });
        this.decoderThread.start();
    }

    @Override
    public int read() throws IOException {
        return this.pipedInputStream.read();
    }

    @Override
    public int read(byte @NotNull [] b, int off, int len) throws IOException {
        return this.pipedInputStream.read(b, off, len);
    }

    @Override
    public byte @NotNull [] readAllBytes() throws IOException {
        return this.pipedInputStream.readAllBytes();
    }

    @Override
    public byte @NotNull [] readNBytes(int len) throws IOException {
        return this.pipedInputStream.readNBytes(len);
    }

    @Override
    public int readNBytes(byte[] b, int off, int len) throws IOException {
        return this.pipedInputStream.readNBytes(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
        return this.pipedInputStream.skip(n);
    }

    @Override
    public void skipNBytes(long n) throws IOException {
        this.pipedInputStream.skipNBytes(n);
    }

    @Override
    public void mark(int readLimit) {
        this.pipedInputStream.mark(readLimit);
    }

    @Override
    public void reset() throws IOException {
        this.pipedInputStream.reset();
    }

    @Override
    public boolean markSupported() {
        return this.pipedInputStream.markSupported();
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
            super.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public long transferTo(OutputStream out) throws IOException {
        return this.pipedInputStream.transferTo(out);
    }

    private static class FlacBitCollapser implements PCMProcessor {
        private final OutputStream outputStream;
        private final int bit;

        public FlacBitCollapser(OutputStream outputStream, int bit) {
            this.outputStream = outputStream;
            this.bit = bit;
        }

        @Override
        public void processStreamInfo(StreamInfo streamInfo) {
            // Process stream info if needed
        }

        @Override
        public void processPCM(ByteData byteData) {
            try {
                if (this.bit == 24) {
                    // Get the PCM data
                    byte[] pcmData = byteData.getData();
                    int len = byteData.getLen();

                    // Convert 24-bit PCM to 16-bit PCM
                    byte[] bytes = new byte[(len / 3) * 2];
                    int k = 0;
                    for (int i = 0; i < len; i += 3) {
                        int sample = ((pcmData[i + 2] & 0xFF) << 16) | ((pcmData[i + 1] & 0xFF) << 8) | (pcmData[i] & 0xFF);
                        short sample16 = (short) (sample >> 8); // Drop the lowest 8 bits
                        bytes[k++] = (byte) (sample16 & 0xFF);
                        bytes[k++] = (byte) ((sample16 >> 8) & 0xFF);
                    }
                    outputStream.write(bytes, 0, bytes.length);
                } else {
                    outputStream.write(byteData.getData(), 0, byteData.getLen());
                }
            } catch (IOException ignored) {}
        }
    }
}
