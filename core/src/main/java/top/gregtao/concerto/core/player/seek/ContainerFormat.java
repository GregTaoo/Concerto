package top.gregtao.concerto.core.player.seek;

import top.gregtao.concerto.core.player.source.AudioByteSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Audio container formats the seek layer understands. */
public enum ContainerFormat {
    MP3,
    /** Ogg carrying Vorbis (or anything that is not Opus). */
    OGG,
    /** Ogg carrying Opus; decoded by a dedicated branch, granules run at 48 kHz. */
    OGG_OPUS,
    FLAC,
    WAV,
    AIFF,
    /** MP4/M4A audio; seekable by restarting its decoder from the container header. */
    M4A,
    /** Raw AAC in an ADTS transport stream. */
    AAC_ADTS,
    /** Playable through the SPI chain (maybe), but not seekable. */
    UNSUPPORTED;

    public SeekIndexBuilder createIndexBuilder() {
        switch (this) {
            case MP3: return new Mp3IndexBuilder();
            case OGG:
            case OGG_OPUS: return new OggIndexBuilder();
            case FLAC: return new FlacIndexBuilder();
            case WAV: return new WavIndexBuilder();
            case AIFF: return new AiffIndexBuilder();
            case AAC_ADTS: return new AdtsIndexBuilder();
            default: return null; // M4A seeks by restarting from the container header
        }
    }
    /** Returns the decoder reopening contract used for this container. */
    public SeekMode getSeekMode() {
        return switch (this) {
            case MP3, OGG, OGG_OPUS, FLAC, WAV, AIFF, AAC_ADTS -> SeekMode.INDEXED;
            case M4A -> SeekMode.RESTART_FROM_START;
            case UNSUPPORTED -> SeekMode.UNSUPPORTED;
        };
    }


    /**
     * Sniffs the container format from the first bytes of the media, falling back
     * to the file-name suffix. Unrecognized data is {@link #UNSUPPORTED}, never a
     * guess.
     */
    public static ContainerFormat detect(AudioByteSource source, String suffixHint) throws IOException {
        byte[] head = new byte[12];
        int total = readUpTo(source, 0, head);
        if (total >= 4) {
            if (head[0] == 'O' && head[1] == 'g' && head[2] == 'g' && head[3] == 'S') {
                return detectOggCodec(source);
            }
            if (head[0] == 'f' && head[1] == 'L' && head[2] == 'a' && head[3] == 'C') return FLAC;
            if (total >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                    && head[8] == 'W' && head[9] == 'A' && head[10] == 'V' && head[11] == 'E') return WAV;
            if (total >= 12 && head[0] == 'F' && head[1] == 'O' && head[2] == 'R' && head[3] == 'M'
                    && head[8] == 'A' && head[9] == 'I' && head[10] == 'F'
                    && (head[11] == 'F' || head[11] == 'C')) return AIFF;
            if (head[0] == 'I' && head[1] == 'D' && head[2] == '3') return MP3;
            if (total >= 8 && head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p') return M4A;
            if ((head[0] & 0xFF) == 0xFF && (head[1] & 0xE0) == 0xE0) {
                // MPEG sync: layer bits 00 means ADTS-AAC, anything else is MP3-family.
                return (head[1] & 0x06) == 0 ? AAC_ADTS : MP3;
            }
        }
        if (suffixHint != null) {
            switch (suffixHint.toLowerCase(Locale.ROOT).replace(".", "")) {
                case "mp3": return MP3;
                case "ogg": return OGG;
                case "opus": return OGG_OPUS;
                case "flac": return FLAC;
                case "wav": return WAV;
                case "aiff":
                case "aif":
                case "aifc": return AIFF;
                case "m4a":
                case "mp4":
                case "m4s": return M4A;
                case "aac": return AAC_ADTS;
                default: break;
            }
        }
        return UNSUPPORTED;
    }

    /**
     * Distinguishes Opus from Vorbis by the first packet of the first Ogg page
     * (27-byte page header + segment table, then {@code OpusHead} or
     * {@code \x01vorbis}). Anything unclear stays plain {@link #OGG}.
     */
    private static ContainerFormat detectOggCodec(AudioByteSource source) throws IOException {
        byte[] header = new byte[27];
        if (readUpTo(source, 0, header) < 27) return OGG;
        int segmentCount = header[26] & 0xFF;
        byte[] segmentTable = new byte[segmentCount];
        if (readUpTo(source, 27, segmentTable) < segmentCount) return OGG;
        byte[] packetHead = new byte[8];
        if (readUpTo(source, 27 + segmentCount, packetHead) < 8) return OGG;
        if ("OpusHead".equals(new String(packetHead, StandardCharsets.US_ASCII))) return OGG_OPUS;
        return OGG;
    }

    private static int readUpTo(AudioByteSource source, long position, byte[] buffer) throws IOException {
        int total = 0;
        while (total < buffer.length) {
            int read = source.read(position + total, buffer, total, buffer.length - total);
            if (read == -1) break;
            total += read;
        }
        return total;
    }
}
