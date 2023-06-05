package top.gregtao.concerto.player.test;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.api.MusicJsonParsers;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.player.MusicPlayerStatus;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Supplier;

public class MusicPlayer1 extends AudioPlayer implements AudioPlayer.Listener {

    public static MusicPlayer1 INSTANCE = new MusicPlayer1(ConcertoClient.LOGGER);

//    public static MusicPlayer INSTANCE = new MusicPlayer();

    public boolean forcePaused = false;

    public boolean started = false;

    public boolean playNextLock = false;

    public boolean isPlayingTemp = false;

    public MusicPlayer1(Logger logger) {
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
            MusicPlayerStatus.INSTANCE.addMusic(music);
            callback.run();
        });
    }

    public void addMusic(List<Music> musics) {
        this.addMusic(musics, () -> {});
    }

    public void addMusic(List<Music> musics, Runnable callback) {
        executeThread(() -> {
            MusicPlayerStatus.INSTANCE.addMusic(musics);
            callback.run();
        });
    }

    public void addMusic(Supplier<List<Music>> musicListAdder, Runnable callback) {
        executeThread(() -> {
            MusicPlayerStatus.INSTANCE.addMusic(musicListAdder.get());
            callback.run();
        });
    }

    public void addMusicHere(Music music, boolean skip) {
        this.addMusicHere(music, skip, () -> {});
    }

    public void addMusicHere(Music music, boolean skip, Runnable callback) {
        executeThread(() -> {
            MusicPlayerStatus.INSTANCE.addMusicHere(music);
            if (skip) this.skipTo(MusicPlayerStatus.INSTANCE.getCurrentIndex() + 1);
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
        MusicPlayerStatus.INSTANCE.writeConfig();
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
        MusicPlayerStatus.INSTANCE.updateDisplayTexts(progress);
    }

    @Override
    public void onStatusUpdated(Status status) {
        if (status == Status.EOM) {
            this.forcePaused = this.isPlayingTemp = false;
            if (!this.playNextLock) {
                MusicPlayerStatus.INSTANCE.resetInfo();
            }
            if (MusicPlayerStatus.INSTANCE.isEmpty()) {
                this.started = false;
            } else if (!this.playNextLock) {
                this.playNext(1);
            }
        }
    }

    public void playTempMusic(Music music) {
        executeThread(() -> {
            InputStream inputStream = music.getMusicStream();
            if (inputStream == null) return;
            this.forcePaused = false;
            this.playNextLock = this.started = true;
            this.reset();
            MusicPlayerStatus status = MusicPlayerStatus.INSTANCE;
            status.resetInfo();
            status.currentMusic = music;
            status.setupMusicStatus();
            status.updateDisplayTexts();
            try {
                this.loadAudioStream(inputStream);
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
                if (!this.started || MusicPlayerStatus.INSTANCE.isEmpty()) {
                    this.started = false;
                    return;
                }
                this.playNextLock = true;
                Music music = MusicPlayerStatus.INSTANCE.playNext(forward);
                if (music != null) {
                    InputStream inputStream;
                    ClientPlayerEntity player = MinecraftClient.getInstance().player;
                    while ((inputStream = music.getMusicStream()) == null) {
                        if (player != null) {
                            player.sendMessage(Text.translatable(
                                    "concerto.player.unable", music.getMeta().title(), music.getMeta().author()));
                        }
                        MusicPlayerStatus.INSTANCE.setCurrentIndex((MusicPlayerStatus.INSTANCE.getCurrentIndex() + 1)
                                % MusicPlayerStatus.INSTANCE.getMusicList().size());
                        MusicPlayerStatus.INSTANCE.resetInfo();
                        music = MusicPlayerStatus.INSTANCE.playNext(0);
                        if (music == null) {
                            return;
                        }
                    }
                    this.loadAudioStream(inputStream);
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
        MusicPlayerStatus.INSTANCE.setCurrentIndex(
                Math.min(MusicPlayerStatus.INSTANCE.getMusicList().size(), index));
        MusicPlayerStatus.INSTANCE.resetInfo();
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
            MusicPlayerStatus.INSTANCE.clear();
        });
    }

    public void reloadConfig(Runnable callback) {
        executeThread(() -> {
            this.started = false;
            this.reset();
            MusicPlayerStatus.INSTANCE = MusicJsonParsers.fromRaw(ConcertoClient.MUSIC_CONFIG.read());
            callback.run();
        });
    }

    public void cut(Runnable callback) {
        executeThread(() -> {
            if (!this.isPlayingTemp) {
                MusicPlayerStatus.INSTANCE.removeCurrent();
            }
            this.playNext(0);
            callback.run();
        });
    }

    public void remove(int index, Runnable callback) {
        if (index == MusicPlayerStatus.INSTANCE.getCurrentIndex()) this.cut(callback);
        else {
            executeThread(() -> {
                MusicPlayerStatus.INSTANCE.remove(index);
                if (MusicPlayerStatus.INSTANCE.isEmpty()) this.cut(callback);
                else callback.run();
            });
        }
    }
}