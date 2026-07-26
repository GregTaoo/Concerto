package top.gregtao.concerto.core.player;

import top.gregtao.concerto.core.Concerto;
import top.gregtao.concerto.core.api.CacheableMusic;
import top.gregtao.concerto.core.config.ClientConfig;
import top.gregtao.concerto.core.event.ConcertoEvents;
import top.gregtao.concerto.core.music.Music;
import top.gregtao.concerto.core.music.MusicTimestamp;
import top.gregtao.concerto.core.music.PathFileMusic;
import top.gregtao.concerto.core.music.SharedMusic;
import top.gregtao.concerto.core.music.lyrics.Lyrics;
import top.gregtao.concerto.core.music.meta.music.MusicMetaData;
import top.gregtao.concerto.core.player.engine.AudioSink;
import top.gregtao.concerto.core.player.engine.EngineListener;
import top.gregtao.concerto.core.player.engine.JavaSoundSink;
import top.gregtao.concerto.core.player.engine.OpenALSink;
import top.gregtao.concerto.core.player.engine.PlaybackEngine;
import top.gregtao.concerto.core.player.engine.PlaybackSession;
import top.gregtao.concerto.core.player.engine.PlaybackState;
import top.gregtao.concerto.core.player.source.BufferedHttpByteSource;
import top.gregtao.concerto.core.room.MusicRoom;
import top.gregtao.concerto.core.util.ConcertoRunner;
import top.gregtao.concerto.core.util.FileUtil;
import top.gregtao.concerto.core.util.Pair;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Application-level music player: owns the {@link PlaybackEngine}, the current
 * track's metadata/lyrics/display state, and the bridge to playlist handling and
 * room synchronisation. All playback work happens inside the engine; this class
 * only posts commands and reacts to engine callbacks.
 */
public class MusicPlayer implements EngineListener {

    public static MusicPlayer INSTANCE;
    public static final Logger PLAYER_LOGGER;

    public Music currentMusic = null;
    public Lyrics currentLyrics = null, currentSubLyrics = null;
    public int[] currentSubLyricsMapping = new int[0];
    public MusicMetaData currentMeta = null;
    private MusicTimestamp currentTime = null;
    private String[] displayTexts = new String[]{"", "", "", ""};
    private String timeFormat = "%s" + " ".repeat(30) + "%s";
    public volatile float progressPercentage = 0;
    private long currentTimeUpdatedAtMs = 0;
    private volatile long displayOverrideUntilMs = 0;

    public volatile boolean started = false;
    public volatile boolean isPlayingTemp = false;

