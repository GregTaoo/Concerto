package top.gregtao.concerto.core.event;

import top.gregtao.concerto.core.enums.OrderType;
import top.gregtao.concerto.core.music.Music;

public class ConcertoEvents {

    public static final Event ON_PLAYER_START = new Event();
    public static final Event ON_PLAYER_PAUSE = new Event();
    public static final Event ON_PLAYER_RESUME = new Event();

    public static final Event ON_MUSIC_INFO_RESET = new Event();
    public static final Event ON_MUSIC_INFO_UPDATE = new Event();

    public static final PayloadEvent<Music> ON_NEW_MUSIC_STARTED = new PayloadEvent<>();
    public static final PayloadEvent<Long> ON_PLAYER_SEEK = new PayloadEvent<>();
    public static final PayloadEvent<OrderType> ON_PLAYER_ORDER_UPDATE = new PayloadEvent<>();
    public static final Event ON_MUSIC_LIST_UPDATE = new Event();
}
