package top.gregtao.concerto.player.exp;

import net.minecraft.client.sound.AudioStream;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class ConcertoAudioStream implements AudioStream {

    private final AudioInputStream audioInputStream;
    private final AudioFormat audioFormat;
    private double speedFactor = 1;

    public ConcertoAudioStream(InputStream inputStream) throws UnsupportedAudioFileException, IOException {
        try {
            AudioInputStream encodedStream = AudioSystem.getAudioInputStream(inputStream);
            this.audioFormat = this.getDecodedFormat(encodedStream.getFormat());
            this.audioInputStream = AudioSystem.getAudioInputStream(this.audioFormat, encodedStream);
        } catch (Exception e) {
            throw new UnsupportedAudioFileException();
        }
    }

    private AudioFormat getDecodedFormat(AudioFormat audioFormat) {
        int nSampleSizeInBits = audioFormat.getSampleSizeInBits();
        if (audioFormat.getEncoding() == AudioFormat.Encoding.ULAW ||
                audioFormat.getEncoding() == AudioFormat.Encoding.ALAW || nSampleSizeInBits != 8) {
            nSampleSizeInBits = 16;
        }
        return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, (float) (audioFormat.getSampleRate() * this.speedFactor),
                nSampleSizeInBits, audioFormat.getChannels(), nSampleSizeInBits / 8 * audioFormat.getChannels(),
                audioFormat.getSampleRate(), false);
    }

    public void seekSeconds(int seconds) throws IOException {
        this.audioInputStream.readNBytes((int) (this.audioFormat.getFrameRate() * this.audioFormat.getFrameSize() * seconds));
    }

    @Override
    public AudioFormat getFormat() {
        return this.audioFormat;
    }

    @Override
    public ByteBuffer getBuffer(int size) throws IOException {
        ByteBuffer buffer = BufferUtils.createByteBuffer(size);
        buffer.put(this.audioInputStream.readNBytes(size));
        buffer.flip();
        return buffer;
    }

    @Override
    public void close() throws IOException {
        this.audioInputStream.close();
    }
}
