package top.gregtao.concerto.core.music.lyrics;

import top.gregtao.concerto.core.Concerto;
import top.gregtao.concerto.core.music.MusicTimestamp;
import top.gregtao.concerto.core.util.MathUtil;
import top.gregtao.concerto.core.util.Pair;

import java.util.ArrayList;
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

    public String nextLine() {
        ++this.index;
        return this.getCurrent();
    }

    public String stayOrNext(long timestamp) {
        if (this.index < this.lyricBody.size() - 1 &&
                timestamp >= this.lyricBody.get(this.index + 1).getFirst().asMilliseconds()) {
            return this.nextLine();
        } else {
            return this.getCurrent();
        }
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

    public boolean isEmpty() {
        return this.getLyricBody().isEmpty();
    }
}
