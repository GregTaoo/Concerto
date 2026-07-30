package top.gregtao.concerto.core.player.engine;

import net.sourceforge.jaad.SampleBuffer;
import net.sourceforge.jaad.aac.AACException;
import net.sourceforge.jaad.aac.Decoder;
import net.sourceforge.jaad.aac.Receiver;
import net.sourceforge.jaad.mp4.MP4Container;
import net.sourceforge.jaad.mp4.api.AudioTrack;
import net.sourceforge.jaad.mp4.api.Movie;
import net.sourceforge.jaad.mp4.api.Track;
import org.jetbrains.annotations.NotNull;
import top.gregtao.concerto.core.player.source.AudioByteSource;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.List;

/**
 * Decoder for fragmented MP4 AAC streams. DASH audio segments keep their frame
 * table in {@code moof/trun} boxes, which JAAD's {@link Track} API does not
 * expose as {@link Track#readNextFrame()} entries.
 */
final class FragmentedMp4AacDecoderStream extends InputStream {
    private static final int BOX_HEADER_SIZE = 8;
    private static final int TRUN_DATA_OFFSET_PRESENT = 0x000001;
    private static final int TRUN_FIRST_SAMPLE_FLAGS_PRESENT = 0x000004;
    private static final int TRUN_SAMPLE_DURATION_PRESENT = 0x000100;
    private static final int TRUN_SAMPLE_SIZE_PRESENT = 0x000200;
    private static final int TRUN_SAMPLE_FLAGS_PRESENT = 0x000400;
    private static final int TRUN_SAMPLE_COMPOSITION_OFFSET_PRESENT = 0x000800;
    private static final int TFHD_BASE_DATA_OFFSET_PRESENT = 0x000001;
    private static final int TFHD_SAMPLE_DESCRIPTION_INDEX_PRESENT = 0x000002;
    private static final int TFHD_DEFAULT_SAMPLE_DURATION_PRESENT = 0x000008;
    private static final int TFHD_DEFAULT_SAMPLE_SIZE_PRESENT = 0x000010;

    private final AudioByteSource source;
    private final Decoder decoder;
    private final SampleBuffer sampleBuffer = new SampleBuffer();
    private final Receiver receiver = (samples, sampleRate, channels) -> {
        this.sampleBuffer.accept(samples, sampleRate, channels);
        this.receivedFrame = true;
    };
    private final ArrayDeque<FrameRange> frames = new ArrayDeque<>();

    private long scanPosition;
    private long downloadPosition;
    private int sampleRate = -1;
    private int channels = -1;
    private byte[] pcm = new byte[0];
    private int pcmPosition;
    private int pcmLength;
    private boolean receivedFrame;
    private boolean endOfStream;

    FragmentedMp4AacDecoderStream(AudioByteSource source) throws IOException, UnsupportedAudioFileException {
        this.source = source;
        try {
            MP4Container container = new MP4Container(new Mp4AacDecoderStream.ByteSourceMP4InputStream(source));
            Movie movie = container.getMovie();
            if (movie == null) throw new UnsupportedAudioFileException("MP4 file has no movie box");
            List<Track> tracks = movie.getTracks(AudioTrack.AudioCodec.AAC);
            if (tracks.isEmpty() || tracks.get(0).getDecoderSpecificInfo() == null) {
                throw new UnsupportedAudioFileException("MP4 file has no AAC decoder configuration");
            }
            this.decoder = Decoder.create(tracks.get(0).getDecoderSpecificInfo().getData());
        } catch (AACException e) {
            throw new UnsupportedAudioFileException("Cannot open fragmented MP4 AAC stream: " + e.getMessage());
        }
        this.sampleBuffer.setBigEndian(false);
        if (!this.decodeNextFrame()) {
            throw new UnsupportedAudioFileException("Fragmented MP4 AAC track contains no decodable frames");
        }
    }

    int getSampleRate() {
        return this.sampleRate;
    }

    int getChannels() {
        return this.channels;
    }
    long getDownloadPosition() {
        return this.downloadPosition;
    }

    @Override
    public int read() throws IOException {
        byte[] single = new byte[1];
        return this.read(single, 0, 1) == -1 ? -1 : single[0] & 0xFF;
    }

