package top.gregtao.concerto.screen.widget;

import top.gregtao.concerto.core.api.WithMetaData;
import top.gregtao.concerto.core.music.Music;
import top.gregtao.concerto.core.music.meta.MetaData;
import top.gregtao.concerto.core.player.ConcertoPlayerList;
import top.gregtao.concerto.core.player.MusicPlayerHandler;
import top.gregtao.concerto.core.util.Pair;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class GeneralPlaylistWidget extends MetadataListWidget<GeneralPlaylistWidget.Entry> {

    public record Entry(UUID index, Music music) implements WithMetaData {

        @Override
        public MetaData getMeta() {
            return this.music.getMeta();
        }

        @Override
        public boolean isMetaLoaded() {
            return this.music.isMetaLoaded();
        }
    }

    public GeneralPlaylistWidget(int width, int height, int top, int itemHeight) {
        super(width, height, top, itemHeight);
        this.reset();
    }

    @Override
    public void onDoubleClicked(ConcertoListWidget<Entry>.Entry entry) {
        MusicPlayerHandler.INSTANCE.setCurrentIndex(entry.item.index);
    }

    public static Pair<List<Entry>, Entry> loadFromMusicList(ConcertoPlayerList list, UUID current) {
        AtomicReference<Entry> entry = new AtomicReference<>(null);
        return Pair.of(list.stream().map((pair) -> {
            Entry newEntry = new Entry(pair.getFirst(), pair.getSecond());
            if (pair.getFirst().equals(current)) {
                entry.set(newEntry);
            }
            return newEntry;
        }).toList(), entry.get());
    }

    public static Pair<List<Entry>, Entry> loadFromMusicList() {
        return loadFromMusicList(MusicPlayerHandler.INSTANCE.getMusicList(), MusicPlayerHandler.INSTANCE.getCurrentIndex());
    }

    public void reset() {
        Pair<List<Entry>, Entry> pair = loadFromMusicList();
        super.reset(pair.getFirst(), pair.getSecond());
    }

    public void reset(String keyword) {
        Pair<List<Entry>, Entry> pair = loadFromMusicList();
        super.reset(pair.getFirst(), pair.getSecond(), keyword);
    }
}
