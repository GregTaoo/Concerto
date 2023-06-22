package top.gregtao.concerto.experimental.player;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.api.MusicJsonParsers;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.music.MusicSource;
import top.gregtao.concerto.player.MusicPlayerHandler;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

public class MusicPlayerE extends AudioPlayer implements AudioPlayer.Listener {

    public static MusicPlayerE INSTANCE = new MusicPlayerE(ConcertoClient.LOGGER);

//    public static MusicPlayer INSTANCE = new MusicPlayer();

    public boolean forcePaused = false;

    public boolean started = false;

    public boolean playNextLock = false;

    public boolean isPlayingTemp = false;

    public MusicPlayerE(Logger logger) {
        super(logger);
        this.addListener(this);
    }

    public static Thread executeThread(Runnable runnable) {
        Thread thread = new Thread(runnable, ConcertoClient.MOD_ID);
        thread.start();
        return thread;
    }

    public void addMusic(Music music) {
        this.addMusic(music, () -> {});
    }

    public void addMusic(Music music, Runnable callback) {
        executeThread(() -> {
            MusicPlayerHandler.INSTANCE.addMusic(music);
            callback.run();
        });
    }

    public void addMusic(List<Music> musics) {
        this.addMusic(musics, () -> {});
    }

    public void addMusic(List<Music> musics, Runnable callback) {
        executeThread(() -> {
            MusicPlayerHandler.INSTANCE.addMusic(musics);
            callback.run();
        });
    }

    public void addMusic(Supplier<List<Music>> musicListAdder, Runnable callback) {
        executeThread(() -> {
            MusicPlayerHandler.INSTANCE.addMusic(musicListAdder.get());
            callback.run();
        });
    }

    public void addMusicHere(Music music, boolean skip) {
        this.addMusicHere(music, skip, () -> {});
    }

    public void addMusicHere(Music music, boolean skip, Runnable callback) {
        executeThread(() -> {
            MusicPlayerHandler.INSTANCE.addMusicHere(music);
            if (skip) this.skipTo(MusicPlayerHandler.INSTANCE.getCurrentIndex() + 1);
            callback.run();
        });
    }

    @Override
    public void loadAudioStream(AudioInputStream inputStream) throws LineUnavailableException, IOException {
        MinecraftClient client = MinecraftClient.getInstance();
        client.getMusicTracker().stop();
        super.loadAudioStream(inputStream);
        this.syncVolume();
    }

    public void forcePause() {
        this.forcePaused = true;
        this.pause();
    }

    public void forceResume() {
        this.forcePaused = false;
        super.resume();
    }

    @Override
    public void pause() {
        MusicPlayerHandler.INSTANCE.writeConfig();
        super.pause();
    }

    @Override
    public void resume() {
        if (this.forcePaused) return;
        super.resume();
    }

    public void syncVolume() {
        this.setGain(getProperVolume());
    }

    public static float getProperVolume() {
        MinecraftClient client = MinecraftClient.getInstance();
        GameOptions options = client.options;
        return (float) (options.getSoundVolume(SoundCategory.MASTER) * options.getSoundVolume(SoundCategory.MUSIC) * 0.5);
    }

    @Override
    public void onProgress(long progress) {
        System.out.println(progress);
        MusicPlayerHandler.INSTANCE.updateDisplayTexts(progress);
    }

    @Override
    public void onStatusUpdated(Status status) {
        if (status == Status.EOM) {
            this.forcePaused = this.isPlayingTemp = false;
            if (!this.playNextLock) {
                MusicPlayerHandler.INSTANCE.resetInfo();
            }
            if (MusicPlayerHandler.INSTANCE.isEmpty()) {
                this.started = false;
            } else if (!this.playNextLock) {
                this.playNext(1);
            }
        }
    }

    public void playTempMusic(Music music) {
        executeThread(() -> {
            MusicSource source = music.getMusicSourceOrNull();
            if (source == null) return;
            this.forcePaused = false;
            this.playNextLock = this.started = true;
            this.reset();
            MusicPlayerHandler status = MusicPlayerHandler.INSTANCE;
            status.resetInfo();
            status.currentMusic = music;
            status.setupMusicStatus();
            status.updateDisplayTexts();
            try {
                this.loadAudioStream(source.getAudioStream());
                this.isPlayingTemp = true;
            } catch (UnsupportedAudioFileException | LineUnavailableException | IOException e) {
                this.started = this.isPlayingTemp = this.forcePaused = false;
                throw new RuntimeException(e);
            }
            this.playNextLock = false;
        });
    }

    public void playNext(int forward) {
        this.playNext(forward, () -> {});
    }

    public void playNext(int forward, Runnable callback) {
        executeThread(() -> {
            try {
                if (!this.started || MusicPlayerHandler.INSTANCE.isEmpty()) {
                    this.started = false;
                    return;
                }
                this.playNextLock = true;
                Music music = MusicPlayerHandler.INSTANCE.playNext(forward);
                if (music != null) {
                    MusicSource source;
                    ClientPlayerEntity player = MinecraftClient.getInstance().player;
                    while ((source = music.getMusicSourceOrNull()) == null) {
                        if (player != null) {
                            player.sendMessage(Text.translatable(
                                    "concerto.player.unable", music.getMeta().title(), music.getMeta().author()));
                        }
                        MusicPlayerHandler.INSTANCE.setCurrentIndex((MusicPlayerHandler.INSTANCE.getCurrentIndex() + 1)
                                % MusicPlayerHandler.INSTANCE.getMusicList().size());
                        MusicPlayerHandler.INSTANCE.resetInfo();
                        music = MusicPlayerHandler.INSTANCE.playNext(0);
                        if (music == null) {
                            return;
                        }
                    }
                    this.loadAudioStream(source.getAudioStream());
                    callback.run();
                }
                this.playNextLock = this.isPlayingTemp = this.forcePaused = false;
            } catch (UnsupportedAudioFileException | LineUnavailableException | IOException e) {
                this.started = this.isPlayingTemp = this.forcePaused = false;
                throw new RuntimeException(e);
            }
        });
    }

    public void skipTo(int index) {
        MusicPlayerHandler.INSTANCE.setCurrentIndex(
                Math.min(MusicPlayerHandler.INSTANCE.getMusicList().size(), index));
        MusicPlayerHandler.INSTANCE.resetInfo();
        this.playNext(0);
    }

    public void start() {
        this.started = true;
        this.forcePaused = false;
        this.playNextLock = false;
        this.playNext(0);
    }

    public void clear() {
        executeThread(() -> {
            this.started = false;
            this.reset();
            MusicPlayerHandler.INSTANCE.clear();
        });
    }

    public void reloadConfig(Runnable callback) {
        executeThread(() -> {
            this.started = false;
            this.reset();
            MusicPlayerHandler.INSTANCE = MusicJsonParsers.fromRaw(ConcertoClient.MUSIC_CONFIG.read());
            callback.run();
        });
    }

    public void cut(Runnable callback) {
        executeThread(() -> {
            if (!this.isPlayingTemp) {
                MusicPlayerHandler.INSTANCE.removeCurrent();
            }
            this.playNext(0);
            callback.run();
        });
    }

    public void remove(int index, Runnable callback) {
        if (index == MusicPlayerHandler.INSTANCE.getCurrentIndex()) this.cut(callback);
        else {
            executeThread(() -> {
                MusicPlayerHandler.INSTANCE.remove(index);
                if (MusicPlayerHandler.INSTANCE.isEmpty()) this.cut(callback);
                else callback.run();
            });
        }
    }
}