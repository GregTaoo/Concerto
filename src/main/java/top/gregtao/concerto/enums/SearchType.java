package top.gregtao.concerto.enums;

import net.minecraft.text.Text;
import net.minecraft.util.StringIdentifiable;
import top.gregtao.concerto.api.SimpleStringIdentifiable;

public enum SearchType implements SimpleStringIdentifiable {
    // 网易云 1: 单曲, 10: 专辑, 100: 歌手, 1000: 歌单, 1002: 用户, 1004: MV, 1006: 歌词, 1009: 电台, 1014: 视频
    MUSIC(1),
    ALBUM(10),
    PLAYLIST(1000);

    public final int searchKey;

    SearchType(int searchKey) {
        this.searchKey = searchKey;
    }

    public static final com.mojang.serialization.Codec<OrderType> CODEC = StringIdentifiable.createCodec(OrderType::values);

    public Text getName() {
        return Text.translatable("concerto.search_type." + this.asString());
    }
}
