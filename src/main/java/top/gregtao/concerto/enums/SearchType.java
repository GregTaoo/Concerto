package top.gregtao.concerto.enums;

import net.minecraft.text.Text;
import top.gregtao.concerto.api.SimpleStringIdentifiable;

public enum SearchType implements SimpleStringIdentifiable {
    // 网易云 1: 单曲, 10: 专辑, 100: 歌手, 1000: 歌单, 1002: 用户, 1004: MV, 1006: 歌词, 1009: 电台, 1014: 视频
    MUSIC(1, 0, "song"),
    ALBUM(10, 2, "album"),
    PLAYLIST(1000, 3, "playlist");

    public final int neteaseKey, qqKey;
    public final String qqSuffix;

    SearchType(int neteaseKey, int qqKey, String qqSuffix) {
        this.neteaseKey = neteaseKey;
        this.qqKey = qqKey;
        this.qqSuffix = qqSuffix;
    }

    public Text getName() {
        return Text.translatable("concerto.search_type." + this.asString());
    }
}
