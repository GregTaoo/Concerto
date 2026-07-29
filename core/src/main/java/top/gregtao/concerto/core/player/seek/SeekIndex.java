package top.gregtao.concerto.core.player.seek;

import java.util.ArrayList;

/**
 * Time-to-byte-offset index for one piece of media, built incrementally by a
 * {@link SeekIndexBuilder} while the media downloads and queried by the playback
 * engine when seeking.
 *
 * Points are appended in strictly increasing time order, so lookups are a plain
 * binary search over an append-only list.
 */
public final class SeekIndex {

    /**
     * A safe decode entry point: decoding from {@code byteOffset} yields audio
     * starting at {@code timeMillis}.
     */
    public record Point(long timeMillis, long byteOffset) {}

    private final ArrayList<Point> points = new ArrayList<>();
    private byte[] prefixBytes = null;
    private long coveredToMillis = 0;
    private long durationMillis = -1;
    private boolean complete = false;

    public synchronized void append(long timeMillis, long byteOffset) {
        if (!this.points.isEmpty() && timeMillis <= this.points.get(this.points.size() - 1).timeMillis()) {
            return;
        }
        this.points.add(new Point(timeMillis, byteOffset));
        this.coveredToMillis = Math.max(this.coveredToMillis, timeMillis);
    }

    /** The latest point at or before {@code timeMillis}, or null if none indexed yet. */
    public synchronized Point floor(long timeMillis) {
        int low = 0, high = this.points.size() - 1, result = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (this.points.get(mid).timeMillis() <= timeMillis) {
                result = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return result < 0 ? null : this.points.get(result);
    }

    /**
     * Container header bytes that must precede mid-stream data for a decoder to
     * accept it (e.g. Ogg header pages, {@code fLaC}+STREAMINFO, the WAV header).
     * Null when the format self-synchronizes (MP3).
     */
    public synchronized byte[] getPrefixBytes() {
        return this.prefixBytes;
    }

    public synchronized void setPrefixBytes(byte[] prefixBytes) {
        this.prefixBytes = prefixBytes;
    }

    /** Timestamps up to this value (ms) can be resolved to a byte offset. */
    public synchronized long getCoveredToMillis() {
        return this.complete && this.durationMillis > 0 ? this.durationMillis : this.coveredToMillis;
    }

    public synchronized void setCoveredToMillis(long coveredToMillis) {
        this.coveredToMillis = Math.max(this.coveredToMillis, coveredToMillis);
    }

    /** Exact media duration in ms as determined by the scan, or -1 if unknown. */
    public synchronized long getDurationMillis() {
        return this.durationMillis;
    }

    public synchronized void setDurationMillis(long durationMillis) {
        this.durationMillis = durationMillis;
    }

    public synchronized boolean isComplete() {
        return this.complete;
    }

    /** Marks the scan finished: the whole media is now covered. */
    public synchronized void markComplete() {
        this.complete = true;
    }

    public synchronized boolean isEmpty() {
        return this.points.isEmpty();
    }
}
