package top.gregtao.concerto.core.player.engine;

import top.gregtao.concerto.core.player.loudness.LoudnessAnalyzer;
import top.gregtao.concerto.core.player.loudness.LoudnessNormalizer;
import top.gregtao.concerto.core.player.seek.ContainerFormat;
import top.gregtao.concerto.core.player.seek.SeekIndex;
import top.gregtao.concerto.core.player.seek.SeekIndexBuilder;
import top.gregtao.concerto.core.player.seek.SeekMode;
import top.gregtao.concerto.core.player.source.AudioByteSource;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Single-threaded playback engine. One long-lived engine thread owns all
 * playback state (session, decoder, sink, clock); the public API only posts
 * commands into a queue and reads volatile snapshots, so there is no shared
 * mutable state and no cross-thread teardown.
 * Seeking never tears the engine thread down: the decode chain is reopened at a
 * {@link SeekIndex} point, the residual up to the exact target is decoded and
 * discarded, the sink is flushed, and the clock base is reset.
 */
public class PlaybackEngine implements Closeable {

    private static final int PUMP_CHUNK = 4096;
    private static final long WAIT_SLICE_MILLIS = 50;
    /** How far ahead of the analysis read the downloader should keep fetching. */
    private static final long ANALYSIS_MARGIN_BYTES = 256 * 1024;
    /** How long playback start may wait for the loudness measurement of an
     *  already-available source (local file, cached track). Covers roughly a
     *  10-minute track at typical decode speed; after this, playback starts at
     *  unity and the gain lands when the measurement completes. */
    private static final long LOUDNESS_ANALYSIS_WAIT_MILLIS = 3000;

    /** Playback position published by the engine thread after every chunk. */
    public record PositionSnapshot(long positionMillis, long atNanos, boolean advancing) {
    }

    /** Loudness measurement completed in the background, tagged with its session. */
    private record LoudnessResult(long generation, float gain) {
    }

    /** Byte position reached by the background analysis, tagged with its session. */
    private record AnalysisProgress(long generation, long position) {
    }

    private interface Command {}
    private record LoadCmd(PlaybackSession session) implements Command {}
    private record SeekCmd(long millis, boolean publishRoomSync) implements Command {}
    private record SetPausedCmd(boolean paused) implements Command {}
    private record SetGainCmd(float gain) implements Command {}
    private record StopCmd() implements Command {}
    private record ShutdownCmd() implements Command {}

