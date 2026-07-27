package top.gregtao.concerto.core.player.engine;

import top.gregtao.concerto.core.player.seek.ContainerFormat;
import top.gregtao.concerto.core.player.seek.SeekIndex;
import top.gregtao.concerto.core.player.seek.SeekIndexBuilder;
import top.gregtao.concerto.core.player.source.AudioByteSource;

import javax.sound.sampled.AudioFormat;
import java.io.Closeable;
import java.io.IOException;
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

    /** Playback position published by the engine thread after every chunk. */
    public record PositionSnapshot(long positionMillis, long atNanos, boolean advancing) {
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
    private final Logger logger;
    private final LinkedBlockingQueue<Command> queue = new LinkedBlockingQueue<>();
    private final ExecutorService indexerExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Concerto-Indexer");
        thread.setDaemon(true);
        return thread;
    });
    private final Thread engineThread;
    private final byte[] pumpBuffer = new byte[PUMP_CHUNK];

    // ---- Engine-thread-only state ----
    private PlaybackSession session;
    private DecoderFactory.DecodedStream decoded;
    private AudioSink sink;
    private AudioFormat sinkFormat;
    private Future<?> indexerTask;
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

    // ---- Published state ----
    private volatile PlaybackState publicState = PlaybackState.IDLE;
    private volatile PositionSnapshot snapshot = new PositionSnapshot(0, System.nanoTime(), false);
    private volatile PlaybackSession sessionView = null;

    public PlaybackEngine(EngineListener listener, Supplier<AudioSink> sinkFactory, Logger logger) {
        this.listener = listener;
        this.sinkFactory = sinkFactory;
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
        this.pendingOpenOffset = point.byteOffset();
        this.pendingOpenPrefix = index.getPrefixBytes();
        this.pendingDiscardMillis = target - point.timeMillis();
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
        long rawPosition = this.decoded.rawStream.position();
        this.updateDownloadWindow(rawPosition, this.snapshot.positionMillis());
        if (!source.awaitAvailable(rawPosition, PUMP_CHUNK * 2, WAIT_SLICE_MILLIS)) {
            this.buffering = true;
            return;
        }
        this.buffering = false;
        int read = this.decoded.pcmStream.read(this.pumpBuffer, 0, PUMP_CHUNK);
        if (read == -1) {
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
        this.session.getByteSource().setPlaybackWindow(bytePosition, positionMillis, durationMillis);
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
        if (this.session != null) {
            this.session.close(); // closes the byte source; a pending indexer read then throws and ends
            this.session = null;
        }
        this.sessionView = null;
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
