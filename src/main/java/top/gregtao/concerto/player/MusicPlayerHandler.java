package top.gregtao.concerto.player;

import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.api.CacheableMusic;
import top.gregtao.concerto.api.LazyLoadable;
import top.gregtao.concerto.api.MusicJsonParsers;
import top.gregtao.concerto.music.lyrics.Lyrics;
import top.gregtao.concerto.music.meta.music.MusicMetaData;
import top.gregtao.concerto.enums.OrderType;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.music.MusicTimestamp;
import top.gregtao.concerto.util.Pair;
import top.gregtao.concerto.util.TextUtil;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

public class MusicPlayerHandler {

    public static MusicPlayerHandler INSTANCE = new MusicPlayerHandler();

    public static int MAX_SIZE = 10000;

    private ArrayList<Music> musicList = new ArrayList<>();

    private int currentIndex = -1;

    public Music currentMusic = null;

    public InputStream currentSource = null;

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
        try (ExecutorService service = Executors.newFixedThreadPool(32)) {
            objects.forEach(object -> {
                if (force || !object.isLoaded()) service.submit(() -> object.load());
            });
            service.shutdown();
            try {
                if (!service.awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS)) {
                    throw new TimeoutException();
                }
            } catch (InterruptedException | TimeoutException e) {
                throw new RuntimeException(e);
            }
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
        try {
            if (this.currentSource != null)
                this.currentSource.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
        } else if (millisecond < 5000) {
            this.displayTexts[0] = Text.translatable("concerto.no_subtitle").getString();
        } else {
            this.displayTexts[0] = "";
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
            Pair<Lyrics, Lyrics> lyrics = this.currentMusic.getLyrics();
            if (lyrics != null) {
                this.currentLyrics = (lyrics.getFirst() == null || lyrics.getFirst().isEmpty()) ? null : lyrics.getFirst();
                this.currentSubLyrics = (lyrics.getSecond() == null || lyrics.getSecond().isEmpty()) ? null : lyrics.getSecond();
            }
        } catch (Exception e) {
            this.currentLyrics = this.currentSubLyrics = null;
        }
        this.displayTexts[2] = "";
    }

    public void removeCurrent() {
        if (this.musicList.size() == 1) {
            this.clear();
        } else if (this.currentIndex < this.musicList.size()) {
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

    private static final Pattern ILLEGAL_CHARS = Pattern.compile("[\\\\/:*?\"<>|]");
    public static String filenameFilter(String str) {
        return ILLEGAL_CHARS.matcher(str).replaceAll(" ");
    }

    public static void downloadMusics(List<Music> musics) {
        MusicPlayer.run(() -> {
            File file = new File("Concerto/Downloads");
            if (!file.exists() || !file.isDirectory()) {
                if (!file.mkdirs()) return;
            }
            try (ExecutorService service = Executors.newFixedThreadPool(32)) {
                musics.forEach(music -> {
                    if (music instanceof CacheableMusic cacheableMusic) {
                        service.submit(() -> {
                            MusicMetaData metaData = music.getMeta();
                            String filename = filenameFilter(metaData.title() + " - " + metaData.author());
                            File file1 = file.toPath().resolve(filename + "." + cacheableMusic.getSuffix()).toFile();
                            try {
                                if (!file1.exists()) {
                                    if (file1.createNewFile()) {
                                        try (FileOutputStream stream = new FileOutputStream(file1)) {
                                            stream.write(music.getMusicSource().readAllBytes());
                                        }
                                    }
                                    ConcertoClient.LOGGER.info("Downloaded: {}", filename);
                                }
                            } catch (IOException e) {
                                ConcertoClient.LOGGER.error("{} - {}", e, file1.getAbsolutePath());
                            }
                        });
                    } else {
                        ConcertoClient.LOGGER.info("Detected non-cacheable music");
                    }
                });
                service.shutdown();
                try {
                    if (!service.awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS)) {
                        throw new TimeoutException();
                    }
                } catch (InterruptedException | TimeoutException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }
}
