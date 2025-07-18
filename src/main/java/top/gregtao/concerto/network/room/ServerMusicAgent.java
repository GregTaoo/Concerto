package top.gregtao.concerto.network.room;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import top.gregtao.concerto.ConcertoServer;
import top.gregtao.concerto.api.DynamicPath;
import top.gregtao.concerto.config.ServerConfig;
import top.gregtao.concerto.http.HttpURLInputStream;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.music.SharedMusic;
import top.gregtao.concerto.network.ServerMusicNetworkHandler;
import top.gregtao.concerto.player.MusicPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class ServerMusicAgent {
    public static ServerMusicAgent INSTANCE = new ServerMusicAgent();

    private final Map<ServerPlayerEntity, Long> members = new HashMap<>();
    private final ScheduledExecutorService voteScheduler = Executors.newScheduledThreadPool(1);

    private final Lock voteLock = new ReentrantLock();
    private volatile boolean isVoting = false;
    private final List<ServerPlayerEntity> yesVoters = new ArrayList<>();
    private final List<ServerPlayerEntity> noVoters = new ArrayList<>();
    private ScheduledFuture<?> voteFuture;

    private final ScheduledExecutorService musicScheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> playNextFuture;
    private final ConcurrentLinkedQueue<Music> musicQueue = new ConcurrentLinkedQueue<>();
    private Music currentMusic = null;
    private Music currentSharedMusic = null;
    private int totalBytes = 0;
    private long playTime = 0;

    private final AtomicBoolean isStopped = new AtomicBoolean(false);
    private final AtomicBoolean isPlaying = new AtomicBoolean(false);
    private final AtomicBoolean currentlyFreeTime = new AtomicBoolean(false);

    public ArrayList<Music> freeTimePlaylist = new ArrayList<>();
    private int freeTimePlaylistIndex = 0;

    public void stop() {
        if (this.isStopped.get()) return;
        this.isStopped.set(true);
        this.playNextFuture.cancel(false);
        this.membersForEach(ServerMusicNetworkHandler::musicAgentSendStop);
    }

    public void start() {
        if (!this.isStopped.get()) return;
        this.isStopped.set(false);
        this.schedulePlayNext(0, false);
    }

    private Music getNextFreeTimeMusic() {
        if (this.freeTimePlaylist.isEmpty()) return null;
        this.freeTimePlaylistIndex = (this.freeTimePlaylistIndex + 1) % this.freeTimePlaylist.size();
        return this.freeTimePlaylist.get(this.freeTimePlaylistIndex);
    }

    public boolean hasNextMusic() {
        return !this.musicQueue.isEmpty() || !this.freeTimePlaylist.isEmpty();
    }

    public void receiveVoteRequest(ServerPlayerEntity player) {
        if (this.isVoting) {
            player.sendMessage(Text.translatable("concerto.agent.vote.voting"));
            return;
        }
        if (!this.isPlaying.get()) {
            player.sendMessage(Text.translatable("concerto.agent.not_playing"));
            return;
        }

        synchronized (this) {
            this.voteLock.lock();

            this.isVoting = true;
            this.yesVoters.clear();
            this.noVoters.clear();
            this.voteFuture = this.voteScheduler.schedule(this::endVoting, 15, TimeUnit.SECONDS);

            this.voteLock.unlock();
            this.membersForEach(ServerMusicNetworkHandler::sendVote2Member);

            ConcertoServer.LOGGER.info("Vote request created");
        }
    }

    public void receiveVote(ServerPlayerEntity player, boolean vote) {
        if (!this.isVoting) {
            player.sendMessage(Text.translatable("concerto.agent.vote.ended"));
            return;
        }

        synchronized (this) {
            if (this.yesVoters.contains(player) || this.noVoters.contains(player)) {
                player.sendMessage(Text.translatable("concerto.agent.vote.duplicate"));
                return;
            }
            this.voteLock.lock();
            (vote ? this.yesVoters : this.noVoters).add(player);
            if (this.yesVoters.size() + this.noVoters.size() == this.members.size() &&
                    this.voteFuture.cancel(false)) {
                this.endVoting();
            }
            this.voteLock.unlock();
            player.sendMessage(Text.translatable("concerto.agent.vote_for", vote ?
                    Text.translatable("concerto.accept") : Text.translatable("concerto.reject")));
            ConcertoServer.LOGGER.info("Player {} voted {}", player.getName().getString(), vote);
        }
    }

    private synchronized void endVoting() {
        boolean success = this.yesVoters.size() > this.noVoters.size();
        if (success) {
            ConcertoServer.LOGGER.info("Vote: Play the next music");
            if (!this.hasNextMusic()) {
                this.membersForEach(ServerMusicNetworkHandler::musicAgentSendStop);
            } else if (this.playNextFuture.isDone() || this.playNextFuture.cancel(false)) {
                this.playNextMusic();
            }
        } else {
            ConcertoServer.LOGGER.info("Vote: Keep current music");
        }
        Text text = Text.translatable(success ? "concerto.agent.vote.success" : "concerto.agent.vote.failed",
                this.yesVoters.size(), this.noVoters.size());
        this.broadcast(text);

        this.isVoting = false;
    }

    public synchronized void playNextMusic() {
        try {
            this.currentMusic = this.musicQueue.poll();
            if (this.currentMusic == null) {
                this.currentMusic = this.getNextFreeTimeMusic();
                this.currentlyFreeTime.set(this.currentMusic != null);
            } else {
                this.currentlyFreeTime.set(false);
            }
            this.currentSharedMusic = null;
            this.totalBytes = 0;
            this.playTime = 0;
            if (this.currentMusic == null) {
                ConcertoServer.LOGGER.info("Music agent paused");
                this.isPlaying.set(false);
            } else {
                ConcertoServer.LOGGER.info("Start playing music {}, duration {}",
                        this.currentMusic.getMeta().title(), this.currentMusic.getMeta().getDuration());
                if (ServerConfig.INSTANCE.options.musicAgentUseShared && this.currentMusic instanceof DynamicPath dynamicPath) {
                    String path = dynamicPath.updateRawPath();
                    if (path != null) {
                        this.totalBytes = HttpURLInputStream.getTotalBytes(path);
                    } else {
                        ConcertoServer.LOGGER.warn("Cannot play music {}", this.currentMusic.getMeta().title());
                        this.broadcast(Text.translatable("concerto.agent.play.failed",
                                this.currentMusic.getMeta().title(), this.currentMusic.getMeta().author()));
                        this.schedulePlayNext(0, false);
                        return;
                    }
                    this.currentSharedMusic = new SharedMusic(path, this.currentMusic.getMeta(),
                            dynamicPath.getLastLyrics(), dynamicPath.getLastSubLyrics());
                } else {
                    this.currentSharedMusic = this.currentMusic;
                }
                this.isPlaying.set(true);
                this.playTime = System.currentTimeMillis();
                ServerMusicNetworkHandler.musicAgentSendMusic(this.getMembers(), this.currentSharedMusic);
                this.schedulePlayNext(this.currentMusic.getMeta().getDuration().asSeconds(), false);
            }
        } catch (Exception e) {
            ConcertoServer.LOGGER.error("Play music failed", e);
            this.broadcast(Text.translatable("concerto.agent.error"));
            this.reset();
        }
    }

    public void broadcast(Text text) {
        this.members.forEach((player, time) -> player.sendMessage(text, false));
    }

    public synchronized boolean isMember(ServerPlayerEntity player) {
        return this.members.containsKey(player);
    }

    public synchronized void addMusic(ServerPlayerEntity player, Music music) {
        Long lastAddTime = this.members.getOrDefault(player, 0L);
        int wait = (int) (ServerConfig.INSTANCE.options.musicAgentAddTimeLimit - (System.currentTimeMillis() - lastAddTime) / 1000);
        if (wait > 0) {
            player.sendMessage(Text.translatable("concerto.agent.add.too_quick", wait));
            return;
        }
        MusicPlayer.run(() -> {
            ConcertoServer.LOGGER.info("Added music {}", music.getMeta().title());
            this.musicQueue.offer(music);
            this.members.put(player, System.currentTimeMillis());
            this.broadcast(Text.translatable("concerto.agent.add",
                    player == null ? Text.translatable("concerto.unknown") : player.getName().getString(),
                    music.getMeta().title(), music.getMeta().author()));
            if (!this.isPlaying.get() || this.currentlyFreeTime.get()) {
                this.schedulePlayNext(0, false);
            }
        });
    }

    public synchronized void playerJoin(ServerPlayerEntity player) {
        ConcertoServer.LOGGER.info("Player {} joined music agent", player.getName().getString());
        this.members.put(player, -1L);
        if (!this.isPlaying.get() && this.hasNextMusic()) {
            this.schedulePlayNext(0, false);
        } else if (this.isPlaying.get() && this.currentSharedMusic != null) {
            MusicPlayer.run(() -> {
                if (this.currentSharedMusic instanceof SharedMusic shared) {
                    shared.startTime = System.currentTimeMillis() - this.playTime;
                    shared.startByte = this.totalBytes * (System.currentTimeMillis() - this.playTime) /
                            this.currentMusic.getMeta().getDuration().asMilliseconds();
                    shared.setRawPath(((DynamicPath) this.currentSharedMusic).updateRawPath());
                }
                ServerMusicNetworkHandler.musicAgentSendMusic(player, this.currentSharedMusic);
            });
        }
    }

    public synchronized void playerQuit(ServerPlayerEntity player) {
        ConcertoServer.LOGGER.info("Player {} quited music agent", player.getName().getString());
        this.members.remove(player);
    }

    public synchronized void schedulePlayNext(int delay, boolean force) {
        if (this.isStopped.get()) return;
        if (this.playNextFuture != null && !this.playNextFuture.isDone()) {
            this.playNextFuture.cancel(force);
        }
        this.playNextFuture = this.musicScheduler.schedule(this::playNextMusic, delay, TimeUnit.SECONDS);
    }

    public synchronized void reset() {
        this.voteLock.lock();
        this.isVoting = false;
        this.yesVoters.clear();
        this.noVoters.clear();
        this.voteLock.unlock();

        if (this.playNextFuture != null) {
            this.playNextFuture.cancel(true);
        }
        if (this.voteFuture != null) {
            this.voteFuture.cancel(true);
        }
        this.musicQueue.clear();
        this.currentMusic = this.currentSharedMusic = null;
        this.totalBytes = 0;
        this.playTime = 0;
        this.isPlaying.set(false);
        this.currentlyFreeTime.set(false);

        ConcertoServer.LOGGER.info("Reset server music agent");
    }

    public List<ServerPlayerEntity> getMembers() {
        return this.members.keySet().stream().toList();
    }

    public void membersForEach(Consumer<ServerPlayerEntity> consumer) {
        this.members.forEach((player, time) -> consumer.accept(player));
    }

    public List<Music> getMusicQueue() {
        return this.musicQueue.stream().toList();
    }
}
