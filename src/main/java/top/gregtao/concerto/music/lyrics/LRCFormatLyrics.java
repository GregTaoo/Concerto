package top.gregtao.concerto.music.lyrics;

import top.gregtao.concerto.music.MusicTimestamp;
import top.gregtao.concerto.util.MathUtil;

public class LRCFormatLyrics extends Lyrics {
    @Override
    public void parse(String raw) {
        String[] lines = raw.split("(\n|\r|\r\n)");
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty() && line.matches(MusicTimestamp.FORMAT_REGEX) && !line.endsWith("]")) {
                String[] args = line.split("]");
                for (int index = 0; index < args.length - 1; ++index) {
                    String[] timesArr = args[index].substring(1).split(":");
                    String[] secondsArr = timesArr[1].split("\\.");
                    if (secondsArr.length == 2) {
                        long milli = MathUtil.parseIntOrElse(secondsArr[1], 0);
                        int place = (int) (1000 / Math.pow(10, secondsArr[1].trim().length()));
                        this.addLine(MusicTimestamp.of(
                                MathUtil.parseIntOrElse(timesArr[0], 0),
                                MathUtil.parseIntOrElse(secondsArr[0], 0),
                                (int) (milli * place)
                        ), args[args.length - 1]);
                    } else {
                        this.addLine(MusicTimestamp.of(
                                MathUtil.parseIntOrElse(timesArr[0], 0),
                                MathUtil.parseIntOrElse(secondsArr[0], 0)
                        ), args[args.length - 1]);
                    }
                }
            }
        }
    }
}
