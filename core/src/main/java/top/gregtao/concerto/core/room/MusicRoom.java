package top.gregtao.concerto.core.room;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.util.concurrent.ConcurrentHashMap;

public class MusicRoom {

    public static class MusicRoomState extends MusicPlayerState {
        public String resolvedMedia = null;
        public long resolvedStartTime = 0L;
        public String errorMessage = "";
        public String owner = "";
        public Map<String, Integer> members = new HashMap<>();
        public String roomName = "";
        public boolean visible = true;
        public boolean joinable = true;

        public static final Field RESOLVED_MEDIA;
        public static final Field RESOLVED_START_TIME;
        public static final Field ERROR_MESSAGE;
        public static final Field OWNER;
        public static final Field MEMBERS;
        public static final Field ROOM_NAME;
        public static final Field VISIBLE;
        public static final Field JOINABLE;

        static {
            try {
                RESOLVED_MEDIA = MusicRoomState.class.getField("resolvedMedia");
                RESOLVED_START_TIME = MusicRoomState.class.getField("resolvedStartTime");
                ERROR_MESSAGE = MusicRoomState.class.getField("errorMessage");
                OWNER = MusicRoomState.class.getField("owner");
                MEMBERS = MusicRoomState.class.getField("members");
                ROOM_NAME = MusicRoomState.class.getField("roomName");
                VISIBLE = MusicRoomState.class.getField("visible");
                JOINABLE = MusicRoomState.class.getField("joinable");
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
            c.playbackHistory = new ArrayList<>(this.playbackHistory);
            c.resolvedMedia = this.resolvedMedia;
            c.resolvedStartTime = this.resolvedStartTime;
            c.errorMessage = this.errorMessage;
            c.owner = this.owner;
            c.members = new HashMap<>(this.members);
            c.roomName = this.roomName;
            c.visible = this.visible;
            c.joinable = this.joinable;
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
        LIST,
    }

    /** A visible room's public summary, as served by {@link Command#LIST}. */
    public record RoomSummary(UUID uuid, String name, String owner, int memberCount, boolean joinable) {}

    public static final int MAX_ROOM_NAME_LENGTH = 32;

    // Concurrent: mutated from netty threads and the agent scheduler, iterated by LIST
    public static final Map<UUID, MusicRoom> ROOMS = new ConcurrentHashMap<>();
    public static MusicRoom CLIENT_ROOM;

    public final UUID uuid;
    public ServerRemoteRecord<MusicPlayerState> serverState;
    public ClientRemoteRecord<MusicPlayerState> clientState;
    public volatile int permission = 0;
    public final ServerNetworkBridge serverBridge;
    public final ClientNetworkBridge clientBridge;

    // Server constructor
    public MusicRoom(String creator, UUID uuid, String roomName, ServerNetworkBridge bridge) {
        this.uuid = uuid;
        this.serverBridge = bridge;
        this.clientBridge = null;
        MusicRoomState state = new MusicRoomState();
        state.owner = creator;
        state.members.put(creator, 3);
        state.roomName = sanitizeRoomName(roomName, creator);
        this.serverState = SyncRecord.createServerRecord(state, this::serverBroadcastSyncPackage);
    }

    // Server constructor
    public MusicRoom(String creator, UUID uuid, ServerNetworkBridge bridge) {
        this(creator, uuid, "", bridge);
    }

    // Server constructor
    public MusicRoom(String creator, String roomName, ServerNetworkBridge bridge) {
        this(creator, UUID.randomUUID(), roomName, bridge);
    }

    private static String sanitizeRoomName(String requested, String owner) {
        String name = requested == null ? "" : requested.trim();
        if (name.isEmpty()) name = owner;
        if (name.length() > MAX_ROOM_NAME_LENGTH) name = name.substring(0, MAX_ROOM_NAME_LENGTH);
        return name;
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
            // putIfAbsent: an op re-joining must not be demoted back to member
            s.members.putIfAbsent(name, 1);
            return s;
        }, List.of(MusicRoomState.MEMBERS));
    }

