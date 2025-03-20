package top.gregtao.concerto.network.room;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import top.gregtao.concerto.ConcertoServer;
import top.gregtao.concerto.api.DynamicPath;
import top.gregtao.concerto.http.HttpURLInputStream;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.music.SharedMusic;
import top.gregtao.concerto.network.ServerMusicNetworkHandler;
import top.gregtao.concerto.player.MusicPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ServerMusicAgent {
    public static ServerMusicAgent INSTANCE = new ServerMusicAgent();

    private final List<ServerPlayerEntity> members = new ArrayList<>();
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

    private final AtomicBoolean isPlaying = new AtomicBoolean(false);

    public boolean receiveVoteRequest() {
        if (this.isVoting || !this.isPlaying.get()) return false;

        synchronized (this) {
            this.voteLock.lock();

            this.isVoting = true;
            this.yesVoters.clear();
            this.noVoters.clear();
            this.voteFuture = this.voteScheduler.schedule(this::endVoting, 15, TimeUnit.SECONDS);

            this.voteLock.unlock();

            ConcertoServer.LOGGER.info("Vote request created");
            return true;
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
            if (this.playNextFuture.cancel(false)) {
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
        this.currentMusic = this.musicQueue.poll();
        this.currentSharedMusic = null;
        this.totalBytes = 0;
        this.playTime = 0;
        if (this.currentMusic == null) {
            ConcertoServer.LOGGER.info("Music agent paused");
            this.isPlaying.set(false);
        } else {
            ConcertoServer.LOGGER.info("Start playing music {}", this.currentMusic.getMeta().title());
            if (this.currentMusic instanceof DynamicPath dynamicPath) {
                String path = dynamicPath.getLastRawPath();
                if (path != null) this.totalBytes = HttpURLInputStream.getTotalBytes(path);
                else {
                    ConcertoServer.LOGGER.warn("Cannot play music {}", this.currentMusic.getMeta().title());
                    this.broadcast(Text.translatable("concerto.agent.play.failed"));
                    this.playNextFuture = this.musicScheduler.schedule(this::playNextMusic, 1, TimeUnit.SECONDS);
                    return;
                }
                this.currentSharedMusic = new SharedMusic(path, this.currentMusic.getMeta(),
                        dynamicPath.getLastLyrics(), dynamicPath.getLastSubLyrics());
            } else {
                this.currentSharedMusic = this.currentMusic;
            }
            this.isPlaying.set(true);
            this.playTime = System.currentTimeMillis();
            ServerMusicNetworkHandler.musicAgentSendMusic(this.members, this.currentSharedMusic);
            this.playNextFuture = this.musicScheduler.schedule(this::playNextMusic,
                    this.currentMusic.getMeta().getDuration().asSeconds(), TimeUnit.SECONDS);
        }
    }

    public void broadcast(Text text) {
        this.members.forEach(player -> player.sendMessage(text, false));
    }

    public synchronized boolean isMember(ServerPlayerEntity player) {
        return this.members.contains(player);
    }

    public synchronized void addMusic(ServerPlayerEntity player, Music music) {
        MusicPlayer.run(() -> {
            ConcertoServer.LOGGER.info("Added music {}", music.getMeta().title());
            this.musicQueue.offer(music);
            this.broadcast(Text.translatable("concerto.agent.add",
                    player == null ? Text.translatable("concerto.unknown") : player.getName().getString(),
                    music.getMeta().title(), music.getMeta().author()));
            if (!this.isPlaying.get()) {
                this.playNextFuture = this.musicScheduler.schedule(this::playNextMusic, 1, TimeUnit.SECONDS);
            }
        });
    }

    public synchronized void playerJoin(ServerPlayerEntity player) {
        ConcertoServer.LOGGER.info("Player {} joined music agent", player.getName().getString());
        this.members.add(player);
        if (this.isPlaying.get() && this.currentSharedMusic != null) {
            if (this.currentSharedMusic instanceof SharedMusic shared) {
                shared.startTime = System.currentTimeMillis() - this.playTime;
                shared.startByte = this.totalBytes * (System.currentTimeMillis() - this.playTime) /
                        this.currentMusic.getMeta().getDuration().asMilliseconds();
            }
            ServerMusicNetworkHandler.musicAgentSendMusic(player, this.currentSharedMusic);
        }
    }

    public synchronized void playerQuit(ServerPlayerEntity player) {
        ConcertoServer.LOGGER.info("Player {} quited music agent", player.getName().getString());
        this.members.remove(player);
    }

    public synchronized void reset() {
        this.voteLock.lock();
        this.isVoting = false;
        this.yesVoters.clear();
        this.noVoters.clear();
        this.voteLock.unlock();

        if (this.playNextFuture != null) {
            this.playNextFuture.cancel(false);
        }
        this.musicQueue.clear();
        this.currentMusic = this.currentSharedMusic = null;
        this.totalBytes = 0;
        this.playTime = 0;
        this.isPlaying.set(false);

        ConcertoServer.LOGGER.info("Reset server music agent");
    }

    public List<ServerPlayerEntity> getMembers() {
        return this.members;
    }

    public List<Music> getMusicQueue() {
        return this.musicQueue.stream().toList();
    }
}
