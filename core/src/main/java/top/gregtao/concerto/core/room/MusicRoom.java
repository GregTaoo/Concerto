package top.gregtao.concerto.core.room;

import com.google.gson.JsonObject;
import top.gregtao.concerto.core.Concerto;
import top.gregtao.concerto.core.api.DynamicPath;
import top.gregtao.concerto.core.api.MusicJsonParsers;
import top.gregtao.concerto.core.enums.OrderType;
import top.gregtao.concerto.core.event.ConcertoEvents;
import top.gregtao.concerto.core.music.Music;
import top.gregtao.concerto.core.music.SharedMusic;
import top.gregtao.concerto.core.network.ClientRemoteRecord;
import top.gregtao.concerto.core.network.ServerRemoteRecord;
import top.gregtao.concerto.core.network.SyncRecord;
import top.gregtao.concerto.core.player.ConcertoPlayerList;
import top.gregtao.concerto.core.player.MusicPlayer;
import top.gregtao.concerto.core.player.MusicPlayerHandler;
import top.gregtao.concerto.core.player.MusicPlayerState;
import top.gregtao.concerto.core.room.agent.ServerMusicAgent;
import top.gregtao.concerto.core.util.JsonUtil;

import java.lang.reflect.Field;
import java.util.*;

public class MusicRoom {

    public static class MusicRoomState extends MusicPlayerState {
        public String resolvedMedia = null;
        public long resolvedStartTime = 0L;
        public String errorMessage = "";
        public String owner = "";
        public Map<String, Integer> members = new HashMap<>();

        public static final Field RESOLVED_MEDIA;
        public static final Field RESOLVED_START_TIME;
        public static final Field ERROR_MESSAGE;
        public static final Field OWNER;
        public static final Field MEMBERS;

        static {
            try {
                RESOLVED_MEDIA = MusicRoomState.class.getField("resolvedMedia");
                RESOLVED_START_TIME = MusicRoomState.class.getField("resolvedStartTime");
                ERROR_MESSAGE = MusicRoomState.class.getField("errorMessage");
                OWNER = MusicRoomState.class.getField("owner");
                MEMBERS = MusicRoomState.class.getField("members");
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }

        public MusicRoomState() {
            super();
        }

        @Override
        public MusicRoomState copy() {
            MusicRoomState c = new MusicRoomState();
            c.musicList = this.musicList.copy();
            c.currentIndex = this.currentIndex;
            c.orderType = this.orderType;
            c.paused = this.paused;
            c.resolvedMedia = this.resolvedMedia;
            c.resolvedStartTime = this.resolvedStartTime;
            c.errorMessage = this.errorMessage;
            c.owner = this.owner;
            c.members = new HashMap<>(this.members);
            return c;
        }
    }

    public enum Command {
        CREATE,
        JOIN,
        REMOVE,
        QUIT,
        SYNC,
        SET_OP,
    }

    public static final Map<UUID, MusicRoom> ROOMS = new HashMap<>();
    public static MusicRoom CLIENT_ROOM;

    public final UUID uuid;
    public ServerRemoteRecord<MusicPlayerState> serverState;
    public ClientRemoteRecord<MusicPlayerState> clientState;
    public int permission = 0;
    public final ServerNetworkBridge serverBridge;
    public final ClientNetworkBridge clientBridge;

    // Server constructor
    public MusicRoom(String creator, UUID uuid, ServerNetworkBridge bridge) {
        this.uuid = uuid;
        this.serverBridge = bridge;
        this.clientBridge = null;
        MusicRoomState state = new MusicRoomState();
        state.owner = creator;
        state.members.put(creator, 3);
        this.serverState = SyncRecord.createServerRecord(state, this::serverBroadcastSyncPackage);
    }

    // Server constructor
    public MusicRoom(String creator, ServerNetworkBridge bridge) {
        this(creator, UUID.randomUUID(), bridge);
    }

    // Client constructor
    public MusicRoom(UUID uuid, ClientNetworkBridge bridge) {
        this.uuid = uuid;
        this.serverBridge = null;
        this.clientBridge = bridge;
        this.clientState = SyncRecord.createClientRecord(new MusicRoomState(), MusicRoom::clientSendSyncPacket);
        this.registerClientListeners(this.clientState);
    }

