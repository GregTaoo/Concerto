package top.gregtao.concerto.core.music.meta.music;

import top.gregtao.concerto.core.Concerto;

public class UnknownMusicMeta extends TimelessMusicMetaData {

    public UnknownMusicMeta(String source) {
        super(
            Concerto.getMinecraft().getTranslatableText("concerto.unknown"),
            Concerto.getMinecraft().getTranslatableText("concerto.unknown"),
            source
        );
    }
}
