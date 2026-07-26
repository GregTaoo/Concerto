package top.gregtao.concerto.core.player.engine;

import io.github.jaredmdobson.concentus.OpusDecoder;
import io.github.jaredmdobson.concentus.OpusException;
import org.jetbrains.annotations.NotNull;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;

/**
 * Pull-based Ogg Opus decoder: parses Ogg pages, reassembles Opus packets
 * (including 255-byte lacing continuations across pages) and decodes them with
 * Concentus, emitting interleaved signed 16-bit little-endian PCM at 48 kHz.
 *
 * No decoder thread is needed: Concentus is synchronous, so every read pulls
 * exactly as much compressed data as required.
 */
public class OpusDecoderStream extends InputStream {

    /** 120 ms at 48 kHz, the longest frame Opus allows (per channel). */
    private static final int MAX_FRAME_SAMPLES = 5760;

    private final InputStream in;
    private final OpusDecoder decoder;
    private final int channels;
    private final short[] decodeBuffer;

    private final ArrayDeque<byte[]> packets = new ArrayDeque<>();
    private ByteArrayOutputStream partialPacket = null;
    /** A fresh mid-stream page may open with the tail of a packet we never saw. */
    private boolean dropContinuedTail = false;

    private byte[] pcm = new byte[0];
    private int pcmPos = 0, pcmLen = 0;
    private long discardSamples;
    private int headerPacketsToSkip = 1; // the OpusTags packet after OpusHead
    private boolean endOfStream = false;

    /**
     * @param applyPreSkip discard the encoder pre-skip samples; only correct when
     *                     decoding from the start of the media (mid-stream reopens
     *                     land inside the audio, where pre-skip no longer applies)
     */
    public OpusDecoderStream(InputStream in, boolean applyPreSkip) throws IOException, UnsupportedAudioFileException {
        this.in = in;
        byte[] head = this.nextPacketRaw();
        if (head == null || head.length < 19
                || !"OpusHead".equals(new String(head, 0, 8, java.nio.charset.StandardCharsets.US_ASCII))) {
            throw new UnsupportedAudioFileException("Missing OpusHead packet");
        }
        this.channels = head[9] & 0xFF;
        if (this.channels < 1 || this.channels > 2) {
            throw new UnsupportedAudioFileException("Unsupported Opus channel count: " + this.channels);
        }
        int preSkip = (head[10] & 0xFF) | ((head[11] & 0xFF) << 8);
        this.discardSamples = applyPreSkip ? preSkip : 0;
        this.decodeBuffer = new short[MAX_FRAME_SAMPLES * this.channels];
        try {
            this.decoder = new OpusDecoder(48000, this.channels);
        } catch (OpusException e) {
            throw new IOException("Cannot create Opus decoder", e);
        }
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
            if (!this.decodeNextPacket()) return -1;
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

    /** Decodes the next audio packet into {@link #pcm}. @return false at end of stream. */
    private boolean decodeNextPacket() throws IOException {
        while (true) {
            byte[] packet = this.nextPacketRaw();
            if (packet == null) return false;
            if (this.headerPacketsToSkip > 0) {
                this.headerPacketsToSkip--;
                if (packet.length >= 8 && "OpusTags".equals(
                        new String(packet, 0, 8, java.nio.charset.StandardCharsets.US_ASCII))) {
                    continue;
                }
                // Not the mandated OpusTags: be lenient and treat it as audio.
            }
            if (packet.length == 0) continue;
            int samples;
            try {
                samples = this.decoder.decode(packet, 0, packet.length, this.decodeBuffer, 0, MAX_FRAME_SAMPLES, false);
            } catch (OpusException e) {
                throw new IOException("Opus decode failed", e);
            }
            int start = 0;
            if (this.discardSamples > 0) {
                start = (int) Math.min(this.discardSamples, samples);
                this.discardSamples -= start;
            }
            int outSamples = samples - start;
            if (outSamples <= 0) continue;
            int byteCount = outSamples * this.channels * 2;
            if (this.pcm.length < byteCount) this.pcm = new byte[byteCount];
            int outIndex = 0;
            for (int i = start * this.channels; i < samples * this.channels; i++) {
                short value = this.decodeBuffer[i];
                this.pcm[outIndex++] = (byte) value;
                this.pcm[outIndex++] = (byte) (value >> 8);
            }
            this.pcmPos = 0;
            this.pcmLen = byteCount;
            return true;
        }
    }

    /** Next reassembled Opus packet from the Ogg layer, or null at end of stream. */
    private byte[] nextPacketRaw() throws IOException {
        while (this.packets.isEmpty()) {
            if (this.endOfStream || !this.readPage()) return null;
        }
        return this.packets.poll();
    }

    /** Reads one Ogg page, appending completed packets. @return false at EOF. */
    private boolean readPage() throws IOException {
        byte[] header = new byte[27];
        int got = this.readUpTo(header, 27);
        if (got == 0) {
            this.endOfStream = true;
            return false;
        }
        if (got < 27 || header[0] != 'O' || header[1] != 'g' || header[2] != 'g' || header[3] != 'S') {
            throw new IOException("Lost Ogg page sync");
        }
        boolean continued = (header[5] & 0x01) != 0;
        int segmentCount = header[26] & 0xFF;
        byte[] segmentTable = new byte[segmentCount];
        this.readFully(segmentTable, segmentCount);

        if (continued && this.partialPacket == null) {
            // Mid-stream open onto a page whose first packet started earlier:
            // its segments must be dropped up to the first packet boundary.
            this.dropContinuedTail = true;
        } else if (!continued && this.partialPacket != null) {
            this.partialPacket = null; // truncated packet (should not happen in well-formed files)
        }

        for (int i = 0; i < segmentCount; i++) {
            int lacing = segmentTable[i] & 0xFF;
            byte[] segment = new byte[lacing];
            this.readFully(segment, lacing);
            if (this.dropContinuedTail) {
                if (lacing < 255) this.dropContinuedTail = false;
                continue;
            }
            if (this.partialPacket == null) this.partialPacket = new ByteArrayOutputStream();
            this.partialPacket.write(segment);
            if (lacing < 255) {
                this.packets.add(this.partialPacket.toByteArray());
                this.partialPacket = null;
            }
        }
        return true;
    }

    private void readFully(byte[] buffer, int length) throws IOException {
        if (this.readUpTo(buffer, length) < length) throw new EOFException("Truncated Ogg page");
    }

    private int readUpTo(byte[] buffer, int length) throws IOException {
        int total = 0;
        while (total < length) {
            int read = this.in.read(buffer, total, length - total);
            if (read == -1) break;
            total += read;
        }
        return total;
    }
}
