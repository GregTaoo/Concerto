package top.gregtao.concerto.core.player.seek;

import top.gregtao.concerto.core.player.streamplayer.stream.DataSource;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

public class ProgressiveDataSource implements DataSource {
    private final ProgressiveMediaDataSource mediaSource;
    private final String suffix;
    private final LiveSeekIndex seekMap;
    private SeekPoint currentSeekPoint = SeekPoint.at(0L, 0L);
    private final String formatName;

    public ProgressiveDataSource(ProgressiveMediaDataSource mediaSource, String suffix) throws IOException {
        this.mediaSource = mediaSource;
        this.suffix = suffix;
        String normalizedSuffix = suffix == null || suffix.isEmpty() ? mediaSource.getSuggestedSuffix() : suffix;
        this.formatName = ExtractorFactory.detectFormat(mediaSource, normalizedSuffix);
        this.seekMap = new LiveSeekIndex(this.formatName);
        this.mediaSource.setLiveSeekIndex(this.seekMap);
    }

    public ProgressiveMediaDataSource getMediaSource() {
        return this.mediaSource;
    }

    public SeekMap getSeekMap() {
        return this.seekMap;
    }

    public boolean isSeekable() {
        return this.seekMap.isSeekable();
    }

    public SeekPoint seekToMilliseconds(long milliseconds) {
        if (!this.seekMap.isSeekable()) {
            this.currentSeekPoint = SeekPoint.at(0L, 0L);
        } else {
            try {
                this.currentSeekPoint = this.seekMap.resolveTimeToSeekPoint(this.mediaSource, milliseconds);
            } catch (IOException ignored) {
                this.currentSeekPoint = this.seekMap.timeToSeekPoint(milliseconds);
            }
        }
        return this.currentSeekPoint;
    }

    public long getCurrentSeekTimeMilliseconds() {
        return this.currentSeekPoint.getTimeMilliseconds();
    }

    @Override
    public Object getSource() {
        return this.mediaSource;
    }

    @Override
    public AudioFileFormat getAudioFileFormat() throws UnsupportedAudioFileException, IOException {
        return AudioSystem.getAudioFileFormat(markable(this.seekMap.openSeekInputStream(this.mediaSource, this.currentSeekPoint)));
    }

    @Override
    public AudioInputStream getAudioInputStream() throws UnsupportedAudioFileException, IOException {
        AudioInputStream direct = this.seekMap.openDirectAudioInputStream(this.mediaSource, this.currentSeekPoint);
        if (direct != null) {
            return direct;
        }
        return AudioSystem.getAudioInputStream(markable(this.seekMap.openSeekInputStream(this.mediaSource, this.currentSeekPoint)));
    }

    @Override
    public int getDurationInSeconds() {
        long ms = this.getDurationInMilliseconds();
        return ms < 0L ? -1 : (int) (ms / 1000L);
    }

    @Override
    public long getDurationInMilliseconds() {
        return this.seekMap.getDurationMilliseconds();
    }

    @Override
    public Duration getDuration() {
        long ms = this.getDurationInMilliseconds();
        return ms < 0L ? null : Duration.ofMillis(ms);
    }

    @Override
    public boolean isFile() {
        return true;
    }

    @Override
    public String toString() {
        return "ProgressiveDataSource{" + this.seekMap.getFormatName() + "}";
    }

    private static InputStream markable(InputStream inputStream) {
        return inputStream.markSupported() ? inputStream : new BufferedInputStream(inputStream, 2 << 18);
    }
}
