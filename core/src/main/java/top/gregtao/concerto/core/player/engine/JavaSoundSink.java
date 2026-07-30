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
    private byte[] conversionBuffer = new byte[0];
    private int conversionBits = 0;
    private FloatControl gainControl;
    private long frameAnchor = 0;
    private float gain = 1f;
    private boolean paused = false;
    private String outputDescription = "default JavaSound mixer";

    @Override
    public void open(AudioFormat format) throws Exception {
        Exception firstFailure = null;
        AudioFormat outputFormat = format;
        try {
            this.openLine(format);
        } catch (LineUnavailableException | IllegalArgumentException e) {
            firstFailure = e;
        }

        if (this.line == null && PcmSampleConverter.isFloat32(format)) {
            for (int bits : new int[]{24, 16}) {
                AudioFormat candidate = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                        format.getSampleRate(), bits, format.getChannels(),
                        bits / 8 * format.getChannels(), format.getSampleRate(), false);
                try {
                    this.openLine(candidate);
                    this.conversionBits = bits;
                    outputFormat = candidate;
                    break;
                } catch (LineUnavailableException | IllegalArgumentException ignored) {
                }
            }
        }
        if (this.line == null) throw firstFailure;

        this.outputDescription = this.line.getClass().getName() + " (" + this.line.getLineInfo()
                + ", output PCM " + outputFormat.getSampleSizeInBits() + "-bit)";
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
    public String getOutputDescription() {
        return this.outputDescription;
    }

    @Override
    public void write(byte[] data, int offset, int length) {
        if (this.conversionBits == 0) {
            this.line.write(data, offset, length);
            return;
        }
        int required = length / Float.BYTES * (this.conversionBits / 8);
        if (this.conversionBuffer.length < required) this.conversionBuffer = new byte[required];
        int converted = PcmSampleConverter.float32ToSignedPcm(
                data, offset, length, this.conversionBuffer, this.conversionBits);
        this.line.write(this.conversionBuffer, 0, converted);
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
            this.conversionBits = 0;
        }
    }

    private void openLine(AudioFormat format) throws LineUnavailableException {
        SourceDataLine candidate = null;
        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format, AudioSystem.NOT_SPECIFIED);
            candidate = (SourceDataLine) AudioSystem.getLine(info);
            candidate.open(format);
            this.line = candidate;
        } catch (LineUnavailableException | IllegalArgumentException e) {
            if (candidate != null) candidate.close();
            throw e;
        }
    }
}
