package top.gregtao.concerto.screen.widget;

import net.minecraft.text.Text;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.music.meta.music.MusicMetaData;
import top.gregtao.concerto.util.Pair;

import java.util.UUID;

public class MusicWithUUIDListWidget extends ConcertoListWidget<Pair<Music, UUID>> {

    public MusicWithUUIDListWidget(int width, int height, int top, int bottom, int itemHeight) {
        this(width, height, top, bottom, itemHeight, 0xffffffff);
    }

    @Override
    public Text getNarration(int index, Pair<Music, UUID> t) {
        MusicMetaData meta = t.getFirst().getMeta();
        return Text.literal(meta.title() + " - " + meta.getSource());
    }

    public MusicWithUUIDListWidget(int width, int height, int top, int bottom, int itemHeight, int color) {
        super(width, height, top, bottom, itemHeight, color);
    }
}
