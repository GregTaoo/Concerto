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

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.LineUnavailableException;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
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
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicInteger preparingCount = new AtomicInteger();
    // The backend is pinned per play request so a mid-track sink rebuild (PCM
    // format change) can't silently switch backends; the option label promises
    // "takes effect next track"
    private volatile ClientConfig.PlaybackBackend sessionBackend = null;
    private volatile float effectiveGain = 1f;

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
        this.engine = new PlaybackEngine(this, this::createSink, logger);
    }

    private AudioSink createSink() {
        ClientConfig.PlaybackBackend backend = this.sessionBackend;
        if (backend == null) backend = configuredBackend();
        return backend == ClientConfig.PlaybackBackend.OPENAL ? new OpenALSink() : new JavaSoundSink();
    }

    private static ClientConfig.PlaybackBackend configuredBackend() {
        return ClientConfig.INSTANCE == null ? ClientConfig.PlaybackBackend.JAVASOUND
                : ClientConfig.INSTANCE.options.playbackBackend;
    }

    private void shutdown() {
        this.requestGeneration.incrementAndGet();
        this.prepareExecutor.shutdownNow();
        this.engine.close();
    }

    private static final AtomicBoolean GLOBAL_SHUTDOWN = new AtomicBoolean(false);

    /**
     * Game-quit hook. Minecraft's quit path releases resources via
     * {@code Minecraft.close()} without necessarily reaching {@code System.exit},
     * so everything Concerto holds must be dropped here: the playback engine
     * (OpenAL device / JavaSound line, session, spool file), the shared runner
     * pool and the player log file handle.
     *
     * <p>Idempotent, and never throws — it runs inside the game's shutdown path.
     * {@link #resetInstance()} keeps using the private per-instance
     * {@link #shutdown()} and is unaffected.
     */
    public static void shutdownAll() {
        if (!GLOBAL_SHUTDOWN.compareAndSet(false, true)) return;
        try {
            if (INSTANCE != null) INSTANCE.shutdown();
        } catch (Throwable t) {
            safeLogShutdownError("player engine", t);
        }
        try {
            ConcertoRunner.shutdown();
        } catch (Throwable t) {
            safeLogShutdownError("runner pool", t);
        }
        try {
            for (Handler handler : PLAYER_LOGGER.getHandlers()) {
                handler.close(); // releases Concerto/player.log (and its .lck)
            }
        } catch (Throwable t) {
            safeLogShutdownError("log handler", t);
        }
    }

    private static void safeLogShutdownError(String what, Throwable t) {
        try {
            Concerto.getLogger().warn("Error while shutting down Concerto {}", what, t);
        } catch (Throwable ignored) {
            // never let the quit hook throw
        }
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
        this.preparingCount.incrementAndGet();
        boolean submitted = false;
        try {
            this.prepareExecutor.execute(() -> this.prepareAndLoad(music, temp, callback, generation));
            submitted = true;
        } finally {
            if (!submitted) this.preparingCount.decrementAndGet();
        }
    }

    private void prepareAndLoad(Music music, boolean temp, Runnable callback, long generation) {
        try {
            if (generation != this.requestGeneration.get()) return; // superseded before it started
            // Sweep leftover spool files; files still open (Windows locks them) survive
            BufferedHttpByteSource.cleanTempDirectory();
            PlaybackSession session = this.createSession(music, generation);
            if (session == null) {
                this.autoSkipAfterFailure(temp, generation);
                return;
            }
            if (generation != this.requestGeneration.get()) {
                session.close();
                return;
            }
            if (this.currentMusic != music) {
                this.currentLyrics = this.currentSubLyrics = null;
                this.currentSubLyricsMapping = new int[0];
            }
            this.currentMusic = music;
            this.isPlayingTemp = temp;
            this.started = true;
            this.displayOverrideUntilMs = 0;
            this.initMusicStatus();
            this.updateDisplayTexts();
            this.updateDisplayTexts(session.getStartMillis());
            this.sessionBackend = configuredBackend();
            this.engine.load(session);
            // Re-check the synced pause state after posting the load: it covers
            // both a local force-pause and a room whose state is paused, and a
            // pause that raced in during preparation.
            if (MusicPlayerHandler.INSTANCE.isPaused()) {
                this.engine.setPaused(true);
            }
            ConcertoEvents.ON_NEW_MUSIC_STARTED.emit(music);
        } catch (Exception e) {
            this.handlePlaybackFailure(music, e);
            this.autoSkipAfterFailure(temp, generation);
        } finally {
            this.preparingCount.decrementAndGet();
            if (callback != null) callback.run();
        }
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

    /**
     * The "unable to play" message promises an automatic skip; deliver it, but
     * cap consecutive failures so a fully broken playlist doesn't loop forever.
     * Room members without playback control never skip — the room decides.
     */
    private void autoSkipAfterFailure(boolean temp, long generation) {
        if (temp || generation != this.requestGeneration.get()) return;
        if (MusicPlayerHandler.INSTANCE.isEmpty()) return;
        if (MusicRoom.clientGetState() != MusicRoom.ClientState.LOCAL
                && (MusicRoom.CLIENT_ROOM == null || MusicRoom.CLIENT_ROOM.permission < 2)) {
            return;
        }
        if (this.consecutiveFailures.incrementAndGet() >= MAX_CONSECUTIVE_FAILURES) {
            this.consecutiveFailures.set(0);
            Concerto.getCoreBridge().sendTranslatableToClientPlayer("concerto.player.skip_aborted", false);
            return;
        }
        MusicPlayerHandler.INSTANCE.playNextAsync(1);
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
        this.effectiveGain = clamped;
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

    /**
     * True while a play request is being prepared (network resolve, source
     * open) and the engine hasn't received the new session yet. UIs show a
     * loading indication instead of "nothing is playing" during this window.
     */
    public boolean isPreparing() {
        return this.preparingCount.get() > 0;
    }

    /** Vanilla background music is suppressed whenever Concerto is audible. */
    public boolean shouldBlockVanillaMusic() {
        PlaybackState state = this.engine.getState();
        return state == PlaybackState.PLAYING || state == PlaybackState.BUFFERING;
    }

    // ---- Seeking ----

    public boolean canSeekCurrentMusic() {
        if (!this.started || this.getEffectiveDurationMillis() <= 0) return false;
        PlaybackSession session = this.engine.getSessionView();
        return session != null && session.isSeekable();
    }

    /**
     * The duration actually playable, in ms (-1 if unknown). Prefers the real
     * media duration measured by the completed seek index over the metadata
     * claim — e.g. a 30s trial clip of a 4-minute song seeks within 30s.
     */
    public long getEffectiveDurationMillis() {
        MusicTimestamp metaDuration = this.currentMeta == null ? null : this.currentMeta.getDuration();
        long meta = metaDuration == null ? -1 : metaDuration.asMilliseconds();
        PlaybackSession session = this.engine.getSessionView();
        if (session != null && session.getSeekIndex() != null) {
            long real = session.getSeekIndex().isComplete() ? session.getSeekIndex().getDurationMillis() : -1;
            if (real > 0 && (meta <= 0 || real < meta)) {
                return real;
            }
        }
        return meta;
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
        this.progressPercentage = 0;
        this.currentTimeUpdatedAtMs = 0;
        this.displayOverrideUntilMs = 0;
        ConcertoEvents.ON_MUSIC_INFO_RESET.emit();
    }

    public void initMusicStatus() {
        if (this.currentMusic == null) return;
        this.currentMeta = this.currentMusic.getMeta();
        if (this.currentLyrics == null && this.currentSubLyrics == null) {
            try {
                Pair<Lyrics, Lyrics> lyrics = this.currentMusic.getLyrics();
                if (lyrics != null) {
                    this.currentLyrics = (lyrics.getFirst() == null || lyrics.getFirst().isEmpty()) ? null : lyrics.getFirst();
                    this.currentSubLyrics = (lyrics.getSecond() == null || lyrics.getSecond().isEmpty()) ? null : lyrics.getSecond();
                }
            } catch (Exception e) {
                this.currentLyrics = this.currentSubLyrics = null;
            }
        }
        this.currentSubLyricsMapping = Lyrics.createTimestampMapping(this.currentLyrics, this.currentSubLyrics, 500);
        this.displayTexts[0] = this.displayTexts[1] = this.displayTexts[2] = "";
    }

    public void updateDisplayTexts() {
        if (this.currentMeta != null) {
            this.displayTexts[2] = this.currentMeta.title() + " | " + this.currentMeta.author() + " | " + this.currentMeta.getSource();
            ConcertoEvents.ON_MUSIC_INFO_UPDATE.emit();
        } else this.displayTexts[2] = "";
    }

    public void updateDisplayTexts(long millisecond) {
        if (this.currentMeta == null) return;
        long duration = this.getEffectiveDurationMillis();
        this.progressPercentage = duration <= 0 ? 0 : ((float) millisecond / duration);
        this.currentTime = MusicTimestamp.ofMilliseconds(millisecond);
        this.currentTimeUpdatedAtMs = System.currentTimeMillis();
        this.displayTexts[3] = duration <= 0 ? this.currentTime.toShortString()
                : this.currentTime.toShortString() + " ".repeat(30) + MusicTimestamp.ofMilliseconds(duration).toShortString();

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
        long duration = this.getEffectiveDurationMillis();
        return duration <= 0 ? currentMs : Math.min(currentMs, duration);
    }

    // ---- Engine callbacks (engine thread) ----

    @Override
    public void onTrackStarted(PlaybackSession session) {
        MusicRoom.clientApplyResolvedStartTime();
        ConcertoEvents.ON_PLAYER_START.emit();
    }

    @Override
    public void onAudioOutputOpened(PlaybackSession session, AudioSink sink, AudioFormat format) {
        this.audioSpectrum.setOpenAlProfile(sink instanceof OpenALSink);
        MusicMetaData meta = session.getMusic().getMeta();
        ClientConfig.ClientConfigOptions options = ClientConfig.INSTANCE.options;
        ClientConfig.PlaybackBackend activeBackend = sink instanceof OpenALSink
                ? ClientConfig.PlaybackBackend.OPENAL : ClientConfig.PlaybackBackend.JAVASOUND;
        Concerto.getLogger().info(
                "Start playing music {} - {} | source={} | container={} | suffix={} | backend={} (configured={}) | "
                        + "output={} | pcm={} {}-bit {} channel(s), {} Hz, frameSize={}, {} endian | "
                        + "volume=config={}, followsMaster={}, effective={}",
                meta.title(), meta.author(), meta.getSource(), session.getFormat(), session.getSuffixHint(),
                activeBackend, options.playbackBackend, sink.getOutputDescription(), format.getEncoding(),
                format.getSampleSizeInBits(), format.getChannels(), format.getSampleRate(), format.getFrameSize(),
                format.isBigEndian() ? "big" : "little", options.playerVolume,
                options.playerVolumeFollowsMaster, this.effectiveGain);
    }

    /**
     * JavaSound is simply absent or broken on some platforms (Android/FCL, or
     * ALSA grabbing a nonexistent device): when opening the JavaSound line
     * fails, retry the same play request on the OpenAL sink. The fallback
     * becomes the session's pinned backend so a mid-track sink rebuild stays on
     * OpenAL; the config file is deliberately left untouched, and the next
     * track starts again from the configured backend.
     */
    @Override
    public AudioSink onSinkOpenFailed(AudioSink failedSink, Exception failure) {
        if (!(failedSink instanceof JavaSoundSink) || !isNoLineFailure(failure)) return null;
        Concerto.getLogger().warn(
                "JavaSound could not open an audio line ({}); retrying this track with the OpenAL backend", failure.toString());
        this.sessionBackend = ClientConfig.PlaybackBackend.OPENAL;
        return new OpenALSink();
    }

    /**
     * {@link LineUnavailableException} is the documented "no line" failure;
     * {@link IllegalArgumentException} is what {@code AudioSystem.getLine}
     * throws when no installed mixer supports the line at all (the headless /
     * Android case).
     */
    private static boolean isNoLineFailure(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof LineUnavailableException || t instanceof IllegalArgumentException) return true;
            if (t == t.getCause()) break;
        }
        return false;
    }

    @Override
    public void onTrackEnded(PlaybackSession session) {
        boolean stale = session.getGeneration() != this.requestGeneration.get();
        boolean wasTemp = this.isPlayingTemp;
        this.isPlayingTemp = false;
        if (stale) return; // a newer play request is already on its way
        this.progressPercentage = 0; // don't show the finished track's ~100% during the gap
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
        boolean wasTemp = this.isPlayingTemp;
        this.handlePlaybackFailure(session.getMusic(), exception);
        this.autoSkipAfterFailure(wasTemp, session.getGeneration());
    }

    @Override
    public void onPositionUpdate(long positionMillis) {
        // Audio is actually flowing, so the failure streak is over
        if (this.consecutiveFailures.get() != 0) this.consecutiveFailures.set(0);
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
    public void onPcm(byte[] data, int offset, int length, AudioFormat format) {
        this.audioSpectrum.onAudioFrame(data, offset, length, format);
    }
}
