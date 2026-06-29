package top.gregtao.concerto.core.player;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.ArtworkFactory;
import top.gregtao.concerto.core.Concerto;
import top.gregtao.concerto.core.api.CacheableMusic;
import top.gregtao.concerto.core.api.LazyLoadable;
import top.gregtao.concerto.core.api.MusicJsonParsers;
import top.gregtao.concerto.core.enums.OrderType;
import top.gregtao.concerto.core.event.ConcertoEvents;
import top.gregtao.concerto.core.music.Music;
import top.gregtao.concerto.core.music.meta.music.MusicMetaData;
import top.gregtao.concerto.core.network.SyncRecord;
import top.gregtao.concerto.core.room.MusicRoom;
import top.gregtao.concerto.core.util.ConcertoRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class MusicPlayerHandler {

    public static MusicPlayerHandler INSTANCE = new MusicPlayerHandler();

    private final SyncRecord<MusicPlayerState> localRecord;
    private boolean forcePaused = false;

    public MusicPlayerHandler() {
        this.localRecord = SyncRecord.createLocalRecord(new MusicPlayerState());
        registerSyncRecordListeners(this.localRecord);
    }

    public MusicPlayerHandler(ConcertoPlayerList musics, UUID currentIndex, OrderType orderType) {
        loadInThreadPool(musics.snapshotMusics());

        MusicPlayerState state = new MusicPlayerState(musics, currentIndex, orderType, true);
        this.localRecord = SyncRecord.createLocalRecord(state);
        registerSyncRecordListeners(this.localRecord);
    }

    public SyncRecord<MusicPlayerState> getState() {
        SyncRecord<MusicPlayerState> remote = Concerto.getCoreBridge().getCurrentPlayerState();
        return remote == null ? this.localRecord : remote;
    }

    public boolean isForcePaused() {
        return this.forcePaused;
    }

    private void forcePause() {
        this.forcePaused = true;
        this.setPaused(true);
        if (MusicPlayer.INSTANCE.isPlaying()) {
            MusicPlayer.INSTANCE.internalPause();
        }
    }

    private void forceResume() {
        this.forcePaused = false;
        this.setPaused(false);
        if (MusicPlayer.INSTANCE.started) {
            MusicPlayer.INSTANCE.internalResume();
        }
    }

    public void tryForcePause(boolean paused) {
        boolean isLocal = MusicRoom.clientGetState() == MusicRoom.ClientState.LOCAL;
        if (this.isForcePaused() && !paused) {
            this.forceResume();
        } else if (paused) {
            this.forcePause();
        }
        if (!isLocal) {
            this.setPaused(paused);
        }
    }

    public static void registerSyncRecordListeners(SyncRecord<MusicPlayerState> record) {
        record.addListener(MusicPlayerState.MUSIC_LIST, (o, state, oldVal, newVal) -> {
            UUID current = state.currentIndex;
            ConcertoPlayerList list = state.musicList;

            UUID next = current;
            if (list.isEmpty()) {
                next = null;
            } else if (!list.contains(current)) {
                next = state.orderType == OrderType.REVERSED ? list.lastUuid() : list.firstUuid();
            }

            if (!Objects.equals(next, current)) {
                UUID finalNext = next;
                o.set((s) -> {
                    s.currentIndex = finalNext;
                    return s;
                }, List.of(MusicPlayerState.CURRENT_INDEX));
            }

            ConcertoEvents.ON_MUSIC_LIST_UPDATE.emit();
        });

        record.addListener(MusicPlayerState.CURRENT_INDEX, (o, state, oldVal, newVal) -> {
            UUID current = state.currentIndex;
            if (current == null) {
                if (MusicPlayer.INSTANCE.started) {
                    MusicPlayer.INSTANCE.stop();
                }
                return;
            }

            if (!state.musicList.contains(current)) return;

            Music targetMusic = state.musicList.get(current);
            if (targetMusic == null) return;

            MusicPlayer.INSTANCE.internalPlayMusic(targetMusic);
        });

        record.addListener(MusicPlayerState.PAUSED, (o, state, oldVal, newVal) -> {
            if (INSTANCE != null && INSTANCE.forcePaused) {
                if (!state.paused) return;
            }

            if (state.paused && MusicPlayer.INSTANCE.isPlaying()) {
                MusicPlayer.INSTANCE.internalPause();
            } else if (!state.paused && MusicPlayer.INSTANCE.isPaused()) {
                MusicPlayer.INSTANCE.internalResume();
            }
        });

        record.addListener(MusicPlayerState.ORDER_TYPE, (o, state, oldVal, newVal) ->
                ConcertoEvents.ON_PLAYER_ORDER_UPDATE.emit(state.orderType));
    }

    public void clear() {
        this.getState().set(
                (state) -> new MusicPlayerState(),
                List.of(MusicPlayerState.MUSIC_LIST, MusicPlayerState.PAUSED, MusicPlayerState.ORDER_TYPE, MusicPlayerState.CURRENT_INDEX)
        );
        this.writeConfig();
    }

    public boolean isPaused() {
        return this.isForcePaused() || this.getState().get().paused;
    }

    public void setPaused(boolean paused) {
        if (this.getState().get().paused != paused) {
            this.getState().set((state) -> {
                state.paused = paused;
                return state;
            }, List.of(MusicPlayerState.PAUSED));
        }
    }

    public boolean addMusic(Music music, boolean skip) {
        this.getState().set((state) -> {
            if (!music.isLoaded()) music.load();
            UUID added = state.musicList.addLast(music);
            if (skip)
                state.currentIndex = added;
            return state;
        }, skip ? List.of(MusicPlayerState.MUSIC_LIST, MusicPlayerState.CURRENT_INDEX) : List.of(MusicPlayerState.MUSIC_LIST));
        this.writeConfig();

        return true;
    }

    public void addMusicAsync(Music music, boolean skip, Runnable callback) {
        ConcertoRunner.run(() -> this.addMusic(music, skip), callback);
    }

    public void addMusicAsync(Music music, boolean skip) {
        ConcertoRunner.run(() -> this.addMusic(music, skip));
    }

    public boolean addMusic(List<Music> musics, boolean skip) {
        this.getState().set((state) -> {
            loadInThreadPool(musics);
            List<UUID> ids = state.musicList.addAllLast(musics);
            if (skip && !ids.isEmpty())
                state.currentIndex = ids.get(0);
            return state;
        }, skip ? List.of(MusicPlayerState.MUSIC_LIST, MusicPlayerState.CURRENT_INDEX) : List.of(MusicPlayerState.MUSIC_LIST));
        this.writeConfig();

        return true;
    }

    public void addMusicAsync(List<Music> musics, boolean skip, Runnable callback) {
        ConcertoRunner.run(() -> this.addMusic(musics, skip), callback);
    }

    public void addMusicAsync(Supplier<List<Music>> musics, boolean skip, Runnable callback) {
        ConcertoRunner.run(() -> this.addMusic(musics.get(), skip), callback);
    }

    public void addMusicAsync(List<Music> musics, boolean skip) {
        ConcertoRunner.run(() -> this.addMusic(musics, skip));
    }

    public void addMusicAsync(Supplier<List<Music>> musics, boolean skip) {
        ConcertoRunner.run(() -> this.addMusic(musics.get(), skip));
    }

    public void addMusicHere(Music music, boolean skip) {
        if (!music.isLoaded()) music.load();
        this.getState().set((state) -> {
            UUID inserted;
            if (state.currentIndex == null || !state.musicList.contains(state.currentIndex)) {
                inserted = state.musicList.addLast(music);
            } else {
                List<UUID> ids = state.musicList.addAfter(state.currentIndex, List.of(music));
                inserted = ids.isEmpty() ? state.musicList.addLast(music) : ids.get(0);
            }
            if (skip || state.currentIndex == null) {
                state.currentIndex = inserted;
            }
            return state;
        }, skip ? List.of(MusicPlayerState.MUSIC_LIST, MusicPlayerState.CURRENT_INDEX) : List.of(MusicPlayerState.MUSIC_LIST));
        this.writeConfig();
    }

    public void addMusicHereAsync(Music music, boolean skip, Runnable callback) {
        ConcertoRunner.run(() -> this.addMusicHere(music, skip), callback);
    }

    public void addMusicHereAsync(Music music, boolean skip) {
        ConcertoRunner.run(() -> this.addMusicHere(music, skip));
    }

    public void start() {
        this.playNextAsync(0);
    }

    public void stop() {
        ConcertoRunner.run(() -> this.setCurrentIndex(null));
    }

    public void playNext(int forward) {
        MusicPlayerState currentState = this.getState().get();
        if (currentState.musicList.isEmpty()) {
            this.stop();
            return;
        }

        this.getState().set((state) -> {
            state.currentIndex = this.getNextUuid(state, forward);
            state.paused = false;
            return state;
        }, List.of(MusicPlayerState.CURRENT_INDEX, MusicPlayerState.PAUSED));

        this.writeConfig();
    }

    public void playNextAsync(int forward) {
        ConcertoRunner.run(() -> this.playNext(forward));
    }

    public void remove(UUID uuid) {
        MusicPlayerState state = this.getState().get();
        if (uuid == null || !state.musicList.contains(uuid)) return;

        this.getState().set((s) -> {
                    UUID replacement = s.currentIndex;
                    if (Objects.equals(s.currentIndex, uuid)) {
                        replacement = pickReplacementAfterRemove(s, uuid);
                    }
                    s.musicList.remove(uuid);
                    s.currentIndex = replacement;
                    return s;
                }, (s) ->
                        Objects.equals(s.currentIndex, uuid) ?
                                List.of(MusicPlayerState.CURRENT_INDEX, MusicPlayerState.MUSIC_LIST) :
                                List.of(MusicPlayerState.MUSIC_LIST)
        );
    }

    public void removeAsync(UUID uuid, Runnable callback) {
        ConcertoRunner.run(() -> this.remove(uuid), callback);
    }

    private UUID getNextUuid(MusicPlayerState state, int forward) {
        if (state.musicList.isEmpty()) return null;

        UUID cur = state.currentIndex;
        if (cur == null || !state.musicList.contains(cur)) {
            cur = switch (state.orderType) {
                case LOOP, NORMAL -> state.musicList.firstUuid();
                case RANDOM -> state.musicList.randomUuid();
                case REVERSED -> state.musicList.lastUuid();
            };
            if (cur == null) return null;
        }

        if (forward == 0) return cur;

        return switch (state.orderType) {
            case LOOP -> cur;
            case RANDOM -> state.musicList.randomUuid();
            case NORMAL -> advanceCircular(state.musicList, cur, true, Math.abs(forward));
            case REVERSED -> advanceCircular(state.musicList, cur, false, Math.abs(forward));
        };
    }

    private UUID advanceCircular(ConcertoPlayerList list, UUID start, boolean nextDir, int steps) {
        UUID cur = start;
        for (int i = 0; i < steps; i++) {
            UUID n = nextDir ? list.nextUuid(cur) : list.previousUuid(cur);
            if (n == null) {
                n = nextDir ? list.firstUuid() : list.lastUuid();
            }
            cur = n;
        }
        return cur;
    }

    private UUID pickReplacementAfterRemove(MusicPlayerState state, UUID removing) {
        UUID next = state.musicList.nextUuid(removing);
        UUID prev = state.musicList.previousUuid(removing);
        if (state.orderType == OrderType.REVERSED) {
            return prev != null ? prev : next;
        }
        return next != null ? next : prev;
    }

    public String[] getDisplayTexts() {
        return MusicPlayer.INSTANCE.getDisplayTexts();
    }

    public Music getCurrentMusic() {
        return MusicPlayer.INSTANCE.currentMusic;
    }

    public UUID getCurrentIndex() {
        return this.getState().get().currentIndex;
    }

    public ConcertoPlayerList getMusicList() {
        return this.getState().get().musicList;
    }

    public OrderType getOrderType() {
        return this.getState().get().orderType;
    }

    public boolean isEmpty() {
        return this.getState().get().musicList.isEmpty();
    }

    public void setOrderType(OrderType type) {
        this.getState().set((s) -> {
            s.orderType = type;
            return s;
        }, List.of(MusicPlayerState.ORDER_TYPE));
        this.writeConfig();
    }

    public void setCurrentIndex(UUID uuid) {
        this.getState().set((state) -> {
            state.currentIndex = uuid == null ? null : (state.musicList.contains(uuid) ? uuid : state.musicList.firstUuid());
            state.paused = false;
            return state;
        }, List.of(MusicPlayerState.CURRENT_INDEX, MusicPlayerState.PAUSED));
    }

    public static void reloadConfig(Runnable callback) {
        ConcertoRunner.run(() ->
                MusicPlayerHandler.INSTANCE = MusicJsonParsers.fromRaw(Concerto.MUSIC_CONFIG.read()), callback);
    }

    public void writeConfig() {
        if (this.getState() == this.localRecord) {
            Concerto.MUSIC_CONFIG.write(MusicJsonParsers.toRaw(this));
        }
    }

    private static final Pattern ILLEGAL_CHARS = Pattern.compile("[\\\\/:*?\"<>|]");

    public static String filenameFilter(String str) {
        return ILLEGAL_CHARS.matcher(str).replaceAll(" ");
    }

    public static <T extends LazyLoadable> void loadInThreadPool(List<T> objects) {
        if (objects.isEmpty()) return;
        ExecutorService service = Executors.newFixedThreadPool(Math.min(objects.size(), 32));
        objects.forEach(obj -> {
            if (!obj.isLoaded()) service.submit(() -> obj.load());
        });
        service.shutdown();
        try {
            service.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
    }

    public static void downloadMusics(List<Music> musics) {
        ConcertoRunner.run(() -> {
            File folder = new File("Concerto/Downloads");
            if (!folder.exists() || !folder.isDirectory()) {
                if (folder.mkdirs()) {
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    return;
                }
            }
            ExecutorService service = Executors.newFixedThreadPool(16);
            musics.forEach(music -> {
                if (music instanceof CacheableMusic cacheableMusic) {
                    service.submit(() -> {
                        MusicMetaData metaData = music.getMeta();
                        String filename = filenameFilter(metaData.title() + " - " + metaData.author() + " - " + metaData.getSource());
                        File file = folder.toPath().resolve(filename + "." + cacheableMusic.getSuffix()).toFile();
                        File lrcFile = folder.toPath().resolve(filename + ".lrc").toFile();
                        try {
                            if (!file.exists()) {
                                if (file.createNewFile()) {
                                    try (FileOutputStream stream = new FileOutputStream(file)) {
                                        stream.write(music.getMusicSource().readAllBytes());
                                    }
                                }
                                Concerto.getLogger().info("Downloaded: {}", filename);
                            }
                            String lyrics = music.getLyrics().getFirst().toString();
                            try {
                                AudioFile audioFile = AudioFileIO.read(file);
                                Tag tag = audioFile.getTagOrCreateAndSetDefault();
                                tag.setField(FieldKey.TITLE, metaData.title());
                                tag.setField(FieldKey.ARTISTS, metaData.author());
                                tag.setField(FieldKey.LYRICS, lyrics);
                                if (!metaData.headPictureUrl().isEmpty()) {
                                    tag.setField(ArtworkFactory.createLinkedArtworkFromURL(metaData.headPictureUrl()));
                                }
                                audioFile.commit();
                            } catch (Exception e) {
                                Concerto.getLogger().warn("Cannot write tags into file: {}", file);
                            }
                            if (!lrcFile.exists()) {
                                if (lrcFile.createNewFile()) {
                                    try (FileOutputStream stream = new FileOutputStream(lrcFile)) {
                                        stream.write(lyrics.getBytes(StandardCharsets.UTF_8));
                                    }
                                }
                                Concerto.getLogger().info("Downloaded LRC: {}", filename);
                            }
                        } catch (IOException e) {
                            Concerto.getLogger().error("{} - {}", e, file.getAbsolutePath());
                        }
                    });
                } else {
                    Concerto.getLogger().info("Detected non-cacheable music");
                }
            });
            service.shutdown();
            try {
                if (!service.awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS)) {
                    throw new TimeoutException();
                }
            } catch (InterruptedException | TimeoutException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
