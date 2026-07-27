package top.gregtao.concerto.core.player;

import top.gregtao.concerto.core.api.Copyable;
import top.gregtao.concerto.core.enums.OrderType;
import top.gregtao.concerto.core.music.Music;
import top.gregtao.concerto.core.music.SharedMusic;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MusicPlayerState implements Copyable<MusicPlayerState> {

    public boolean paused = true;
    public ConcertoPlayerList musicList = new ConcertoPlayerList();
    public UUID currentIndex = null;
    public OrderType orderType = OrderType.NORMAL;
    public List<UUID> playbackHistory = new ArrayList<>();

    public static Field MUSIC_LIST, CURRENT_INDEX, ORDER_TYPE, PAUSED, PLAYBACK_HISTORY;

    static {
        try {
            MUSIC_LIST = MusicPlayerState.class.getDeclaredField("musicList");
            CURRENT_INDEX = MusicPlayerState.class.getDeclaredField("currentIndex");
            ORDER_TYPE = MusicPlayerState.class.getDeclaredField("orderType");
            PAUSED = MusicPlayerState.class.getDeclaredField("paused");
            PLAYBACK_HISTORY = MusicPlayerState.class.getDeclaredField("playbackHistory");
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    public MusicPlayerState(ConcertoPlayerList musicList, UUID currentIndex, OrderType orderType, boolean paused) {
        this(musicList, currentIndex, orderType, paused, List.of());
    }

    public MusicPlayerState(ConcertoPlayerList musicList, UUID currentIndex, OrderType orderType, boolean paused,
                            List<UUID> playbackHistory) {
        this.musicList = musicList;
        this.currentIndex = currentIndex;
        this.orderType = orderType;
        this.paused = paused;
        this.playbackHistory = new ArrayList<>(playbackHistory);
    }

    public MusicPlayerState() {
    }

    public MusicPlayerState copy() {
        return new MusicPlayerState(this.musicList.copy(), this.currentIndex, this.orderType, this.paused,
                this.playbackHistory);
    }

    public void setCurrentIndex(UUID target, int historyLimit) {
        if (!this.isHistoryMusic(this.currentIndex) || this.currentIndex.equals(target)) {
            this.currentIndex = target;
            this.trimPlaybackHistory(historyLimit);
            return;
        }
        if (!this.playbackHistory.isEmpty()
                && this.playbackHistory.get(this.playbackHistory.size() - 1).equals(target)) {
            this.playbackHistory.remove(this.playbackHistory.size() - 1);
        } else {
            this.playbackHistory.add(this.currentIndex);
        }
        this.currentIndex = target;
        this.trimPlaybackHistory(historyLimit);
    }

    public UUID getPreviousIndex(int historyLimit) {
        this.trimPlaybackHistory(historyLimit);
        return this.playbackHistory.isEmpty() ? null : this.playbackHistory.get(this.playbackHistory.size() - 1);
    }

    public void removeFromPlaybackHistory(UUID uuid) {
        this.playbackHistory.removeIf(uuid::equals);
    }

    public void clearPlaybackHistory() {
        this.playbackHistory.clear();
    }

    private boolean isHistoryMusic(UUID uuid) {
        Music music = uuid == null ? null : this.musicList.get(uuid);
        return music != null && !(music instanceof SharedMusic);
    }

    private void trimPlaybackHistory(int historyLimit) {
        int limit = Math.max(0, historyLimit);
        this.playbackHistory.removeIf(uuid -> !this.isHistoryMusic(uuid));
        while (this.playbackHistory.size() > limit) {
            this.playbackHistory.remove(0);
        }
    }
}
