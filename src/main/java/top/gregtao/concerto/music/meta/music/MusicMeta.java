package top.gregtao.concerto.music.meta.music;

import top.gregtao.concerto.api.JsonParsable;
import top.gregtao.concerto.music.MusicTimestamp;
import top.gregtao.concerto.music.meta.MetaData;

public interface MusicMeta extends JsonParsable<MusicMeta>, MetaData {

    String getSource();

    MusicTimestamp getDuration();

    String headPictureUrl();

    @Override
    default String createTime() {
        return "Unknown";
    }

    @Override
    default String description() {
        return "Unknown";
    }

    default String asString() {
        MusicTimestamp timestamp = this.getDuration();
        return this.title() + " | " + this.author() + "\n" + this.getSource()
                + " - %s" + (timestamp == null ? "" : " -> " + this.getDuration().toStringWithoutMillisecond()) + " - %s";
    }

}