    @Override
    public int read(byte @NotNull [] buffer, int offset, int length) throws IOException {
        if (length == 0) return 0;
        while (this.pcmPosition >= this.pcmLength) {
            if (!this.decodeNextFrame()) return -1;
        }
        int count = Math.min(length, this.pcmLength - this.pcmPosition);
        System.arraycopy(this.pcm, this.pcmPosition, buffer, offset, count);
        this.pcmPosition += count;
        return count;
    }

    @Override
    public void close() {
        this.endOfStream = true;
    }

    private boolean decodeNextFrame() throws IOException {
        while (!this.endOfStream) {
            if (this.frames.isEmpty() && !this.readNextFragment()) {
                this.endOfStream = true;
                return false;
            }
            FrameRange frame = this.frames.removeFirst();
            byte[] encoded = new byte[frame.size];
            this.readFully(frame.position, encoded, 0, encoded.length);
            this.downloadPosition = frame.position + frame.size;
            try {
                this.receivedFrame = false;
                this.decoder.decodeFrame(encoded, this.receiver);
            } catch (AACException ignored) {
                continue;
            }
            if (!this.receivedFrame) continue;
            byte[] data = this.sampleBuffer.getData();
            if (data.length == 0) continue;
            this.sampleRate = this.sampleBuffer.getSampleRate();
            this.channels = this.sampleBuffer.getChannels();
            this.pcm = this.toLittleEndian(data);
            this.pcmPosition = 0;
            this.pcmLength = this.pcm.length;
            return true;
        }
        return false;
    }

    private boolean readNextFragment() throws IOException {
        while (true) {
            Box box = this.readBox(this.scanPosition);
            if (box == null) return false;
            this.scanPosition += box.size;
            if (box.type.equals("moof")) {
                this.readMoof(box);
                if (!this.frames.isEmpty()) return true;
            }
        }
    }

    private Box readBox(long position) throws IOException {
        byte[] header = new byte[BOX_HEADER_SIZE];
        int first = this.source.read(position, header, 0, header.length);
        if (first == -1) return null;
        this.readFully(position, header, first, header.length - first);
        long size = uint32(header, 0);
        if (size == 0) {
            long length = this.source.length();
            if (length < 0) return null;
            size = length - position;
        }
        if (size < BOX_HEADER_SIZE || size > Integer.MAX_VALUE) {
            throw new IOException("Invalid MP4 box size " + size + " at " + position);
        }
        return new Box(position, (int) size, fourcc(header, 4));
    }

    private void readMoof(Box moof) throws IOException {
        byte[] bytes = new byte[moof.size];
        this.readFully(moof.position, bytes, 0, bytes.length);
        int end = bytes.length;
        for (int child = BOX_HEADER_SIZE; child + BOX_HEADER_SIZE <= end; ) {
            int size = boxSize(bytes, child, end);
            if (size == 0) break;
            if (fourcc(bytes, child + 4).equals("traf")) {
                this.readTraf(bytes, child, size, moof.position, moof.size);
            }
            child += size;
        }
    }

    private void readTraf(byte[] bytes, int trafStart, int trafSize, long moofPosition, int moofSize) throws IOException {
        int end = trafStart + trafSize;
        int defaultSampleSize = -1;
        for (int child = trafStart + BOX_HEADER_SIZE; child + BOX_HEADER_SIZE <= end; ) {
            int size = boxSize(bytes, child, end);
            if (size == 0) break;
            if (fourcc(bytes, child + 4).equals("tfhd")) {
                defaultSampleSize = readDefaultSampleSize(bytes, child + BOX_HEADER_SIZE, size - BOX_HEADER_SIZE);
            }
            child += size;
        }
        long dataPosition = moofPosition + moofSize + BOX_HEADER_SIZE;
        for (int child = trafStart + BOX_HEADER_SIZE; child + BOX_HEADER_SIZE <= end; ) {
            int size = boxSize(bytes, child, end);
            if (size == 0) break;
            if (fourcc(bytes, child + 4).equals("trun")) {
                dataPosition = this.readTrun(bytes, child + BOX_HEADER_SIZE, size - BOX_HEADER_SIZE,
                        moofPosition, dataPosition, defaultSampleSize);
            }
            child += size;
        }
    }

