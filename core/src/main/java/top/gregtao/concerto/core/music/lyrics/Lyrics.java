package top.gregtao.concerto.core.music.lyrics;

import top.gregtao.concerto.core.Concerto;
import top.gregtao.concerto.core.music.MusicTimestamp;
import top.gregtao.concerto.core.util.MathUtil;
import top.gregtao.concerto.core.util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

public abstract class Lyrics {

    private final ArrayList<Pair<MusicTimestamp, String>> lyricBody = new ArrayList<>();

    private int index = 0;

    public String getCurrent(int delta) {
        if (this.index + delta < 0 || this.index + delta >= this.lyricBody.size()) {
            throw new UnsupportedOperationException("Out of bound");
        }
        return this.lyricBody.get(this.index + delta).getSecond();
    }

    public String getCurrent() {
        return this.getCurrent(0);
    }

    public int getCurrentIndex() {
        return this.index;
    }

    public long getLineStartMilliseconds(int index) {
        if (index < 0 || index >= this.lyricBody.size()) {
            throw new UnsupportedOperationException("Out of bound");
        }
        return this.lyricBody.get(index).getFirst().asMilliseconds();
    }

    public long getLineEndMilliseconds(int index, MusicTimestamp fallbackEnd) {
        if (index < 0 || index >= this.lyricBody.size()) {
            throw new UnsupportedOperationException("Out of bound");
        }
        if (index + 1 < this.lyricBody.size()) {
            return this.lyricBody.get(index + 1).getFirst().asMilliseconds();
        }
        return fallbackEnd == null ? this.getLineStartMilliseconds(index) : fallbackEnd.asMilliseconds();
    }

    public String nextLine() {
        ++this.index;
        return this.getCurrent();
    }

    public String stayOrNext(long timestamp) {
        if (this.lyricBody.isEmpty()) {
            return "";
        }
        while (this.index > 0 && timestamp < this.lyricBody.get(this.index).getFirst().asMilliseconds()) {
            --this.index;
        }
        while (this.index < this.lyricBody.size() - 1 &&
                timestamp >= this.lyricBody.get(this.index + 1).getFirst().asMilliseconds()) {
            ++this.index;
        }
        return this.getCurrent();
    }

    public void addLine(MusicTimestamp timestamp, String line) {
        Collections.addAll(this.lyricBody, new Pair<>(timestamp, line));
    }

    // Override this function with this.addLine()
    public abstract void parse(String raw);

    public abstract String toString();

    public Lyrics load(String raw) {
        try {
            this.parse(raw);
        } catch (Exception e) {
            Concerto.getLogger().error("Error parsing lyric", e);
        }
        this.sortLines();
        return this;
    }

    public void sortLines() {
        this.lyricBody.sort((Comparator.comparing(Pair::getFirst)));
    }

    public String startFrom(long timestamp) {
        this.index = MathUtil.lowerBound(this.lyricBody, Pair.of(MusicTimestamp.ofMilliseconds(timestamp), ""),
                (o1, o2) -> o2.getFirst().compareTo(o1.getFirst()));
        return this.getCurrent();
    }

    public ArrayList<Pair<MusicTimestamp, String>> getLyricBody() {
        return this.lyricBody;
    }

    public static int[] createTimestampMapping(Lyrics source, Lyrics target, long toleranceMs) {
        if (source == null || target == null || source.isEmpty() || target.isEmpty()) {
            return new int[0];
        }

        ArrayList<Pair<MusicTimestamp, String>> sourceBody = source.getLyricBody();
        ArrayList<Pair<MusicTimestamp, String>> targetBody = target.getLyricBody();
        int[] mapping = new int[sourceBody.size()];
        Arrays.fill(mapping, -1);

        int targetIndex = 0;
        for (int i = 0; i < sourceBody.size(); i++) {
            long sourceTime = sourceBody.get(i).getFirst().asMilliseconds();
            while (targetIndex + 1 < targetBody.size() &&
                    sourceTime >= targetBody.get(targetIndex + 1).getFirst().asMilliseconds()) {
                ++targetIndex;
            }
            if (Math.abs(targetBody.get(targetIndex).getFirst().asMilliseconds() - sourceTime) < toleranceMs) {
                mapping[i] = targetIndex;
            }
        }
        return mapping;
    }

    public boolean isEmpty() {
        return this.getLyricBody().isEmpty();
    }
}
