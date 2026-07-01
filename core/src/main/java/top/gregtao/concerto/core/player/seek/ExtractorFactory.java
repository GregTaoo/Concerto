package top.gregtao.concerto.core.player.seek;

import java.io.IOException;
import java.util.Locale;

public final class ExtractorFactory {
    private ExtractorFactory() {
    }

    public static String detectFormat(ProgressiveMediaDataSource source, String suffix) throws IOException {
        String normalizedSuffix = suffix == null ? "" : suffix.toLowerCase(Locale.ROOT).replace(".", "");
        int probeLength = source.hasLength() ? (int) Math.min(64L, Math.max(16L, source.length())) : 64;
        byte[] probe = source.readAt(0L, probeLength);
        if (probe.length >= 12 && SeekParsing.asciiEquals(probe, 0, "RIFF") && SeekParsing.asciiEquals(probe, 8, "WAVE")
                || "wav".equals(normalizedSuffix)) {
            return "wav";
        }
        if (probe.length >= 4 && SeekParsing.asciiEquals(probe, 0, "fLaC") || "flac".equals(normalizedSuffix)) {
            return "flac";
        }
        if (probe.length >= 4 && SeekParsing.asciiEquals(probe, 0, "OggS")
                || "ogg".equals(normalizedSuffix) || "opus".equals(normalizedSuffix)) {
            return "ogg";
        }
        if (probe.length >= 12 && SeekParsing.asciiEquals(probe, 4, "ftyp")
                || "m4a".equals(normalizedSuffix) || "mp4".equals(normalizedSuffix) || "m4s".equals(normalizedSuffix)) {
            return "mp4-aac";
        }
        if (probe.length >= 2 && SeekParsing.u8(probe, 0) == 0xFF && (SeekParsing.u8(probe, 1) & 0xF0) == 0xF0
                || "aac".equals(normalizedSuffix)) {
            return "adts-aac";
        }
        return "mp3";
    }
}
