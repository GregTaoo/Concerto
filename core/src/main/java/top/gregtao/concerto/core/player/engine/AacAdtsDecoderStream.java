package top.gregtao.concerto.core.player.engine;

import net.sourceforge.jaad.SampleBuffer;
import net.sourceforge.jaad.aac.AACException;
import net.sourceforge.jaad.aac.Decoder;
import net.sourceforge.jaad.adts.ADTSDemultiplexer;
import org.jetbrains.annotations.NotNull;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Pull-based ADTS AAC decoder built on jaad: walks ADTS frames and decodes them
 * one by one, emitting interleaved signed 16-bit little-endian PCM.
 *
 * The output rate/channel count are taken from the first decoded frame, not the
 * ADTS header: HE-AAC (SBR) doubles the sample rate relative to the header.
 */
public class AacAdtsDecoderStream extends InputStream {

    private final InputStream in;
    private final ADTSDemultiplexer demultiplexer;
    private final Decoder decoder;
    private final SampleBuffer sampleBuffer = new SampleBuffer();

    private int sampleRate = -1;
    private int channels = -1;
    private byte[] pcm = new byte[0];
    private int pcmPos = 0, pcmLen = 0;
    private boolean endOfStream = false;

    public AacAdtsDecoderStream(InputStream in) throws IOException, UnsupportedAudioFileException {
        this.in = in;
        this.sampleBuffer.setBigEndian(false);
        try {
            this.demultiplexer = new ADTSDemultiplexer(in);
            this.decoder = Decoder.create(this.demultiplexer.getDecoderInfo());
        } catch (AACException e) {
            throw new UnsupportedAudioFileException("Cannot open ADTS AAC stream: " + e.getMessage());
        }
        // Decode the first frame eagerly so the true output format is known.
        if (!this.decodeNextFrame()) {
            throw new UnsupportedAudioFileException("ADTS stream contains no decodable frames");
        }
        if (this.channels < 1 || this.channels > 2) {
            throw new UnsupportedAudioFileException("Unsupported AAC channel count: " + this.channels);
        }
    }

    public int getSampleRate() {
        return this.sampleRate;
    }

    public int getChannels() {
        return this.channels;
    }

    @Override
    public int read() throws IOException {
        byte[] single = new byte[1];
        int read = this.read(single, 0, 1);
        return read == -1 ? -1 : (single[0] & 0xFF);
    }

    @Override
    public int read(byte @NotNull [] buffer, int offset, int length) throws IOException {
        if (length == 0) return 0;
        while (this.pcmPos >= this.pcmLen) {
            if (!this.decodeNextFrame()) return -1;
        }
        int count = Math.min(length, this.pcmLen - this.pcmPos);
        System.arraycopy(this.pcm, this.pcmPos, buffer, offset, count);
        this.pcmPos += count;
        return count;
    }

    @Override
    public void close() throws IOException {
        this.endOfStream = true;
        this.in.close();
    }

    /** @return false at end of stream */
    private boolean decodeNextFrame() throws IOException {
        while (!this.endOfStream) {
            byte[] frame;
            try {
                frame = this.demultiplexer.readNextFrame();
            } catch (EOFException e) {
                this.endOfStream = true;
                return false;
            }
            try {
                this.decoder.decodeFrame(frame, this.sampleBuffer);
            } catch (AACException e) {
                continue; // skip a corrupt frame; ADTS self-syncs on the next one
            }
            byte[] data = this.sampleBuffer.getData();
            if (data.length == 0) continue;
            this.sampleRate = this.sampleBuffer.getSampleRate();
            this.channels = this.sampleBuffer.getChannels();
            this.pcm = this.toLittleEndian(data);
            this.pcmPos = 0;
            this.pcmLen = this.pcm.length;
            return true;
        }
        return false;
    }

    private byte[] toLittleEndian(byte[] data) {
        if (!this.sampleBuffer.isBigEndian()) return data;
        for (int i = 0; i + 1 < data.length; i += 2) {
            byte high = data[i];
            data[i] = data[i + 1];
            data[i + 1] = high;
        }
        return data;
    }
}
