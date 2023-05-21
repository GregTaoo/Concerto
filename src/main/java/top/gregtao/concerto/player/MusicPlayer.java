package top.gregtao.concerto.player;

import com.goxr3plus.streamplayer.enums.Status;
import com.goxr3plus.streamplayer.stream.StreamPlayer;
import com.goxr3plus.streamplayer.stream.StreamPlayerEvent;
import com.goxr3plus.streamplayer.stream.StreamPlayerException;
import com.goxr3plus.streamplayer.stream.StreamPlayerListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.api.MusicJsonParsers;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.util.SilentLogger;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class MusicPlayer extends StreamPlayer implements StreamPlayerListener {

    public static MusicPlayer INSTANCE = new MusicPlayer(new SilentLogger("player"));

    public boolean forcePaused = false;

    public boolean started = false;

    public boolean playNextLock = false;

    public boolean isPlayingTemp = false;

    public MusicPlayer(Logger logger) {
        super(logger);
        this.addStreamPlayerListener(this);
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

    public void addMusic(MusicListAdder musicListAdder, Runnable callback) {
        executeThread(() -> {
            MusicPlayerStatus.INSTANCE.addMusic(musicListAdder.getMusicList());
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
    public void play() throws StreamPlayerException {
        MinecraftClient client = MinecraftClient.getInstance();
        client.getMusicTracker().stop();
        super.play();
        this.syncVolume();
    }

    public boolean forcePause() {
        this.forcePaused = true;
        return super.pause();
    }

    public boolean forceResume() {
        this.forcePaused = false;
        return super.resume();
    }

    @Override
    public boolean resume() {
        if (this.forcePaused) return false;
        return super.resume();
    }

    public void syncVolume() {
        this.setGain(getProperVolume());
    }

    public static double getProperVolume() {
        MinecraftClient client = MinecraftClient.getInstance();
        GameOptions options = client.options;
        return options.getSoundVolume(SoundCategory.MASTER) * options.getSoundVolume(SoundCategory.MUSIC) * 0.5;
    }

    @Override
    public void opened(Object dataSource, Map<String, Object> properties) {}

    @Override
    public void progress(int nEncodedBytes, long microsecondPosition, byte[] pcmData, Map<String, Object> properties) {
        MusicPlayerStatus.INSTANCE.updateDisplayTexts(microsecondPosition / 1000);
    }

    @Override
    public void statusUpdated(StreamPlayerEvent event) {
        Status status = event.getPlayerStatus();
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
            this.stop();
            MusicPlayerStatus status = MusicPlayerStatus.INSTANCE;
            status.resetInfo();
            status.currentMusic = music;
            status.setupMusicStatus();
            status.updateDisplayTexts();
            try {
                this.open(inputStream);
                this.play();
                this.isPlayingTemp = true;
            } catch (StreamPlayerException e) {
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
                this.stop();
                Music music = MusicPlayerStatus.INSTANCE.playNext(forward);
                if (music != null) {
                    InputStream inputStream = music.getMusicStream();
                    ClientPlayerEntity player = MinecraftClient.getInstance().player;
                    while (inputStream == null) {
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
                        inputStream = music.getMusicStream();
                    }
                    this.open(inputStream);
                    this.play();
                    callback.run();
                }
                this.playNextLock = false;
            } catch (StreamPlayerException e) {
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
        this.playNextLock = false;
        this.playNext(0);
    }

    public void clear() {
        executeThread(() -> {
            this.started = false;
            this.stop();
            MusicPlayerStatus.INSTANCE.clear();
        });
    }

    public void reloadConfig(Runnable callback) {
        executeThread(() -> {
            this.started = false;
            this.stop();
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
                if (MusicPlayerStatus.INSTANCE.isEmpty()) this.stop();
                callback.run();
            });
        }
    }

    public interface MusicListAdder {
        List<Music> getMusicList();
    }
}