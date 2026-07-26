package top.gregtao.concerto.core.player.seek;

import top.gregtao.concerto.core.player.source.AudioByteSource;

import java.io.IOException;

/**
 * Parses the RIFF/WAVE header; PCM data maps to time by plain arithmetic. The
 * bytes before the data chunk are kept as the decode prefix so mid-stream opens
 * still look like a WAV file to the SPI.
 */
public class WavIndexBuilder implements SeekIndexBuilder {

    @Override
    public void build(AudioByteSource source, SeekIndex index) throws IOException {
        ScanInput in = new ScanInput(source, 0);
        byte[] riff = new byte[12];
        if (in.readUpTo(riff, 0, 12) < 12
                || riff[0] != 'R' || riff[1] != 'I' || riff[2] != 'F' || riff[3] != 'F'
                || riff[8] != 'W' || riff[9] != 'A' || riff[10] != 'V' || riff[11] != 'E') {
            return;
        }

        int audioFormat = -1, blockAlign = -1;
        long byteRate = -1;
        long dataStart = -1, dataSize = -1;

        while (dataStart < 0) {
            byte[] chunkHeader = new byte[8];
            if (in.readUpTo(chunkHeader, 0, 8) < 8) return;
            String chunkId = new String(chunkHeader, 0, 4, java.nio.charset.StandardCharsets.US_ASCII);
            long chunkSize = (chunkHeader[4] & 0xFFL) | ((chunkHeader[5] & 0xFFL) << 8)
                    | ((chunkHeader[6] & 0xFFL) << 16) | ((chunkHeader[7] & 0xFFL) << 24);
            if ("fmt ".equals(chunkId) && chunkSize >= 16) {
                audioFormat = in.readU16LE();
                in.readU16LE(); // channels
                in.readU32LE(); // sample rate
                byteRate = in.readU32LE();
                blockAlign = in.readU16LE();
                in.readU16LE(); // bits per sample
                in.skipFully(chunkSize - 16 + (chunkSize & 1));
            } else if ("data".equals(chunkId)) {
                dataStart = in.position();
                dataSize = chunkSize;
            } else {
                in.skipFully(chunkSize + (chunkSize & 1));
            }
        }

        // 1 = PCM, 0xFFFE = WAVE_FORMAT_EXTENSIBLE (usually PCM)
        if ((audioFormat != 1 && audioFormat != 0xFFFE) || byteRate <= 0 || blockAlign <= 0) return;

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
}
