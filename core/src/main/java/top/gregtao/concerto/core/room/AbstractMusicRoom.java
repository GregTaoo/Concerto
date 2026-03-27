package top.gregtao.concerto.core.room;

import com.google.gson.JsonObject;
import top.gregtao.concerto.core.Concerto;
import top.gregtao.concerto.core.api.DynamicPath;
import top.gregtao.concerto.core.api.MusicJsonParsers;
import top.gregtao.concerto.core.enums.OrderType;
import top.gregtao.concerto.core.music.Music;
import top.gregtao.concerto.core.music.SharedMusic;
import top.gregtao.concerto.core.network.ClientRemoteRecord;
import top.gregtao.concerto.core.network.ServerRemoteRecord;
import top.gregtao.concerto.core.network.SyncRecord;
import top.gregtao.concerto.core.player.ConcertoPlayerList;
import top.gregtao.concerto.core.player.MusicPlayer;
import top.gregtao.concerto.core.player.MusicPlayerState;

import java.lang.reflect.Field;
import java.util.*;

public abstract class AbstractMusicRoom<C> {

    public static class RoomPlayerState extends MusicPlayerState {
        public String resolvedMedia = null;
        public String errorMessage = "";
        public String owner = "";
        public Map<String, Integer> members = new HashMap<>();

        public static final Field RESOLVED_MEDIA;
        public static final Field ERROR_MESSAGE;
        public static final Field OWNER;
        public static final Field MEMBERS;

        static {
            try {
                RESOLVED_MEDIA = RoomPlayerState.class.getField("resolvedMedia");
                ERROR_MESSAGE = RoomPlayerState.class.getField("errorMessage");
                OWNER = RoomPlayerState.class.getField("owner");
                MEMBERS = RoomPlayerState.class.getField("members");
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }

        public RoomPlayerState() {
            super();
        }

        @Override
        public RoomPlayerState copy() {
            RoomPlayerState c = new RoomPlayerState();
            c.musicList = this.musicList.copy();
            c.currentIndex = this.currentIndex;
            c.orderType = this.orderType;
            c.paused = this.paused;
            c.resolvedMedia = this.resolvedMedia;
            c.errorMessage = this.errorMessage;
            c.owner = this.owner;
            c.members = new HashMap<>(this.members);
            return c;
        }
    }

    public final UUID uuid;
    public ServerRemoteRecord<MusicPlayerState> serverState;
    public ClientRemoteRecord<MusicPlayerState> clientState;
    public int permission = 0;

    // Server constructors
    protected AbstractMusicRoom(String creator, UUID uuid, C context) {
        this.uuid = uuid;
        RoomPlayerState state = new RoomPlayerState();
        state.owner = creator;
        state.members.put(creator, 3);
        this.serverState = SyncRecord.createServerRecord(state, this::broadcastSync, context);
    }

    protected AbstractMusicRoom(String creator, C context) {
        this(creator, UUID.randomUUID(), context);
    }

    // Client constructor
    protected AbstractMusicRoom(UUID uuid) {
        this.uuid = uuid;
        this.clientState = SyncRecord.createClientRecord(new RoomPlayerState(), this::sendSyncPackage);
        this.registerClientListeners(this.clientState);
    }

    protected abstract void broadcastSync(JsonObject patch, C context);

    protected abstract void sendSyncPackage(JsonObject patch);

