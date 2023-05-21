package top.gregtao.concerto.music.meta.music;

import top.gregtao.concerto.api.JsonParser;
import top.gregtao.concerto.api.MusicJsonParsers;
import top.gregtao.concerto.music.MusicTimestamp;

public class TimelessMusicMeta implements MusicMeta {

    private final String author;
    private final String title;
    private final String source;

    public TimelessMusicMeta(String author, String title, String source) {
        this.author = author;
        this.title = title;
        this.source = source;
    }

    @Override
    public String author() {
        return this.author;
    }

    @Override
    public String title() {
        return this.title;
    }

    @Override
    public String getSource() {
        return this.source;
    }

    @Override
    public MusicTimestamp getDuration() {
        return null;
    }

    @Override
    public JsonParser<MusicMeta> getJsonParser() {
        return MusicJsonParsers.TIMELESS_META;
    }
}
