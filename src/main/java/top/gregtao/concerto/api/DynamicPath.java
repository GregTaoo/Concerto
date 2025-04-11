package top.gregtao.concerto.api;

public interface DynamicPath {

    String getLastRawPath();

    String updateRawPath();

    String getLastSuffix();

    String getLastLyrics();

    String getLastSubLyrics();
}
