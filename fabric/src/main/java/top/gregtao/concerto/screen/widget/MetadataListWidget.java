package top.gregtao.concerto.screen.widget;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import top.gregtao.concerto.core.api.WithMetaData;
import top.gregtao.concerto.core.music.meta.MetaData;
import top.gregtao.concerto.core.util.ConcertoRunner;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MetadataListWidget<T extends WithMetaData> extends ConcertoListWidget<T> {

    private final Set<T> loadingSet = ConcurrentHashMap.newKeySet();

    public MetadataListWidget(int width, int height, int top, int itemHeight) {
        this(width, height, top, itemHeight, 0xffffffff);
    }

    public MetadataListWidget(int width, int height, int top, int itemHeight, int color) {
        super(width, height, top, itemHeight, color);
    }

    @Override
    public Text getNarration(int index, T t) {
        if (t.isMetaLoaded()) {
            MetaData meta = t.getMeta();
            return Text.literal(meta.title()).append("  ").append(Text.literal(meta.author()).formatted(Formatting.BOLD, Formatting.GRAY));
        } else {
              if (!this.loadingSet.contains(t)) {
                  this.loadingSet.add(t);
                  ConcertoRunner.run(t::getMeta, () -> this.loadingSet.remove(t));
              }
            return Text.translatable("concerto.loading");
        }
    }
}
