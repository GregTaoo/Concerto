package top.gregtao.concerto.core.api;

import java.util.Map;

public interface DynamicPath {

    String getLastRawPath();

    String updateRawPath();

    String getLastSuffix();

    String getLastLyrics();

    String getLastSubLyrics();

    /** Request headers for a refreshed media URL. */
    default Map<String, String> getCustomHeaders() {
        return Map.of();
    }
}
