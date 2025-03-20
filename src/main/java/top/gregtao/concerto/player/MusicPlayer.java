package top.gregtao.concerto.player;

import top.gregtao.concerto.music.SharedMusic;
import top.gregtao.concerto.player.streamplayer.enums.Status;
import top.gregtao.concerto.player.streamplayer.stream.StreamPlayer;
import top.gregtao.concerto.player.streamplayer.stream.StreamPlayerEvent;
import top.gregtao.concerto.player.streamplayer.stream.StreamPlayerException;
import top.gregtao.concerto.player.streamplayer.stream.StreamPlayerListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.api.MusicJsonParsers;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.network.room.MusicRoom;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class MusicPlayer extends StreamPlayer implements StreamPlayerListener {

    public static MusicPlayer INSTANCE;
    public static final Logger PLAYER_LOGGER;

    static {
        PLAYER_LOGGER = Logger.getLogger(MusicPlayer.class.getName());
        File file = new File("Concerto");
        if (!file.exists() && !file.isDirectory() && !file.mkdir()) {
            throw new RuntimeException("Cannot mkdir!");
        }
        FileHandler fileHandler;
        try {
            fileHandler = new FileHandler("Concerto/player.log", false);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        fileHandler.setFormatter(new SimpleFormatter());
        PLAYER_LOGGER.addHandler(fileHandler);
        PLAYER_LOGGER.setLevel(Level.ALL);
        resetInstance();
    }

    public static void resetInstance() {
        try {
            if (MusicPlayerHandler.INSTANCE.currentSource != null) {
                MusicPlayerHandler.INSTANCE.currentSource.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        INSTANCE = new MusicPlayer(PLAYER_LOGGER);
    }

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

    public static final Executor RUNNERS_POOL = Executors.newFixedThreadPool(16);

    public static void run(Runnable runnable) {
        CompletableFuture.runAsync(runnable, RUNNERS_POOL);
    }

    public static void run(Runnable runnable, Runnable callback) {
        CompletableFuture.runAsync(runnable, RUNNERS_POOL).thenRunAsync(callback, RUNNERS_POOL);
    }

    public void addMusic(Music music) {
        this.addMusic(music, () -> {});
    }

    public void addMusic(List<Music> musics) {
        this.addMusic(musics, () -> {});
    }

    public void addMusic(Music music, Runnable callback) {
        run(() -> MusicPlayerHandler.INSTANCE.addMusic(music), callback);
    }

    public void addMusic(List<Music> musics, Runnable callback) {
        run(() -> MusicPlayerHandler.INSTANCE.addMusic(musics), callback);
    }

    public void addMusic(Supplier<List<Music>> musicListAdder, Runnable callback) {
        run(() -> MusicPlayerHandler.INSTANCE.addMusic(musicListAdder.get()), callback);
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
        }, callback);
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
        MusicRoom.clientPause(false);
        super.resume();
    }

    @Override
    public boolean pause() {
        if (!super.isPaused()) {
            MusicPlayerHandler.INSTANCE.writeConfig();
            MusicRoom.clientPause(true);
            return super.pause();
        } else {
            return false;
        }
    }

    @Override
    public boolean resume() {
        if (this.forcePaused) return false;
        if (super.isPaused()) {
            MusicRoom.clientPause(false);
            return super.resume();
        } else {
            return false;
        }
    }

    public boolean musicRoomPause() {
        this.forcePaused = true;
        return super.pause();
    }

    public boolean musicRoomResume() {
        this.forcePaused = false;
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
            if (!this.playNextLock) {
                MusicPlayerHandler.INSTANCE.resetInfo();
            }
            if (MusicPlayerHandler.INSTANCE.isEmpty()) {
                this.started = false;
            } else if (!this.playNextLock && !this.isPlayingTemp) {
                this.playNext(1);
            }
            this.forcePaused = this.isPlayingTemp = false;
        }
    }

    public void playTempMusic(Music music, Runnable callback) {
        run(() -> {
            InputStream source = music.getMusicSourceOrNull();
            if (source == null) return;
            this.forcePaused = false;
            this.playNextLock = this.started = true;
            this.stop();
            MusicPlayerHandler status = MusicPlayerHandler.INSTANCE;
            status.resetInfo();
            status.currentMusic = music;
            status.currentSource = source;
            long startTime = 0;
            if (music instanceof SharedMusic shared) {
                startTime = shared.getStartTime();
            }
            status.initMusicStatus(startTime);
            status.updateDisplayTexts();
            try {
                this.open(source);
                this.play();
                ConcertoClient.LOGGER.info("Start playing temporary music {} - {}", music.getMeta().title(), music.getMeta().author());
                this.isPlayingTemp = true;
            } catch (StreamPlayerException e) {
                this.started = this.isPlayingTemp = this.forcePaused = false;
                ConcertoClient.LOGGER.error(e.toString());
                throw new RuntimeException(e);
            }
            this.playNextLock = false;
        }, callback);
    }

    public void playTempMusic(Music music) {
        this.playTempMusic(music, () -> {});
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
                    InputStream source;
                    ClientPlayerEntity player = MinecraftClient.getInstance().player;
                    while ((source = music.getMusicSourceOrNull()) == null) {
                        ConcertoClient.LOGGER.error("Unable to play music: '{}' of '{}'", music.getMeta().title(), music.getMeta().author());
                        if (player != null) {
                            player.sendMessage(Text.translatable(
                                    "concerto.player.unable", music.getMeta().title(), music.getMeta().author()), false);
                        }
                        MusicPlayerHandler.INSTANCE.setCurrentIndex((MusicPlayerHandler.INSTANCE.getCurrentIndex() + 1)
                                % MusicPlayerHandler.INSTANCE.getMusicList().size());
                        MusicPlayerHandler.INSTANCE.resetInfo();
                        music = MusicPlayerHandler.INSTANCE.playNext(0);
                        if (music == null) {
                            return;
                        }
                    }
                    MusicPlayerHandler.INSTANCE.currentSource = source;
                    this.open(source);
                    this.play();
                    ConcertoClient.LOGGER.info("Start playing music {} - {}", music.getMeta().title(), music.getMeta().author());
                    MusicRoom.clientUpdate(music);
                    callback.accept(MusicPlayerHandler.INSTANCE.getCurrentIndex());
                }
                this.playNextLock = this.isPlayingTemp = this.forcePaused = false;
            } catch (Exception e) {
                this.started = this.isPlayingTemp = this.forcePaused = false;
                ConcertoClient.LOGGER.error(e.toString());
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
        }, callback);
    }

    public void cut(Runnable callback) {
        run(() -> {
            if (!this.isPlayingTemp) {
                MusicPlayerHandler.INSTANCE.removeCurrent();
            }
            this.playNext(0);
        }, callback);
    }

    public void remove(int index, Runnable callback) {
        if (index == MusicPlayerHandler.INSTANCE.getCurrentIndex()) this.cut(callback);
        else {
            run(() -> {
                MusicPlayerHandler.INSTANCE.remove(index);
                if (MusicPlayerHandler.INSTANCE.isEmpty()) this.cut(() -> {});
            }, callback);
        }
    }
}