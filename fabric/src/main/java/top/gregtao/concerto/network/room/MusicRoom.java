package top.gregtao.concerto.network.room;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.ConcertoServer;
import top.gregtao.concerto.core.config.ServerConfig;
import top.gregtao.concerto.core.player.MusicPlayerHandler;
import top.gregtao.concerto.core.util.JsonUtil;
import top.gregtao.concerto.core.util.TextUtil;
import top.gregtao.concerto.network.ConcertoPayload;

import top.gregtao.concerto.core.room.AbstractMusicRoom;

import java.util.*;
import java.util.function.Supplier;

public class MusicRoom extends AbstractMusicRoom<Supplier<MinecraftServer>> {

    public static final Map<UUID, MusicRoom> ROOMS = new HashMap<>();
    public static MusicRoom CLIENT_ROOM;

    public MusicRoom(String creator, UUID uuid, Supplier<MinecraftServer> serverSupplier) {
        super(creator, uuid, serverSupplier);
    }

    public MusicRoom(String creator, Supplier<MinecraftServer> serverSupplier) {
        super(creator, serverSupplier);
    }

    public MusicRoom(UUID uuid) {
        super(uuid);
    }

    @Override
    protected void broadcastSync(JsonObject patch, Supplier<MinecraftServer> supplier) {
        MinecraftServer server = supplier.get();
        String raw = TextUtil.toBase64(patch.toString());
        this.serverGetMembers().forEach((member, permission) -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(member);
            if (player != null) {
                serverSender("SYN", raw, player);
            }
        });
    }

    @Override
    protected void sendSyncPackage(JsonObject patch) {
        if (CLIENT_ROOM == null) return;
        String raw = TextUtil.toBase64(patch.toString());
        clientSender("SYN", CLIENT_ROOM.uuid.toString() + ":" + raw);
    }

    @Override
    protected void onErrorMessageUpdate(String message) {
        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.sendMessage(Text.literal(message), false);
        }
    }

    @Override
    protected String getClientPlayerName() {
        if (MinecraftClient.getInstance().player != null) {
            return MinecraftClient.getInstance().player.getName().getString();
        }
        return null;
    }

    public static void serverSendSyncPackage(JsonObject patch, ServerPlayerEntity member) {
        serverSender("SYN", TextUtil.toBase64(patch.toString()), member);
    }

    public void serverSendToEachMember(String command, String args, String ignores, MinecraftServer server) {
        List<String> strings = Arrays.asList(ignores.split(","));
        this.serverGetMembers().forEach((member, permission) -> {
            if (!strings.contains(member)) {
                serverSender(command, args, server.getPlayerManager().getPlayer(member));
            }
        });
    }

    public void serverOnRemove(String name, MinecraftServer server) throws IllegalAccessException {
        if (this.serverGetMembers().getOrDefault(name, 0) < 2) throw new IllegalAccessException("No permission");
        this.serverSendToEachMember("REM", "", "", server);
    }

    // Override/Delegate specific server logic that needs player feedback
    public void serverOnSetOp(String name, ServerPlayerEntity player, String target) {
        super.serverOnSetOp(name, target,
                () -> player.sendMessage(Text.translatable("concerto.room.op", target)),
                () -> player.sendMessage(Text.translatable("concerto.room.de_op", target)),
                () -> player.sendMessage(Text.translatable("concerto.room.update.fail"))
        );
    }

    public static void serverSender(String command, String args, ServerPlayerEntity player) {
        if (player == null) return;
        ConcertoPayload payload = new ConcertoPayload(ConcertoPayload.Channel.MUSIC_ROOM, command + ":" + args);
        ServerPlayNetworking.send(player, payload);
    }

    public static void serverReceiver(ConcertoPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayerEntity player = context.player();
        MinecraftServer server = context.player().getServer();
        String[] args = payload.string.split(":");
        String cmd = args[0];

        try {
            switch (cmd) {
                case "CRE" -> {
                    if (!player.hasPermissionLevel(ServerConfig.INSTANCE.options.musicRoomCommandPermission)) {
                        player.sendMessage(Text.translatable("concerto.room.permission_denied"));
                        break;
                    }
                    MusicRoom room = new MusicRoom(player.getName().getString(), () -> server);
                    ROOMS.put(room.uuid, room);
                    serverSender("JOI", room.uuid.toString(), player);
                    serverSendSyncPackage(room.serverState.buildFull(), player);
                    player.sendMessage(Text.translatable("concerto.room.create", room.uuid.toString()));
                }
                case "JOI" -> {
                    UUID uuid = UUID.fromString(args[1]);
                    MusicRoom room = Objects.requireNonNull(ROOMS.get(uuid));
                    room.serverOnJoin(player.getName().getString());
                    serverSender("JOI", room.uuid.toString(), player);
                    serverSendSyncPackage(room.serverState.buildFull(), player);
                    player.sendMessage(Text.translatable("concerto.room.join", uuid.toString()));
                }
                case "REM" -> {
                    UUID uuid = UUID.fromString(args[1]);
                    MusicRoom room = Objects.requireNonNull(ROOMS.get(uuid));
                    room.serverOnRemove(player.getName().getString(), server);
                    ROOMS.remove(uuid);
                    player.sendMessage(Text.translatable("concerto.room.remove", uuid.toString()));
                }
                case "QUI" -> {
                    UUID uuid = UUID.fromString(args[1]);
                    MusicRoom room = Objects.requireNonNull(ROOMS.get(uuid));
                    room.serverOnQuit(player.getName().getString());
                    serverSender("QUI", uuid.toString(), player);
                    player.sendMessage(Text.translatable("concerto.room.quit", uuid.toString()));
                }
                case "SOP" -> {
                    UUID uuid = UUID.fromString(args[1]);
                    MusicRoom room = Objects.requireNonNull(ROOMS.get(uuid));
                    room.serverOnSetOp(player.getName().getString(), player, args[2]);
                }
                case "SYN" -> {
                    UUID uuid = UUID.fromString(args[1]);
                    MusicRoom room = Objects.requireNonNull(ROOMS.get(uuid));
                    if (room.serverGetMembers().getOrDefault(player.getName().getString(), 0) < 2) return;
                    JsonObject patch = JsonUtil.from(TextUtil.fromBase64(args[2]));
                    room.serverState.receivePatch(patch);
                }
            }
        } catch (Exception e) {
            ConcertoServer.LOGGER.warn("Server Room Error", e);
            player.sendMessage(Text.translatable("concerto.room.update.fail"));
        }
    }

    public static void clientReceiver(ConcertoPayload payload, ClientPlayNetworking.Context context) {
        MinecraftClient client = context.client();
        if (client.player == null) return;
        ClientPlayerEntity player = client.player;
        String[] args = payload.string.split(":");

        try {
            switch (args[0]) {
                case "JOI" -> {
                    UUID uuid = UUID.fromString(args[1]);
                    CLIENT_ROOM = new MusicRoom(uuid);
                    client.keyboard.setClipboard(uuid.toString());
                    ConcertoClient.clientState = uuid.compareTo(ServerMusicAgent.ROOM_UUID) == 0 ?
                            ConcertoClient.ClientState.MUSIC_AGENT : ConcertoClient.ClientState.MUSIC_ROOM;
                }
                case "SYN" -> {
                    if (CLIENT_ROOM == null) break;
                    JsonObject patch = JsonUtil.from(TextUtil.fromBase64(args[1]));
                    CLIENT_ROOM.clientState.receivePatch(patch);
                }
                case "REM" -> {
                    if (CLIENT_ROOM == null) break;
                    CLIENT_ROOM = null;
                    ConcertoClient.clientState = ConcertoClient.ClientState.LOCAL;
                    MusicPlayerHandler.INSTANCE.playNext(0);
                }
                case "QUI" -> {
                    if (CLIENT_ROOM == null) break;
                    UUID uuid = UUID.fromString(args[1]);
                    if (CLIENT_ROOM.uuid.equals(uuid)) {
                        CLIENT_ROOM = null;
                        MusicPlayerHandler.INSTANCE.playNext(0);
                    }
                    ConcertoClient.clientState = ConcertoClient.ClientState.LOCAL;
                }
            }
        } catch (Exception e) {
            ConcertoClient.LOGGER.warn("Client Room Error", e);
            player.sendMessage(Text.translatable("concerto.room.update.fail"), false);
        }
    }

    public static void clientCreate() {
        if (CLIENT_ROOM == null) clientSender("CRE", "");
    }

    public static void clientJoin(String uuid) {
        if (CLIENT_ROOM == null) clientSender("JOI", uuid);
    }

    public static void clientRemove() {
        if (CLIENT_ROOM != null) clientSender("REM", CLIENT_ROOM.uuid.toString());
    }

    public static void clientQuit() {
        if (CLIENT_ROOM == null) return;
        if (CLIENT_ROOM.permission == 3) clientRemove();
        else clientSender("QUI", CLIENT_ROOM.uuid.toString());
    }

    public static void clientSetOp(String target) {
        if (CLIENT_ROOM == null || CLIENT_ROOM.permission < 3) return;
        clientSender("SOP", CLIENT_ROOM.uuid.toString() + ":" + target);
    }

    public static void clientSender(String command, String args) {
        ConcertoPayload payload = new ConcertoPayload(ConcertoPayload.Channel.MUSIC_ROOM, command + ":" + args);
        ClientPlayNetworking.send(payload);
    }
}