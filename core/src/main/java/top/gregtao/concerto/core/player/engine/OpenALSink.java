package top.gregtao.concerto.core.player.engine;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.openal.EXTFloat32;
import org.lwjgl.openal.EXTThreadLocalContext;
import org.lwjgl.openal.SOFTDirectChannels;
import org.lwjgl.openal.SOFTSourceResampler;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.LineUnavailableException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.Objects;

/**
 * {@link AudioSink} backed by a private OpenAL device and context, streaming
 * through a fixed pool of reusable AL buffers.
 * The context is bound with ALC_EXT_thread_local_context so Minecraft's own
 * sound engine context is never disturbed. All calls happen on the engine
 * thread, which stays the same for the sink's whole lifetime.
 */
public class OpenALSink implements AudioSink {

    private static final int BUFFER_COUNT = 8;
    private static final int BUFFER_BYTES = 16 * 1024;

    private long device = 0;
    private long context = 0;
    private int source = 0;
    private int alFormat;
    private int sampleRate;
    private int frameSize;
    private boolean convertFloatTo16;
    private byte[] conversionBuffer = new byte[0];

    private final ArrayDeque<Integer> freeBuffers = new ArrayDeque<>();
    private final ArrayDeque<int[]> queued = new ArrayDeque<>(); // [bufferId, frameCount]
    private ByteBuffer scratch;
    private long playedFramesBase = 0;
    private float gain = 1f;
    private boolean paused = false;
    private String outputDescription = "default OpenAL device";

    @Override
    public void open(AudioFormat format) throws Exception {
        boolean signed16 = AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())
                && format.getSampleSizeInBits() == 16 && !format.isBigEndian();
        boolean float32 = PcmSampleConverter.isFloat32(format);
        if ((!signed16 && !float32) || format.getChannels() < 1 || format.getChannels() > 2) {
            throw new LineUnavailableException("OpenAL sink only supports little-endian 16-bit or float32 PCM, got " + format);
        }
        this.sampleRate = Math.round(format.getSampleRate());
        this.device = ALC10.alcOpenDevice((ByteBuffer) null);
        if (this.device == 0) throw new LineUnavailableException("Cannot open an OpenAL device");
        String deviceName = ALC10.alcGetString(this.device, ALC10.ALC_DEVICE_SPECIFIER);
        if (deviceName != null && !deviceName.isBlank()) this.outputDescription = deviceName;
        ALCCapabilities deviceCaps = ALC.createCapabilities(this.device);
        if (!deviceCaps.ALC_EXT_thread_local_context) {
            ALC10.alcCloseDevice(this.device);
            this.device = 0;
            throw new LineUnavailableException("OpenAL device lacks ALC_EXT_thread_local_context");
        }
        this.context = ALC10.alcCreateContext(this.device,
                new int[]{ALC10.ALC_FREQUENCY, this.sampleRate, 0});
        if (this.context == 0) {
            this.context = ALC10.alcCreateContext(this.device, (int[]) null);
        }
        if (this.context == 0 || !EXTThreadLocalContext.alcSetThreadContext(this.context)) {
            this.destroyContext();
            throw new LineUnavailableException("Cannot create/bind an OpenAL context");
        }
        var capabilities = AL.createCapabilities(deviceCaps);

