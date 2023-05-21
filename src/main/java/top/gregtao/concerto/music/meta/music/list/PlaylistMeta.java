package top.gregtao.concerto.music.meta.music.list;

import top.gregtao.concerto.music.meta.MetaData;

public record PlaylistMeta(String author, String title, String createTime, String description) implements MetaData {
    public static PlaylistMeta EMPTY = new PlaylistMeta("", "", "", "");
}
