package top.gregtao.concerto.network.room;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import top.gregtao.concerto.ConcertoServer;
import top.gregtao.concerto.core.api.DynamicPath;
import top.gregtao.concerto.core.api.MusicJsonParsers;
import top.gregtao.concerto.core.config.ServerConfig;
import top.gregtao.concerto.core.music.Music;
import top.gregtao.concerto.core.music.SharedMusic;
import top.gregtao.concerto.core.player.ConcertoPlayerList;
import top.gregtao.concerto.core.room.MusicRoom;
import top.gregtao.concerto.core.room.MusicRoom.*;
import top.gregtao.concerto.network.ServerMusicNetworkHandler;
import top.gregtao.concerto.core.util.ConcertoRunner;
import top.gregtao.concerto.core.player.MusicPlayerState;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class ServerMusicAgent {

    public static final UUID ROOM_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    public static ServerMusicAgent INSTANCE;

    public final MusicRoom room;

    private final Map<UUID, Long> addMusicTimeRecord = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Lock voteLock = new ReentrantLock();
    private volatile boolean isVoting = false;
    private final Set<ServerPlayerEntity> yesVoters = ConcurrentHashMap.newKeySet();
    private final Set<ServerPlayerEntity> noVoters = ConcurrentHashMap.newKeySet();
    private ScheduledFuture<?> voteFuture;

    private ScheduledFuture<?> playNextFuture;
    private Music currentMusic = null;
    private UUID trackedIndex = null;
    private boolean trackedPauseState = true;
    private final AtomicBoolean isStopped = new AtomicBoolean(false);
    private final AtomicBoolean currentlyFreeTime = new AtomicBoolean(false);
    public List<Music> freeTimePlaylist = new CopyOnWriteArrayList<>();
    private final MinecraftServer server;

    public ServerMusicAgent(MinecraftServer server) {
        this.room = new MusicRoom("#Server", ROOM_UUID, MusicRoomManager.createServerBridge(server));
        this.server = server;
        MusicRoom.ROOMS.put(this.room.uuid, this.room);

        // Listeners for manual triggers or unexpected external state changes
        this.room.serverState.addListener(MusicPlayerState.CURRENT_INDEX,
                (o, state, oldVal, newVal) -> this.syncIndexState());
        this.room.serverState.addListener(MusicPlayerState.PAUSED,
                (o, state, oldVal, newVal) -> this.syncPauseState());
    }

    private void updateState(Consumer<MusicRoomState> consumer, List<Field> changeList) {
        this.room.serverState.set(state -> {
            consumer.accept((MusicRoomState) state);
            return state;
        }, changeList);

        if (changeList.contains(MusicRoomState.CURRENT_INDEX)) {
            this.syncIndexState();
        }
        if (changeList.contains(MusicRoomState.PAUSED)) {
            this.syncPauseState();
        }
    }

    private synchronized void syncIndexState() {
        UUID currentIndex = this.room.serverState.get().currentIndex;
        if (Objects.equals(currentIndex, this.trackedIndex)) return;
        this.trackedIndex = currentIndex;
        this.resolveAndPlayCurrentMusic();
    }

    private synchronized void syncPauseState() {
        boolean paused = this.room.serverState.get().paused;
        if (paused == this.trackedPauseState) return;
        this.trackedPauseState = paused;

        if (paused) {
            if (this.playNextFuture != null) this.playNextFuture.cancel(false);
        } else {
            if (this.currentMusic != null) {
                this.schedulePlayNext(this.currentMusic.getMeta().getDuration().asSeconds(), false);
            }
        }
    }

    public void stop() {
        if (this.isStopped.getAndSet(true)) return;
        if (this.playNextFuture != null) this.playNextFuture.cancel(false);

        this.updateState(s -> s.paused = true, List.of(MusicRoomState.PAUSED));
    }

    public void start() {
        if (!this.isStopped.getAndSet(false)) return;
        this.playNextMusic();
    }

    public void receiveVoteRequest(ServerPlayerEntity player) {
        if (this.isVoting) {
            player.sendMessage(Text.translatable("concerto.agent.vote.voting"));
            return;
        }
        if (this.trackedPauseState) {
            player.sendMessage(Text.translatable("concerto.agent.not_playing"));
            return;
        }
        this.voteLock.lock();
        try {
            if (this.isVoting) return;
            this.isVoting = true;
            this.yesVoters.clear();
            this.noVoters.clear();
            this.voteFuture = this.scheduler.schedule(this::endVoting, 15, TimeUnit.SECONDS);
        } finally {
            this.voteLock.unlock();
        }
        this.membersForEach((playerName) -> {
            ServerPlayerEntity p = this.server.getPlayerManager().getPlayer(playerName);
            if (p != null) ServerMusicNetworkHandler.sendVote2Member(p);
        });
        ConcertoServer.LOGGER.info("Vote request created");
    }

    public void receiveVote(ServerPlayerEntity player, boolean vote) {
        if (!this.isVoting) {
            player.sendMessage(Text.translatable("concerto.agent.vote.ended"));
            return;
        }
        if (this.yesVoters.contains(player) || this.noVoters.contains(player)) {
            player.sendMessage(Text.translatable("concerto.agent.vote.duplicate"));
            return;
        }

        this.voteLock.lock();
        try {
            (vote ? this.yesVoters : this.noVoters).add(player);
            if (this.yesVoters.size() + this.noVoters.size() >= this.getMembers().size() - 1) {
                if (this.voteFuture != null) this.voteFuture.cancel(false);
                this.endVoting();
            }
        } finally {
            this.voteLock.unlock();
        }

        player.sendMessage(Text.translatable("concerto.agent.vote_for", vote ?
                Text.translatable("concerto.accept") : Text.translatable("concerto.reject")));
    }

    private void endVoting() {
        boolean success;
        int yes, no;
        this.voteLock.lock();
        try {
            if (!this.isVoting) return;
            this.isVoting = false;
            yes = this.yesVoters.size();
            no = this.noVoters.size();
            success = yes > no;
        } finally {
            this.voteLock.unlock();
        }

        if (success) {
            ConcertoServer.LOGGER.info("Vote: Play the next music");
            this.playNextMusic();
        } else {
            ConcertoServer.LOGGER.info("Vote: Keep current music");
        }
        this.broadcast(Text.translatable(success ? "concerto.agent.vote.success" : "concerto.agent.vote.failed", yes, no));
    }

    public void playNextMusic() {
        if (this.isStopped.get()) return;

        this.updateState(s -> {
            ConcertoPlayerList list = s.musicList;
            UUID cur = s.currentIndex;
            UUID nextUid = cur == null ? list.firstUuid() : list.nextUuid(cur);

            if (nextUid == null && this.currentlyFreeTime.get()) {
                nextUid = list.firstUuid();
            }

            if (nextUid != null) {
                s.currentIndex = nextUid;
                if (!this.currentlyFreeTime.get()) {
                    while (list.firstUuid() != null && !list.firstUuid().equals(nextUid)) {
                        list.removeFirst();
                    }
                }
            } else {
                list.clear();
                if (!this.freeTimePlaylist.isEmpty()) {
                    this.freeTimePlaylist.forEach(list::addLast);
                    s.currentIndex = list.firstUuid();
                    this.currentlyFreeTime.set(true);
                } else {
                    s.currentIndex = null;
                    s.paused = true;
                    this.currentlyFreeTime.set(false);
                }
            }
        }, List.of(MusicRoomState.MUSIC_LIST, MusicRoomState.CURRENT_INDEX, MusicRoomState.PAUSED));
    }

    private void resolveAndPlayCurrentMusic() {
        UUID currentUUID = this.room.serverState.get().currentIndex;
        Music taskMusic = this.room.serverState.get().musicList.get(currentUUID);
        this.currentMusic = taskMusic;

        if (taskMusic == null) {
            this.updateState(s -> {
                s.resolvedMedia = null;
                s.paused = true;
            }, List.of(MusicRoomState.RESOLVED_MEDIA, MusicRoomState.PAUSED));
            return;
        }

        ConcertoRunner.run(() -> {
            try {
                if (!(taskMusic instanceof DynamicPath dynamicPath)) {
                    if (!Objects.equals(this.room.serverState.get().currentIndex, currentUUID)) return;
                    this.broadcast(Text.translatable("concerto.agent.play.failed", taskMusic.getMeta().title(), taskMusic.getMeta().author()));
                    this.playNextMusic();
                    return;
                }

                SharedMusic resolvedShared;
                if (ServerConfig.INSTANCE.options.musicAgentUseShared) {
                    String path = dynamicPath.updateRawPath();
                    if (!Objects.equals(this.room.serverState.get().currentIndex, currentUUID)) return;
                    if (path == null) {
                        this.broadcast(Text.translatable("concerto.agent.play.failed", taskMusic.getMeta().title(), taskMusic.getMeta().author()));
                        this.playNextMusic();
                        return;
                    }
                    resolvedShared = new SharedMusic(path, taskMusic.getMeta(), dynamicPath.getLastLyrics(), dynamicPath.getLastSubLyrics());
                } else {
                    resolvedShared = (SharedMusic) taskMusic;
                }

                if (!Objects.equals(this.room.serverState.get().currentIndex, currentUUID)) return;
                
                String media = MusicJsonParsers.to(resolvedShared).toString();
                this.updateState(s -> {
                    s.resolvedMedia = media;
                    s.paused = false;
                }, List.of(MusicRoomState.RESOLVED_MEDIA, MusicRoomState.PAUSED));
                
                // If the paused state is already false, syncPauseState won't schedule playNext. We must manually schedule it.
                if (!this.trackedPauseState) {
                    this.schedulePlayNext(taskMusic.getMeta().getDuration().asSeconds(), true);
                }

            } catch (Exception e) {
                if (Objects.equals(this.room.serverState.get().currentIndex, currentUUID)) {
                    ConcertoServer.LOGGER.error("Resolve media failed", e);
                    this.playNextMusic();
                }
            }
        });
    }

    public void broadcast(Text text) {
        this.membersForEach((playerName) -> {
            ServerPlayerEntity p = this.server.getPlayerManager().getPlayer(playerName);
            if (p != null) p.sendMessage(text, false);
        });
    }

    public boolean isMember(ServerPlayerEntity player) {
        return this.getMembers().containsKey(player.getName().getString());
    }

    public void addMusic(ServerPlayerEntity player, Music music) {
        UUID playerUuid = player.getUuid();
        String playerName = player.getName().getString();
        
        long lastAdd = this.addMusicTimeRecord.getOrDefault(playerUuid, 0L);
        int wait = (int) (ServerConfig.INSTANCE.options.musicAgentAddTimeLimit - (System.currentTimeMillis() - lastAdd) / 1000);
        if (wait > 0) {
            player.sendMessage(Text.translatable("concerto.agent.add.too_quick", wait));
            return;
        }

        ConcertoRunner.run(() -> {
            this.updateState(state -> {
                if (this.currentlyFreeTime.get()) {
                    state.musicList.clear();
                    state.currentIndex = null;
                    this.currentlyFreeTime.set(false);
                }
                UUID addedUuid = state.musicList.addLast(music);
                if (state.currentIndex == null) {
                    state.currentIndex = addedUuid;
                }
            }, List.of(MusicRoomState.MUSIC_LIST, MusicRoomState.CURRENT_INDEX));

            this.addMusicTimeRecord.put(playerUuid, System.currentTimeMillis());
            this.broadcast(Text.translatable("concerto.agent.add", playerName, music.getMeta().title(), music.getMeta().author()));
        });
    }

    public synchronized void schedulePlayNext(int delay, boolean force) {
        if (this.isStopped.get()) return;
        if (this.playNextFuture != null && !this.playNextFuture.isDone()) {
            this.playNextFuture.cancel(force);
        }
        this.playNextFuture = this.scheduler.schedule(this::playNextMusic, delay, TimeUnit.SECONDS);
    }

    public void reset() {
        this.voteLock.lock();
        try {
            this.isVoting = false;
            this.yesVoters.clear();
            this.noVoters.clear();
            if (this.voteFuture != null) this.voteFuture.cancel(true);
        } finally {
            this.voteLock.unlock();
        }

        if (this.playNextFuture != null) this.playNextFuture.cancel(true);
        this.currentMusic = null;
        this.currentlyFreeTime.set(false);

        this.updateState(s -> {
            s.musicList.clear();
            s.currentIndex = null;
            s.resolvedMedia = null;
            s.paused = true;
        }, List.of(MusicRoomState.MUSIC_LIST, MusicRoomState.CURRENT_INDEX, MusicRoomState.RESOLVED_MEDIA, MusicRoomState.PAUSED));
    }

    public Map<String, Integer> getMembers() {
        return ((MusicRoomState) this.room.serverState.get()).members;
    }

    public void membersForEach(Consumer<String> consumer) {
        this.getMembers().keySet().forEach(consumer);
    }

    public List<Music> getMusicQueue() {
        return this.room.serverState.get().musicList.snapshotMusics();
    }
}
