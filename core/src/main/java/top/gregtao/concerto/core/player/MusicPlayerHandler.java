package top.gregtao.concerto.core.player;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.ArtworkFactory;
import top.gregtao.concerto.core.Concerto;
import top.gregtao.concerto.core.api.CacheableMusic;
import top.gregtao.concerto.core.api.LazyLoadable;
import top.gregtao.concerto.core.api.MusicJsonParsers;
import top.gregtao.concerto.core.event.ConcertoEvents;
import top.gregtao.concerto.core.music.lyrics.Lyrics;
import top.gregtao.concerto.core.music.meta.music.MusicMetaData;
import top.gregtao.concerto.core.enums.OrderType;
import top.gregtao.concerto.core.music.Music;
import top.gregtao.concerto.core.music.MusicTimestamp;
import top.gregtao.concerto.core.network.SyncRecord;
import top.gregtao.concerto.core.util.ConcertoRunner;
import top.gregtao.concerto.core.util.MathUtil;
import top.gregtao.concerto.core.util.Pair;

import java.io.*;
import java.nio.charset.StandardCharsets;
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

    public SyncRecord<MusicPlayerState> playerState;
//    private ArrayList<Music> musicList = new ArrayList<>();
//    private int currentIndex = -1;
//    private OrderType orderType = OrderType.NORMAL;

    public Music currentMusic = null;

    public InputStream currentSource = null;

    public Lyrics currentLyrics = null, currentSubLyrics = null;

    public MusicMetaData currentMeta = null;

    private MusicTimestamp currentTime = null;

    private String[] displayTexts = new String[]{ "", "", "", ""}; // Lyrics; SubLyrics; Title | Author; Source | Time;

    private String timeFormat = "%s" + " ".repeat(30) + "%s";

    public float progressPercentage = 0;

    private long startTime = 0;

    private final Random random = new Random();

    public MusicPlayerHandler() {}

    public MusicPlayerHandler(ArrayList<Music> musics, int currentIndex, OrderType orderType) {
//        this.currentIndex = currentIndex;
//        this.orderType = orderType;
        if (musics.size() > MAX_SIZE) {
            musics = (ArrayList<Music>) musics.subList(0, MAX_SIZE - 1);
        }
        loadInThreadPool(musics);
        MusicPlayerState state = new MusicPlayerState(musics, currentIndex, orderType, true);
        this.playerState = MusicPlayerState.createLocalRecord(state);
    }

    public static <T extends LazyLoadable> void loadInThreadPool(List<T> objects, boolean force) {
        ExecutorService service = Executors.newFixedThreadPool(32);
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
        this.startTime = 0;
        ConcertoEvents.ON_MUSIC_INFO_RESET.emit();
    }

    public void clear() {
        try {
            if (this.currentSource != null)
                this.currentSource.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.resetInfo();
        this.playerState.set((state) -> new MusicPlayerState());
        this.writeConfig();
    }

    public boolean addMusic(Music music) {
        if (this.playerState.get().musicList.size() - this.maxRemovable() >= MAX_SIZE) {
            return false;
        }
        this.playerState.set((state) -> {
            this.removeMusic(state.musicList.size() + 1 - MAX_SIZE);
            if (!music.isLoaded()) music.load();
            state.musicList.add(music);
            return state;
        });
        this.writeConfig();
        return true;
    }

    public boolean addMusic(List<Music> musics) {
        if (musics.size() + this.playerState.get().musicList.size() - this.maxRemovable() > MAX_SIZE) {
            return false;
        }
        this.playerState.set((state) -> {
            this.removeMusic(state.musicList.size() + musics.size() - MAX_SIZE);
            loadInThreadPool(musics);
            state.musicList.addAll(musics);
            return state;
        });
        this.writeConfig();
        return true;
    }

    public void addMusicHere(Music music) {
        if (!music.isLoaded()) music.load();
        this.playerState.set((state) -> {
            state.musicList.add(this.getCurrentIndex() + 1, music);
            return state;
        });
        this.writeConfig();
    }

    private int maxRemovable() {
        MusicPlayerState state = this.playerState.get();
        return state.orderType == OrderType.REVERSED ? state.musicList.size() - state.currentIndex - 1 : state.currentIndex;
    }

    private void removeMusic(int n) {
        this.playerState.set((state) -> {
            int i = n;
            if (state.orderType == OrderType.REVERSED) {
                while (i-- > 0) {
                    state.musicList.remove(state.musicList.size() - 1);
                }
            } else {
                while (i-- > 0) {
                    state.musicList.remove(0);
                }
            }
            return state;
        });
    }

    public void updateDisplayTexts() {
        if (this.currentMeta != null) {
            this.displayTexts[2] = this.currentMeta.title() + " | " + this.currentMeta.author() + " | " + this.currentMeta.getSource();
            MusicTimestamp timestamp = this.currentMeta.getDuration();
            this.timeFormat = "%s" + (timestamp == null ? "" : " ".repeat(30) + this.currentMeta.getDuration().toShortString());
            ConcertoEvents.ON_MUSIC_INFO_UPDATE.emit();
        } else {
            this.displayTexts[2] = "";
        }
    }

    public void updateDisplayTexts(long millisecond) {
        millisecond += this.startTime;
        MusicTimestamp duration = this.currentMeta.getDuration();
        this.progressPercentage = duration == null ? 0 : ((float) millisecond / duration.asMilliseconds());
        this.currentTime = MusicTimestamp.ofMilliseconds(millisecond);
        this.displayTexts[3] = this.timeFormat.formatted(this.currentTime.toShortString());
        if (this.currentLyrics != null) {
            this.displayTexts[0] = this.currentLyrics.stayOrNext(millisecond);
        } else if (millisecond < 5000) {
            this.displayTexts[0] = Concerto.getMinecraft().getTranslatableText("concerto.no_subtitle");
        } else {
            this.displayTexts[0] = "";
        }
        if (this.currentSubLyrics != null) {
            this.displayTexts[1] = this.currentSubLyrics.stayOrNext(millisecond);
        } else {
            this.displayTexts[1] = "";
        }
    }

    public Music playNext(int forward) {
        if (this.playerState.get().musicList.isEmpty()) return null;
        this.displayTexts[2] = Concerto.getMinecraft().getTranslatableText("concerto.loading");
        try {
            this.playerState.set((state) -> {
                state.currentIndex = this.getNext(forward);
                this.currentMusic = state.musicList.get(state.currentIndex);
                return state;
            });
        } catch (IndexOutOfBoundsException e) {
            return this.currentMusic = null;
        }
        this.initMusicStatus();
        this.updateDisplayTexts();
        this.writeConfig();
        return this.currentMusic;
    }

    public void initMusicStatus(long startTime) {
        this.initMusicStatus();
        this.startTime = startTime;
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
        this.playerState.set((state) -> {
            if (state.musicList.size() == 1) {
                this.clear();
            } else if (state.currentIndex < state.musicList.size()) {
                state.musicList.remove(state.currentIndex);
            }
            return state;
        });
    }

    public void remove(int index) {
        this.playerState.set((state) -> {
            if (index <= state.currentIndex) state.currentIndex--;
            if (index < state.musicList.size()) {
                state.musicList.remove(index);
            }
            return state;
        });
    }

    private int getNext(int forward) {
        MusicPlayerState state = this.playerState.get();
        if (forward == 0) {
            return MathUtil.clamp(state.currentIndex, 0, this.getMusicList().size() - 1);
        } else if (state.orderType == OrderType.NORMAL) {
            return (state.currentIndex + forward) % state.musicList.size();
        } else if (state.orderType == OrderType.REVERSED) {
            forward %= state.musicList.size();
            if (state.currentIndex - forward < 0) {
                return state.musicList.size() - (forward - state.currentIndex);
            } else {
                return state.currentIndex - forward;
            }
        } else if (state.orderType == OrderType.LOOP) {
            return state.currentIndex;
        } else {
            return state.musicList.isEmpty() ? -1 : this.random.nextInt(state.musicList.size());
        }
    }

    public void setOrderType(OrderType type) {
        this.playerState.set((state) -> {
            state.orderType = type;
            return state;
        });
        this.writeConfig();
    }

    public OrderType getOrderType() {
        return this.playerState.get().orderType;
    }

    public boolean isEmpty() {
        return this.playerState.get().musicList.isEmpty();
    }

    public String[] getDisplayTexts() {
        return this.displayTexts;
    }

    public Music getCurrentMusic() {
        return this.currentMusic;
    }

    public int getCurrentIndex() {
        return MathUtil.clamp(0, this.playerState.get().currentIndex, this.playerState.get().musicList.size() - 1);
    }

    public ArrayList<Music> getMusicList() {
        return this.playerState.get().musicList;
    }

    public void setCurrentIndex(int index) {
        this.playerState.set((state) -> {
            state.currentIndex = index;
            return state;
        });
    }

    public void writeConfig() {
        Concerto.MUSIC_CONFIG.write(MusicJsonParsers.toRaw(this));
    }

    private static final Pattern ILLEGAL_CHARS = Pattern.compile("[\\\\/:*?\"<>|]");
    public static String filenameFilter(String str) {
        return ILLEGAL_CHARS.matcher(str).replaceAll(" ");
    }

    public static void downloadMusics(List<Music> musics) {
        ConcertoRunner.run(() -> {
            File folder = new File("Concerto/Downloads");
            if (!folder.exists() || !folder.isDirectory()) {
                if (folder.mkdirs()) {
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    return;
                }
            }
            ExecutorService service = Executors.newFixedThreadPool(16);
            musics.forEach(music -> {
                if (music instanceof CacheableMusic cacheableMusic) {
                    service.submit(() -> {
                        MusicMetaData metaData = music.getMeta();
                        String filename = filenameFilter(metaData.title() + " - " + metaData.author() + " - " + metaData.getSource());
                        File file = folder.toPath().resolve(filename + "." + cacheableMusic.getSuffix()).toFile();
                        File lrcFile = folder.toPath().resolve(filename + ".lrc").toFile();
                        try {
                            if (!file.exists()) {
                                if (file.createNewFile()) {
                                    try (FileOutputStream stream = new FileOutputStream(file)) {
                                        stream.write(music.getMusicSource().readAllBytes());
                                    }
                                }
                                Concerto.getLogger().info("Downloaded: {}", filename);
                            }
                            String lyrics = music.getLyrics().getFirst().toString();
                            try {
                                AudioFile audioFile = AudioFileIO.read(file);
                                Tag tag = audioFile.getTagOrCreateAndSetDefault();
                                tag.setField(FieldKey.TITLE, metaData.title());
                                tag.setField(FieldKey.ARTISTS, metaData.author());
                                tag.setField(FieldKey.LYRICS, lyrics);
                                if (!metaData.headPictureUrl().isEmpty()) {
                                    tag.setField(ArtworkFactory.createLinkedArtworkFromURL(metaData.headPictureUrl()));
                                }
                                audioFile.commit();
                            } catch (Exception e) {
                                Concerto.getLogger().warn("Cannot write tags into file: {}", file);
                            }
                            if (!lrcFile.exists()) {
                                if (lrcFile.createNewFile()) {
                                    try (FileOutputStream stream = new FileOutputStream(lrcFile)) {
                                        stream.write(lyrics.getBytes(StandardCharsets.UTF_8));
                                    }
                                }
                                Concerto.getLogger().info("Downloaded LRC: {}", filename);
                            }
                        } catch (IOException e) {
                            Concerto.getLogger().error("{} - {}", e, file.getAbsolutePath());
                        }
                    });
                } else {
                    Concerto.getLogger().info("Detected non-cacheable music");
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
        });
    }
}
