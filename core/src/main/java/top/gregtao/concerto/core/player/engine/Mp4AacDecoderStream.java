package top.gregtao.concerto.core.player.engine;

import net.sourceforge.jaad.SampleBuffer;
import net.sourceforge.jaad.aac.AACException;
import net.sourceforge.jaad.aac.Decoder;
import net.sourceforge.jaad.mp4.MP4Container;
import net.sourceforge.jaad.mp4.MP4InputStream;
import net.sourceforge.jaad.mp4.api.AudioTrack;
import net.sourceforge.jaad.mp4.api.Frame;
import net.sourceforge.jaad.mp4.api.Movie;
import net.sourceforge.jaad.mp4.api.Track;
import org.jetbrains.annotations.NotNull;
import top.gregtao.concerto.core.player.source.AudioByteSource;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Pull-based M4A (MP4/AAC) decoder built on jaad: reads the first AAC audio
 * track and decodes it frame by frame, emitting interleaved signed 16-bit
 * little-endian PCM.
 *
 * The MP4 layer reads through a seekable view over the {@link AudioByteSource},
 * so trailing-moov files work too: over streaming sources the seek to the moov
 * box blocks until that part of the media is buffered (wait-then-play).
 */
public class Mp4AacDecoderStream extends InputStream {

    private final Track track;
    private final Decoder decoder;
    private final SampleBuffer sampleBuffer = new SampleBuffer();

    private int sampleRate = -1;
    private int channels = -1;
    private byte[] pcm = new byte[0];
    private int pcmPos = 0, pcmLen = 0;
    private boolean endOfStream = false;

    public Mp4AacDecoderStream(AudioByteSource source) throws IOException, UnsupportedAudioFileException {
        try {
            MP4Container container = new MP4Container(new ByteSourceMP4InputStream(source));
            Movie movie = container.getMovie();
            if (movie == null) throw new UnsupportedAudioFileException("MP4 file has no movie box");
            List<Track> tracks = movie.getTracks(AudioTrack.AudioCodec.AAC);
            if (tracks.isEmpty()) throw new UnsupportedAudioFileException("MP4 file has no AAC audio track");
            this.track = tracks.get(0);
            if (this.track.getDecoderSpecificInfo() == null) {
                throw new UnsupportedAudioFileException("MP4 AAC track has no decoder specific info");
            }
            this.decoder = Decoder.create(this.track.getDecoderSpecificInfo().getData());
        } catch (AACException e) {
            throw new UnsupportedAudioFileException("Cannot open MP4 AAC stream: " + e.getMessage());
        }
        this.sampleBuffer.setBigEndian(false);
        // Decode the first frame eagerly so the true output format is known
        // (HE-AAC/SBR doubles the rate relative to the track header).
        if (!this.decodeNextFrame()) {
            throw new UnsupportedAudioFileException("MP4 AAC track contains no decodable frames");
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
    public void close() {
        // The AudioByteSource is owned by the playback session, not this stream.
        this.endOfStream = true;
    }

    /** @return false at end of stream */
    private boolean decodeNextFrame() throws IOException {
        while (!this.endOfStream && this.track.hasMoreFrames()) {
            Frame frame = this.track.readNextFrame();
            if (frame == null) break;
            try {
                this.decoder.decodeFrame(frame.getData(), this.sampleBuffer);
            } catch (AACException e) {
                continue; // skip a corrupt frame
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
        this.endOfStream = true;
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

    /**
     * Seekable {@link MP4InputStream} over an {@link AudioByteSource}, mirroring
     * the semantics of jaad's RandomAccessFile-backed implementation. Reads past
     * the buffered part of a streaming source block until the data arrives.
     * Closing does nothing: the playback session owns the source.
     */
    private static class ByteSourceMP4InputStream extends MP4InputStream {
        private final AudioByteSource source;
        private long position = 0;

        ByteSourceMP4InputStream(AudioByteSource source) {
            this.source = source;
        }

        @Override
        public int read() throws IOException {
            byte[] single = new byte[1];
            int read = this.read(single, 0, 1);
            return read == -1 ? -1 : (single[0] & 0xFF);
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = this.source.read(this.position, buffer, offset, length);
            if (read > 0) this.position += read;
            return read;
        }

        @Override
        public long skip(long count) {
            if (count <= 0) return 0;
            long total = this.source.length();
            long skipped = total >= 0 ? Math.min(count, Math.max(0, total - this.position)) : count;
            this.position += skipped;
            return skipped;
        }

        @Override
        public long getOffset() {
            return this.position;
        }

        @Override
        public void seek(long position) {
            this.position = position;
        }

        @Override
        public boolean seekSupported() {
            return true;
        }

        @Override
        public boolean hasLeft() {
            long total = this.source.length();
            return total < 0 || this.position < total - 1;
        }

        @Override
        public int available() {
            long remaining = this.source.availableTo() - this.position;
            return remaining <= 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, remaining);
        }

        @Override
        public void close() {
            // Owned by the session; never close the source from the MP4 layer.
        }
    }
}
