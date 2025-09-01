package top.gregtao.concerto.screen.widget;

import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import top.gregtao.concerto.api.WithMetaData;
import top.gregtao.concerto.music.meta.MetaData;
import top.gregtao.concerto.util.ConcertoRunner;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MetadataListWidget<T extends WithMetaData> extends ConcertoListWidget<T> {

    private final Set<T> loadingSet = ConcurrentHashMap.newKeySet();

    public MetadataListWidget(int width, int height, int top, int bottom, int itemHeight) {
        this(width, height, top, bottom, itemHeight, 0xffffffff);
    }

    public MetadataListWidget(int width, int height, int top, int bottom, int itemHeight, int color) {
        super(width, height, top, bottom, itemHeight, color);
    }

    @Override
    public Text getNarration(int index, T t) {
        if (t.isMetaLoaded()) {
            MetaData meta = t.getMeta();
            return new LiteralText(meta.title()).append("  ").append(new LiteralText(meta.author()).formatted(Formatting.BOLD, Formatting.GRAY));
        } else {
              if (!this.loadingSet.contains(t)) {
                  this.loadingSet.add(t);
                  ConcertoRunner.run(t::getMeta, () -> this.loadingSet.remove(t));
              }
            return new TranslatableText("concerto.loading");
        }
    }
}
