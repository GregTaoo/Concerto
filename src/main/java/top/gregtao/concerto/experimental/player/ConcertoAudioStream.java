package top.gregtao.concerto.experimental.player;

import net.minecraft.client.sound.AudioStream;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ConcertoAudioStream implements AudioStream {
    private AudioInputStream audioInputStream;
    private final AudioFormat audioFormat;

    public ConcertoAudioStream(AudioInputStream inputStream) {
        this.audioInputStream = inputStream;
        AudioFormat sourceFormat = this.audioInputStream.getFormat();
        int nSampleSizeInBits = sourceFormat.getSampleSizeInBits();
        if (sourceFormat.getEncoding() == AudioFormat.Encoding.ULAW || sourceFormat.getEncoding() == AudioFormat.Encoding.ALAW
                || nSampleSizeInBits != 8) {
            nSampleSizeInBits = 16;
        }
        AudioFormat targetFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, sourceFormat.getSampleRate(), nSampleSizeInBits,
                sourceFormat.getChannels(), nSampleSizeInBits / 8 * sourceFormat.getChannels(), sourceFormat.getSampleRate(), false);
        this.audioInputStream = AudioSystem.getAudioInputStream(targetFormat, this.audioInputStream);
        this.audioFormat = audioInputStream.getFormat();
    }

    @Override
    public AudioFormat getFormat() {
        return this.audioFormat;
    }

    @Override
    public ByteBuffer getBuffer(int size) throws IOException {
        ByteBuffer byteBuffer = BufferUtils.createByteBuffer(size);
        byte[] bytes = this.audioInputStream.readNBytes(size);
        byteBuffer.put(bytes);
        byteBuffer.flip();
        return byteBuffer;
    }

    @Override
    public void close() throws IOException {
        this.audioInputStream.close();
    }
}
