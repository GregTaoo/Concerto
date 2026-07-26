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

    public static DecodedStream open(AudioByteSource source, long byteOffset, byte[] prefixBytes, Logger logger)
            throws IOException, UnsupportedAudioFileException {
        ByteSourceInputStream raw = new ByteSourceInputStream(source, byteOffset);
        InputStream input = (byteOffset > 0 && prefixBytes != null)
                ? new SequenceInputStream(new ByteArrayInputStream(prefixBytes), raw)
                : raw;
        // The SPI probe needs mark/reset support
        BufferedInputStream buffered = new BufferedInputStream(input, 128 * 1024);

        AudioInputStream encoded = AudioSystem.getAudioInputStream(buffered);
        AudioFormat sourceFormat = encoded.getFormat();

        // Always decode to 16-bit signed PCM: both sinks handle it and it avoids
        // the signed/unsigned mismatch of 8-bit formats between JavaSound and AL.
        int bitBackup = sourceFormat.getSampleSizeInBits();
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

        AudioInputStream pcm;
        if (sourceFormat.toString().toLowerCase().startsWith("flac")) {
            pcm = new AudioInputStream(new FlacDecoderStream(encoded, targetFormat, bitBackup, logger),
                    targetFormat, AudioSystem.NOT_SPECIFIED);
        } else {
            pcm = AudioSystem.getAudioInputStream(targetFormat, encoded);
        }
        return new DecodedStream(pcm, targetFormat, raw);
    }
}
