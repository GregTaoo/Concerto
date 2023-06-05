package top.gregtao.concerto.player;

import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.api.LazyLoadable;
import top.gregtao.concerto.api.MusicJsonParsers;
import top.gregtao.concerto.music.meta.music.MusicMeta;
import top.gregtao.concerto.enums.OrderType;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.music.MusicTimestamp;
import top.gregtao.concerto.music.lyric.Lyric;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MusicPlayerStatus {
    public static MusicPlayerStatus INSTANCE = new MusicPlayerStatus();

    public static int MAX_SIZE = 1500;

    private ArrayList<Music> musicList = new ArrayList<>();

    private int currentIndex = -1;

    public Music currentMusic = null;

    public Lyric currentLyric = null;

    public MusicMeta currentMeta = null;

    private MusicTimestamp currentTime = null;

    private String[] displayTexts = new String[]{ "", "", ""}; // Caption; Title | Author; Source | Time;

    private String timeFormat = "%s - %s";

    private OrderType orderType = OrderType.NORMAL;

    private final Random random = new Random();

    public MusicPlayerStatus() {}

    public MusicPlayerStatus(ArrayList<Music> musics, int currentIndex, OrderType orderType) {
        this.currentIndex = currentIndex;
        this.orderType = orderType;
        if (musics.size() > MAX_SIZE) {
            this.musicList = (ArrayList<Music>) musics.subList(0, MAX_SIZE - 1);
        } else {
            this.musicList = musics;
        }
        loadInThreadPool(this.musicList);
    }

    public static <T extends LazyLoadable> void loadInThreadPool(List<T> objects) {
        ExecutorService service = Executors.newFixedThreadPool(64);
        objects.forEach(object -> {
            if (!object.isLoaded()) service.submit(() -> object.load());
        });
        service.shutdown();
        try {
            if (!service.awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS)) {
                throw new Exception();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void resetInfo() {
        this.currentLyric = null;
        this.currentMeta = null;
        this.currentTime = MusicTimestamp.of(0);
        this.displayTexts = new String[]{ "", "", ""};
        this.timeFormat = "%s - %s";
    }

    public void clear() {
        this.resetInfo();
        this.musicList.clear();
        this.orderType = OrderType.NORMAL;
        this.currentIndex = -1;
        this.writeConfig();
    }

    public boolean addMusic(Music music) {
        if (this.musicList.size() - this.maxRemovable() >= MAX_SIZE) return false;
        this.removeMusic(this.musicList.size() + 1 - MAX_SIZE);
        if (!music.isLoaded()) music.load();
        this.musicList.add(music);
        this.writeConfig();
        return true;
    }

    public boolean addMusic(List<Music> musics) {
        if (musics.size() + this.musicList.size() - this.maxRemovable() > MAX_SIZE) return false;
        this.removeMusic(this.musicList.size() + musics.size() - MAX_SIZE);
        loadInThreadPool(musics);
        this.musicList.addAll(musics);
        this.writeConfig();
        return true;
    }

    public void addMusicHere(Music music) {
        if (!music.isLoaded()) music.load();
        this.musicList.add(this.getCurrentIndex() + 1, music);
        this.writeConfig();
    }

    private int maxRemovable() {
        return this.orderType == OrderType.REVERSED ? this.musicList.size() - this.currentIndex - 1 : this.currentIndex;
    }

    private void removeMusic(int size) {
        if (this.orderType == OrderType.REVERSED) {
            while (size-- > 0) {
                this.musicList.remove(this.musicList.size() - 1);
            }
        } else {
            while (size-- > 0) {
                this.musicList.remove(0);
            }
        }
    }

    public void updateDisplayTexts() {
        if (this.currentMeta != null) {
            String[] strings = this.currentMeta.asString().split("\n");
            this.displayTexts[1] = strings[0];
            this.timeFormat = strings[1];
        } else {
            this.displayTexts[1] = "";
        }
    }

    public void updateDisplayTexts(long millisecond) {
        this.currentTime = MusicTimestamp.of(millisecond);
        this.displayTexts[2] = this.timeFormat.formatted(this.currentTime.toStringWithoutMillisecond(), this.orderType.getName().getString())
                + " - " + (MusicPlayer.INSTANCE.isPlayingTemp ? "?" : this.currentIndex + 1) + "/" + this.musicList.size();
        if (this.currentLyric != null) {
            this.displayTexts[0] = this.currentLyric.stayOrNext(millisecond).getString();
        } else {
            this.displayTexts[0] = Text.translatable("concerto.no_caption").getString();
        }
    }

    public Music playNext(int forward) {
        if (this.musicList.isEmpty()) return null;
        this.displayTexts[1] = Text.translatable("concerto.loading").getString();
        this.currentIndex = this.getNext(forward);
        try {
            this.currentMusic = this.musicList.get(currentIndex);
        } catch (Exception e) {
            return this.currentMusic = null;
        }
        this.setupMusicStatus();
        this.updateDisplayTexts();
        this.writeConfig();
        return this.currentMusic;
    }

    public void setupMusicStatus() {
        this.currentMeta = this.currentMusic.getMeta();
        try {
            this.currentLyric = this.currentMusic.getLyric();
        } catch (Exception e) {
            this.currentLyric = null;
        }
        this.displayTexts[1] = "";
    }

    public void removeCurrent() {
        if (this.currentIndex < this.musicList.size()) {
            this.musicList.remove(this.currentIndex);
        }
    }

    public void remove(int index) {
        if (index <= this.currentIndex) this.currentIndex--;
        if (index < this.musicList.size()) {
            this.musicList.remove(index);
        }
    }

    private int getNext(int forward) {
        if (forward == 0) {
            return Math.max(Math.min(this.currentIndex, this.getMusicList().size() - 1), 0);
        } else if (this.orderType == OrderType.NORMAL) {
            return (this.currentIndex + forward) % this.musicList.size();
        } else if (this.orderType == OrderType.REVERSED) {
            forward %= this.musicList.size();
            if (this.currentIndex - forward < 0) {
                return this.musicList.size() - (forward - this.currentIndex);
            } else {
                return this.currentIndex - forward;
            }
        } else if (this.orderType == OrderType.LOOP) {
            return this.currentIndex;
        } else {
            return this.musicList.isEmpty() ? -1 : this.random.nextInt(this.musicList.size());
        }
    }

    public void setOrderType(OrderType type) {
        this.orderType = type;
        this.writeConfig();
    }

    public OrderType getOrderType() {
        return this.orderType;
    }

    public boolean isEmpty() {
        return this.musicList.isEmpty();
    }

    public String[] getDisplayTexts() {
        return this.displayTexts;
    }

    public Music getCurrentMusic() {
        return this.currentMusic;
    }

    public int getCurrentIndex() {
        return MathHelper.clamp(0, this.currentIndex, this.musicList.size() - 1);
    }

    public ArrayList<Music> getMusicList() {
        return this.musicList;
    }

    public void setCurrentIndex(int index) {
        this.currentIndex = index;
    }

    public void writeConfig() {
        ConcertoClient.MUSIC_CONFIG.write(MusicJsonParsers.toRaw(this));
    }
}
