package top.gregtao.concerto.screen.widget;

import net.minecraft.text.Text;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.music.meta.music.MusicMetaData;
import top.gregtao.concerto.util.Pair;

import java.util.UUID;

public class MusicWithUUIDListWidget extends ConcertoListWidget<Pair<Music, UUID>> {

    public MusicWithUUIDListWidget(int width, int height, int top, int itemHeight) {
        this(width, height, top, itemHeight, 0xffffffff);
    }

    @Override
    public Text getNarration(int index, Pair<Music, UUID> t) {
        if (t.getFirst().isMetaLoaded()) {
            MusicMetaData meta = t.getFirst().getMeta();
            return Text.literal(meta.title() + " - " + meta.getSource());
        } else {
            return Text.translatable("concerto.loading");
        }
    }

    public MusicWithUUIDListWidget(int width, int height, int top, int itemHeight, int color) {
        super(width, height, top, itemHeight, color);
    }
}
