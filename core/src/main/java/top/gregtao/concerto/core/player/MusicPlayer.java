package top.gregtao.concerto.core.player;

import top.gregtao.concerto.core.Concerto;
import top.gregtao.concerto.core.event.ConcertoEvents;
import top.gregtao.concerto.core.music.Music;
import top.gregtao.concerto.core.music.MusicTimestamp;
import top.gregtao.concerto.core.music.PathFileMusic;
import top.gregtao.concerto.core.music.SharedMusic;
import top.gregtao.concerto.core.music.lyrics.Lyrics;
import top.gregtao.concerto.core.music.meta.music.MusicMetaData;
import top.gregtao.concerto.core.player.seek.ProgressiveDataSource;
import top.gregtao.concerto.core.player.seek.ProgressiveMediaDataSource;
import top.gregtao.concerto.core.room.MusicRoom;
import top.gregtao.concerto.core.player.streamplayer.enums.Status;
import top.gregtao.concerto.core.player.streamplayer.stream.StreamPlayer;
import top.gregtao.concerto.core.player.streamplayer.stream.StreamPlayerEvent;
import top.gregtao.concerto.core.player.streamplayer.stream.StreamPlayerException;
import top.gregtao.concerto.core.player.streamplayer.stream.StreamPlayerListener;
import top.gregtao.concerto.core.api.CacheableMusic;
import top.gregtao.concerto.core.util.Pair;
import top.gregtao.concerto.core.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class MusicPlayer extends StreamPlayer implements StreamPlayerListener {

    public static MusicPlayer INSTANCE;
    public static final Logger PLAYER_LOGGER;

    public Music currentMusic = null;
    public InputStream currentSource = null;
    private ProgressiveMediaDataSource currentMediaSource = null;
    public Lyrics currentLyrics = null, currentSubLyrics = null;
    public int[] currentSubLyricsMapping = new int[0];
    public MusicMetaData currentMeta = null;
    private MusicTimestamp currentTime = null;
    private String[] displayTexts = new String[]{"", "", "", ""};
    private String timeFormat = "%s" + " ".repeat(30) + "%s";
    public float progressPercentage = 0;
    private long startTime = 0;
    private long currentTimeUpdatedAtMs = 0;
    private volatile boolean seekDisplayLocked = false;

    public boolean started = false;
    public final AtomicBoolean playNextLock = new AtomicBoolean(false);
    public boolean isPlayingTemp = false;
    private final ExecutorService playbackExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean playbackWorkerScheduled = new AtomicBoolean(false);

    public final AudioSpectrum audioSpectrum = new AudioSpectrum();

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
        this.enqueuePlaybackRequest(music, false, null);
    }

    public void playTempMusic(Music music, Runnable callback) {
        if (music == null) return;
        this.enqueuePlaybackRequest(music, true, callback);
    }

    public void playTempMusic(Music music) {
        this.playTempMusic(music, null);
    }

    private void tryCloseStream(InputStream source) {
        if (source == null) return;
        try {
            source.close();
        } catch (IOException ignored) {
        }
    }

    private void handlePlaybackFailure(InputStream source, Exception e) {
        this.tryCloseStream(source);
        this.tryCloseMediaSource(this.currentMediaSource);
        this.currentMediaSource = null;
        this.resetInfo();
        Concerto.getLogger().error("Internal player error: " + e);
        Concerto.getCoreBridge().sendTranslatableToClientPlayer("concerto.player.error", false, e.getMessage());
    }

    private void enqueuePlaybackRequest(Music music, boolean temp, Runnable callback) {
        if (this.playbackWorkerScheduled.compareAndSet(false, true)) {
            this.playbackExecutor.execute(() -> this.drainPlaybackRequests(music, temp, callback));
        }
    }

    private void drainPlaybackRequests(Music music, boolean temp, Runnable callback) {
        try {
            try {
                this.playManagedMusic(music, temp);
            } catch (Exception e) {
                this.handlePlaybackFailure(null, e);
            }
            if (callback != null) {
                callback.run();
            }
        } finally {
            this.playbackWorkerScheduled.set(false);
        }
    }

    private void playManagedMusic(Music music, boolean temp) {
        this.playNextLock.set(true);
        InputStream source = null;
        ProgressiveMediaDataSource mediaSource = null;
        try {
            this.stop();
            mediaSource = music.createProgressiveMediaDataSource();
            if (mediaSource == null) {
                this.resetInfo();
                Concerto.getLogger().error("Unable to play music: {} - {}", music.getMeta().title(), music.getMeta().author());
                Concerto.getCoreBridge().sendTranslatableToClientPlayer(
                        "concerto.player.unable", false, music.getMeta().title(), music.getMeta().author(), music.getMeta().getSource());
                return;
            }

            this.currentMusic = music;
            long sharedStartTime = music instanceof SharedMusic sharedMusic ? sharedMusic.getStartTime() : 0L;
            this.initMusicStatus(sharedStartTime);
            this.updateDisplayTexts();
            this.updateDisplayTexts(0);

            ProgressiveDataSource progressiveSource = new ProgressiveDataSource(mediaSource, this.inferSuffix(music, mediaSource));
            if (sharedStartTime > 0L) {
                progressiveSource.seekToMilliseconds(sharedStartTime);
            }
            this.open(progressiveSource);

            this.currentSource = null;
            this.currentMediaSource = mediaSource;
            this.play();
            if (MusicPlayerHandler.INSTANCE.isForcePaused()) {
                this.pause();
            }
            this.started = true;
            this.isPlayingTemp = temp;

            Concerto.getLogger().info("Start playing music {} - {}", music.getMeta().title(), music.getMeta().author());
            ConcertoEvents.ON_NEW_MUSIC_STARTED.emit(music);
        } catch (Exception e) {
            this.handlePlaybackFailure(source, e);
            this.tryCloseMediaSource(mediaSource);
        } finally {
            this.playNextLock.set(false);
        }
    }

    private String inferSuffix(Music music, ProgressiveMediaDataSource mediaSource) {
        if (music instanceof CacheableMusic cacheableMusic) {
            return cacheableMusic.getSuffix();
        }
        if (music instanceof PathFileMusic pathFileMusic) {
            return FileUtil.getSuffix(pathFileMusic.getRawPath());
        }
        String suffix = mediaSource.getSuggestedSuffix();
        return suffix == null ? "" : suffix;
    }

    private void tryCloseMediaSource(ProgressiveMediaDataSource source) {
        if (source == null) return;
        try {
            source.close();
        } catch (IOException ignored) {
        }
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
        this.started = false;
        this.isPlayingTemp = false;
        this.currentMusic = null;
        this.currentSource = null;
        this.currentMediaSource = null;

        this.currentLyrics = this.currentSubLyrics = null;
        this.currentSubLyricsMapping = new int[0];
        this.currentMeta = null;
        this.currentTime = MusicTimestamp.of(0);
        this.displayTexts = new String[]{"", "", "", ""};
        this.timeFormat = "%s" + " ".repeat(30) + "%s";
        this.progressPercentage = 0;
        this.startTime = 0;
        this.currentTimeUpdatedAtMs = 0;
        this.seekDisplayLocked = false;
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
        this.currentSubLyricsMapping = Lyrics.createTimestampMapping(this.currentLyrics, this.currentSubLyrics, 500);
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
        this.currentTimeUpdatedAtMs = System.currentTimeMillis();
        this.displayTexts[3] = this.timeFormat.formatted(this.currentTime.toShortString());

        if (this.currentLyrics != null) this.displayTexts[0] = this.currentLyrics.stayOrNext(millisecond);
        else if (millisecond < 5000)
            this.displayTexts[0] = Concerto.getCoreBridge().getTranslatable("concerto.no_subtitle");
        else this.displayTexts[0] = "";

        if (this.currentSubLyrics != null) this.displayTexts[1] = this.currentSubLyrics.stayOrNext(millisecond);
        else this.displayTexts[1] = "";
    }

    public String[] getDisplayTexts() {
        return this.displayTexts;
    }

    public long getInterpolatedCurrentTimeMilliseconds() {
        if (this.currentTime == null) {
            return 0L;
        }

        long currentMs = this.currentTime.asMilliseconds();
        if (this.isPlaying() && !MusicPlayerHandler.INSTANCE.isPaused()) {
            currentMs += Math.max(0L, System.currentTimeMillis() - this.currentTimeUpdatedAtMs);
        }

        MusicTimestamp duration = this.currentMeta == null ? null : this.currentMeta.getDuration();
        return duration == null ? currentMs : Math.min(currentMs, duration.asMilliseconds());
    }

    public boolean canSeekCurrentMusic() {
        return this.started && this.currentMeta != null && this.currentMeta.getDuration() != null && this.isSeekable();
    }

    public void seekToMillisecondsAsync(long milliseconds) {
        this.seekToMillisecondsAsync(milliseconds, true);
    }

    public void seekToMillisecondsAsync(long milliseconds, boolean publishRoomSync) {
        this.seekDisplayLocked = true;
        this.updateDisplayTexts(milliseconds);
        this.playbackExecutor.execute(() -> {
            try {
                this.seekToMilliseconds(milliseconds);
                this.updateDisplayTexts(milliseconds);
                if (publishRoomSync) {
                    MusicRoom.clientPublishCurrentSeek(milliseconds);
                }
            } catch (Exception e) {
                Concerto.getLogger().error("Seek failed: " + e);
                Concerto.getCoreBridge().sendTranslatableToClientPlayer("concerto.player.error", false, e.getMessage());
            } finally {
                this.seekDisplayLocked = false;
            }
        });
    }

    @Override
    public void statusUpdated(StreamPlayerEvent event) {
        Status status = event.getPlayerStatus();
        if (status == Status.EOM) {
            if (MusicPlayerHandler.INSTANCE.isEmpty()) {
                this.stop();
            } else if (!this.playNextLock.get() && !this.isPlayingTemp) {
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
        if (!this.seekDisplayLocked) {
            this.updateDisplayTexts(microsecondPosition / 1000);
        }
        this.audioSpectrum.onAudioFrame(pcmData);
    }

    @Override
    public void play() throws StreamPlayerException {
        super.play();
        ConcertoEvents.ON_PLAYER_START.emit();
    }

    public void stop() {
        ProgressiveMediaDataSource mediaSource = this.currentMediaSource;
        this.resetInfo();
        super.stop();
        this.tryCloseStream(this.currentSource);
        this.tryCloseMediaSource(mediaSource);
        this.currentSource = null;
        this.currentMediaSource = null;
    }
}
