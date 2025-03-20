package top.gregtao.concerto.screen.widget;

import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import top.gregtao.concerto.api.WithMetaData;
import top.gregtao.concerto.music.meta.MetaData;

public class MetadataListWidget<T extends WithMetaData> extends ConcertoListWidget<T> {

    public MetadataListWidget(int width, int height, int top, int bottom, int itemHeight) {
        this(width, height, top, bottom, itemHeight, 0xffffffff);
    }

    public MetadataListWidget(int width, int height, int top, int bottom, int itemHeight, int color) {
        super(width, height, top, bottom, itemHeight, color);
    }

    @Override
    public Text getNarration(int index, T t) {
        MetaData meta = t.getMeta();
        return new LiteralText(meta.title()).append("  ").append(new LiteralText(meta.author()).formatted(Formatting.BOLD, Formatting.GRAY));
    }
}
