package top.gregtao.concerto.music.meta.music;

import top.gregtao.concerto.api.JsonParser;
import top.gregtao.concerto.api.MusicJsonParsers;
import top.gregtao.concerto.music.MusicTimestamp;

public class BasicMusicMeta extends TimelessMusicMeta {
    private final MusicTimestamp duration;

    public BasicMusicMeta(String author, String title, String source, long duration) {
        super(author, title, source);
        this.duration = MusicTimestamp.of(duration);
    }

    public BasicMusicMeta(String author, String title, String source, long duration, String headPictureUrl) {
        super(author, title, source, headPictureUrl);
        this.duration = MusicTimestamp.of(duration);
    }

    public BasicMusicMeta(TimelessMusicMeta meta, long duration) {
        this(meta.author(), meta.title(), meta.getSource(), duration, meta.headPictureUrl());
    }

    @Override
    public MusicTimestamp getDuration() {
        return this.duration;
    }

    @Override
    public JsonParser<MusicMeta> getJsonParser() {
        return MusicJsonParsers.BASIC_META;
    }
}
