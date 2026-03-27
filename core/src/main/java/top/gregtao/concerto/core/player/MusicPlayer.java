package top.gregtao.concerto.core.player;

import top.gregtao.concerto.core.Concerto;
import top.gregtao.concerto.core.event.ConcertoEvents;
import top.gregtao.concerto.core.music.Music;
import top.gregtao.concerto.core.music.MusicTimestamp;
import top.gregtao.concerto.core.music.lyrics.Lyrics;
import top.gregtao.concerto.core.music.meta.music.MusicMetaData;
import top.gregtao.concerto.core.player.streamplayer.enums.Status;
import top.gregtao.concerto.core.player.streamplayer.stream.*;
import top.gregtao.concerto.core.util.ConcertoRunner;
import top.gregtao.concerto.core.util.Pair;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class MusicPlayer extends StreamPlayer implements StreamPlayerListener {

    public static MusicPlayer INSTANCE;
    public static final Logger PLAYER_LOGGER;

    public Music currentMusic = null;
    public InputStream currentSource = null;
    public Lyrics currentLyrics = null, currentSubLyrics = null;
    public MusicMetaData currentMeta = null;
    private MusicTimestamp currentTime = null;
    private String[] displayTexts = new String[]{"", "", "", ""};
    private String timeFormat = "%s" + " ".repeat(30) + "%s";
    public float progressPercentage = 0;
    private long startTime = 0;

    public boolean started = false;
    public final Object playNextLock = new Object();
    public boolean isPlayingTemp = false;
    private volatile Music pendingMusic = null;

    static {
        PLAYER_LOGGER = Logger.getLogger(MusicPlayer.class.getName());
        File file = new File("Concerto");
        if (!file.exists() && !file.isDirectory() && !file.mkdir()) throw new RuntimeException("Cannot mkdir!");
        FileHandler fileHandler;
        try {
            fileHandler = new FileHandler("Concerto/player.log", false);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        fileHandler.setFormatter(new SimpleFormatter());
        PLAYER_LOGGER.addHandler(fileHandler);
        PLAYER_LOGGER.setLevel(Level.ALL);
        resetInstance();
    }

    public static void resetInstance() {
        try {
            if (INSTANCE != null && INSTANCE.currentSource != null)
                INSTANCE.currentSource.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        INSTANCE = new MusicPlayer(PLAYER_LOGGER);
    }

    public MusicPlayer(Logger logger) {
        super(logger);
        this.addStreamPlayerListener(this);
    }

    public synchronized void internalPlayMusic(Music music) {
        if (music == null) return;
        this.pendingMusic = music;
        ConcertoRunner.run(() -> {
            synchronized (this.playNextLock) {
                // 如果不匹配直接忽略
                if (this.pendingMusic != music) return;
                try {
                    this.isPlayingTemp = false;
                    this.stop();
                    this.resetInfo();
                    
                    this.currentMusic = music;
                    this.initMusicStatus();
                    this.updateDisplayTexts();
                    this.updateDisplayTexts(0);

                    InputStream source = music.getMusicSourceOrNull();
                    if (source == null) {
                        this.resetInfo();
                        Concerto.getLogger().error("Unable to play music: {} - {}", music.getMeta().title(), music.getMeta().author());
                        Concerto.getMinecraft().sendMessageToClientPlayer(
                                Concerto.getMinecraft().getTranslatableText("concerto.player.unable", music.getMeta().title(), music.getMeta().author(), music.getMeta().getSource()), false);

                        MusicPlayerHandler.INSTANCE.playNext(1);
                        return;
                    }

                    this.currentSource = source;

                    this.open(source);
                    this.play();
                    if (MusicPlayerHandler.INSTANCE.isForcePaused()) {
                        this.pause();
                    }
                    this.started = true;

                    Concerto.getLogger().info("Start playing music {} - {}", music.getMeta().title(), music.getMeta().author());
                    ConcertoEvents.ON_NEXT_MUSIC.emit(music);
                } catch (Exception e) {
                    Concerto.getLogger().error("Internal player error: " + e);
                    Concerto.getMinecraft().sendMessageToClientPlayer(
                            Concerto.getMinecraft().getTranslatableText("concerto.player.error", e.getMessage()), false);
                }
            }
        });
    }

    public boolean internalPause() {
        ConcertoEvents.ON_PLAYER_PAUSE.emit();
        return super.pause();
    }

    public boolean internalResume() {
        ConcertoEvents.ON_PLAYER_RESUME.emit();
        return super.resume();
    }

    public void resetInfo() {
        this.currentLyrics = this.currentSubLyrics = null;
        this.currentMeta = null;
        this.currentTime = MusicTimestamp.of(0);
        this.displayTexts = new String[]{"", "", "", ""};
        this.timeFormat = "%s" + " ".repeat(30) + "%s";
        this.progressPercentage = 0;
        this.startTime = 0;
        ConcertoEvents.ON_MUSIC_INFO_RESET.emit();
    }

    public void initMusicStatus() {
        if (this.currentMusic == null) return;
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

    public void initMusicStatus(long startTime) {
        this.initMusicStatus();
        this.startTime = startTime;
    }

    public void updateDisplayTexts() {
        if (this.currentMeta != null) {
            this.displayTexts[2] = this.currentMeta.title() + " | " + this.currentMeta.author() + " | " + this.currentMeta.getSource();
            MusicTimestamp timestamp = this.currentMeta.getDuration();
            this.timeFormat = "%s" + (timestamp == null ? "" : " ".repeat(30) + timestamp.toShortString());
            ConcertoEvents.ON_MUSIC_INFO_UPDATE.emit();
        } else this.displayTexts[2] = "";
    }

    public void updateDisplayTexts(long millisecond) {
        millisecond += this.startTime;
        if (this.currentMeta == null) return;
        MusicTimestamp duration = this.currentMeta.getDuration();
        this.progressPercentage = duration == null ? 0 : ((float) millisecond / duration.asMilliseconds());
        this.currentTime = MusicTimestamp.ofMilliseconds(millisecond);
        this.displayTexts[3] = this.timeFormat.formatted(this.currentTime.toShortString());

        if (this.currentLyrics != null) this.displayTexts[0] = this.currentLyrics.stayOrNext(millisecond);
        else if (millisecond < 5000)
            this.displayTexts[0] = Concerto.getMinecraft().getTranslatableText("concerto.no_subtitle");
        else this.displayTexts[0] = "";

        if (this.currentSubLyrics != null) this.displayTexts[1] = this.currentSubLyrics.stayOrNext(millisecond);
        else this.displayTexts[1] = "";
    }

    public String[] getDisplayTexts() {
        return this.displayTexts;
    }

    @Override
    public void statusUpdated(StreamPlayerEvent event) {
        Status status = event.getPlayerStatus();
        if (status == Status.EOM) {
            if (MusicPlayerHandler.INSTANCE.isEmpty()) {
                this.stop();
            } else if (!this.isPlayingTemp) {
                MusicPlayerHandler.INSTANCE.playNext(1);
            }
            this.isPlayingTemp = false;
        }
    }

    @Override
    public void opened(Object dataSource, Map<String, Object> properties) {
    }

    @Override
    public void progress(int nEncodedBytes, long microsecondPosition, byte[] pcmData, Map<String, Object> properties) {
        this.updateDisplayTexts(microsecondPosition / 1000);
    }

    @Override
    public void play() throws StreamPlayerException {
        super.play();
        ConcertoEvents.ON_PLAYER_START.emit();
    }

    public void stop() {
        this.started = false;
        super.stop();
    }

    public void playTempMusic(Music music, Runnable callback) {
        ConcertoRunner.run(() -> {
            synchronized (this.playNextLock) {
                this.started = true;
                this.stop();
                this.resetInfo();
                
                this.currentMusic = music;
                this.initMusicStatus();
                this.updateDisplayTexts();
                this.updateDisplayTexts(0);
                
                InputStream source = music.getMusicSourceOrNull();
                if (source == null) {
                    this.started = false;
                    this.resetInfo();
                    return;
                }
                
                this.currentSource = source;
                try {
                    this.open(source);
                    this.play();
                    if (MusicPlayerHandler.INSTANCE.isForcePaused()) {
                        this.pause();
                    }
                    this.isPlayingTemp = true;
                } catch (StreamPlayerException e) {
                    this.started = this.isPlayingTemp = false;
                }
            }
        }, callback);
    }

    public void playTempMusic(Music music) {
        this.playTempMusic(music, () -> {
        });
    }
}
