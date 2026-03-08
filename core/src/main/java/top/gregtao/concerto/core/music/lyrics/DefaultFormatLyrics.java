package top.gregtao.concerto.core.music.lyrics;

import top.gregtao.concerto.core.music.MusicTimestamp;
import top.gregtao.concerto.core.util.MathUtil;
import top.gregtao.concerto.core.util.TextUtil;

public class DefaultFormatLyrics extends Lyrics {
    @Override
    public void parse(String raw) {
        raw = TextUtil.trimSurrounding(raw, "[", "]");
        String[] lines = raw.split("(\n|\r|\r\n)");
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty() && line.matches(MusicTimestamp.FORMAT_REGEX) && !line.endsWith("]")) {
                String[] args = line.split("]");
                for (int index = 0; index < args.length - 1; ++index) {
                    String[] timesArr = args[index].substring(1).split(":");
                    if (timesArr.length < 2) continue;
                    String[] secondsArr = timesArr[1].split("\\.");
                    String lyric = args[args.length - 1].trim();
                    if (!lyric.isEmpty() && !lyric.startsWith("//")) {
                        lyric = lyric.replaceAll(MusicTimestamp.INLINE_REGEX, "");
                        if (secondsArr.length == 2) {
                            long milli = MathUtil.parseIntOrElse(secondsArr[1], 0);
                            int place = (int) (1000 / Math.pow(10, secondsArr[1].trim().length()));
                            this.addLine(MusicTimestamp.of(
                                    MathUtil.parseIntOrElse(timesArr[0], 0),
                                    MathUtil.parseIntOrElse(secondsArr[0], 0),
                                    (int) (milli * place)
                            ), lyric);
                        } else {
                            this.addLine(MusicTimestamp.of(
                                    MathUtil.parseIntOrElse(timesArr[0], 0),
                                    MathUtil.parseIntOrElse(secondsArr[0], 0)
                            ), lyric);
                        }
                    }
                }
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        this.getLyricBody().forEach(pair ->
                builder.append("[").append(pair.getFirst().toString()).append("] ").append(pair.getSecond()).append("\n"));
        return builder.toString();
    }
}
