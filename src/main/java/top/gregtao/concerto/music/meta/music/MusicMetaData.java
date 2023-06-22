package top.gregtao.concerto.music.meta.music;

import top.gregtao.concerto.api.JsonParsable;
import top.gregtao.concerto.music.MusicTimestamp;
import top.gregtao.concerto.music.meta.MetaData;

public interface MusicMetaData extends JsonParsable<MusicMetaData>, MetaData {

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
        return this.title() + " | " + this.author() + " | " + this.getSource()
                + "\n%s" + (timestamp == null ? "" : " ".repeat(30) + this.getDuration().toShortString());
    }

}
