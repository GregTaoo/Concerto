package top.gregtao.concerto.core.player.engine;

import top.gregtao.concerto.core.player.source.AudioByteSource;
import top.gregtao.concerto.core.player.source.ByteSourceInputStream;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.logging.Logger;

/**
 * Opens a decoded 16-bit PCM stream over an {@link AudioByteSource}, optionally
 * starting mid-media at a container-safe byte offset (with the format's header
 * bytes prepended so the SPI decoder accepts the stream).
 */
public final class DecoderFactory {

    private DecoderFactory() {
    }

    public static class DecodedStream {
        public final AudioInputStream pcmStream;
        public final AudioFormat pcmFormat;
        public final ByteSourceInputStream rawStream;

        DecodedStream(AudioInputStream pcmStream, AudioFormat pcmFormat, ByteSourceInputStream rawStream) {
            this.pcmStream = pcmStream;
            this.pcmFormat = pcmFormat;
            this.rawStream = rawStream;
        }

        public void close() {
            try {
                this.pcmStream.close();
            } catch (IOException ignored) {
            }
        }
    }

    public static DecodedStream open(AudioByteSource source, long byteOffset, byte[] prefixBytes,
                                     top.gregtao.concerto.core.player.seek.ContainerFormat format, Logger logger)
            throws IOException, UnsupportedAudioFileException {
        ByteSourceInputStream raw = new ByteSourceInputStream(source, byteOffset);
        InputStream input = (byteOffset > 0 && prefixBytes != null)
                ? new SequenceInputStream(new ByteArrayInputStream(prefixBytes), raw)
                : raw;

        // FLAC bypasses the SPI entirely: the SPI-returned stream is length-capped
        // by totalSamples * frameSize, which truncates larger files mid-track, and
        // its probe cannot handle the prefixed mid-stream form at all.
        if (format == top.gregtao.concerto.core.player.seek.ContainerFormat.FLAC) {
            return openFlac(source, byteOffset, prefixBytes, input, raw, logger);
        }

        // The SPI probe needs mark/reset support
        BufferedInputStream buffered = new BufferedInputStream(input, 128 * 1024);

        AudioInputStream encoded = AudioSystem.getAudioInputStream(buffered);
        AudioFormat sourceFormat = encoded.getFormat();

        // Always decode to 16-bit signed PCM: both sinks handle it and it avoids
        // the signed/unsigned mismatch of 8-bit formats between JavaSound and AL.
        int sampleSizeInBits = 16;
        AudioFormat targetFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sourceFormat.getSampleRate(),
                sampleSizeInBits,
                sourceFormat.getChannels(),
                sampleSizeInBits / 8 * sourceFormat.getChannels(),
                sourceFormat.getSampleRate(),
                false
        );

        AudioInputStream pcm = AudioSystem.getAudioInputStream(targetFormat, encoded);
        return new DecodedStream(pcm, targetFormat, raw);
    }

    private static DecodedStream openFlac(AudioByteSource source, long byteOffset, byte[] prefixBytes,
                                          InputStream input, ByteSourceInputStream raw, Logger logger)
            throws IOException, UnsupportedAudioFileException {
        byte[] header = prefixBytes;
        if (byteOffset > 0 && header == null) {
            throw new UnsupportedAudioFileException("Mid-stream FLAC open requires header bytes");
        }
        if (header == null) {
            header = readFlacHeader(source);
        }
        int[] info = parseFlacStreamInfo(header);
        int sampleRate = info[0], channels = info[1], bitsPerSample = info[2];
        AudioFormat targetFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sampleRate, 16, channels, 2 * channels, sampleRate, false);
        AudioInputStream pcm = new AudioInputStream(
                new FlacDecoderStream(input, targetFormat, bitsPerSample, logger),
                targetFormat, AudioSystem.NOT_SPECIFIED);
        return new DecodedStream(pcm, targetFormat, raw);
    }

    /** Reads the marker + metadata blocks up to and including STREAMINFO. */
    private static byte[] readFlacHeader(AudioByteSource source) throws IOException {
        byte[] head = new byte[4 + 4 + 38];
        int total = 0;
        while (total < head.length) {
            int read = source.read(total, head, total, head.length - total);
            if (read == -1) break;
            total += read;
        }
        return head;
    }

    /** @return {sampleRate, channels, bitsPerSample} from a fLaC header block */
    private static int[] parseFlacStreamInfo(byte[] header) throws UnsupportedAudioFileException {
        if (header.length < 8 || header[0] != 'f' || header[1] != 'L' || header[2] != 'a' || header[3] != 'C') {
            throw new UnsupportedAudioFileException("Not a FLAC header");
        }
        int position = 4;
        while (position + 4 <= header.length) {
            int type = header[position] & 0x7F;
            int length = ((header[position + 1] & 0xFF) << 16) | ((header[position + 2] & 0xFF) << 8) | (header[position + 3] & 0xFF);
            position += 4;
            if (type == 0 && position + 18 <= header.length) {
                // bytes 10-12 of STREAMINFO: 20-bit rate, 3-bit channels-1, 5-bit bits-1
                int sampleRate = ((header[position + 10] & 0xFF) << 12) | ((header[position + 11] & 0xFF) << 4)
                        | ((header[position + 12] & 0xF0) >> 4);
                int channels = ((header[position + 12] & 0x0E) >> 1) + 1;
                int bitsPerSample = (((header[position + 12] & 0x01) << 4) | ((header[position + 13] & 0xF0) >> 4)) + 1;
                if (sampleRate <= 0) throw new UnsupportedAudioFileException("Bad FLAC STREAMINFO");
                return new int[]{sampleRate, channels, bitsPerSample};
            }
            position += length;
        }
        throw new UnsupportedAudioFileException("FLAC STREAMINFO not found in header");
    }
}