    private final PlaybackEngine engine;
    private final ExecutorService prepareExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Concerto-Loader");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicLong requestGeneration = new AtomicLong();

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
        BufferedHttpByteSource.cleanTempDirectory(); // drop spool files orphaned by a crash
        resetInstance();
    }

    public static void resetInstance() {
        if (INSTANCE != null) {
            INSTANCE.shutdown();
        }
        INSTANCE = new MusicPlayer(PLAYER_LOGGER);
    }

    public MusicPlayer(Logger logger) {
        this.engine = new PlaybackEngine(this, MusicPlayer::createSink, logger);
    }

    private static AudioSink createSink() {
        if (ClientConfig.INSTANCE != null
                && ClientConfig.INSTANCE.options.playbackBackend == ClientConfig.PlaybackBackend.OPENAL) {
            return new OpenALSink();
        }
        return new JavaSoundSink();
    }

    private void shutdown() {
        this.requestGeneration.incrementAndGet();
        this.prepareExecutor.shutdownNow();
        this.engine.close();
    }

    // ---- Play requests ----

    public synchronized void internalPlayMusic(Music music) {
        if (music == null) return;
        this.requestPlay(music, false, null);
    }

    public void playTempMusic(Music music, Runnable callback) {
        if (music == null) return;
        this.requestPlay(music, true, callback);
    }

    public void playTempMusic(Music music) {
        this.playTempMusic(music, null);
    }

    private void requestPlay(Music music, boolean temp, Runnable callback) {
        long generation = this.requestGeneration.incrementAndGet();
        this.prepareExecutor.execute(() -> {
            try {
                if (generation != this.requestGeneration.get()) return; // superseded before it started
                // Sweep leftover spool files; files still open (Windows locks them) survive
                BufferedHttpByteSource.cleanTempDirectory();
                PlaybackSession session = this.createSession(music, generation);
                if (session == null) return;
                if (generation != this.requestGeneration.get()) {
                    session.close();
                    return;
                }
                this.currentMusic = music;
                this.isPlayingTemp = temp;
                this.started = true;
                this.displayOverrideUntilMs = 0;
                this.initMusicStatus();
                this.updateDisplayTexts();
                this.updateDisplayTexts(session.getStartMillis());
                this.engine.load(session);
                // Re-check the synced pause state after posting the load: it covers
                // both a local force-pause and a room whose state is paused, and a
                // pause that raced in during preparation.
                if (MusicPlayerHandler.INSTANCE.isPaused()) {
                    this.engine.setPaused(true);
                }
                Concerto.getLogger().info("Start playing music {} - {}", music.getMeta().title(), music.getMeta().author());
                ConcertoEvents.ON_NEW_MUSIC_STARTED.emit(music);
            } catch (Exception e) {
                this.handlePlaybackFailure(music, e);
            } finally {
                if (callback != null) callback.run();
            }
        });
    }

    private PlaybackSession createSession(Music music, long generation) {
        long startMillis = music instanceof SharedMusic sharedMusic ? Math.max(0, sharedMusic.getStartTime()) : 0;
        var byteSource = music.createByteSource();
        if (byteSource == null) {
            this.resetInfo();
            Concerto.getLogger().error("Unable to play music: {} - {}", music.getMeta().title(), music.getMeta().author());
            Concerto.getCoreBridge().sendTranslatableToClientPlayer(
                    "concerto.player.unable", false, music.getMeta().title(), music.getMeta().author(), music.getMeta().getSource());
            return null;
        }
        return new PlaybackSession(music, byteSource, inferSuffix(music), generation, startMillis);
    }

    private static String inferSuffix(Music music) {
        if (music instanceof CacheableMusic cacheableMusic) {
            return cacheableMusic.getSuffix();
        }
        if (music instanceof PathFileMusic pathFileMusic) {
            return FileUtil.getSuffix(pathFileMusic.getRawPath());
        }
        return "";
    }

    private void handlePlaybackFailure(Music music, Exception e) {
        this.resetInfo();
        Concerto.getLogger().error("Internal player error: " + e);
        Concerto.getCoreBridge().sendTranslatableToClientPlayer("concerto.player.error", false, e.getMessage());
    }

    // ---- Playback controls ----

    public boolean internalPause() {
        ConcertoEvents.ON_PLAYER_PAUSE.emit();
        this.engine.setPaused(true);
        return true;
    }

    public boolean internalResume() {
        ConcertoEvents.ON_PLAYER_RESUME.emit();
        this.engine.setPaused(false);
        return true;
    }

    public void stop() {
        this.requestGeneration.incrementAndGet();
        this.engine.stop();
        this.resetInfo();
    }

    public void setGain(double gain) {
        float clamped = (float) Math.max(0.0, Math.min(1.0, gain));
        this.engine.setGain(clamped);
    }

    // ---- State queries (old StreamPlayer-compatible surface) ----

    public boolean isPlaying() {
        PlaybackState state = this.engine.getState();
        return state == PlaybackState.PLAYING || state == PlaybackState.BUFFERING;
    }

    public boolean isPaused() {
        return this.engine.getState() == PlaybackState.PAUSED;
    }

    public boolean isOpened() {
        return this.engine.getState() != PlaybackState.IDLE;
    }

    public boolean isSeeking() {
        return this.engine.getState() == PlaybackState.BUFFERING;
    }

    /** Vanilla background music is suppressed whenever Concerto is audible. */
    public boolean shouldBlockVanillaMusic() {
        PlaybackState state = this.engine.getState();
        return state == PlaybackState.PLAYING || state == PlaybackState.BUFFERING;
    }

    // ---- Seeking ----

    public boolean canSeekCurrentMusic() {
        if (!this.started || this.currentMeta == null || this.currentMeta.getDuration() == null) return false;
        PlaybackSession session = this.engine.getSessionView();
        return session != null && session.isSeekable();
    }

    public void seekToMillisecondsAsync(long milliseconds) {
        this.seekToMillisecondsAsync(milliseconds, true);
    }

    public void seekToMillisecondsAsync(long milliseconds, boolean publishRoomSync) {
        // Freeze display updates on the drag target until the engine confirms the jump
        this.displayOverrideUntilMs = System.currentTimeMillis() + 2000;
        this.updateDisplayTexts(milliseconds);
        this.engine.seek(milliseconds, publishRoomSync);
    }

    /** Fraction (0..1) of the media already buffered locally; for the seek bar. */
    public float getBufferedPercentage() {
        PlaybackSession session = this.engine.getSessionView();
        if (session == null) return 0;
        long length = session.getByteSource().length();
        if (length <= 0) return session.getByteSource().isComplete() ? 1 : 0;
        return Math.min(1f, (float) ((double) session.getByteSource().availableTo() / length));
    }

    // ---- Display state ----

    public void resetInfo() {
        this.started = false;
        this.isPlayingTemp = false;
        this.currentMusic = null;
        this.currentLyrics = this.currentSubLyrics = null;
        this.currentSubLyricsMapping = new int[0];
        this.currentMeta = null;
        this.currentTime = MusicTimestamp.of(0);
        this.displayTexts = new String[]{"", "", "", ""};
        this.timeFormat = "%s" + " ".repeat(30) + "%s";
        this.progressPercentage = 0;
        this.currentTimeUpdatedAtMs = 0;
        this.displayOverrideUntilMs = 0;
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

    public void updateDisplayTexts() {
        if (this.currentMeta != null) {
            this.displayTexts[2] = this.currentMeta.title() + " | " + this.currentMeta.author() + " | " + this.currentMeta.getSource();
            MusicTimestamp timestamp = this.currentMeta.getDuration();
            this.timeFormat = "%s" + (timestamp == null ? "" : " ".repeat(30) + timestamp.toShortString());
            ConcertoEvents.ON_MUSIC_INFO_UPDATE.emit();
        } else this.displayTexts[2] = "";
    }

    public void updateDisplayTexts(long millisecond) {
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
        if (this.engine.getState() == PlaybackState.PLAYING && !MusicPlayerHandler.INSTANCE.isPaused()) {
            currentMs += Math.max(0L, System.currentTimeMillis() - this.currentTimeUpdatedAtMs);
        }
        MusicTimestamp duration = this.currentMeta == null ? null : this.currentMeta.getDuration();
        return duration == null ? currentMs : Math.min(currentMs, duration.asMilliseconds());
    }

    // ---- Engine callbacks (engine thread) ----

    @Override
    public void onTrackStarted(PlaybackSession session) {
        ConcertoEvents.ON_PLAYER_START.emit();
    }

    @Override
    public void onTrackEnded(PlaybackSession session) {
        boolean stale = session.getGeneration() != this.requestGeneration.get();
        boolean wasTemp = this.isPlayingTemp;
        this.isPlayingTemp = false;
        if (stale) return; // a newer play request is already on its way
        ConcertoRunner.run(() -> {
            if (MusicPlayerHandler.INSTANCE.isEmpty()) {
                this.stop();
            } else if (!wasTemp) {
                MusicPlayerHandler.INSTANCE.playNext(1);
            }
        });
    }

    @Override
    public void onPlaybackError(PlaybackSession session, Exception exception) {
        if (session.getGeneration() != this.requestGeneration.get()) return;
        this.handlePlaybackFailure(session.getMusic(), exception);
    }

    @Override
    public void onPositionUpdate(long positionMillis) {
        if (System.currentTimeMillis() >= this.displayOverrideUntilMs) {
            this.updateDisplayTexts(positionMillis);
        }
    }

    @Override
    public void onSeekApplied(PlaybackSession session, long positionMillis, boolean publishRoomSync) {
        this.displayOverrideUntilMs = 0;
        if (this.currentLyrics != null) this.currentLyrics.startFrom(positionMillis);
        if (this.currentSubLyrics != null) this.currentSubLyrics.startFrom(positionMillis);
        this.updateDisplayTexts(positionMillis);
        ConcertoEvents.ON_PLAYER_SEEK.emit(positionMillis);
        if (publishRoomSync) {
            MusicRoom.clientPublishCurrentSeek(positionMillis);
        }
    }

    @Override
    public void onPcm(byte[] data, int offset, int length) {
        if (offset == 0) {
            this.audioSpectrum.onAudioFrame(data);
        }
    }
}
