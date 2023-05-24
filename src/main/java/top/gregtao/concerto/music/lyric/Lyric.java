package top.gregtao.concerto.music.lyric;

import com.mojang.datafixers.util.Pair;
import net.minecraft.text.Text;
import top.gregtao.concerto.music.MusicTimestamp;
import top.gregtao.concerto.util.MathUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public abstract class Lyric {

    private final ArrayList<Pair<MusicTimestamp, String>> lyricBody = new ArrayList<>();

    private int index = 0;

    public Text getCurrent() {
        return Text.literal(this.lyricBody.get(this.index).getSecond());
    }

    public Text nextLine() {
        ++this.index;
        return this.getCurrent();
    }

    public Text stayOrNext(long timestamp) {
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

    public abstract void parse(String raw);

    public Lyric load(String raw) {
        this.parse(raw);
        this.sortLines();
        return this;
    }

    public void sortLines() {
        this.lyricBody.sort((Comparator.comparing(Pair::getFirst)));
    }

    public Text startFrom(long timestamp) {
        this.index = MathUtil.lowerBound(this.lyricBody, Pair.of(MusicTimestamp.of(timestamp), ""),
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
