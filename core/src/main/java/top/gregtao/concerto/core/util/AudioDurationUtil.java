package top.gregtao.concerto.core.util;

import org.jaudiotagger.audio.mp3.MP3AudioHeader;
import org.jaudiotagger.audio.mp3.MP3File;
import org.kc7bfi.jflac.FLACDecoder;
import org.kc7bfi.jflac.io.RandomFileInputStream;
import org.kc7bfi.jflac.metadata.StreamInfo;
import top.gregtao.concerto.core.Concerto;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;

/** Determines the duration of local audio files without decoding them. */
public final class AudioDurationUtil {

    private AudioDurationUtil() {
    }

    /** @return duration in milliseconds, or -1 when it cannot be determined */
    public static long durationInMilliseconds(File file) {
        if (!file.exists() || file.length() == 0) return -1;
        String extension = FileUtil.getSuffix(file.getName()).toLowerCase();
        long milliseconds = -1;
        if ("mp3".equals(extension)) {
            try {
                MP3AudioHeader header = new MP3File(file).getMP3AudioHeader();
                milliseconds = header.getTrackLength() * 1000L;
                if (milliseconds == 0) {
                    int samplesPerFrame;
                    switch (header.getMpegLayer()) {
                        case "Layer 1":
                            samplesPerFrame = 384;
                            break;
                        default:
                            samplesPerFrame = 1152;
                            break;
                    }
                    double frameLengthMillis = ((double) samplesPerFrame / header.getSampleRateAsNumber()) * 1000;
                    milliseconds = (long) (header.getNumberOfFrames() * frameLengthMillis);
                }
            } catch (Exception e) {
                Concerto.getLogger().warn("Problem getting the duration of {}", file.getAbsolutePath());
            }
        } else if ("ogg".equals(extension) || "wav".equals(extension)) {
            try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(file)) {
                AudioFormat format = audioInputStream.getFormat();
                milliseconds = (long) (((double) file.length() / (format.getFrameSize() * (double) format.getFrameRate())) * 1000);
            } catch (IOException | UnsupportedAudioFileException e) {
                Concerto.getLogger().warn("Problem getting the duration of {}", file.getAbsolutePath());
            }
        } else if ("flac".equals(extension)) {
            try {
                FLACDecoder decoder = new FLACDecoder(new RandomFileInputStream(file));
                StreamInfo streamInfo = decoder.readStreamInfo();
                if (streamInfo != null && streamInfo.getSampleRate() > 0) {
                    milliseconds = streamInfo.getTotalSamples() / streamInfo.getSampleRate() * 1000;
                }
            } catch (Exception e) {
                milliseconds = -1;
            }
        }
        return milliseconds <= 0 ? -1 : milliseconds;
    }
}
