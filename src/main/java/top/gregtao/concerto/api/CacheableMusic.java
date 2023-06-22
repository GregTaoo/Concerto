package top.gregtao.concerto.api;

import top.gregtao.concerto.music.Music;

public interface CacheableMusic {

    String getSuffix();

    Music getMusic();
}
