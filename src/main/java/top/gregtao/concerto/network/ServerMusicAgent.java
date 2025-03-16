package top.gregtao.concerto.network;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import top.gregtao.concerto.ConcertoServer;
import top.gregtao.concerto.music.Music;

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

    private final ScheduledExecutorService musicScheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> playNextFuture;
    private final ConcurrentLinkedQueue<Music> musicQueue = new ConcurrentLinkedQueue<>();
    private Music currentMusic = null;

    private final AtomicBoolean isPlaying = new AtomicBoolean(false);

    public boolean receiveVoteRequest() {
        if (this.isVoting || !this.isPlaying.get()) return false;

        synchronized (this) {
            this.voteLock.lock();

            this.isVoting = true;
            this.yesVoters.clear();
            this.noVoters.clear();
            this.voteScheduler.schedule(this::endVoting, 15, TimeUnit.SECONDS);

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
            this.voteLock.unlock();
            player.sendMessage(Text.translatable("concerto.agent.vote", vote ?
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
        Text text = success ? Text.translatable("concerto.agent.vote.success") : Text.translatable("concerto.agent.vote.failed");
        this.members.forEach(player -> player.sendMessage(text, false));

        this.isVoting = false;
    }

    private synchronized void playNextMusic() {
        this.currentMusic = this.musicQueue.poll();
        if (this.currentMusic == null) {
            ConcertoServer.LOGGER.info("Music agent paused");
            this.isPlaying.set(false);
        } else {
            ConcertoServer.LOGGER.info("Start playing music {}", this.currentMusic.getMeta().title());
            this.isPlaying.set(true);
            ServerMusicNetworkHandler.musicAgentSendMusic(this.members, this.currentMusic, 0);
            this.playNextFuture = this.musicScheduler.schedule(this::playNextMusic,
                    this.currentMusic.getMeta().getDuration().asSeconds(), TimeUnit.SECONDS);
        }
    }

    public synchronized boolean isMember(ServerPlayerEntity player) {
        return this.members.contains(player);
    }

    public synchronized void addMusic(Music music) {
        ConcertoServer.LOGGER.info("Added music {}", music.getMeta().title());
        this.musicQueue.offer(music);
        if (!this.isPlaying.get()) {
            this.playNextFuture = this.musicScheduler.schedule(this::playNextMusic, 1, TimeUnit.SECONDS);
        }
    }

    public synchronized void playerJoin(ServerPlayerEntity player) {
        ConcertoServer.LOGGER.info("Player {} joined music agent", player.getName().getString());
        this.members.add(player);
        if (this.isPlaying.get() && this.currentMusic != null) {
            ServerMusicNetworkHandler.musicAgentSendMusic(this.members, this.currentMusic, 0);
        }
    }

    public synchronized void playerQuit(ServerPlayerEntity player) {
        ConcertoServer.LOGGER.info("Player {} quited music agent", player.getName().getString());
        this.members.remove(player);
    }

    public synchronized void reset() {
        this.members.clear();

        this.voteLock.lock();
        this.isVoting = false;
        this.yesVoters.clear();
        this.noVoters.clear();
        this.voteLock.unlock();

        if (this.playNextFuture != null) {
            this.playNextFuture.cancel(false);
        }
        this.musicQueue.clear();
        this.currentMusic = null;
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