    // Call this if implementing a client-side room
    protected void registerClientListeners(ClientRemoteRecord<MusicPlayerState> record) {
        record.addListener(RoomPlayerState.MEMBERS, (o, state, oldVal, newVal) -> {
            RoomPlayerState rs = (RoomPlayerState) state;
            String name = getClientPlayerName();
            if (name != null) {
                this.permission = rs.members.getOrDefault(name, 0);
            }
        });

        record.addListener(MusicPlayerState.MUSIC_LIST, (o, state, oldVal, newVal) -> {
            int permission = this.permission;
            if (permission <= 1) return;

            UUID current = state.currentIndex;
            ConcertoPlayerList list = state.musicList;

            UUID target = current;
            if (list.isEmpty()) {
                target = null;
            } else if (!list.contains(current)) {
                target = state.orderType == OrderType.REVERSED ? list.lastUuid() : list.firstUuid();
            }

            if (!Objects.equals(target, current)) {
                UUID finalTarget = target;
                o.set((s) -> {
                    s.currentIndex = finalTarget;
                    return s;
                }, List.of(MusicPlayerState.CURRENT_INDEX));
            }
        });

        record.addListener(MusicPlayerState.CURRENT_INDEX, (o, playerState, oldVal, newVal) -> {
            RoomPlayerState state = (RoomPlayerState) playerState;
            int permission = this.permission;

            if (permission == 3) {
                UUID current = state.currentIndex;
                if (current != null && state.musicList.contains(current)) {
                    Music music = state.musicList.get(current);
                    if (music instanceof DynamicPath dp) {
                        SharedMusic sm = new SharedMusic(dp.getLastRawPath(), music.getMeta(), dp.getLastLyrics(), dp.getLastSubLyrics());
                        String rawSm = MusicJsonParsers.to(sm).toString();
                        if (rawSm != null) {
                            o.set(s -> {
                                RoomPlayerState rs = (RoomPlayerState) s;
                                rs.resolvedMedia = rawSm;
                                return rs;
                            }, List.of(RoomPlayerState.RESOLVED_MEDIA));
                            return;
                        }
                    }
                    String error = Concerto.getMinecraft().getTranslatableText("concerto.player.unable",
                            music.getMeta().title(), music.getMeta().author(), music.getMeta().getSource());
                    o.set(s -> {
                        RoomPlayerState rs = (RoomPlayerState) s;
                        rs.errorMessage = error;
                        return rs;
                    }, List.of(RoomPlayerState.ERROR_MESSAGE));
                } else if (state.resolvedMedia != null) {
                    o.set(s -> {
                        RoomPlayerState rs = (RoomPlayerState) s;
                        rs.resolvedMedia = null;
                        return rs;
                    }, List.of(RoomPlayerState.RESOLVED_MEDIA));
                }
            }
        });

        record.addListener(RoomPlayerState.RESOLVED_MEDIA, (o, playerState, oldVal, newVal) -> {
            RoomPlayerState state = (RoomPlayerState) playerState;
            onResolvedMediaUpdate(state);
        });

        record.addListener(RoomPlayerState.ERROR_MESSAGE, (o, playerState, oldVal, newVal) -> {
            RoomPlayerState state = (RoomPlayerState) playerState;
            if (state.errorMessage != null && !state.errorMessage.isEmpty()) {
                onErrorMessageUpdate(state.errorMessage);
            }
        });

        record.addListener(MusicPlayerState.PAUSED, (o, state, oldVal, newVal) -> {
            if (state.paused && MusicPlayer.INSTANCE.isPlaying()) {
                MusicPlayer.INSTANCE.internalPause();
            } else if (!state.paused && MusicPlayer.INSTANCE.isPaused()) {
                MusicPlayer.INSTANCE.internalResume();
            }
        });
    }

    protected abstract String getClientPlayerName();

    protected abstract void onErrorMessageUpdate(String message);

    protected void onResolvedMediaUpdate(RoomPlayerState state) {
        if (state.resolvedMedia == null) {
            if (MusicPlayer.INSTANCE.started) {
                MusicPlayer.INSTANCE.started = false;
                MusicPlayer.INSTANCE.stop();
            }
        } else {
            try {
                Music resolved = MusicJsonParsers.from(state.resolvedMedia);
                MusicPlayer.INSTANCE.resetInfo();
                MusicPlayer.INSTANCE.internalPlayMusic(resolved);
            } catch (Exception e) {
                Concerto.getLogger().error("Failed to parse resolved media", e);
                Concerto.getMinecraft().sendMessageToClientPlayer(
                        Concerto.getMinecraft().getTranslatableText("concerto.player.error", e.getMessage()), false);
            }
        }
    }


    // Server logic utils

    public void serverOnJoin(String name) {
        this.serverState.set(state -> {
            RoomPlayerState s = (RoomPlayerState) state;
            s.members.put(name, 1);
            return s;
        }, List.of(RoomPlayerState.MEMBERS));
    }

    public void serverOnQuit(String name) {
        this.serverState.set(state -> {
            RoomPlayerState s = (RoomPlayerState) state;
            s.members.remove(name);
            return s;
        }, List.of(RoomPlayerState.MEMBERS));
    }

    public void serverOnSetOp(String name, String target, Runnable onSuccessOp, Runnable onSuccessDeOp, Runnable onFail) {
        Map<String, Integer> members = this.serverGetMembers();
        if (members.getOrDefault(name, 0) < 3 || name.equals(target)) {
            if (onFail != null) onFail.run();
            return;
        }
        Integer targetPerm = members.get(target);
        int newPerm;

        if (targetPerm == null) {
            // Target not found
            if (onFail != null) onFail.run();
            return;
        } else if (targetPerm == 2) {
            newPerm = 1;
            if (onSuccessDeOp != null) onSuccessDeOp.run();
        } else {
            newPerm = 2;
            if (onSuccessOp != null) onSuccessOp.run();
        }

        this.serverState.set(state -> {
            RoomPlayerState s = (RoomPlayerState) state;
            s.members.put(target, newPerm);
            return s;
        }, List.of(RoomPlayerState.MEMBERS));
    }

    public Map<String, Integer> serverGetMembers() {
        return ((RoomPlayerState) this.serverState.get()).members;
    }

    public String serverGetOwner() {
        return ((RoomPlayerState) this.serverState.get()).owner;
    }

    public Map<String, Integer> clientGetMembers() {
        return ((RoomPlayerState) this.clientState.get()).members;
    }

    public String clientGetOwner() {
        return ((RoomPlayerState) this.clientState.get()).owner;
    }
}