    public void serverOnQuit(String name) {
        this.serverState.set(state -> {
            MusicRoomState s = (MusicRoomState) state;
            boolean ownerLeaving = s.owner.equals(name);
            s.members.remove(name);
            if (ownerLeaving && !s.members.isEmpty()) {
                String successor = s.members.entrySet().stream()
                        .min(Comparator
                                .comparingInt((Map.Entry<String, Integer> entry) -> entry.getValue() >= 2 ? 0 : 1)
                                .thenComparing(Map.Entry::getKey))
                        .map(Map.Entry::getKey)
                        .orElseThrow();
                s.owner = successor;
                s.members.put(successor, 3);
            }
            return s;
        }, List.of(MusicRoomState.MEMBERS, MusicRoomState.OWNER));
        if (this.serverGetMembers().isEmpty()) {
            ROOMS.remove(this.uuid, this);
        }
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

    /** Server-side disconnect cleanup, shared by the vanilla mixin and Paper. */
    public static void serverOnPlayerDisconnect(String name, ServerNetworkBridge bridge) {
        ROOMS.forEach((uuid, room) -> {
            if (room.serverGetMembers().containsKey(name)) {
                room.serverOnQuit(name);
            }
        });
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
                    // payload = requested room name (may be empty -> owner's name)
                    MusicRoom room = new MusicRoom(sender, payload, bridge);
                    ROOMS.put(room.uuid, room);
                    bridge.sendRoomCommand(sender, Command.JOIN, room.uuid.toString());
                    bridge.sendRoomCommand(sender, Command.SYNC, room.serverState.buildFull().toString());
                    bridge.sendMessage(sender, "concerto.room.create", room.uuid.toString());
                }
                case JOIN -> {
                    UUID uuid = UUID.fromString(uuidString);
                    MusicRoom room = ROOMS.get(uuid);
                    if (room != null) {
                        MusicRoomState state = (MusicRoomState) room.serverState.get();
                        if (!state.joinable && !state.members.containsKey(sender)) {
                            bridge.sendMessage(sender, "concerto.room.join.denied");
                            break;
                        }
                        room.serverOnJoin(sender);
                        if (ServerMusicAgent.isServerAgent(uuid)) {
                            ServerMusicAgent.INSTANCE.refreshPlaybackTimestamp();
                        }
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
                case LIST -> {
                    JsonArray array = new JsonArray();
                    ROOMS.forEach((roomUuid, room) -> {
                        if (ServerMusicAgent.isServerAgent(roomUuid)) return;
                        MusicRoomState state = (MusicRoomState) room.serverState.get();
                        if (!state.visible) return;
                        JsonObject entry = new JsonObject();
                        entry.addProperty("uuid", roomUuid.toString());
                        entry.addProperty("name", state.roomName);
                        entry.addProperty("owner", state.owner);
                        entry.addProperty("members", state.members.size());
                        entry.addProperty("joinable", state.joinable);
                        array.add(entry);
                    });
                    bridge.sendRoomCommand(sender, Command.LIST, array.toString());
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

    // ---- Client-side room list cache (filled by Command.LIST replies) ----
    public static volatile List<RoomSummary> clientRoomList = List.of();
    public static volatile Runnable clientRoomListListener = null;

    public static void clientCreate(ClientNetworkBridge bridge) {
        clientCreate("", bridge);
    }

    public static void clientCreate(String roomName, ClientNetworkBridge bridge) {
        if (CLIENT_ROOM == null) bridge.sendRoomCommand(null, Command.CREATE, roomName == null ? "" : roomName);
    }

    public static void clientRequestList(ClientNetworkBridge bridge) {
        bridge.sendRoomCommand(null, Command.LIST, "");
    }

    /**
     * Updates the room's public info (any member with permission >= 2, via the
     * regular SYNC channel — the server needs no extra command for this).
     */
    public static void clientSetRoomInfo(String roomName, boolean visible, boolean joinable) {
        MusicRoom room = CLIENT_ROOM;
        if (room == null || room.permission < 2) return;
        room.clientState.set(s -> {
            MusicRoomState rs = (MusicRoomState) s;
            if (roomName != null && !roomName.isBlank()) {
                rs.roomName = roomName.trim().length() > MAX_ROOM_NAME_LENGTH
                        ? roomName.trim().substring(0, MAX_ROOM_NAME_LENGTH) : roomName.trim();
            }
            rs.visible = visible;
            rs.joinable = joinable;
            return rs;
        }, List.of(MusicRoomState.ROOM_NAME, MusicRoomState.VISIBLE, MusicRoomState.JOINABLE));
    }

    public static String clientGetRoomName() {
        MusicRoom room = CLIENT_ROOM;
        if (room == null) return "";
        return ((MusicRoomState) room.clientState.get()).roomName;
    }

    public static MusicRoomState clientGetRoomState() {
        MusicRoom room = CLIENT_ROOM;
        return room == null ? null : (MusicRoomState) room.clientState.get();
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
        bridge.sendRoomCommand(room.uuid.toString(), Command.QUIT, "");
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
                        CLIENT_ROOM = null;
                        MusicPlayerHandler.INSTANCE.restoreLocalPlaybackPaused();
                    }
                }
                case QUIT -> {
                    MusicRoom room = CLIENT_ROOM;
                    if (room != null) {
                        UUID uuid = UUID.fromString(payload);
                        if (room.uuid.equals(uuid)) {
                            CLIENT_ROOM = null;
                            MusicPlayerHandler.INSTANCE.restoreLocalPlaybackPaused();
                        }
                    }
                }
                case LIST -> {
                    List<RoomSummary> rooms = new ArrayList<>();
                    for (JsonElement element : JsonParser.parseString(payload).getAsJsonArray()) {
                        JsonObject entry = element.getAsJsonObject();
                        rooms.add(new RoomSummary(
                                UUID.fromString(entry.get("uuid").getAsString()),
                                entry.get("name").getAsString(),
                                entry.get("owner").getAsString(),
                                entry.get("members").getAsInt(),
                                entry.get("joinable").getAsBoolean()));
                    }
                    clientRoomList = List.copyOf(rooms);
                    Runnable listener = clientRoomListListener;
                    if (listener != null) listener.run();
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
            this.refreshClientPermission((MusicRoomState) state);

            // The owner is the room clock authority. When membership changes,
            // publish one fresh position for a joining listener; operators only
            // publish positions when they explicitly seek.
            if (this.permission == 3 && MusicPlayer.INSTANCE != null && MusicPlayer.INSTANCE.started) {
                clientPublishCurrentSeek(MusicPlayer.INSTANCE.getInterpolatedCurrentTimeMilliseconds());
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
                    s.setCurrentIndex(finalTarget, 25);
                    return s;
                }, List.of(MusicPlayerState.CURRENT_INDEX, MusicPlayerState.PLAYBACK_HISTORY));
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
            clientApplyResolvedStartTime((MusicRoomState) playerState);
        });

        record.addListener(MusicRoomState.ERROR_MESSAGE, (o, playerState, oldVal, newVal) -> {
            MusicRoomState state = (MusicRoomState) playerState;
            if (state.errorMessage != null && !state.errorMessage.isEmpty()) {
                this.clientBridge.onErrorMessageUpdate(state.errorMessage);
            }
        });

        record.addListener(MusicRoomState.PAUSED, (o, state, oldVal, newVal) -> {
            // Same contract as the local listener: a local force-pause
            // overrides remote resumes
            if (MusicPlayerHandler.INSTANCE != null && MusicPlayerHandler.INSTANCE.isForcePaused() && !state.paused) {
                return;
            }
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

    private void refreshClientPermission(MusicRoomState state) {
        String name = Concerto.getCoreBridge().getClientPlayerName();
        if (name == null) return;
        this.permission = state.members.getOrDefault(name, 0);
    }

    /** Applies a timestamp that may have arrived while the resolved track was loading. */
    public static void clientApplyResolvedStartTime() {
        MusicRoom room = CLIENT_ROOM;
        if (room != null) clientApplyResolvedStartTime((MusicRoomState) room.clientState.get());
    }

    private static void clientApplyResolvedStartTime(MusicRoomState state) {
        if (state.resolvedMedia == null || !MusicPlayer.INSTANCE.started) return;
        long currentTime = MusicPlayer.INSTANCE.getInterpolatedCurrentTimeMilliseconds();
        if (Math.abs(currentTime - state.resolvedStartTime) > 750L) {
            MusicPlayer.INSTANCE.seekToMillisecondsAsync(state.resolvedStartTime, false);
        }
    }

    protected void clientOnResolvedMediaUpdate(MusicRoomState state) {
        if (state.resolvedMedia == null) {
            if (MusicPlayer.INSTANCE.started) {
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
