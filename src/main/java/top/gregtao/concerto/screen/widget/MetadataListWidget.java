package top.gregtao.concerto.screen.widget;

import net.minecraft.text.Text;
import top.gregtao.concerto.api.WithMetaData;
import top.gregtao.concerto.music.meta.MetaData;

import java.util.function.BiFunction;

public class MetadataListWidget<T extends WithMetaData> extends ConcertoListWidget<T> {

    public MetadataListWidget(int width, int height, int top, int bottom, int itemHeight) {
        this(width, height, top, bottom, itemHeight, (t, index) -> {
            MetaData meta = t.getMeta();
            return Text.literal(meta.title() + " - " + meta.author());
        }, 0xffffffff);
    }

    public MetadataListWidget(int width, int height, int top, int bottom, int itemHeight, BiFunction<T, Integer, Text> narrationSupplier, int color) {
        super(width, height, top, bottom, itemHeight, narrationSupplier, color);
    }
}