    private final EngineListener listener;
    private final Supplier<AudioSink> sinkFactory;
    private final Supplier<LoudnessSettings> loudnessSettings;
    private final Logger logger;
    private final LinkedBlockingQueue<Command> queue = new LinkedBlockingQueue<>();
    private final ExecutorService indexerExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Concerto-Indexer");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService analyzerExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Concerto-Loudness");
        thread.setDaemon(true);
        return thread;
    });
    private final Thread engineThread;
    private final byte[] pumpBuffer = new byte[PUMP_CHUNK];
    private final LoudnessNormalizer loudnessNormalizer = new LoudnessNormalizer();

    // ---- Engine-thread-only state ----
    private PlaybackSession session;
    private DecoderFactory.DecodedStream decoded;
    private AudioSink sink;
    private AudioFormat sinkFormat;
    private Future<?> indexerTask;
    private Future<?> loudnessTask;
    private CountDownLatch loudnessAnalysisLatch;
    private float gain = 1f;
    private boolean paused = false;
    private boolean buffering = false;
    private boolean trackEnded = false;
    private boolean running = true;
    private long clockBaseMillis = 0;
    private long pendingSeekMillis = -1;
    private boolean pendingSeekPublish = false;
    private long pendingOpenOffset = 0;
    private byte[] pendingOpenPrefix = null;
    private long pendingDiscardMillis = 0;
    private long discardBytesRemaining = 0;
    private float appliedLoudnessGain = 1f;

    // ---- Published state ----
    private volatile PlaybackState publicState = PlaybackState.IDLE;
    private volatile PositionSnapshot snapshot = new PositionSnapshot(0, System.nanoTime(), false);
    private volatile PlaybackSession sessionView = null;
    private volatile LoudnessResult loudnessResult = new LoudnessResult(-1, 1f);
    /** Analysis read progress; only honored while the generation matches the session. */
    private volatile AnalysisProgress loudnessAnalysisProgress = new AnalysisProgress(-1, 0);

    public PlaybackEngine(EngineListener listener, Supplier<AudioSink> sinkFactory,
                          Supplier<LoudnessSettings> loudnessSettings, Logger logger) {
        this.listener = listener;
        this.sinkFactory = sinkFactory;
        this.loudnessSettings = loudnessSettings;
        this.logger = logger;
        this.engineThread = new Thread(this::run, "Concerto-Playback");
        this.engineThread.setDaemon(true);
        this.engineThread.start();
    }

    // ---- Public API (any thread) ----

    public void load(PlaybackSession newSession) {
        this.queue.offer(new LoadCmd(newSession));
    }

    public void seek(long millis, boolean publishRoomSync) {
        this.queue.offer(new SeekCmd(millis, publishRoomSync));
    }

    public void setPaused(boolean paused) {
        this.queue.offer(new SetPausedCmd(paused));
    }

    public void setGain(float gain) {
        this.queue.offer(new SetGainCmd(gain));
    }

    public void stop() {
        this.queue.offer(new StopCmd());
    }

    public PlaybackState getState() {
        return this.publicState;
    }

    public PositionSnapshot getPosition() {
        return this.snapshot;
    }

    /** The currently loaded session (read-only view for UI), or null. */
    public PlaybackSession getSessionView() {
        return this.sessionView;
    }

    @Override
    public void close() {
        this.queue.offer(new ShutdownCmd());
        try {
            this.engineThread.join(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        this.indexerExecutor.shutdownNow();
        this.analyzerExecutor.shutdownNow();
    }

    // ---- Engine thread ----

    private void run() {
        try {
            while (this.running) {
                try {
                    Command command = this.queue.poll();
                    if (command == null && !this.hasPendingWork()) {
                        command = this.queue.poll(200, TimeUnit.MILLISECONDS);
                    }
                    if (command != null) {
                        this.handle(command);
                    } else if (this.hasPendingWork()) {
                        if (this.ensureSessionPrepared()) {
                            if (this.pendingSeekMillis >= 0) {
                                this.tryApplyPendingSeek();
                            } else if (!this.paused) {
                                this.pump();
                            }
                        }
                    }
                    this.publishState();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    this.handleFailure(e);
                    this.publishState();
                }
            }
        } finally {
            this.closeSession();
            this.publishState();
        }
    }

    private boolean hasPendingWork() {
        return this.session != null && !this.trackEnded && (this.pendingSeekMillis >= 0 || !this.paused);
    }

    private void handle(Command command) {
        if (command instanceof LoadCmd load) {
            this.handleLoad(load.session());
        } else if (command instanceof SeekCmd seek) {
            if (this.session != null && !this.trackEnded) {
                this.pendingSeekMillis = Math.max(0, seek.millis());
                this.pendingSeekPublish = seek.publishRoomSync();
            }
        } else if (command instanceof SetPausedCmd setPaused) {
            this.paused = setPaused.paused();
            // A paused engine isn't buffering; a stale flag would otherwise
            // stick until the next pump() and misreport BUFFERING on resume
            this.buffering = false;
            if (this.sink != null && this.sink.isOpen()) {
                if (this.paused) this.sink.pause();
                else this.sink.resume();
            }
            this.updateSnapshot();
        } else if (command instanceof SetGainCmd setGain) {
            this.gain = setGain.gain();
            if (this.sink != null && this.sink.isOpen()) {
                this.sink.setGain(this.gain);
            }
        } else if (command instanceof StopCmd) {
            this.closeSession();
        } else if (command instanceof ShutdownCmd) {
            this.running = false;
        }
    }

    private void handleLoad(PlaybackSession newSession) {
        this.closeSession();
        this.session = newSession;
        this.sessionView = newSession;
        this.clockBaseMillis = 0;
        this.pendingOpenOffset = 0;
        this.pendingOpenPrefix = null;
        this.pendingDiscardMillis = 0;
        this.updateSnapshot();
    }

    /**
     * Detects the container format and starts the indexer once the first bytes
     * are available. @return true when the session is ready for seek/pump work.
     */
    private boolean ensureSessionPrepared() throws Exception {
        if (this.session.getFormat() != null) return true;
        AudioByteSource source = this.session.getByteSource();
        if (!source.awaitAvailable(0, 16, WAIT_SLICE_MILLIS)) {
            this.buffering = true;
            return false;
        }
        ContainerFormat format = ContainerFormat.detect(source, this.session.getSuffixHint());
        this.session.setFormat(format);
        SeekIndexBuilder builder = format.createIndexBuilder();
        if (builder != null) {
            SeekIndex index = new SeekIndex();
            this.session.setSeekIndex(index);
            this.indexerTask = this.indexerExecutor.submit(() -> {
                try {
                    builder.build(source, index);
                } catch (IOException e) {
                    // Source closed (session ended) or download failure: keep the partial index.
                    this.logger.fine(() -> "Seek indexing ended early: " + e.getMessage());
                }
            });
        }
        this.updateDownloadWindow(0, 0);
        this.startLoudnessAnalysis();
        if (this.session.getStartMillis() > 0) {
            if (this.session.isSeekable()) {
                this.pendingSeekMillis = this.session.getStartMillis();
                this.pendingSeekPublish = false;
            } else {
                this.logger.warning("Start offset requested on an unseekable format, playing from 0");
            }
        }
        this.listener.onTrackStarted(this.session);
        return true;
    }

    private void tryApplyPendingSeek() throws Exception {
        switch (this.session.getSeekMode()) {
            case INDEXED -> this.applyIndexedSeek();
            case RESTART_FROM_START -> this.applyRestartSeek(this.pendingSeekMillis);
            case UNSUPPORTED -> this.pendingSeekMillis = -1;
        }
    }

    // ---- Loudness normalization ----

    /**
     * Decodes the whole track once and measures its integrated loudness
     * (EBU R128 / BS.1770-4). The resulting gain is published for the engine
     * thread, which ramps the {@link LoudnessNormalizer} toward it.
     *
     * <p>When the whole file is already available locally (local files, cached
     * tracks), the measurement typically takes a second or two, so the engine
     * thread waits for it (capped, see LOUDNESS_ANALYSIS_WAIT_MILLIS) and the
     * track starts normalized from the first sample. For sources that still
     * need downloading the wait is skipped and playback starts at unity, with
     * the gain landing as soon as the measurement can read the data
     * (progressively downloaded, see updateDownloadWindow).
     *
     * <p>Skipped when the policy is off or the source has no known length (live
     * radio): such streams cannot be measured as a whole. A measurement that
     * cannot finish (source closed, download failure) is dropped silently —
     * loudness analysis must never break playback.
     */
    private void startLoudnessAnalysis() {
        if (this.loudnessTask != null) return;
        LoudnessSettings settings;
        try {
            settings = this.loudnessSettings.get();
        } catch (Exception e) {
            settings = LoudnessSettings.disabled();
        }
        if (!settings.enabled()) return;
        AudioByteSource source = this.session.getByteSource();
        if (source.length() < 0 && !source.isComplete()) {
            this.logger.fine("Loudness analysis skipped: source length is unknown (live stream?)");
            return;
        }
        // Keep the limiter's delay line warm from the first sample: if the gain
        // arrives mid-track (analysis takes a moment), the ring already holds
        // real audio and there is no 5 ms dropout at the transition.
        this.loudnessNormalizer.setEngaged(true);
        long generation = this.session.getGeneration();
        ContainerFormat format = this.session.getFormat();
        LoudnessSettings policy = settings;
        CountDownLatch latch = new CountDownLatch(1);
        this.loudnessAnalysisLatch = latch;
        this.loudnessTask = this.analyzerExecutor.submit(() -> {
            try {
                this.runLoudnessAnalysis(source, format, generation, policy);
            } finally {
                latch.countDown();
            }
        });
        if (source.length() >= 0 && source.isComplete()) {
            // Local or cached: hold playback start so the gain is ready before
            // the first sample plays.
            this.awaitLoudnessAnalysis(latch, generation);
        }
    }

    private void awaitLoudnessAnalysis(CountDownLatch latch, long generation) {
        try {
            latch.await(LOUDNESS_ANALYSIS_WAIT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        this.applyLoudnessGainIfChanged();
    }

    private void runLoudnessAnalysis(AudioByteSource source, ContainerFormat format, long generation,
                                     LoudnessSettings settings) {
        float gain = 1f;
        double integratedLufs = Double.NEGATIVE_INFINITY;
        DecoderFactory.DecodedStream stream = null;
        try {
            stream = DecoderFactory.open(source, 0, null, format, this.logger);
            LoudnessAnalyzer analyzer = new LoudnessAnalyzer(
                    (int) stream.pcmFormat.getSampleRate(), stream.pcmFormat.getChannels());
            byte[] buffer = new byte[8192];
            int read;
            long lastPosition = 0;
            while ((read = stream.pcmStream.read(buffer)) != -1) {
                if (read > 0) analyzer.feed(buffer, 0, read, stream.pcmFormat);
                // Publish progress so the download window follows the analysis
                // instead of only the playhead (see updateDownloadWindow).
                long position = stream.getDownloadPosition();
                if (position != lastPosition) {
                    lastPosition = position;
                    this.loudnessAnalysisProgress = new AnalysisProgress(generation, position);
                }
            }
            LoudnessAnalyzer.Result result = analyzer.finish(settings.targetLufs(), settings.maxGainDb());
            integratedLufs = result.integratedLufs();
            gain = result.linearGain();
        } catch (Exception e) {
            // Session ended mid-analysis or the media cannot be decoded:
            // keep unity gain; this must never break playback.
            this.logger.fine(() -> "Loudness analysis aborted: " + e.getMessage());
            return;
        } finally {
            if (stream != null) stream.close();
        }
        this.loudnessResult = new LoudnessResult(generation, gain);
        this.logger.info(String.format("Loudness analysis done: integrated=%.1f LUFS, applied gain=%.1f dB",
                integratedLufs, 20.0 * Math.log10(gain)));
    }

    /** Applies a freshly published analysis result with a smooth ramp. Engine thread only. */
    private void applyLoudnessGainIfChanged() {
        LoudnessResult result = this.loudnessResult;
        if (this.session != null && result.generation() == this.session.getGeneration()
                && result.gain() != this.appliedLoudnessGain) {
            this.loudnessNormalizer.setGain(result.gain());
            this.appliedLoudnessGain = result.gain();
        }
    }

    private void applyIndexedSeek() throws Exception {
        SeekIndex index = this.session.getSeekIndex();
        if (index == null) {
            this.pendingSeekMillis = -1;
            return;
        }
        long target = this.pendingSeekMillis;
        long duration = index.getDurationMillis();
        if (duration > 0) target = Math.min(target, duration);
        if (target > index.getCoveredToMillis() && !index.isComplete()) {
            // Use the duration-derived byte estimate to let the indexer reach a
            // requested seek without resuming an unrestricted full download.
            this.session.getByteSource().setPlaybackWindow(0, target, duration);
            this.buffering = true;
            Thread.sleep(WAIT_SLICE_MILLIS); // wait for the indexer/download to advance
            return;
        }
        SeekIndex.Point point = index.floor(target);
        if (point == null) {
            if (index.isComplete()) this.pendingSeekMillis = -1; // nothing indexed: give up on this seek
            else this.buffering = true;
            return;
        }
        this.closeDecoded();
        if (this.sink != null && this.sink.isOpen()) {
            this.sink.flush();
        }
        this.loudnessNormalizer.clearDelay();
        this.pendingOpenOffset = point.byteOffset();
        this.pendingOpenPrefix = index.getPrefixBytes();
        this.pendingDiscardMillis = target - point.timeMillis();
        this.completeSeek(target);
    }

    /** Reopens a decoder that requires its container initialization at byte zero. */
    private void applyRestartSeek(long target) throws IOException {
        this.closeDecoded();
        if (this.sink != null && this.sink.isOpen()) {
            this.sink.flush();
        }
        this.loudnessNormalizer.clearDelay();
        this.pendingOpenOffset = 0;
        this.pendingOpenPrefix = null;
        this.pendingDiscardMillis = target;
        this.completeSeek(target);
    }

    private void completeSeek(long target) {
        this.clockBaseMillis = target;
        boolean publish = this.pendingSeekPublish;
        this.pendingSeekMillis = -1;
        this.buffering = false;
        this.snapshot = new PositionSnapshot(target, System.nanoTime(), false);
        this.listener.onSeekApplied(this.session, target, publish);
    }

    private void pump() throws Exception {
        if (this.decoded == null && !this.openPendingDecode()) {
            return; // still buffering
        }
        AudioByteSource source = this.session.getByteSource();
        long rawPosition = this.decoded.getDownloadPosition();
        this.updateDownloadWindow(rawPosition, this.snapshot.positionMillis());
        if (!source.awaitAvailable(rawPosition, PUMP_CHUNK * 2, WAIT_SLICE_MILLIS)) {
            this.buffering = true;
            return;
        }
        this.buffering = false;
        this.applyLoudnessGainIfChanged();
        int read = this.decoded.pcmStream.read(this.pumpBuffer, 0, PUMP_CHUNK);
        if (read == -1) {
            // Render the limiter's lookahead tail so the last few milliseconds
            // of the track are not dropped.
            int tail = this.loudnessNormalizer.flushTail(this.pumpBuffer, this.decoded.pcmFormat);
            if (tail > 0) this.sink.write(this.pumpBuffer, 0, tail);
            this.finishTrack();
            return;
        }
        if (read == 0) return;
        int offset = 0;
        if (this.discardBytesRemaining > 0) {
            int drop = (int) Math.min(read, this.discardBytesRemaining);
            this.discardBytesRemaining -= drop;
            if (drop >= read) return;
            offset = drop;
        }
        this.loudnessNormalizer.process(this.pumpBuffer, offset, read - offset, this.decoded.pcmFormat);
        this.listener.onPcm(this.pumpBuffer, offset, read - offset, this.decoded.pcmFormat);
        this.sink.write(this.pumpBuffer, offset, read - offset);
        this.updateSnapshot();
        this.listener.onPositionUpdate(this.snapshot.positionMillis());
    }

    private boolean openPendingDecode() throws Exception {
        AudioByteSource source = this.session.getByteSource();
        this.updateDownloadWindow(this.pendingOpenOffset, this.clockBaseMillis);
        // Enough headroom for the SPI probe to sniff the container without blocking long
        if (!source.awaitAvailable(this.pendingOpenOffset, 64 * 1024, WAIT_SLICE_MILLIS)) {
            this.buffering = true;
            return false;
        }
        this.buffering = false;
        this.decoded = DecoderFactory.open(source, this.pendingOpenOffset, this.pendingOpenPrefix,
                this.session.getFormat(), this.logger);
        AudioFormat format = this.decoded.pcmFormat;
        this.discardBytesRemaining = millisToBytes(this.pendingDiscardMillis, format);
        this.pendingDiscardMillis = 0;
        if (this.sink != null && !format.matches(this.sinkFormat)) {
            this.sink.close();
            this.sink = null;
        }
        if (this.sink == null) {
            this.sink = this.sinkFactory.get();
        }
        if (!this.sink.isOpen()) {
            try {
                this.sink.open(format);
            } catch (Exception openFailure) {
                // Sink-level failure (e.g. JavaSound has no output line on this
                // platform): give the listener one chance to swap in a fallback
                // sink and continue the same session. Anything else propagates
                // into the generic failure handling.
                AudioSink fallback = this.listener.onSinkOpenFailed(this.sink, openFailure);
                if (fallback == null) throw openFailure;
                try {
                    this.sink.close();
                } catch (Exception ignored) {
                }
                this.sink = fallback;
                this.sink.open(format);
            }
            this.sinkFormat = format;
            this.sink.setGain(this.gain);
            if (this.paused) this.sink.pause();
            this.listener.onAudioOutputOpened(this.session, this.sink, format);
        }
        return true;
    }

    private static long millisToBytes(long millis, AudioFormat format) {
        long frames = (long) (millis * format.getSampleRate() / 1000.0);
        return frames * format.getFrameSize();
    }

    private void updateDownloadWindow(long bytePosition, long positionMillis) {
        if (this.session == null) return;
        SeekIndex index = this.session.getSeekIndex();
        long durationMillis = index == null ? -1 : index.getDurationMillis();
        long window = bytePosition;
        // While the background loudness analysis is running it reads from byte 0
        // (faster than real time), so the source must fetch ahead of the analysis
        // position, not just the playhead, or the measurement can never complete.
        // Tagged by generation: a straggler from the previous session must not
        // inflate the new session's download window.
        AnalysisProgress progress = this.loudnessAnalysisProgress;
        if (progress.generation() == this.session.getGeneration() && progress.position() > 0) {
            window = Math.max(window, progress.position() + ANALYSIS_MARGIN_BYTES);
        }
        this.session.getByteSource().setPlaybackWindow(window, positionMillis, durationMillis);
    }

    private void finishTrack() {
        if (this.sink != null && this.sink.isOpen() && !this.paused) {
            this.sink.drain();
        }
        this.updateSnapshot();
        this.closeDecoded();
        this.trackEnded = true;
        // Stop advertising the finished session: the UI would otherwise keep
        // reading its buffered fraction and duration while the state is IDLE
        this.sessionView = null;
        this.listener.onTrackEnded(this.session);
    }

    private void handleFailure(Exception exception) {
        this.logger.log(Level.WARNING, "Playback failure", exception);
        PlaybackSession failed = this.session;
        this.closeSession();
        if (failed != null) {
            this.listener.onPlaybackError(failed, exception);
        }
    }

    private void updateSnapshot() {
        long positionMillis = this.clockBaseMillis;
        if (this.sink != null && this.sink.isOpen() && this.sinkFormat != null) {
            positionMillis += (long) (this.sink.playedFrames() * 1000.0 / this.sinkFormat.getSampleRate());
        }
        boolean advancing = this.session != null && !this.trackEnded && !this.paused && !this.buffering
                && this.pendingSeekMillis < 0 && this.decoded != null;
        this.snapshot = new PositionSnapshot(positionMillis, System.nanoTime(), advancing);
    }

    private void publishState() {
        PlaybackState state;
        if (this.session == null || this.trackEnded) {
            state = PlaybackState.IDLE;
        } else if (this.paused) {
            state = PlaybackState.PAUSED;
        } else if (this.buffering || this.pendingSeekMillis >= 0 || this.decoded == null) {
            state = PlaybackState.BUFFERING;
        } else {
            state = PlaybackState.PLAYING;
        }
        this.publicState = state;
    }

    private void closeDecoded() {
        if (this.decoded != null) {
            this.decoded.close();
            this.decoded = null;
        }
    }

    private void closeSession() {
        this.closeDecoded();
        if (this.indexerTask != null) {
            this.indexerTask.cancel(false);
            this.indexerTask = null;
        }
        if (this.loudnessTask != null) {
            this.loudnessTask.cancel(false);
            this.loudnessTask = null;
        }
        this.loudnessAnalysisLatch = null;
        if (this.session != null) {
            this.session.close(); // closes the byte source; a pending indexer read then throws and ends
            this.session = null;
        }
        this.sessionView = null;
        this.loudnessResult = new LoudnessResult(-1, 1f);
        this.appliedLoudnessGain = 1f;
        this.loudnessAnalysisProgress = new AnalysisProgress(-1, 0);
        this.loudnessNormalizer.reset();
        if (this.sink != null) {
            this.sink.close();
            this.sink = null;
            this.sinkFormat = null;
        }
        this.paused = false;
        this.buffering = false;
        this.trackEnded = false;
        this.pendingSeekMillis = -1;
        this.discardBytesRemaining = 0;
        this.clockBaseMillis = 0;
        this.snapshot = new PositionSnapshot(0, System.nanoTime(), false);
    }
}
