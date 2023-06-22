package top.gregtao.concerto.music.meta.music;

import top.gregtao.concerto.util.TextUtil;

public class UnknownMusicMeta extends TimelessMusicMetaData {

    public UnknownMusicMeta(String source) {
        super(TextUtil.getTranslatable("concerto.unknown"), TextUtil.getTranslatable("concerto.unknown"), source);
    }
}
