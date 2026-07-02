package top.gregtao.concerto.core.player.seek;

public class SeekPoint {
    private final long timeMilliseconds;
    private final long byteOffset;
    private final long pcmSkipMilliseconds;

    public SeekPoint(long timeMilliseconds, long byteOffset, long pcmSkipMilliseconds) {
        this.timeMilliseconds = Math.max(0L, timeMilliseconds);
        this.byteOffset = Math.max(0L, byteOffset);
        this.pcmSkipMilliseconds = Math.max(0L, pcmSkipMilliseconds);
    }

    public static SeekPoint at(long timeMilliseconds, long byteOffset) {
        return new SeekPoint(timeMilliseconds, byteOffset, 0L);
    }

    public SeekPoint withRequestedTime(long requestedTimeMilliseconds) {
        return new SeekPoint(this.timeMilliseconds, this.byteOffset,
                Math.max(0L, requestedTimeMilliseconds - this.timeMilliseconds));
    }

    public long getTimeMilliseconds() {
        return this.timeMilliseconds;
    }

    public long getByteOffset() {
        return this.byteOffset;
    }

    public long getPcmSkipMilliseconds() {
        return this.pcmSkipMilliseconds;
    }
}