    // Server side
    public interface ServerNetworkBridge {
        void sendMessage(String targetPlayer, String translationKey, Object... args);

        void sendRoomCommand(String targetPlayer, Command command, String payload);

        boolean hasExternalPermission(String player, int level);
    }

    public void serverBroadcastSyncPackage(JsonObject patch) {
        this.serverGetMembers().forEach((member, permission) ->
                this.serverBridge.sendRoomCommand(member, Command.SYNC, patch.toString()));
    }

    public void serverOnJoin(String name) {
        this.serverState.set(state -> {
            MusicRoomState s = (MusicRoomState) state;
            s.members.put(name, 1);
            return s;
        }, List.of(MusicRoomState.MEMBERS));
    }

    public void serverOnQuit(String name) {
        this.serverState.set(state -> {
            MusicRoomState s = (MusicRoomState) state;
            s.members.remove(name);
            return s;
        }, List.of(MusicRoomState.MEMBERS));
    }

    public void serverOnSetOp(String name, String target) {
        Map<String, Integer> members = this.serverGetMembers();
        if (members.getOrDefault(name, 0) < 3 || name.equals(target)) {
            this.serverBridge.sendMessage(name, "concerto.room.update.fail");
            return;
        }
        Integer targetPerm = members.get(target);
        int newPerm;
        if (targetPerm == null) {
            // Target not found
            this.serverBridge.sendMessage(name, "concerto.room.update.fail");
            return;
        } else if (targetPerm == 2) {
            newPerm = 1;
            this.serverBridge.sendMessage(name, "concerto.room.de_op", target);
            this.serverBridge.sendMessage(target, "concerto.room.de_op.notify");
        } else {
            newPerm = 2;
            this.serverBridge.sendMessage(name, "concerto.room.op", target);
            this.serverBridge.sendMessage(target, "concerto.room.op.notify");
        }

        this.serverState.set(state -> {
            MusicRoomState s = (MusicRoomState) state;
            s.members.put(target, newPerm);
            return s;
        }, List.of(MusicRoomState.MEMBERS));
    }

    public Map<String, Integer> serverGetMembers() {
        return ((MusicRoomState) this.serverState.get()).members;
    }

    public String serverGetOwner() {
        return ((MusicRoomState) this.serverState.get()).owner;
    }

