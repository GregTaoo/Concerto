package top.gregtao.concerto.music.meta.music;

import top.gregtao.concerto.api.JsonParser;
import top.gregtao.concerto.api.MusicJsonParsers;
import top.gregtao.concerto.music.MusicTimestamp;

public class BasicMusicMetaData extends TimelessMusicMetaData {
    private final MusicTimestamp duration;

    public BasicMusicMetaData(String author, String title, String source, long duration) {
        super(author, title, source);
        this.duration = MusicTimestamp.ofMilliseconds(duration);
    }

    public BasicMusicMetaData(String author, String title, String source, long duration, String headPictureUrl) {
        super(author, title, source, headPictureUrl);
        this.duration = MusicTimestamp.ofMilliseconds(duration);
    }

    public BasicMusicMetaData(TimelessMusicMetaData meta, long duration) {
        this(meta.author(), meta.title(), meta.getSource(), duration, meta.headPictureUrl());
    }

    @Override
    public MusicTimestamp getDuration() {
        return this.duration;
    }

    @Override
    public JsonParser<MusicMetaData> getJsonParser() {
        return MusicJsonParsers.BASIC_META;
    }
}
