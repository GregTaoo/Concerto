package top.gregtao.concerto.core.event;

import top.gregtao.concerto.core.music.Music;

public class ConcertoEvents {

    public static final Event ON_PLAYER_START = new Event();
    public static final Event ON_PLAYER_PAUSE = new Event();
    public static final Event ON_PLAYER_RESUME = new Event();

    public static final Event ON_MUSIC_INFO_RESET = new Event();
    public static final Event ON_MUSIC_INFO_UPDATE = new Event();

    public static final PayloadEvent<Music> ON_NEXT_MUSIC = new PayloadEvent<>();
}
