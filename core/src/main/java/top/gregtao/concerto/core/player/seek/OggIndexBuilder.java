package top.gregtao.concerto.core.player.seek;

import top.gregtao.concerto.core.player.source.AudioByteSource;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Scans Ogg pages, using granule positions as the timeline. The header pages
 * (identification/comment/setup) are captured as prefix bytes so a decoder can
 * be opened mid-stream.
 */
public class OggIndexBuilder implements SeekIndexBuilder {

    @Override
    public void build(AudioByteSource source, SeekIndex index) throws IOException {
        ScanInput in = new ScanInput(source, 0);
        ByteArrayOutputStream prefix = new ByteArrayOutputStream();
        boolean prefixDone = false;
        int sampleRate = -1;
        long prevGranule = 0;
        long nextPointAt = 0;

        try {
            while (true) {
                long pageStart = in.position();
                byte[] header = new byte[27];
                if (in.readUpTo(header, 0, 27) < 27) break;
                if (header[0] != 'O' || header[1] != 'g' || header[2] != 'g' || header[3] != 'S') {
                    // Lost sync: this indexer only handles well-formed files.
                    return;
                }
                long granule = u64LE(header, 6);
                int segmentCount = header[26] & 0xFF;
                byte[] segmentTable = new byte[segmentCount];
                in.readFully(segmentTable, 0, segmentCount);
                int bodyLength = 0;
                for (byte segment : segmentTable) bodyLength += segment & 0xFF;

                if (!prefixDone && granule <= 0) {
                    byte[] body = new byte[bodyLength];
                    in.readFully(body, 0, bodyLength);
                    prefix.write(header);
                    prefix.write(segmentTable);
                    prefix.write(body);
                    if (sampleRate < 0 && bodyLength >= 16) {
                        sampleRate = parseSampleRate(body);
                    }
                } else {
                    if (!prefixDone) {
                        prefixDone = true;
                        if (sampleRate <= 0) return; // cannot build a timeline
                        index.setPrefixBytes(prefix.toByteArray());
                    }
                    in.skipFully(bodyLength);
                    if (granule >= 0) {
                        long timeMillis = prevGranule * 1000L / sampleRate;
                        if (timeMillis >= nextPointAt) {
                            index.append(timeMillis, pageStart);
                            nextPointAt = timeMillis + POINT_INTERVAL_MILLIS;
                        }
                        prevGranule = granule;
                        index.setCoveredToMillis(granule * 1000L / sampleRate);
                    }
                }
            }
        } catch (EOFException e) {
            // truncated final page: keep what we have
        }
        if (sampleRate > 0) {
            index.setDurationMillis(prevGranule * 1000L / sampleRate);
        }
        index.markComplete();
    }

    /** Sample rate from a Vorbis identification packet or an OpusHead packet. */
    private static int parseSampleRate(byte[] body) {
        if (body.length >= 16 && body[0] == 0x01
                && "vorbis".equals(new String(body, 1, 6, StandardCharsets.US_ASCII))) {
            return (int) u32LE(body, 12);
        }
        if (body.length >= 8 && "OpusHead".equals(new String(body, 0, 8, StandardCharsets.US_ASCII))) {
            return 48000; // Opus granules always run at 48 kHz
        }
        return -1;
    }

    private static long u32LE(byte[] data, int offset) {
        return (data[offset] & 0xFFL) | ((data[offset + 1] & 0xFFL) << 8)
                | ((data[offset + 2] & 0xFFL) << 16) | ((data[offset + 3] & 0xFFL) << 24);
    }

    private static long u64LE(byte[] data, int offset) {
        return u32LE(data, offset) | (u32LE(data, offset + 4) << 32);
    }
}
