package top.gregtao.concerto.music.meta.music.list;

import top.gregtao.concerto.music.meta.MetaData;

public record PlaylistMetaData(String author, String title, String createTime, String description) implements MetaData {
    public static PlaylistMetaData EMPTY = new PlaylistMetaData("", "", "", "");
}