        this.convertFloatTo16 = float32 && !capabilities.AL_EXT_FLOAT32;
        if (float32 && !this.convertFloatTo16) {
            this.alFormat = format.getChannels() == 1
                    ? EXTFloat32.AL_FORMAT_MONO_FLOAT32 : EXTFloat32.AL_FORMAT_STEREO_FLOAT32;
        } else {
            this.alFormat = format.getChannels() == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
        }
        this.frameSize = format.getFrameSize();
        this.source = AL10.alGenSources();
        if (format.getChannels() == 2 && capabilities.AL_SOFT_direct_channels) {
            AL10.alSourcei(this.source, SOFTDirectChannels.AL_DIRECT_CHANNELS_SOFT, AL10.AL_TRUE);
        }
        String resampler = "default";
        if (capabilities.AL_SOFT_source_resampler) {
            int count = AL10.alGetInteger(SOFTSourceResampler.AL_NUM_RESAMPLERS_SOFT);
            if (count > 0) {
                int best = count - 1;
                AL10.alSourcei(this.source, SOFTSourceResampler.AL_SOURCE_RESAMPLER_SOFT, best);
                String selected = SOFTSourceResampler.alGetStringiSOFT(
                        SOFTSourceResampler.AL_RESAMPLER_NAME_SOFT, best);
                if (selected != null) resampler = selected;
            }
        }
        AL10.alSourcef(this.source, AL10.AL_GAIN, this.gain);
        this.scratch = ByteBuffer.allocateDirect(BUFFER_BYTES).order(ByteOrder.nativeOrder());
        this.freeBuffers.clear();
        this.queued.clear();
        this.playedFramesBase = 0;
        for (int i = 0; i < BUFFER_COUNT; i++) {
            this.freeBuffers.add(AL10.alGenBuffers());
        }
        int mixerRate = ALC10.alcGetInteger(this.device, ALC10.ALC_FREQUENCY);
        this.outputDescription += ", mixer=" + mixerRate + " Hz, resampler=" + resampler
                + (this.convertFloatTo16 ? ", float32->16-bit fallback" : "");
    }

    @Override
    public boolean isOpen() {
        return this.context != 0;
    }

    @Override
    public String getOutputDescription() {
        return this.outputDescription;
    }

    @Override
    public void write(byte[] data, int offset, int length) {
        int written = 0;
        while (written < length) {
            this.reclaimProcessed();
            Integer buffer = this.freeBuffers.poll();
            if (buffer == null) {
                try {
                    Thread.sleep(5); // backpressure: wait for a queued buffer to play out
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                continue;
            }
            int chunk = Math.min(length - written, BUFFER_BYTES);
            this.scratch.clear();
            if (this.convertFloatTo16) {
                int required = chunk / Float.BYTES * Short.BYTES;
                if (this.conversionBuffer.length < required) this.conversionBuffer = new byte[required];
                int converted = PcmSampleConverter.float32ToSignedPcm(
                        data, offset + written, chunk, this.conversionBuffer, 16);
                this.scratch.put(this.conversionBuffer, 0, converted);
            } else {
                this.scratch.put(data, offset + written, chunk);
            }
            this.scratch.flip();
            AL10.alBufferData(buffer, this.alFormat, this.scratch, this.sampleRate);
            AL10.alSourceQueueBuffers(this.source, buffer);
            this.queued.add(new int[]{buffer, chunk / this.frameSize});
            written += chunk;
            this.ensurePlaying();
        }
    }

    private void reclaimProcessed() {
        int processed = AL10.alGetSourcei(this.source, AL10.AL_BUFFERS_PROCESSED);
        for (int i = 0; i < processed; i++) {
            int buffer = AL10.alSourceUnqueueBuffers(this.source);
            int[] entry = this.queued.poll();
            if (entry != null) this.playedFramesBase += entry[1];
            this.freeBuffers.add(buffer);
        }
    }

    private void ensurePlaying() {
        if (!this.paused && !this.queued.isEmpty()
                && AL10.alGetSourcei(this.source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
            AL10.alSourcePlay(this.source);
        }
    }

    @Override
    public void pause() {
        this.paused = true;
        if (this.source != 0) AL10.alSourcePause(this.source);
    }

    @Override
    public void resume() {
        this.paused = false;
        if (this.source != 0) this.ensurePlaying();
    }

    @Override
    public void flush() {
        if (this.source == 0) return;
        AL10.alSourceStop(this.source);
        this.reclaimProcessed();
        // Anything still queued after a stop is stale too
        while (!this.queued.isEmpty()) {
            AL10.alSourceUnqueueBuffers(this.source);
            this.freeBuffers.add(Objects.requireNonNull(this.queued.poll())[0]);
        }
        this.playedFramesBase = 0;
    }

    @Override
    public void drain() {
        if (this.source == 0) return;
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (!this.queued.isEmpty() && System.nanoTime() < deadline) {
            this.reclaimProcessed();
            if (this.queued.isEmpty()) break;
            if (this.paused) break;
            this.ensurePlaying();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @Override
    public long playedFrames() {
        if (this.source == 0) return 0;
        this.reclaimProcessed();
        return this.playedFramesBase + AL10.alGetSourcei(this.source, AL11.AL_SAMPLE_OFFSET);
    }

    @Override
    public void setGain(float gain) {
        this.gain = gain;
        if (this.source != 0) AL10.alSourcef(this.source, AL10.AL_GAIN, gain);
    }

    @Override
    public void close() {
        if (this.context != 0) {
            if (this.source != 0) {
                AL10.alSourceStop(this.source);
                this.reclaimProcessed();
                while (!this.queued.isEmpty()) {
                    AL10.alSourceUnqueueBuffers(this.source);
                    this.freeBuffers.add(Objects.requireNonNull(this.queued.poll())[0]);
                }
                AL10.alDeleteSources(this.source);
                this.source = 0;
            }
            while (!this.freeBuffers.isEmpty()) {
                AL10.alDeleteBuffers(this.freeBuffers.poll());
            }
        }
        this.destroyContext();
        this.convertFloatTo16 = false;
    }

    private void destroyContext() {
        if (this.context != 0) {
            EXTThreadLocalContext.alcSetThreadContext(0);
            ALC10.alcDestroyContext(this.context);
            this.context = 0;
        }
        if (this.device != 0) {
            ALC10.alcCloseDevice(this.device);
            this.device = 0;
        }
    }
}
