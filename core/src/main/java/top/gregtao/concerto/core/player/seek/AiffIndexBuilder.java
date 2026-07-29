package top.gregtao.concerto.core.player.seek;

import top.gregtao.concerto.core.player.source.AudioByteSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Parses the AIFF/AIFC header (big-endian chunks); PCM data maps to time by
 * plain arithmetic. The bytes before the SSND sample data are kept as the decode
 * prefix so mid-stream opens still look like an AIFF file to the SPI. Only
 * uncompressed audio is indexed ('AIFF', or 'AIFC' with compression 'NONE').
 */
public class AiffIndexBuilder implements SeekIndexBuilder {

    @Override
    public void build(AudioByteSource source, SeekIndex index) throws IOException {
        ScanInput in = new ScanInput(source, 0);
        byte[] form = new byte[12];
        if (in.readUpTo(form, 0, 12) < 12
                || form[0] != 'F' || form[1] != 'O' || form[2] != 'R' || form[3] != 'M'
                || form[8] != 'A' || form[9] != 'I' || form[10] != 'F'
                || (form[11] != 'F' && form[11] != 'C')) {
            return;
        }
        boolean aifc = form[11] == 'C';

        int channels = -1, sampleSize = -1, sampleRate = -1;
        boolean commSeen = false;
        long dataStart = -1, dataSize = -1;

        while (dataStart < 0) {
            byte[] chunkHeader = new byte[8];
            if (in.readUpTo(chunkHeader, 0, 8) < 8) return;
            String chunkId = new String(chunkHeader, 0, 4, StandardCharsets.US_ASCII);
            long chunkSize = ((chunkHeader[4] & 0xFFL) << 24) | ((chunkHeader[5] & 0xFFL) << 16)
                    | ((chunkHeader[6] & 0xFFL) << 8) | (chunkHeader[7] & 0xFFL);
            if ("COMM".equals(chunkId) && chunkSize >= 18) {
                channels = in.readU16BE();
                in.readU32BE(); // numSampleFrames; data size is derived from SSND instead
                sampleSize = in.readU16BE();
                sampleRate = readExtendedFloat(in);
                long consumed = 18;
                if (aifc && chunkSize >= consumed + 4) {
                    byte[] compression = new byte[4];
                    in.readFully(compression, 0, 4);
                    consumed += 4;
                    if (!"NONE".equals(new String(compression, StandardCharsets.US_ASCII))) {
                        return; // compressed AIFC: not arithmetic-seekable
                    }
                }
                in.skipFully(chunkSize - consumed + (chunkSize & 1));
                commSeen = true;
            } else if ("SSND".equals(chunkId)) {
                long offset = in.readU32BE();
                in.readU32BE(); // blockSize
                dataStart = in.position() + offset;
                dataSize = chunkSize - 8 - offset;
            } else {
                in.skipFully(chunkSize + (chunkSize & 1));
            }
        }

        if (!commSeen || channels <= 0 || sampleSize <= 0 || sampleRate <= 0) return;
        int blockAlign = channels * ((sampleSize + 7) / 8);
        long byteRate = (long) sampleRate * blockAlign;

        long total = source.length();
        if (total >= 0 && (dataSize <= 0 || dataStart + dataSize > total)) {
            dataSize = total - dataStart;
        }
        if (dataSize <= 0) return;

        byte[] prefix = new byte[(int) dataStart];
        int filled = 0;
        while (filled < prefix.length) {
            int read = source.read(filled, prefix, filled, prefix.length - filled);
            if (read == -1) return;
            filled += read;
        }
        index.setPrefixBytes(prefix);

        long durationMillis = dataSize * 1000L / byteRate;
        for (long timeMillis = 0; timeMillis <= durationMillis; timeMillis += POINT_INTERVAL_MILLIS) {
            long offset = timeMillis * byteRate / 1000L;
            offset -= offset % blockAlign;
            index.append(timeMillis, dataStart + offset);
        }
        index.setDurationMillis(durationMillis);
        index.setCoveredToMillis(durationMillis);
        index.markComplete();
    }

    /**
     * The COMM sample rate is an 80-bit IEEE 754 extended float: 1 sign bit,
     * 15 exponent bits (bias 16383), 64 mantissa bits with an explicit leading
     * integer bit. @return the rounded rate, or -1 when not a usable rate
     */
    private static int readExtendedFloat(ScanInput in) throws IOException {
        int signExponent = in.readU16BE();
        long mantissa = in.readU64BE();
        int exponent = signExponent & 0x7FFF;
        if ((signExponent & 0x8000) != 0) return -1;            // negative rate
        if (exponent == 0 && mantissa == 0) return 0;
        if (exponent == 0x7FFF) return -1;                      // infinity/NaN
        double high = Math.scalb((double) (mantissa >>> 32), exponent - 16383 - 31);
        double low = Math.scalb((double) (mantissa & 0xFFFFFFFFL), exponent - 16383 - 63);
        double value = high + low;
        if (value <= 0 || value > Integer.MAX_VALUE) return -1;
        return (int) Math.round(value);
    }
}
