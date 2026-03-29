package top.gregtao.concerto.core.music.meta.music;

import top.gregtao.concerto.core.Concerto;

public class UnknownMusicMeta extends TimelessMusicMetaData {

    public UnknownMusicMeta(String source) {
        super(
            Concerto.getCoreBridge().getTranslatableText("concerto.unknown"),
            Concerto.getCoreBridge().getTranslatableText("concerto.unknown"),
            source
        );
    }
}