    public static void handleServerCommand(String uuidString, String commandString, String payload,
                                           String sender, ServerNetworkBridge bridge) {
        try {
            Command command = Command.valueOf(commandString.toUpperCase());
            switch (command) {
                case CREATE -> {
                    if (!bridge.hasExternalPermission(sender, 2)) {
                        bridge.sendMessage(sender, "concerto.room.permission_denied");
                        break;
                    }
                    MusicRoom room = new MusicRoom(sender, bridge);
                    ROOMS.put(room.uuid, room);
                    bridge.sendRoomCommand(sender, Command.JOIN, room.uuid.toString());
                    bridge.sendRoomCommand(sender, Command.SYNC, room.serverState.buildFull().toString());
                    bridge.sendMessage(sender, "concerto.room.create", room.uuid.toString());
                }
                case JOIN -> {
                    UUID uuid = UUID.fromString(uuidString);
                    MusicRoom room = ROOMS.get(uuid);
                    if (room != null) {
                        room.serverOnJoin(sender);
                        bridge.sendRoomCommand(sender, Command.JOIN, room.uuid.toString());
                        bridge.sendRoomCommand(sender, Command.SYNC, room.serverState.buildFull().toString());
                        if (ServerMusicAgent.isServerAgent(uuid)) {
                            bridge.sendMessage(sender, "concerto.agent.join");
                        } else {
                            bridge.sendMessage(sender, "concerto.room.join", uuid.toString());
                        }
                    } else {
                        bridge.sendMessage(sender, "concerto.room.join.fail");
                    }
                }
                case REMOVE -> {
                    UUID uuid = UUID.fromString(uuidString);
                    MusicRoom room = ROOMS.get(uuid);
                    if (room != null) {
                        if (room.serverGetMembers().getOrDefault(sender, 0) < 3) {
                            bridge.sendMessage(sender, "concerto.room.update.fail");
                            break;
                        }
                        room.serverGetMembers().keySet().forEach(member -> {
                            bridge.sendMessage(member, "concerto.room.remove", uuid.toString());
                            bridge.sendRoomCommand(member, Command.REMOVE, "");
                        });
                        ROOMS.remove(uuid);
                    } else {
                        bridge.sendMessage(sender, "concerto.room.remove.fail");
                    }
                }
                case QUIT -> {
                    UUID uuid = UUID.fromString(uuidString);
                    MusicRoom room = ROOMS.get(uuid);
                    if (room != null) {
                        room.serverOnQuit(sender);
                        bridge.sendRoomCommand(sender, Command.QUIT, uuid.toString());
                        if (ServerMusicAgent.isServerAgent(uuid)) {
                            bridge.sendMessage(sender, "concerto.agent.quit");
                        } else {
                            bridge.sendMessage(sender, "concerto.room.quit", uuid.toString());
                        }
                    } else {
                        bridge.sendMessage(sender, "concerto.room.quit.fail");
                    }
                }
                case SET_OP -> {
                    UUID uuid = UUID.fromString(uuidString);
                    MusicRoom room = ROOMS.get(uuid);
                    if (room != null) {
                        room.serverOnSetOp(sender, payload);
                    }
                }
                case SYNC -> {
                    UUID uuid = UUID.fromString(uuidString);
                    MusicRoom room = ROOMS.get(uuid);
                    if (room != null) {
                        if (room.serverGetMembers().getOrDefault(sender, 0) < 2) return;
                        JsonObject patch = JsonUtil.from(payload);
                        room.serverState.receivePatch(patch);
                    }
                }
            }
        } catch (Exception e) {
            Concerto.getLogger().warn("Server Room Error", e);
            bridge.sendMessage(sender, "concerto.room.update.fail");
        }
    }

    // Client side
    public interface ClientNetworkBridge {
        void sendRoomCommand(String uuid, Command command, String payload);

        void onErrorMessageUpdate(String message);
    }

    public enum ClientState {
        LOCAL,
        MUSIC_ROOM,
        MUSIC_AGENT
    }

    public static ClientState clientGetState() {
        if (MusicRoom.CLIENT_ROOM != null) {
            if (MusicRoom.CLIENT_ROOM.uuid.equals(ServerMusicAgent.ROOM_UUID)) {
                return ClientState.MUSIC_AGENT;
            }
            return ClientState.MUSIC_ROOM;
        }
        return ClientState.LOCAL;
    }

    public static void clientSendSyncPacket(JsonObject patch) {
        if (MusicRoom.CLIENT_ROOM == null || MusicRoom.CLIENT_ROOM.permission < 2) return;
        MusicRoom.CLIENT_ROOM.clientBridge.sendRoomCommand(
                MusicRoom.CLIENT_ROOM.uuid.toString(), Command.SYNC, patch.toString());
    }

    public Map<String, Integer> clientGetMembers() {
        return ((MusicRoomState) this.clientState.get()).members;
    }

    public String clientGetOwner() {
        return ((MusicRoomState) this.clientState.get()).owner;
    }

    public static void clientCreate(ClientNetworkBridge bridge) {
        if (CLIENT_ROOM == null) bridge.sendRoomCommand(null, Command.CREATE, "");
    }

    public static void clientJoin(String uuid, ClientNetworkBridge bridge) {
        if (CLIENT_ROOM == null) bridge.sendRoomCommand(uuid, Command.JOIN, "");
    }

    public static void clientRemove(ClientNetworkBridge bridge) {
        MusicRoom room = CLIENT_ROOM;
        if (room != null) bridge.sendRoomCommand(room.uuid.toString(), Command.REMOVE, "");
    }

    public static void clientQuit(ClientNetworkBridge bridge) {
        MusicRoom room = CLIENT_ROOM;
        if (room == null) return;
        if (room.permission == 3) {
            clientRemove(bridge);
        } else {
            bridge.sendRoomCommand(room.uuid.toString(), Command.QUIT, "");
        }
    }

