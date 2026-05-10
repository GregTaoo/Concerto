package top.gregtao.concerto.core.player;

import top.gregtao.concerto.core.api.Copyable;
import top.gregtao.concerto.core.enums.OrderType;

import java.lang.reflect.Field;
import java.util.UUID;

public class MusicPlayerState implements Copyable<MusicPlayerState> {

    public boolean paused = true;
    public ConcertoPlayerList musicList = new ConcertoPlayerList();
    public UUID currentIndex = null;
    public OrderType orderType = OrderType.NORMAL;

    public static Field MUSIC_LIST, CURRENT_INDEX, ORDER_TYPE, PAUSED;

    static {
        try {
            MUSIC_LIST = MusicPlayerState.class.getDeclaredField("musicList");
            CURRENT_INDEX = MusicPlayerState.class.getDeclaredField("currentIndex");
            ORDER_TYPE = MusicPlayerState.class.getDeclaredField("orderType");
            PAUSED = MusicPlayerState.class.getDeclaredField("paused");
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    public MusicPlayerState(ConcertoPlayerList musicList, UUID currentIndex, OrderType orderType, boolean paused) {
        this.musicList = musicList;
        this.currentIndex = currentIndex;
        this.orderType = orderType;
        this.paused = paused;
    }

    public MusicPlayerState() {
    }

    public MusicPlayerState copy() {
        return new MusicPlayerState(this.musicList.copy(), this.currentIndex, this.orderType, this.paused);
    }
}