    private int readDefaultSampleSize(byte[] bytes, int offset, int length) throws IOException {
        if (length < 8) throw new IOException("Truncated tfhd box");
        int flags = uint24(bytes, offset + 1);
        int cursor = offset + 8;
        if ((flags & TFHD_BASE_DATA_OFFSET_PRESENT) != 0) cursor += 8;
        if ((flags & TFHD_SAMPLE_DESCRIPTION_INDEX_PRESENT) != 0) cursor += 4;
        if ((flags & TFHD_DEFAULT_SAMPLE_DURATION_PRESENT) != 0) cursor += 4;
        if ((flags & TFHD_DEFAULT_SAMPLE_SIZE_PRESENT) == 0 || cursor + 4 > offset + length) return -1;
        return (int) uint32(bytes, cursor);
    }

    private long readTrun(byte[] bytes, int offset, int length, long moofPosition, long dataPosition,
                          int defaultSampleSize) throws IOException {
        if (length < 8) throw new IOException("Truncated trun box");
        int flags = uint24(bytes, offset + 1);
        int sampleCount = (int) uint32(bytes, offset + 4);
        int cursor = offset + 8;
        if ((flags & TRUN_DATA_OFFSET_PRESENT) != 0) {
            if (cursor + 4 > offset + length) throw new IOException("Truncated trun data offset");
            dataPosition = moofPosition + int32(bytes, cursor);
            cursor += 4;
        }
        if ((flags & TRUN_FIRST_SAMPLE_FLAGS_PRESENT) != 0) cursor += 4;
        for (int sample = 0; sample < sampleCount; sample++) {
            if ((flags & TRUN_SAMPLE_DURATION_PRESENT) != 0) cursor += 4;
            int sampleSize = defaultSampleSize;
            if ((flags & TRUN_SAMPLE_SIZE_PRESENT) != 0) {
                if (cursor + 4 > offset + length) throw new IOException("Truncated trun sample size");
                sampleSize = (int) uint32(bytes, cursor);
                cursor += 4;
            }
            if ((flags & TRUN_SAMPLE_FLAGS_PRESENT) != 0) cursor += 4;
            if ((flags & TRUN_SAMPLE_COMPOSITION_OFFSET_PRESENT) != 0) cursor += 4;
            if (sampleSize <= 0 || cursor > offset + length) throw new IOException("Invalid trun sample size");
            this.frames.addLast(new FrameRange(dataPosition, sampleSize));
            dataPosition += sampleSize;
        }
        return dataPosition;
    }

    private void readFully(long position, byte[] bytes, int offset, int length) throws IOException {
        int read = 0;
        while (read < length) {
            int count = this.source.read(position + read, bytes, offset + read, length - read);
            if (count < 0) throw new IOException("Unexpected end of fragmented MP4 stream");
            read += count;
        }
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

    private static int boxSize(byte[] bytes, int offset, int end) throws IOException {
        long size = uint32(bytes, offset);
        if (size < BOX_HEADER_SIZE || size > end - offset) throw new IOException("Invalid nested MP4 box size");
        return (int) size;
    }

    private static long uint32(byte[] bytes, int offset) {
        return ((long) (bytes[offset] & 0xFF) << 24) | ((long) (bytes[offset + 1] & 0xFF) << 16)
                | ((long) (bytes[offset + 2] & 0xFF) << 8) | (bytes[offset + 3] & 0xFFL);
    }

    private static int int32(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) << 24 | (bytes[offset + 1] & 0xFF) << 16
                | (bytes[offset + 2] & 0xFF) << 8 | bytes[offset + 3] & 0xFF;
    }

    private static int uint24(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) << 16 | (bytes[offset + 1] & 0xFF) << 8 | bytes[offset + 2] & 0xFF;
    }

    private static String fourcc(byte[] bytes, int offset) {
        return new String(bytes, offset, 4, java.nio.charset.StandardCharsets.US_ASCII);
    }

    private record Box(long position, int size, String type) {
    }

    private record FrameRange(long position, int size) {
    }
}
