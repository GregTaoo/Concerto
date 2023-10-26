package top.gregtao.concerto.music;

import com.goxr3plus.streamplayer.stream.StreamPlayer;
import com.goxr3plus.streamplayer.stream.StreamPlayerException;
import top.gregtao.concerto.util.FileUtil;
import top.gregtao.concerto.util.HttpURLInputStream;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class MusicSource {
    private final Object object;
    private final Type type;

    private MusicSource(Object object, Type type) {
        this.object = object;
        this.type = type;
    }

    public void open(StreamPlayer player) throws StreamPlayerException, IOException {
        switch (this.type) {
            case URL -> player.open(FileUtil.createBuffered(new HttpURLInputStream((URL) this.object)));
            case FILE -> player.open((File) this.object);
            case STREAM -> player.open((InputStream) this.object);
        }
    }

    public AudioInputStream getAudioStream() throws UnsupportedAudioFileException, IOException {
        switch (this.type) {
            case URL -> {
                return AudioSystem.getAudioInputStream((URL) this.object);
            }
            case FILE -> {
                return AudioSystem.getAudioInputStream((File) this.object);
            }
            case STREAM -> {
                return AudioSystem.getAudioInputStream((InputStream) this.object);
            }
        }
        throw new UnsupportedOperationException();
    }

    public static MusicSource of(URL object) {
        return new MusicSource(object, Type.URL);
    }

    public static MusicSource of(File object) {
        return new MusicSource(object, Type.FILE);
    }

    public static MusicSource of(InputStream object) {
        return new MusicSource(object, Type.STREAM);
    }

    public enum Type {
        URL,
        FILE,
        STREAM
    }
}
