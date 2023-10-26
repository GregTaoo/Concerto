package top.gregtao.concerto.player;

import com.mojang.datafixers.util.Pair;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.api.LazyLoadable;
import top.gregtao.concerto.api.MusicJsonParsers;
import top.gregtao.concerto.music.lyric.Lyrics;
import top.gregtao.concerto.music.meta.music.MusicMetaData;
import top.gregtao.concerto.enums.OrderType;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.music.MusicTimestamp;
import top.gregtao.concerto.util.TextUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MusicPlayerHandler {

    public static MusicPlayerHandler INSTANCE = new MusicPlayerHandler();

    public static int MAX_SIZE = 1500;

    private ArrayList<Music> musicList = new ArrayList<>();

    private int currentIndex = -1;

    public Music currentMusic = null;

    public Lyrics currentLyrics = null, currentSubLyrics = null;

    public MusicMetaData currentMeta = null;

    private MusicTimestamp currentTime = null;

    private String[] displayTexts = new String[]{ "", "", "", ""}; // Lyrics; SubLyrics; Title | Author; Source | Time;

    private String timeFormat = "%s" + " ".repeat(30) + "%s";

    private OrderType orderType = OrderType.NORMAL;

    public float progressPercentage = 0;

    private final Random random = new Random();

    public MusicPlayerHandler() {}

    public MusicPlayerHandler(ArrayList<Music> musics, int currentIndex, OrderType orderType) {
        this.currentIndex = currentIndex;
        this.orderType = orderType;
        if (musics.size() > MAX_SIZE) {
            this.musicList = (ArrayList<Music>) musics.subList(0, MAX_SIZE - 1);
        } else {
            this.musicList = musics;
        }
        loadInThreadPool(this.musicList);
    }

    public static <T extends LazyLoadable> void loadInThreadPool(List<T> objects, boolean force) {
        ExecutorService service = Executors.newFixedThreadPool(64);
        objects.forEach(object -> {
            if (force || !object.isLoaded()) service.submit(() -> object.load());
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

    public static <T extends LazyLoadable> void loadInThreadPool(List<T> objects) {
        loadInThreadPool(objects, false);
    }

    public void resetInfo() {
        this.currentLyrics = this.currentSubLyrics = null;
        this.currentMeta = null;
        this.currentTime = MusicTimestamp.of(0);
        this.displayTexts = new String[]{ "", "", "", ""};
        this.timeFormat = "%s" + " ".repeat(30) + "%s";
        this.progressPercentage = 0;
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
            this.displayTexts[2] = TextUtil.cutIfTooLong(this.currentMeta.title(), 50) + " | " +
                    TextUtil.cutIfTooLong(this.currentMeta.author(), 40) + " | " + this.currentMeta.getSource();
            MusicTimestamp timestamp = this.currentMeta.getDuration();
            this.timeFormat = "%s" + (timestamp == null ? "" : " ".repeat(30) + this.currentMeta.getDuration().toShortString());
        } else {
            this.displayTexts[2] = "";
        }
    }

    public void updateDisplayTexts(long millisecond) {
        MusicTimestamp duration = this.currentMeta.getDuration();
        this.progressPercentage = duration == null ? 0 : ((float) millisecond / duration.asMilliseconds());
        this.currentTime = MusicTimestamp.ofMilliseconds(millisecond);
        this.displayTexts[3] = this.timeFormat.formatted(this.currentTime.toShortString());
        if (this.currentLyrics != null) {
            this.displayTexts[0] = this.currentLyrics.stayOrNext(millisecond).getString();
        } else {
            this.displayTexts[0] = Text.translatable("concerto.no_caption").getString();
        }
        if (this.currentSubLyrics != null) {
            this.displayTexts[1] = this.currentSubLyrics.stayOrNext(millisecond).getString();
        } else {
            this.displayTexts[1] = "";
        }
    }

    public Music playNext(int forward) {
        if (this.musicList.isEmpty()) return null;
        this.displayTexts[2] = Text.translatable("concerto.loading").getString();
        this.currentIndex = this.getNext(forward);
        try {
            this.currentMusic = this.musicList.get(this.currentIndex);
        } catch (IndexOutOfBoundsException e) {
            return this.currentMusic = null;
        }
        this.initMusicStatus();
        this.updateDisplayTexts();
        this.writeConfig();
        return this.currentMusic;
    }

    public void initMusicStatus() {
        this.currentMeta = this.currentMusic.getMeta();
        try {
            Pair<Lyrics, Lyrics> lyrics = this.currentMusic.getLyric();
            this.currentLyrics = lyrics.getFirst();
            this.currentSubLyrics = lyrics.getSecond();
        } catch (Exception e) {
            this.currentLyrics = null;
        }
        this.displayTexts[2] = "";
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
            return MathHelper.clamp(this.currentIndex, 0, this.getMusicList().size() - 1);
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
