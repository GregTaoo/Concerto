package top.gregtao.concerto.core.music.meta.music;

import top.gregtao.concerto.core.Concerto;

public class UnknownMusicMeta extends TimelessMusicMetaData {

    public UnknownMusicMeta(String source) {
        super(
                Concerto.getCoreBridge().getTranslatable("concerto.unknown"),
                Concerto.getCoreBridge().getTranslatable("concerto.unknown"),
                source
        );
    }
}
