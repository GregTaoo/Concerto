package top.gregtao.concerto.core.player.seek;

import top.gregtao.concerto.core.player.source.AudioByteSource;

import java.io.IOException;
import java.util.Locale;

/** Audio container formats the seek layer understands. */
public enum ContainerFormat {
    MP3,
    OGG,
    FLAC,
    WAV,
    /** Playable through the SPI chain (maybe), but not seekable. */
    UNSUPPORTED;

    public SeekIndexBuilder createIndexBuilder() {
        switch (this) {
            case MP3: return new Mp3IndexBuilder();
            case OGG: return new OggIndexBuilder();
            case FLAC: return new FlacIndexBuilder();
            case WAV: return new WavIndexBuilder();
            default: return null;
        }
    }

    /**
     * Sniffs the container format from the first bytes of the media, falling back
     * to the file-name suffix. Unrecognized data is {@link #UNSUPPORTED}, never a
     * guess.
     */
    public static ContainerFormat detect(AudioByteSource source, String suffixHint) throws IOException {
        byte[] head = new byte[12];
        int total = 0;
        while (total < head.length) {
            int read = source.read(total, head, total, head.length - total);
            if (read == -1) break;
            total += read;
        }
        if (total >= 4) {
            if (head[0] == 'O' && head[1] == 'g' && head[2] == 'g' && head[3] == 'S') return OGG;
            if (head[0] == 'f' && head[1] == 'L' && head[2] == 'a' && head[3] == 'C') return FLAC;
            if (total >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                    && head[8] == 'W' && head[9] == 'A' && head[10] == 'V' && head[11] == 'E') return WAV;
            if (head[0] == 'I' && head[1] == 'D' && head[2] == '3') return MP3;
            if (total >= 8 && head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p') return UNSUPPORTED; // mp4/m4a
            if ((head[0] & 0xFF) == 0xFF && (head[1] & 0xE0) == 0xE0) {
                // MPEG sync: layer bits 00 means ADTS-AAC, anything else is MP3-family.
                return (head[1] & 0x06) == 0 ? UNSUPPORTED : MP3;
            }
        }
        if (suffixHint != null) {
            switch (suffixHint.toLowerCase(Locale.ROOT).replace(".", "")) {
                case "mp3": return MP3;
                case "ogg": return OGG;
                case "flac": return FLAC;
                case "wav": return WAV;
                default: break;
            }
        }
        return UNSUPPORTED;
    }
}
