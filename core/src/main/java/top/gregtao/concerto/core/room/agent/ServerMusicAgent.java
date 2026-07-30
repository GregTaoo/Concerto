package top.gregtao.concerto.core.room.agent;

import com.google.gson.JsonObject;
import top.gregtao.concerto.core.Concerto;
import top.gregtao.concerto.core.api.DynamicPath;
import top.gregtao.concerto.core.api.MusicJsonParsers;
import top.gregtao.concerto.core.config.ServerConfig;
import top.gregtao.concerto.core.music.Music;
import top.gregtao.concerto.core.music.SharedMusic;
import top.gregtao.concerto.core.player.ConcertoPlayerList;
import top.gregtao.concerto.core.player.MusicPlayerState;
import top.gregtao.concerto.core.room.MusicRoom;
import top.gregtao.concerto.core.room.MusicRoom.MusicRoomState;
import top.gregtao.concerto.core.util.ConcertoRunner;

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

    public static boolean isServerAgent(UUID uuid) {
        return uuid.equals(ROOM_UUID);
    }

    public static void init(ServerNetworkBridge serverBridge, MusicRoom.ServerNetworkBridge roomServerBridge) {
        INSTANCE = new ServerMusicAgent(serverBridge, roomServerBridge);
    }

    public final MusicRoom room;

    public interface ServerNetworkBridge {
        void serverSendVoteRequest(String playerName);
    }

    public interface ClientNetworkBridge {
        void clientSendAgentCommand(Command command, String payload);
    }

    private final ServerNetworkBridge serverBridge;

    private final Map<String, Long> addMusicTimeRecord = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(2, ConcertoRunner.daemonThreadFactory("Concerto-Agent-Scheduler"));
    private final Lock voteLock = new ReentrantLock();
    private volatile boolean isVoting = false;
    private final Set<String> yesVoters = ConcurrentHashMap.newKeySet();
    private final Set<String> noVoters = ConcurrentHashMap.newKeySet();
    private ScheduledFuture<?> voteFuture;

    private ScheduledFuture<?> playNextFuture;
    private Music currentMusic = null;
    /** Monotonic start time for the track currently advertised to clients. */
    private volatile long currentMusicStartedAtNanos = -1L;
    private UUID trackedIndex = null;
    private boolean trackedPauseState = true;
    private final AtomicBoolean isStopped = new AtomicBoolean(false);
    private final AtomicBoolean currentlyFreeTime = new AtomicBoolean(false);
    public List<Music> freeTimePlaylist = new CopyOnWriteArrayList<>();

    private ServerMusicAgent(ServerNetworkBridge serverBridge, MusicRoom.ServerNetworkBridge roomServerBridge) {
        this.serverBridge = serverBridge;
        this.room = new MusicRoom("#Server", ROOM_UUID, roomServerBridge);
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

    public void receiveVoteRequest(String playerName) {
        if (this.isVoting) {
            this.room.serverBridge.sendMessage(playerName, "concerto.agent.vote.voting");
            return;
        }
        if (this.trackedPauseState) {
            this.room.serverBridge.sendMessage(playerName, "concerto.not_playing");
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
        this.membersForEach(this.serverBridge::serverSendVoteRequest);
        Concerto.getLogger().info("Vote request created");
    }

    public void receiveVote(String playerName, boolean vote) {
        if (!this.isVoting) {
            this.room.serverBridge.sendMessage(playerName, "concerto.agent.vote.ended");
            return;
        }
        if (this.yesVoters.contains(playerName) || this.noVoters.contains(playerName)) {
            this.room.serverBridge.sendMessage(playerName, "concerto.agent.vote.duplicate");
            return;
        }

        this.voteLock.lock();
        try {
            (vote ? this.yesVoters : this.noVoters).add(playerName);
            if (this.yesVoters.size() + this.noVoters.size() >= this.getMembers().size() - 1) {
                if (this.voteFuture != null) this.voteFuture.cancel(false);
                this.endVoting();
            }
        } finally {
            this.voteLock.unlock();
        }

        this.room.serverBridge.sendMessage(playerName, "concerto.agent.vote_for",
                Concerto.getCoreBridge().getTranslatable(vote ? "concerto.accept" : "concerto.reject"));
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
            Concerto.getLogger().info("Vote: Play the next music");
            this.playNextMusic();
        } else {
            Concerto.getLogger().info("Vote: Keep current music");
        }
        this.broadcast(success ? "concerto.agent.vote.success" : "concerto.agent.vote.failed", yes, no);
    }

    public void playNextMusic() {
        if (this.isStopped.get()) return;

        this.updateState(s -> {
            ConcertoPlayerList list = s.musicList;
            UUID cur = s.currentIndex;
            UUID nextUid;
            if (this.currentlyFreeTime.get()) {
                nextUid = this.getFreeTimeNextUuid(list, cur);
            } else {
                nextUid = cur == null ? list.firstUuid() : list.nextUuid(cur);
            }

            if (nextUid != null) {
                s.setCurrentIndex(nextUid, 25);
                if (!this.currentlyFreeTime.get()) {
                    while (list.firstUuid() != null && !list.firstUuid().equals(nextUid)) {
                        list.removeFirst();
                    }
                }
            } else {
                list.clear();
                if (!this.freeTimePlaylist.isEmpty()) {
                    this.freeTimePlaylist.forEach(list::addLast);
                    s.setCurrentIndex(this.getFreeTimeStartUuid(list), 25);
                    this.currentlyFreeTime.set(true);
                } else {
                    s.setCurrentIndex(null, 25);
                    s.paused = true;
                    this.currentlyFreeTime.set(false);
                }
            }
        }, List.of(MusicRoomState.MUSIC_LIST, MusicRoomState.CURRENT_INDEX, MusicRoomState.PAUSED,
                MusicPlayerState.PLAYBACK_HISTORY));
    }

    private UUID getFreeTimeStartUuid(ConcertoPlayerList list) {
        return this.getFreeTimeNextUuid(list, null);
    }

    private UUID getFreeTimeNextUuid(ConcertoPlayerList list, UUID current) {
        if (ServerConfig.INSTANCE.options.freeTimePlaylistRandom) return list.randomUuid();
        UUID next = current == null ? list.firstUuid() : list.nextUuid(current);
        return next == null ? list.firstUuid() : next;
    }

    private void resolveAndPlayCurrentMusic() {
        UUID currentUUID = this.room.serverState.get().currentIndex;
        Music taskMusic = this.room.serverState.get().musicList.get(currentUUID);
        this.currentMusic = taskMusic;

        if (taskMusic == null) {
            this.currentMusicStartedAtNanos = -1L;
            this.updateState(s -> {
                s.resolvedMedia = null;
                s.resolvedStartTime = 0L;
                s.paused = true;
            }, List.of(MusicRoomState.RESOLVED_MEDIA, MusicRoomState.RESOLVED_START_TIME, MusicRoomState.PAUSED));
            return;
        }

        ConcertoRunner.run(() -> {
            try {
                if (!(taskMusic instanceof DynamicPath dynamicPath)) {
                    if (!Objects.equals(this.room.serverState.get().currentIndex, currentUUID)) return;
                    this.broadcast("concerto.agent.play.failed", taskMusic.getMeta().title(), taskMusic.getMeta().author());
                    this.playNextMusic();
                    return;
                }

                SharedMusic resolvedShared;
                if (ServerConfig.INSTANCE.options.musicAgentUseShared) {
                    String path = dynamicPath.updateRawPath();
                    if (!Objects.equals(this.room.serverState.get().currentIndex, currentUUID)) return;
                    if (path == null) {
                        this.broadcast("concerto.agent.play.failed", taskMusic.getMeta().title(), taskMusic.getMeta().author());
                        this.playNextMusic();
                        return;
                    }
                    resolvedShared = new SharedMusic(path, taskMusic.getMeta(), dynamicPath.getLastLyrics(), dynamicPath.getLastSubLyrics());
                } else {
                    resolvedShared = (SharedMusic) taskMusic;
                }

                if (!Objects.equals(this.room.serverState.get().currentIndex, currentUUID)) return;

                String media = MusicJsonParsers.to(resolvedShared).toString();
                this.currentMusicStartedAtNanos = System.nanoTime();
                this.updateState(s -> {
                    s.resolvedMedia = media;
                    s.resolvedStartTime = 0L;
                    s.paused = false;
                }, List.of(MusicRoomState.RESOLVED_MEDIA, MusicRoomState.RESOLVED_START_TIME, MusicRoomState.PAUSED));

                // If the paused state is already false, syncPauseState won't schedule playNext. We must manually schedule it.
                if (!this.trackedPauseState) {
                    this.schedulePlayNext(taskMusic.getMeta().getDuration().asSeconds(), true);
                }

            } catch (Exception e) {
                if (Objects.equals(this.room.serverState.get().currentIndex, currentUUID)) {
                    Concerto.getLogger().error("Resolve media failed", e);
                    this.playNextMusic();
                }
            }
        });
    }

    public void broadcast(String translationKey, Object... args) {
        this.membersForEach((playerName) -> this.room.serverBridge.sendMessage(playerName, translationKey, args));
    }

    public boolean isMember(String playerName) {
        return this.getMembers().containsKey(playerName);
    }

    /**
     * Updates the shared position before a new agent member receives its full state.
     * The server is the only clock authority for the server-wide music agent.
     */
    public void refreshPlaybackTimestamp() {
        long startedAtNanos = this.currentMusicStartedAtNanos;
        Music music = this.currentMusic;
        if (startedAtNanos < 0L || music == null || this.room.serverState.get().paused) return;

        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
        long durationMillis = music.getMeta().getDuration().asMilliseconds();
        long positionMillis = Math.max(0L, Math.min(elapsedMillis, durationMillis));
        this.updateState(s -> s.resolvedStartTime = positionMillis,
                List.of(MusicRoomState.RESOLVED_START_TIME));
    }

    public void addMusic(String playerName, Music music) {
        long lastAdd = this.addMusicTimeRecord.getOrDefault(playerName, 0L);
        int wait = (int) (ServerConfig.INSTANCE.options.musicAgentAddTimeLimit - (System.currentTimeMillis() - lastAdd) / 1000);
        if (wait > 0) {
            this.room.serverBridge.sendMessage(playerName, "concerto.agent.add.too_quick", wait);
            return;
        }

        ConcertoRunner.run(() -> {
            this.updateState(state -> {
                if (this.currentlyFreeTime.get()) {
                    state.musicList.clear();
                    state.setCurrentIndex(null, 25);
                    this.currentlyFreeTime.set(false);
                }
                UUID addedUuid = state.musicList.addLast(music);
                if (state.currentIndex == null) {
                    state.setCurrentIndex(addedUuid, 25);
                }
            }, List.of(MusicRoomState.MUSIC_LIST, MusicRoomState.CURRENT_INDEX, MusicPlayerState.PLAYBACK_HISTORY));

            this.addMusicTimeRecord.put(playerName, System.currentTimeMillis());
            this.broadcast("concerto.agent.add", playerName, music.getMeta().title(), music.getMeta().author());
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
        this.currentMusicStartedAtNanos = -1L;
        this.currentlyFreeTime.set(false);

        this.updateState(s -> {
            s.musicList.clear();
            s.setCurrentIndex(null, 25);
            s.clearPlaybackHistory();
            s.resolvedMedia = null;
            s.resolvedStartTime = 0L;
            s.paused = true;
        }, List.of(MusicRoomState.MUSIC_LIST, MusicRoomState.CURRENT_INDEX, MusicRoomState.RESOLVED_MEDIA,
                MusicRoomState.RESOLVED_START_TIME, MusicRoomState.PAUSED, MusicPlayerState.PLAYBACK_HISTORY));
    }

    /**
     * Permanent teardown, for server shutdown / plugin disable: clears state
     * like {@link #reset()} and then stops the scheduler threads. A fresh agent
     * (with a fresh scheduler) is created per server start, so without this the
     * old scheduler leaked two threads per integrated-server session.
     */
    public void dispose() {
        this.reset();
        this.scheduler.shutdownNow();
    }

    public Map<String, Integer> getMembers() {
        return ((MusicRoomState) this.room.serverState.get()).members;
    }

    public void membersForEach(Consumer<String> consumer) {
        this.getMembers().keySet().forEach(consumer);
    }

    public enum Command {
        NEW_VOTE,
        VOTE,
        ADD_MUSIC,
    }

    public static void handleServerCommand(String commandString, String payload, String sender) {
        MusicRoom.ServerNetworkBridge bridge = INSTANCE == null ? null : INSTANCE.room.serverBridge;
        try {
            if (ServerMusicAgent.INSTANCE == null) {
                Concerto.getLogger().warn("Server Music Agent is null");
                return;
            }
            if (!ServerConfig.INSTANCE.options.serverMusicAgent) {
                bridge.sendMessage(sender, "concerto.agent.not_available");
                return;
            }
            if (!ServerMusicAgent.INSTANCE.isMember(sender)) {
                bridge.sendMessage(sender, "concerto.agent.error");
                return;
            }
            Command command = Command.valueOf(commandString.toUpperCase());
            switch (command) {
                case NEW_VOTE -> ServerMusicAgent.INSTANCE.receiveVoteRequest(sender);
                case VOTE -> ServerMusicAgent.INSTANCE.receiveVote(sender, payload.equals("1"));
                case ADD_MUSIC -> {
                    Music music = MusicJsonParsers.from(payload, false);
                    if (music != null) {
                        ServerMusicAgent.INSTANCE.addMusic(sender, music);
                    } else {
                        bridge.sendMessage(sender, "concerto.agent.error");
                    }
                }
            }
        } catch (Exception e) {
            Concerto.getLogger().warn("Server Music Agent Error", e);
            if (bridge != null) {
                bridge.sendMessage(sender, "concerto.room.update.fail");
            }
        }
    }

    public static void clientNewVote(ClientNetworkBridge bridge) {
        bridge.clientSendAgentCommand(Command.NEW_VOTE, "");
    }

    public static void clientVote(ClientNetworkBridge bridge, boolean vote) {
        bridge.clientSendAgentCommand(Command.VOTE, vote ? "1" : "0");
    }

    public static void clientAddMusic(ClientNetworkBridge bridge, Music music) {
        JsonObject object = MusicJsonParsers.to(music);
        if (object != null) {
            bridge.clientSendAgentCommand(Command.ADD_MUSIC, object.toString());
        }
    }
}
