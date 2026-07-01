package top.gregtao.concerto.core.player.seek;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class LiveSeekIndex implements SeekMap {
    interface Resolver {
        SeekPoint resolve(ProgressiveMediaDataSource source, long timeMilliseconds) throws IOException;
    }

    private final String formatName;
    private final List<SeekPoint> points = new ArrayList<>();
    private long durationMilliseconds = -1L;
    private byte[] prefixBytes;
    private SeekMap delegate;
    private Resolver resolver;
    private AudioFormat directAudioFormat;
    private long directAudioDataEndOffset = -1L;
    private long wavDataStart = -1L;
    private long wavDataSize = -1L;
    private long wavByteRate = -1L;
    private int wavBlockAlign = -1;

    public LiveSeekIndex(String formatName) {
        this.formatName = formatName;
        this.points.add(SeekPoint.at(0L, 0L));
    }

    public synchronized void addPoint(long timeMilliseconds, long byteOffset) {
        if (byteOffset < 0L || timeMilliseconds < 0L) {
            return;
        }
        for (SeekPoint point : this.points) {
            if (point.getByteOffset() == byteOffset || point.getTimeMilliseconds() == timeMilliseconds) {
                return;
            }
        }
        this.points.add(SeekPoint.at(timeMilliseconds, byteOffset));
        this.points.sort(Comparator.comparingLong(SeekPoint::getTimeMilliseconds));
    }

    public synchronized void setDelegate(SeekMap delegate) {
        this.delegate = delegate;
        if (delegate != null && delegate.getDurationMilliseconds() >= 0L) {
            this.durationMilliseconds = delegate.getDurationMilliseconds();
        }
    }

    public synchronized void setResolver(Resolver resolver) {
        this.resolver = resolver;
    }

    public synchronized void setPrefixBytes(byte[] prefixBytes) {
        this.prefixBytes = prefixBytes == null || prefixBytes.length == 0 ? null : prefixBytes.clone();
    }

    public synchronized void configureWav(long dataStart, long dataSize, long byteRate, int blockAlign, AudioFormat format) {
        this.wavDataStart = dataStart;
        this.wavDataSize = dataSize;
        this.wavByteRate = byteRate;
        this.wavBlockAlign = blockAlign;
        this.directAudioFormat = format;
        this.directAudioDataEndOffset = dataStart + dataSize;
        this.durationMilliseconds = byteRate > 0L ? dataSize * 1000L / byteRate : -1L;
        this.points.clear();
        this.points.add(SeekPoint.at(0L, dataStart));
    }

    public synchronized void setDurationMilliseconds(long durationMilliseconds) {
        this.durationMilliseconds = durationMilliseconds;
    }

    @Override
    public synchronized boolean isSeekable() {
        return this.delegate != null && this.delegate.isSeekable()
                || this.points.size() > 1
                || this.directAudioFormat != null && this.wavDataStart >= 0L
                || this.resolver != null;
    }

    @Override
    public synchronized long getDurationMilliseconds() {
        return this.delegate != null && this.delegate.getDurationMilliseconds() >= 0L
                ? this.delegate.getDurationMilliseconds()
                : this.durationMilliseconds;
    }

    @Override
    public synchronized SeekPoint timeToSeekPoint(long timeMilliseconds) {
        if (this.delegate != null && this.delegate.isSeekable()) {
            return this.delegate.timeToSeekPoint(timeMilliseconds);
        }
        if (this.directAudioFormat != null && this.wavDataStart >= 0L && this.wavByteRate > 0L) {
            long target = Math.max(0L, timeMilliseconds);
            long byteOffset = this.wavDataStart + alignDown(target * this.wavByteRate / 1000L, this.wavBlockAlign);
            byteOffset = Math.min(byteOffset, this.wavDataStart + this.wavDataSize);
            long actualMs = (byteOffset - this.wavDataStart) * 1000L / this.wavByteRate;
            return SeekPoint.at(actualMs, byteOffset).withRequestedTime(target);
        }
        long target = Math.max(0L, timeMilliseconds);
        this.points.sort(Comparator.comparingLong(SeekPoint::getTimeMilliseconds));
        int low = 0;
        int high = this.points.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            long midTime = this.points.get(mid).getTimeMilliseconds();
            if (midTime <= target) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return this.points.get(Math.max(0, high)).withRequestedTime(target);
    }

    public SeekPoint resolveTimeToSeekPoint(ProgressiveMediaDataSource source, long timeMilliseconds) throws IOException {
        SeekPoint known = this.timeToSeekPoint(timeMilliseconds);
        SeekMap activeDelegate;
        Resolver activeResolver;
        synchronized (this) {
            activeDelegate = this.delegate;
            activeResolver = this.resolver;
        }
        if (activeDelegate != null && activeDelegate.isSeekable()) {
            return known;
        }
        if (activeResolver == null || known.getTimeMilliseconds() == Math.max(0L, timeMilliseconds)) {
            return known;
        }
        SeekPoint resolved = activeResolver.resolve(source, Math.max(0L, timeMilliseconds));
        if (resolved != null) {
            this.addPoint(resolved.getTimeMilliseconds(), resolved.getByteOffset());
            return resolved.withRequestedTime(timeMilliseconds);
        }
        return known;
    }

    @Override
    public javax.sound.sampled.AudioFormat getDirectAudioFormat() {
        return this.directAudioFormat;
    }

    @Override
    public long getDirectAudioDataEndOffset() {
        return this.directAudioDataEndOffset;
    }

    @Override
    public String getFormatName() {
        return this.formatName;
    }

    @Override
    public synchronized InputStream openSeekInputStream(ProgressiveMediaDataSource source, SeekPoint seekPoint) throws IOException {
        if (this.delegate != null) {
            return this.delegate.openSeekInputStream(source, seekPoint);
        }
        if (this.prefixBytes != null && seekPoint.getByteOffset() > 0L) {
            return new PrefixInputStream(this.prefixBytes, source.openStream(seekPoint.getByteOffset()));
        }
        return source.openStream(seekPoint.getByteOffset());
    }

    @Override
    public synchronized AudioInputStream openDirectAudioInputStream(ProgressiveMediaDataSource source, SeekPoint seekPoint) throws IOException {
        if (this.directAudioFormat == null || this.wavDataStart < 0L) {
            return null;
        }
        long offset = Math.max(this.wavDataStart, Math.min(seekPoint.getByteOffset(), this.wavDataStart + this.wavDataSize));
        long remainingBytes = Math.max(0L, this.wavDataStart + this.wavDataSize - offset);
        InputStream stream = new LimitedInputStream(source.openStream(offset), remainingBytes);
        long frameLength = this.wavBlockAlign <= 0 ? AudioSystem.NOT_SPECIFIED : remainingBytes / this.wavBlockAlign;
        return new AudioInputStream(stream, this.directAudioFormat, frameLength);
    }

    private static long alignDown(long value, int alignment) {
        return alignment <= 1 ? value : value - (value % alignment);
    }
}
