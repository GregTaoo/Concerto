package top.gregtao.concerto.screen.widget;

import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.player.MusicPlayerStatus;

public class GeneralPlaylistWidget extends ConcertoListWidget<Music> {

    public GeneralPlaylistWidget(int width, int height, int top, int bottom, int itemHeight) {
        super(width, height, top, bottom, itemHeight);
        this.reset();
    }

    public void reset() {
        super.reset(MusicPlayerStatus.INSTANCE.getMusicList() ,MusicPlayerStatus.INSTANCE.currentMusic);
    }
}
