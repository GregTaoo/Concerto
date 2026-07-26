package top.gregtao.concerto.core.player.seek;

import top.gregtao.concerto.core.player.source.AudioByteSource;

import java.io.EOFException;
import java.io.IOException;

/**
 * Walks MPEG audio frames, emitting a seek point roughly every second of audio.
 * Xing/Info/VBRI header frames are recognized (for an early duration estimate)
 * and excluded from the audio timeline.
 */
public class Mp3IndexBuilder implements SeekIndexBuilder {

    // [layerIndex 1..3][bitrateIndex] in kbps; layerIndex: 1=Layer1, 2=Layer2, 3=Layer3
    private static final int[][] BITRATES_V1 = {
            {},
            {0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448},
            {0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384},
            {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320},
    };
    private static final int[][] BITRATES_V2 = {
            {},
            {0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256},
            {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160},
            {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160},
    };
    private static final int[][] SAMPLE_RATES = {
            {11025, 12000, 8000},   // MPEG2.5
            {},                     // reserved
            {22050, 24000, 16000},  // MPEG2
            {44100, 48000, 32000},  // MPEG1
    };

    @Override
    public void build(AudioByteSource source, SeekIndex index) throws IOException {
        ScanInput in = new ScanInput(source, 0);
        long samples = 0;
        int sampleRate = -1;
        long nextPointAt = 0;
        boolean firstFrame = true;

        int b0 = in.read(), b1 = in.read(), b2 = in.read(), b3 = in.read();
        // ID3v2 tag: syncsafe size in bytes 6-9 of the 10-byte header
        if (b0 == 'I' && b1 == 'D' && b2 == '3') {
            in.readU8(); // version minor (b3 was the version major byte)
            in.readU8(); // flags
            long size = 0;
            for (int i = 0; i < 4; i++) size = (size << 7) | (in.readU8() & 0x7F);
            try {
                in.skipFully(size);
            } catch (EOFException e) {
                return;
            }
            b0 = in.read(); b1 = in.read(); b2 = in.read(); b3 = in.read();
        }

        while (b3 != -1) {
            FrameHeader header = parseHeader(b0, b1, b2, b3);
            if (header == null) {
                b0 = b1; b1 = b2; b2 = b3; b3 = in.read();
                continue;
            }
            long frameStart = in.position() - 4;
            if (sampleRate < 0) sampleRate = header.sampleRate;

            boolean countAudio = true;
            try {
                if (firstFrame) {
                    firstFrame = false;
                    byte[] body = new byte[header.frameLength - 4];
                    in.readFully(body, 0, body.length);
                    long headerFrames = parseVbrHeaderFrames(body, header);
                    if (headerFrames >= 0) {
                        countAudio = false; // Xing/Info/VBRI frame carries no audio
                        if (headerFrames > 0) {
                            index.setDurationMillis(headerFrames * header.samplesPerFrame * 1000L / header.sampleRate);
                        }
                    }
                } else {
                    in.skipFully(header.frameLength - 4);
                }
            } catch (EOFException e) {
                break; // truncated final frame
            }

            if (countAudio) {
                long timeMillis = samples * 1000L / sampleRate;
                if (timeMillis >= nextPointAt) {
                    index.append(timeMillis, frameStart);
                    nextPointAt = timeMillis + POINT_INTERVAL_MILLIS;
                }
                samples += header.samplesPerFrame;
                index.setCoveredToMillis(samples * 1000L / sampleRate);
            }

            b0 = in.read(); b1 = in.read(); b2 = in.read(); b3 = in.read();
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
        boolean mpeg1;
        boolean mono;
    }

    private static FrameHeader parseHeader(int b0, int b1, int b2, int b3) {
        if (b0 != 0xFF || (b1 & 0xE0) != 0xE0) return null;
        int versionBits = (b1 >> 3) & 0x03;   // 0=2.5, 1=reserved, 2=MPEG2, 3=MPEG1
        int layerBits = (b1 >> 1) & 0x03;     // 1=Layer3, 2=Layer2, 3=Layer1
        int bitrateIndex = (b2 >> 4) & 0x0F;
        int sampleRateIndex = (b2 >> 2) & 0x03;
        if (versionBits == 1 || layerBits == 0 || bitrateIndex == 0 || bitrateIndex == 15 || sampleRateIndex == 3) {
            return null;
        }
        FrameHeader header = new FrameHeader();
        header.mpeg1 = versionBits == 3;
        int layer = 4 - layerBits; // 1, 2, 3
        int bitrate = (header.mpeg1 ? BITRATES_V1 : BITRATES_V2)[layer][bitrateIndex] * 1000;
        header.sampleRate = SAMPLE_RATES[versionBits][sampleRateIndex];
        int padding = (b2 >> 1) & 0x01;
        if (layer == 1) {
            header.samplesPerFrame = 384;
            header.frameLength = (12 * bitrate / header.sampleRate + padding) * 4;
        } else {
            header.samplesPerFrame = layer == 2 || header.mpeg1 ? 1152 : 576;
            int coefficient = header.samplesPerFrame / 8;
            header.frameLength = coefficient * bitrate / header.sampleRate + padding;
        }
        header.mono = ((b3 >> 6) & 0x03) == 3;
        if (header.frameLength < 21 || header.frameLength > 8192) return null;
        return header;
    }

    /**
     * @return the frame count from a Xing/Info/VBRI header, 0 if the frame is such
     *         a header without a count, or -1 if this is a plain audio frame
     */
    private static long parseVbrHeaderFrames(byte[] body, FrameHeader header) {
        int xingOffset = header.mpeg1 ? (header.mono ? 17 : 32) : (header.mono ? 9 : 17);
        if (body.length >= xingOffset + 8 && isTag(body, xingOffset, "Xing", "Info")) {
            long flags = u32(body, xingOffset + 4);
            if ((flags & 0x1) != 0 && body.length >= xingOffset + 12) {
                return u32(body, xingOffset + 8);
            }
            return 0;
        }
        if (body.length >= 32 + 18 && isTag(body, 32, "VBRI", "VBRI")) {
            return u32(body, 32 + 14);
        }
        return -1;
    }

    private static boolean isTag(byte[] body, int offset, String a, String b) {
        String tag = new String(body, offset, 4, java.nio.charset.StandardCharsets.US_ASCII);
        return tag.equals(a) || tag.equals(b);
    }

    private static long u32(byte[] body, int offset) {
        return ((long) (body[offset] & 0xFF) << 24) | ((body[offset + 1] & 0xFF) << 16)
                | ((body[offset + 2] & 0xFF) << 8) | (body[offset + 3] & 0xFF);
    }
}
