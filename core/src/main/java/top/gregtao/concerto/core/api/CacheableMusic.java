package top.gregtao.concerto.core.api;

import top.gregtao.concerto.core.music.Music;

public interface CacheableMusic {

    String getSuffix();

    Music getMusic();
}
