package top.gregtao.concerto.core.player.streamplayer.stream;

import top.gregtao.concerto.core.player.streamplayer.enums.AudioType;
import top.gregtao.concerto.core.player.streamplayer.tools.TimeTool;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.time.Duration;

public class FileDataSource implements DataSource {

    private final File source;

    FileDataSource(File source) {
        this.source = source;
    }

    @Override
    public AudioFileFormat getAudioFileFormat() throws UnsupportedAudioFileException, IOException {
        return AudioSystem.getAudioFileFormat(this.source);
    }

    @Override
    public AudioInputStream getAudioInputStream() throws UnsupportedAudioFileException, IOException {
        return AudioSystem.getAudioInputStream(source);
    }

    @Override
    public int getDurationInSeconds() {
        return TimeTool.durationInSeconds(source.getAbsolutePath(), AudioType.FILE);
    }
    
    @Override
    public long getDurationInMilliseconds() {
    	return TimeTool.durationInMilliseconds(source.getAbsolutePath(), AudioType.FILE);
    }
    
    @Override
    public Duration getDuration() {
        return Duration.ofMillis(getDurationInMilliseconds());
    }

    @Override
    public Object getSource() {
        return source;
    }

    @Override
    public String toString() {
        return "FileDataSource with " + source.toString();
    }

    @Override
    public boolean isFile() {
       return true;
   }
}
