package top.gregtao.concerto.core.api;

import top.gregtao.concerto.core.music.meta.MetaData;

public interface WithMetaData {

    MetaData getMeta();

    boolean isMetaLoaded();
}
