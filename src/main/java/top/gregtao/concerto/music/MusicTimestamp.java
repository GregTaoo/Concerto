package top.gregtao.concerto.music;

import org.jetbrains.annotations.NotNull;

public class MusicTimestamp implements Comparable<MusicTimestamp> {

    public static String FORMAT_REGEX = "\\[[\\d]+(:)?[\\d]+(.)?[\\d]+][\\s\\S]+";

    private final int minute;
    private final int second;
    private final int millisecond;

    private MusicTimestamp(int minute, int second, int millisecond) {
        this.minute = minute;
        this.second = second;
        this.millisecond = millisecond;
    }

    public static MusicTimestamp of(int minute, int second, int millisecond) {
        return new MusicTimestamp(minute, second, millisecond);
    }

    public static MusicTimestamp of(int minute, int second) {
        return new MusicTimestamp(minute, second, 0);
    }

    public static MusicTimestamp of(int seconds) {
        return new MusicTimestamp(seconds / 60, seconds % 60, 0);
    }

    public static MusicTimestamp ofMilliseconds(long milliseconds) {
        return new MusicTimestamp((int) (milliseconds / 1000 / 60), (int) (milliseconds / 1000 % 60), (int) (milliseconds % 1000));
    }

    public long asMilliseconds() {
        return this.minute * 60L * 1000L + this.second * 1000L + this.millisecond;
    }

    public int asSeconds() {
        return this.minute * 60 + this.second;
    }

    @Override
    public int compareTo(@NotNull MusicTimestamp o) {
        return (int) (this.asMilliseconds() - o.asMilliseconds());
    }

    public String toString() {
        return String.format("%02d:%02d.%03d", this.minute, this.second, this.millisecond);
    }

    public String toShortString() {
        return String.format("%02d:%02d", this.minute, this.second);
    }
}
