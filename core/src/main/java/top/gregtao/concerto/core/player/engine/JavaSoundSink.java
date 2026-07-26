package top.gregtao.concerto.core.player.engine;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/** {@link AudioSink} backed by a JavaSound {@link SourceDataLine}. */
public class JavaSoundSink implements AudioSink {

    private SourceDataLine line;
    private FloatControl gainControl;
    private long frameAnchor = 0;
    private float gain = 1f;
    private boolean paused = false;

    @Override
    public void open(AudioFormat format) throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format, AudioSystem.NOT_SPECIFIED);
        this.line = (SourceDataLine) AudioSystem.getLine(info);
        this.line.open(format);
        this.gainControl = this.line.isControlSupported(FloatControl.Type.MASTER_GAIN)
                ? (FloatControl) this.line.getControl(FloatControl.Type.MASTER_GAIN)
                : null;
        this.frameAnchor = 0;
        this.applyGain();
        this.line.start();
        if (this.paused) this.line.stop();
    }

    @Override
    public boolean isOpen() {
        return this.line != null && this.line.isOpen();
    }

    @Override
    public void write(byte[] data, int offset, int length) {
        this.line.write(data, offset, length);
    }

    @Override
    public void pause() {
        this.paused = true;
        if (this.line != null) this.line.stop();
    }

    @Override
    public void resume() {
        this.paused = false;
        if (this.line != null) this.line.start();
    }

    @Override
    public void flush() {
        if (this.line == null) return;
        this.line.flush();
        this.frameAnchor = this.line.getLongFramePosition();
    }

    @Override
    public void drain() {
        if (this.line != null) this.line.drain();
    }

    @Override
    public long playedFrames() {
        return this.line == null ? 0 : Math.max(0, this.line.getLongFramePosition() - this.frameAnchor);
    }

    @Override
    public void setGain(float gain) {
        this.gain = gain;
        this.applyGain();
    }

    private void applyGain() {
        if (this.gainControl == null) return;
        // -80 dB is an inaudible floor that keeps 20*log10(0) = -Inf out of the control
        float decibels = this.gain <= 0.0001f ? -80f : (float) (20.0 * Math.log10(this.gain));
        decibels = Math.max(this.gainControl.getMinimum(), Math.min(this.gainControl.getMaximum(), decibels));
        this.gainControl.setValue(decibels);
    }

    @Override
    public void close() {
        if (this.line != null) {
            this.line.flush();
            this.line.close();
            this.line = null;
            this.gainControl = null;
        }
    }
}
