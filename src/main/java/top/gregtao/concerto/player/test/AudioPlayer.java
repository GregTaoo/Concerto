package top.gregtao.concerto.player.test;

import org.slf4j.Logger;

import javax.sound.sampled.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class AudioPlayer {

    private static final int READ_BUFF_LENGTH = 1024;

    private SourceDataLine dataLine;
    private AudioInputStream audio;
    private double speedFactor = 1;
    private long startTimestamp = -1;
    private long progressTimestamp = -1;
    private boolean isLoaded = false, isPaused = false;
    private final Logger logger;
    private Status status = Status.NOT_SPECIFIED;

    private final List<Listener> listeners = new ArrayList<>();

    public AudioPlayer(Logger logger) {
        this.logger = logger;
    }

    public void addListener(Listener listener) {
        this.listeners.add(listener);
    }

    private void updateStatus(Status status) {
        if (this.status == status) return;
        this.status = status;
        this.listeners.forEach(listener -> listener.onStatusUpdated(status));
    }

    public void reset() {
        if (this.audio != null) {
            try {
                this.audio.close();
                this.audio = null;
            } catch (IOException e) {
                this.logger.error("Cannot close stream: " + e);
            }
        }
        if (this.dataLine != null) {
            this.dataLine.drain();
            this.dataLine.stop();
            this.dataLine.close();
        }
        this.startTimestamp = this.progressTimestamp = -1;
        this.isLoaded = this.isPaused = false;
        this.updateStatus(Status.NOT_SPECIFIED);
    }

    public void loadAudioStream(InputStream inputStream) throws UnsupportedAudioFileException, IOException, LineUnavailableException {
        this.loadAudioStream(AudioSystem.getAudioInputStream(inputStream));
    }

    public void loadAudioStream(AudioInputStream inputStream) throws LineUnavailableException, IOException {
        this.reset();
        this.updateStatus(Status.OPENING);

        this.audio = inputStream;
        this.createLine();
        this.dataLine.open(this.audio.getFormat());
        this.dataLine.start();

        int bytesRead;
        byte[] readBuff = new byte[READ_BUFF_LENGTH];
        this.startTimestamp = System.currentTimeMillis();
        this.updateStatus(Status.PLAYING);
        this.isLoaded = true;

        while (this.isLoaded && (bytesRead = this.audio.read(readBuff, 0, readBuff.length)) != -1) {
            try {
                synchronized (this) {
                    while (this.isPaused) wait();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                this.logger.warn("Interrupted: " + e.getMessage());
            }
            this.progressTimestamp = System.currentTimeMillis() - this.startTimestamp;
            this.listeners.forEach(listener -> listener.onProgress(this.progressTimestamp));
            this.dataLine.write(readBuff, 0, bytesRead);
        }

        if (this.audio != null) this.updateStatus(Status.EOM);
        this.reset();
    }

    private void createLine() throws LineUnavailableException {
        AudioFormat sourceFormat = this.audio.getFormat();
        int nSampleSizeInBits = sourceFormat.getSampleSizeInBits();
        if (sourceFormat.getEncoding() == AudioFormat.Encoding.ULAW || sourceFormat.getEncoding() == AudioFormat.Encoding.ALAW
                || nSampleSizeInBits != 8)
            nSampleSizeInBits = 16;

        AudioFormat targetFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                (float) (sourceFormat.getSampleRate() * this.speedFactor), nSampleSizeInBits, sourceFormat.getChannels(),
                nSampleSizeInBits / 8 * sourceFormat.getChannels(), sourceFormat.getSampleRate(), false);

        this.audio = AudioSystem.getAudioInputStream(targetFormat, this.audio);
        DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, this.audio.getFormat(),
                AudioSystem.NOT_SPECIFIED);

        this.dataLine = (SourceDataLine) AudioSystem.getLine(lineInfo);
    }

    public void setSpeedFactor(double speedFactor) {
        this.speedFactor = speedFactor;
    }

    public void setGain(float value) {
        if (this.dataLine == null || !this.dataLine.isControlSupported(FloatControl.Type.MASTER_GAIN)) return;
        FloatControl control = (FloatControl) this.dataLine.getControl(FloatControl.Type.MASTER_GAIN);
        control.setValue((float) (10 * Math.log10(value)));
    }

    public long getProgress() {
        return this.progressTimestamp;
    }

    public Status getStatus() {
        return this.status;
    }

    public void pause() {
        if (!this.isLoaded || this.isPaused) return;
        synchronized (this) {
            this.isPaused = true;
            this.updateStatus(Status.PAUSED);
            notifyAll();
        }
    }

    public void resume() {
        if (!this.isLoaded || !this.isPaused) return;
        synchronized (this) {
            this.isPaused = false;
            this.updateStatus(Status.PLAYING);
            this.startTimestamp = System.currentTimeMillis() - this.progressTimestamp;
            notifyAll();
        }
    }

    public void stop() {
        this.reset();
    }

    public boolean isPlaying() {
        return this.status == Status.PLAYING;
    }

    public interface Listener {

        void onProgress(long progress);

        void onStatusUpdated(Status status);
    }

    public enum Status {
        NOT_SPECIFIED,
        OPENING,
        PLAYING,
        PAUSED,
        EOM
    }
}
