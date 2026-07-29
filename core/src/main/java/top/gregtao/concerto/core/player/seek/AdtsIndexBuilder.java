package top.gregtao.concerto.core.player.seek;

import top.gregtao.concerto.core.player.source.AudioByteSource;

import java.io.EOFException;
import java.io.IOException;

/**
 * Walks ADTS AAC frames, emitting a seek point roughly every second of audio.
 * Each frame carries 1024 samples per raw data block at the header sample rate;
 * with HE-AAC/SBR the output rate doubles but so does the samples-per-frame, so
 * the timeline stays correct. ADTS self-synchronizes, so no prefix bytes are
 * needed for mid-stream decoder opens.
 */
public class AdtsIndexBuilder implements SeekIndexBuilder {

    private static final int[] SAMPLE_RATES = {
            96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350
    };

    @Override
    public void build(AudioByteSource source, SeekIndex index) throws IOException {
        ScanInput in = new ScanInput(source, 0);
        long samples = 0;
        int sampleRate = -1;
        long nextPointAt = 0;

        // 7-byte sliding window: the fixed+variable ADTS header
        int[] window = new int[7];
        for (int i = 0; i < 7; i++) window[i] = in.read();

        // ID3v2 tag: syncsafe size in bytes 6-9 of the 10-byte header
        if (window[0] == 'I' && window[1] == 'D' && window[2] == '3') {
            // window[3..5] are version/flags bytes, window[6] starts the syncsafe size
            long size = ((long) (window[6] & 0x7F) << 21) | ((long) (in.readU8() & 0x7F) << 14)
                    | ((long) (in.readU8() & 0x7F) << 7) | (in.readU8() & 0x7F);
            try {
                in.skipFully(size);
            } catch (EOFException e) {
                return;
            }
            for (int i = 0; i < 7; i++) window[i] = in.read();
        }

        while (window[6] != -1) {
            FrameHeader header = parseHeader(window);
            if (header == null) {
                System.arraycopy(window, 1, window, 0, 6);
                window[6] = in.read();
                continue;
            }
            long frameStart = in.position() - 7;
            if (sampleRate < 0) sampleRate = header.sampleRate;
            try {
                in.skipFully(header.frameLength - 7);
            } catch (EOFException e) {
                break; // truncated final frame
            }

            long timeMillis = samples * 1000L / sampleRate;
            if (timeMillis >= nextPointAt) {
                index.append(timeMillis, frameStart);
                nextPointAt = timeMillis + POINT_INTERVAL_MILLIS;
            }
            samples += header.samplesPerFrame;
            index.setCoveredToMillis(samples * 1000L / sampleRate);

            for (int i = 0; i < 7; i++) window[i] = in.read();
        }

        if (sampleRate > 0) {
            index.setDurationMillis(samples * 1000L / sampleRate);
        }
        index.markComplete();
    }

    private static class FrameHeader {
        int frameLength;
        int samplesPerFrame;
        int sampleRate;
    }

    private static FrameHeader parseHeader(int[] b) {
        // Sync 0xFFF with layer bits 00 (an MPEG layer value here would be MP3-family)
        if (b[0] != 0xFF || (b[1] & 0xF6) != 0xF0) return null;
        int sampleRateIndex = (b[2] >> 2) & 0x0F;
        if (sampleRateIndex >= SAMPLE_RATES.length) return null;
        int frameLength = ((b[3] & 0x03) << 11) | (b[4] << 3) | ((b[5] >> 5) & 0x07);
        boolean crcPresent = (b[1] & 0x01) == 0;
        if (frameLength < (crcPresent ? 9 : 7)) return null;
        FrameHeader header = new FrameHeader();
        header.frameLength = frameLength;
        header.sampleRate = SAMPLE_RATES[sampleRateIndex];
        header.samplesPerFrame = 1024 * ((b[6] & 0x03) + 1);
        return header;
    }
}
