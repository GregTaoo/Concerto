package top.gregtao.concerto.core.player.seek;

import top.gregtao.concerto.core.player.source.AudioByteSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Reads the FLAC metadata region: STREAMINFO provides the duration and becomes
 * the decode prefix, SEEKTABLE entries (when present) become seek points. Files
 * without a SEEKTABLE get a single point at the first frame; the engine then
 * decodes-and-discards from there, which FLAC decodes fast enough for.
 */
public class FlacIndexBuilder implements SeekIndexBuilder {

    @Override
    public void build(AudioByteSource source, SeekIndex index) throws IOException {
        ScanInput in = new ScanInput(source, 0);
        byte[] magic = new byte[4];
        if (in.readUpTo(magic, 0, 4) < 4 || magic[0] != 'f' || magic[1] != 'L' || magic[2] != 'a' || magic[3] != 'C') {
            return;
        }

        int sampleRate = -1;
        long totalSamples = -1;
        byte[] streamInfoBody = null;
        long[][] seekTable = null; // [sampleNumber, byteOffset]

        boolean last = false;
        while (!last) {
            int blockHeader = in.readU8();
            last = (blockHeader & 0x80) != 0;
            int type = blockHeader & 0x7F;
            int length = (in.readU8() << 16) | (in.readU8() << 8) | in.readU8();
            if (type == 0 && length >= 34) {
                streamInfoBody = new byte[length];
                in.readFully(streamInfoBody, 0, length);
                // bytes 10-17: 20-bit rate, 3-bit channels-1, 5-bit bits-1, 36-bit total samples
                sampleRate = ((streamInfoBody[10] & 0xFF) << 12) | ((streamInfoBody[11] & 0xFF) << 4)
                        | ((streamInfoBody[12] & 0xF0) >> 4);
                totalSamples = ((long) (streamInfoBody[13] & 0x0F) << 32)
                        | ((streamInfoBody[14] & 0xFFL) << 24) | ((streamInfoBody[15] & 0xFFL) << 16)
                        | ((streamInfoBody[16] & 0xFFL) << 8) | (streamInfoBody[17] & 0xFFL);
            } else if (type == 3) {
                int entries = length / 18;
                seekTable = new long[entries][2];
                int used = 0;
                for (int i = 0; i < entries; i++) {
                    long sampleNumber = in.readU64BE();
                    long byteOffset = in.readU64BE();
                    in.readU16BE(); // samples in target frame
                    if (sampleNumber != -1L) { // skip placeholder points
                        seekTable[used][0] = sampleNumber;
                        seekTable[used][1] = byteOffset;
                        used++;
                    }
                }
                if (used < entries) {
                    long[][] trimmed = new long[used][2];
                    System.arraycopy(seekTable, 0, trimmed, 0, used);
                    seekTable = trimmed;
                }
                in.skipFully(length % 18);
            } else {
                in.skipFully(length);
            }
        }

        if (sampleRate <= 0 || streamInfoBody == null) return;
        long firstFrameOffset = in.position();

        ByteArrayOutputStream prefix = new ByteArrayOutputStream();
        prefix.write('f'); prefix.write('L'); prefix.write('a'); prefix.write('C');
        prefix.write(0x80); // STREAMINFO marked as the last metadata block
        prefix.write((streamInfoBody.length >> 16) & 0xFF);
        prefix.write((streamInfoBody.length >> 8) & 0xFF);
        prefix.write(streamInfoBody.length & 0xFF);
        prefix.write(streamInfoBody, 0, streamInfoBody.length);
        index.setPrefixBytes(prefix.toByteArray());

        index.append(0, firstFrameOffset);
        if (seekTable != null) {
            for (long[] entry : seekTable) {
                index.append(entry[0] * 1000L / sampleRate, firstFrameOffset + entry[1]);
            }
        }
        long durationMillis = totalSamples > 0 ? totalSamples * 1000L / sampleRate : -1;
        if (durationMillis > 0) {
            index.setDurationMillis(durationMillis);
            index.setCoveredToMillis(durationMillis);
        }
        index.markComplete();
    }
}
