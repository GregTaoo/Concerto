package top.gregtao.concerto.core.api;

import java.util.Map;

public interface DynamicPath {

    record ResolvedPath(String path, boolean trial) {
    }

    String getLastRawPath();

    String updateRawPath();

    /** Resolves a fresh media URL and any source-specific playback restriction. */
    default ResolvedPath resolvePath() {
        return new ResolvedPath(updateRawPath(), false);
    }

    String getLastSuffix();

    String getLastLyrics();

    String getLastSubLyrics();

    /** Request headers for a refreshed media URL. */
    default Map<String, String> getCustomHeaders() {
        return Map.of();
    }
}
