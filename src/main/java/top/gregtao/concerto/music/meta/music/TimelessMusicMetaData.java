package top.gregtao.concerto.music.meta.music;

import top.gregtao.concerto.api.JsonParser;
import top.gregtao.concerto.api.MusicJsonParsers;
import top.gregtao.concerto.music.MusicTimestamp;

public class TimelessMusicMetaData implements MusicMetaData {

    private final String author;
    private final String title;
    private final String source;
    private String headPictureUrl = "";
    private String status = "";

    public TimelessMusicMetaData(String author, String title, String source) {
        this.author = author;
        this.title = title;
        this.source = source;
    }

    public TimelessMusicMetaData(String author, String title, String source, String headPictureUrl) {
        this(author, title, source);
        this.headPictureUrl = headPictureUrl;
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
    public String headPictureUrl() {
        return this.headPictureUrl;
    }

    public void setHeadPictureUrl(String s) {
        this.headPictureUrl = s;
    }

    @Override
    public JsonParser<MusicMetaData> getJsonParser() {
        return MusicJsonParsers.TIMELESS_META;
    }
}