    public static void clientSetOp(String target, ClientNetworkBridge bridge) {
        MusicRoom room = CLIENT_ROOM;
        if (room == null || room.permission < 3) return;
        bridge.sendRoomCommand(room.uuid.toString(), Command.SET_OP, target);
    }

    public static void handleClientCommand(String commandString, String payload, ClientNetworkBridge bridge) {
        try {
            Command command = Command.valueOf(commandString.toUpperCase());
            switch (command) {
                case JOIN -> {
                    UUID uuid = UUID.fromString(payload);
                    CLIENT_ROOM = new MusicRoom(uuid, bridge);
                    MusicPlayerHandler.INSTANCE.tryForcePause(false);
                    Concerto.getCoreBridge().setClientClipboard(uuid.toString());
                }
                case SYNC -> {
                    MusicRoom room = CLIENT_ROOM;
                    if (room != null) {
                        JsonObject patch = JsonUtil.from(payload);
                        room.clientState.receivePatch(patch);
                    }
                }
                case REMOVE -> {
                    MusicRoom room = CLIENT_ROOM;
                    if (room != null) {
                        MusicPlayerHandler.INSTANCE.playNextAsync(0);
                        CLIENT_ROOM = null;
                    }
                }
                case QUIT -> {
                    MusicRoom room = CLIENT_ROOM;
                    if (room != null) {
                        UUID uuid = UUID.fromString(payload);
                        if (room.uuid.equals(uuid)) {
                            MusicPlayerHandler.INSTANCE.playNextAsync(0);
                            CLIENT_ROOM = null;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Concerto.getLogger().warn("Client Room Error", e);
            Concerto.getCoreBridge().sendTranslatableToClientPlayer("concerto.room.update.fail", false);
        }
    }

    // Call this if implementing a client-side room
    protected void registerClientListeners(ClientRemoteRecord<MusicPlayerState> record) {
        record.addListener(MusicRoomState.MEMBERS, (o, state, oldVal, newVal) -> {
            MusicRoomState rs = (MusicRoomState) state;
            String name = Concerto.getCoreBridge().getClientPlayerName();
            if (name != null) {
                this.permission = rs.members.getOrDefault(name, 0);
            }
        });

        record.addListener(MusicRoomState.MUSIC_LIST, (o, state, oldVal, newVal) -> {
            ConcertoEvents.ON_MUSIC_LIST_UPDATE.emit();

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

        record.addListener(MusicRoomState.CURRENT_INDEX, (o, playerState, oldVal, newVal) -> {
            MusicRoomState state = (MusicRoomState) playerState;
            int permission = this.permission;

            if (permission == 3) {
                UUID current = state.currentIndex;
                if (current != null && state.musicList.contains(current)) {
                    Music music = state.musicList.get(current);
                    if (music instanceof DynamicPath dp) {
                        String rawSm = buildResolvedMediaPayload(music, dp);
                        if (rawSm != null) {
                            o.set(s -> {
                                MusicRoomState rs = (MusicRoomState) s;
                                rs.resolvedMedia = rawSm;
                                rs.resolvedStartTime = 0L;
                                return rs;
                            }, List.of(MusicRoomState.RESOLVED_MEDIA, MusicRoomState.RESOLVED_START_TIME));
                            return;
                        }
                    }
                    String error = Concerto.getCoreBridge().getTranslatable("concerto.player.unable",
                            music.getMeta().title(), music.getMeta().author(), music.getMeta().getSource());
                    o.set(s -> {
                        MusicRoomState rs = (MusicRoomState) s;
                        rs.errorMessage = error;
                        return rs;
                    }, List.of(MusicRoomState.ERROR_MESSAGE));
                } else if (state.resolvedMedia != null) {
                    o.set(s -> {
                        MusicRoomState rs = (MusicRoomState) s;
                        rs.resolvedMedia = null;
                        rs.resolvedStartTime = 0L;
                        return rs;
                    }, List.of(MusicRoomState.RESOLVED_MEDIA, MusicRoomState.RESOLVED_START_TIME));
                }
            }
        });

        record.addListener(MusicRoomState.RESOLVED_MEDIA, (o, playerState, oldVal, newVal) -> {
            MusicRoomState state = (MusicRoomState) playerState;
            clientOnResolvedMediaUpdate(state);
        });

        record.addListener(MusicRoomState.RESOLVED_START_TIME, (o, playerState, oldVal, newVal) -> {
            MusicRoomState state = (MusicRoomState) playerState;
            if (state.resolvedMedia == null || !MusicPlayer.INSTANCE.started) {
                return;
            }
            long currentTime = MusicPlayer.INSTANCE.getInterpolatedCurrentTimeMilliseconds();
            if (Math.abs(currentTime - state.resolvedStartTime) > 750L) {
                MusicPlayer.INSTANCE.seekToMillisecondsAsync(state.resolvedStartTime, false);
            }
        });

        record.addListener(MusicRoomState.ERROR_MESSAGE, (o, playerState, oldVal, newVal) -> {
            MusicRoomState state = (MusicRoomState) playerState;
            if (state.errorMessage != null && !state.errorMessage.isEmpty()) {
                this.clientBridge.onErrorMessageUpdate(state.errorMessage);
            }
        });

        record.addListener(MusicRoomState.PAUSED, (o, state, oldVal, newVal) -> {
            // The room state is authoritative; the engine pause is idempotent,
            // so apply it even while a track is still loading.
            if (state.paused) {
                MusicPlayer.INSTANCE.internalPause();
            } else {
                MusicPlayer.INSTANCE.internalResume();
            }
        });

        record.addListener(MusicRoomState.ORDER_TYPE, (o, state, oldVal, newVal) ->
                ConcertoEvents.ON_PLAYER_ORDER_UPDATE.emit(state.orderType));
    }

    protected void clientOnResolvedMediaUpdate(MusicRoomState state) {
        if (state.resolvedMedia == null) {
            if (MusicPlayer.INSTANCE.started) {
                MusicPlayer.INSTANCE.started = false;
                MusicPlayer.INSTANCE.stop();
            }
        } else {
            try {
                Music resolved = MusicJsonParsers.from(state.resolvedMedia);
                if (resolved instanceof SharedMusic sharedMusic) {
                    sharedMusic.startTime = state.resolvedStartTime;
                }
                MusicPlayer.INSTANCE.resetInfo();
                MusicPlayer.INSTANCE.internalPlayMusic(resolved);
            } catch (Exception e) {
                Concerto.getLogger().error("Failed to parse resolved media", e);
                Concerto.getCoreBridge().sendTranslatableToClientPlayer("concerto.player.error", false, e.getMessage());
            }
        }
    }

    public static void clientPublishCurrentSeek(long milliseconds) {
        MusicRoom room = CLIENT_ROOM;
        if (room == null || room.permission < 2 || room.uuid.equals(ServerMusicAgent.ROOM_UUID)) {
            return;
        }
        MusicPlayerState state = room.clientState.get();
        UUID current = state.currentIndex;
        if (current == null || !state.musicList.contains(current)) {
            return;
        }
        Music music = state.musicList.get(current);
        if (!(music instanceof DynamicPath dynamicPath)) {
            return;
        }
        if (buildResolvedMediaPayload(music, dynamicPath) == null) {
            return;
        }
        room.clientState.set(s -> {
            MusicRoomState rs = (MusicRoomState) s;
            rs.resolvedStartTime = Math.max(0L, milliseconds);
            return rs;
        }, List.of(MusicRoomState.RESOLVED_START_TIME));
    }

    private static String buildResolvedMediaPayload(Music music, DynamicPath dynamicPath) {
        try {
            SharedMusic sharedMusic = new SharedMusic(dynamicPath.getLastRawPath(), music.getMeta(),
                    dynamicPath.getLastLyrics(), dynamicPath.getLastSubLyrics());
            sharedMusic.setMusicMeta(music.getMeta());
            return MusicJsonParsers.to(sharedMusic).toString();
        } catch (Exception e) {
            Concerto.getLogger().warn("Failed to build room resolved media", e);
            return null;
        }
    }

}
