package top.gregtao.concerto.enums;

import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import top.gregtao.concerto.api.SimpleStringIdentifiable;

public enum SearchType implements SimpleStringIdentifiable {
    // 网易云 1: 单曲, 10: 专辑, 100: 歌手, 1000: 歌单, 1002: 用户, 1004: MV, 1006: 歌词, 1009: 电台, 1014: 视频
    MUSIC(1, 0, "song", "song"),
    ALBUM(10, 2, "album", "album"),
    PLAYLIST(1000, 3, "playlist", "special");

    public final int neteaseKey, qqKey;
    public final String qqSuffix;
    public final String kuGouKey;

    SearchType(int neteaseKey, int qqKey, String qqSuffix, String kuGouKey) {
        this.neteaseKey = neteaseKey;
        this.qqKey = qqKey;
        this.qqSuffix = qqSuffix;
        this.kuGouKey = kuGouKey;
    }

    public Text getName() {
        return new TranslatableText("concerto.search_type." + this.asString());
    }
}
