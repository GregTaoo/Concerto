package top.gregtao.concerto.core.player.seek;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.io.IOException;
import java.io.InputStream;

public interface SeekMap {
    boolean isSeekable();

    long getDurationMilliseconds();

    SeekPoint timeToSeekPoint(long timeMilliseconds);

    AudioFormat getDirectAudioFormat();

    long getDirectAudioDataEndOffset();

    String getFormatName();

    default InputStream openSeekInputStream(ProgressiveMediaDataSource source, SeekPoint seekPoint) throws IOException {
        return source.openStream(seekPoint.getByteOffset());
    }

    default AudioInputStream openDirectAudioInputStream(ProgressiveMediaDataSource source, SeekPoint seekPoint) throws IOException {
        return null;
    }
}
