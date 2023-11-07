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
import top.gregtao.concerto.music.MusicSource;
import top.gregtao.concerto.util.SilentLogger;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class MusicPlayer extends StreamPlayer implements StreamPlayerListener {

    public static final MusicPlayer INSTANCE = new MusicPlayer(new SilentLogger("player"));

//    public static MusicPlayer INSTANCE = new MusicPlayer();

    public boolean forcePaused = false;

    public boolean started = false;

    public boolean playNextLock = false;

    public boolean isPlayingTemp = false;

    public MusicPlayer() {
        super();
        this.addStreamPlayerListener(this);
    }

    public MusicPlayer(Logger logger) {
        super(logger);
        this.addStreamPlayerListener(this);
    }

    public static Thread run(Runnable runnable) {
        Thread thread = new Thread(runnable, ConcertoClient.MOD_ID);
        thread.start();
        return thread;
    }

    public void addMusic(Music music) {
        this.addMusic(music, () -> {});
    }

    public void addMusic(Music music, Runnable callback) {
        run(() -> {
            MusicPlayerHandler.INSTANCE.addMusic(music);
            callback.run();
        });
    }

    public void addMusic(List<Music> musics, Runnable callback) {
        run(() -> {
            MusicPlayerHandler.INSTANCE.addMusic(musics);
            callback.run();
        });
    }

    public void addMusic(Supplier<List<Music>> musicListAdder, Runnable callback) {
        run(() -> {
            MusicPlayerHandler.INSTANCE.addMusic(musicListAdder.get());
            callback.run();
        });
    }

    public void addMusicHere(Music music, boolean skip) {
        this.addMusicHere(music, skip, () -> {});
    }

    public void addMusicHere(Music music, boolean skip, Runnable callback) {
        run(() -> {
            MusicPlayerHandler.INSTANCE.addMusicHere(music);
            if (skip) {
                this.skipTo(MusicPlayerHandler.INSTANCE.getCurrentIndex() + 1);
            }
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

    public void forcePause() {
        this.forcePaused = true;
        this.pause();
    }

    public void forceResume() {
        this.forcePaused = false;
        super.resume();
    }

    @Override
    public boolean pause() {
        MusicPlayerHandler.INSTANCE.writeConfig();
        return super.pause();
    }

    @Override
    public boolean resume() {
        if (this.forcePaused) return false;
        return super.resume();
    }

    public void syncVolume() {
        try {
            this.setGain(getProperVolume());
        } catch (NullPointerException ignore) {}
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
        MusicPlayerHandler.INSTANCE.updateDisplayTexts(microsecondPosition / 1000);
    }

    @Override
    public void statusUpdated(StreamPlayerEvent event) {
        Status status = event.getPlayerStatus();
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
        run(() -> {
            MusicSource source = music.getMusicSourceOrNull();
            if (source == null) return;
            this.forcePaused = false;
            this.playNextLock = this.started = true;
            this.stop();
            MusicPlayerHandler status = MusicPlayerHandler.INSTANCE;
            status.resetInfo();
            status.currentMusic = music;
            status.initMusicStatus();
            status.updateDisplayTexts();
            try {
                source.open(this);
                this.play();
                this.isPlayingTemp = true;
            } catch (StreamPlayerException | IOException e) {
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
        this.playNext(forward, index -> callback.run());
    }

    public void playNext(int forward, Consumer<Integer> callback) {
        run(() -> {
            try {
                if (!this.started || MusicPlayerHandler.INSTANCE.isEmpty()) {
                    this.started = false;
                    return;
                }
                this.playNextLock = true;
                this.stop();
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
                    source.open(this);
                    this.play();
                    callback.accept(MusicPlayerHandler.INSTANCE.getCurrentIndex());
                }
                this.playNextLock = this.isPlayingTemp = this.forcePaused = false;
            } catch (StreamPlayerException | IOException e) {
                this.started = this.isPlayingTemp = this.forcePaused = false;
                throw new RuntimeException(e);
            }
        });
    }

    public void skipTo(int index) {
        MusicPlayerHandler.INSTANCE.setCurrentIndex(
                Math.min(MusicPlayerHandler.INSTANCE.getMusicList().size(), index));
        MusicPlayerHandler.INSTANCE.resetInfo();
        this.start();
    }

    public void start() {
        this.started = true;
        this.forcePaused = false;
        this.playNextLock = false;
        this.playNext(0);
    }

    public void clear() {
        run(() -> {
            this.started = false;
            this.stop();
            MusicPlayerHandler.INSTANCE.clear();
        });
    }

    public void reloadConfig(Runnable callback) {
        run(() -> {
            this.started = false;
            this.stop();
            MusicPlayerHandler.INSTANCE = MusicJsonParsers.fromRaw(ConcertoClient.MUSIC_CONFIG.read());
            callback.run();
        });
    }

    public void cut(Runnable callback) {
        run(() -> {
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
            run(() -> {
                MusicPlayerHandler.INSTANCE.remove(index);
                if (MusicPlayerHandler.INSTANCE.isEmpty()) this.cut(callback);
                else callback.run();
            });
        }
    }
}