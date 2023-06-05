package top.gregtao.concerto.screen.widget;

import com.mojang.datafixers.util.Pair;
import net.minecraft.text.Text;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.music.meta.music.MusicMeta;

import java.util.UUID;
import java.util.function.BiFunction;

public class MusicWithUUIDListWidget extends ConcertoListWidget<Pair<Music, UUID>> {

    public MusicWithUUIDListWidget(int width, int height, int top, int bottom, int itemHeight) {
        this(width, height, top, bottom, itemHeight, (t, index) -> {
            MusicMeta meta = t.getFirst().getMeta();
            return Text.literal(meta.title() + " - " + meta.getSource());
        }, 0xffffffff);
    }

    public MusicWithUUIDListWidget(int width, int height, int top, int bottom, int itemHeight, BiFunction<Pair<Music, UUID>, Integer, Text> narrationSupplier, int color) {
        super(width, height, top, bottom, itemHeight, narrationSupplier, color);
    }
}
