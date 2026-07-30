package top.gregtao.concerto.network.room;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.ConcertoServer;
import top.gregtao.concerto.bridge.MinecraftServerBridge;
import top.gregtao.concerto.core.config.ServerConfig;
import top.gregtao.concerto.core.room.MusicRoom;
import top.gregtao.concerto.network.ConcertoPayload;

public class MusicRoomManager {

    public static void serverSender(String command, String payloadString, ServerPlayer player) {
        if (player == null) return;
        ConcertoPayload payload = new ConcertoPayload(ConcertoPayload.Channel.MUSIC_ROOM, command + ":" + payloadString);
        ConcertoServer.getBridge().sendPayload(player, payload);
    }

    public static MusicRoom.ServerNetworkBridge createServerBridge(MinecraftServer server) {
        return new MusicRoom.ServerNetworkBridge() {
            @Override
            public void sendMessage(String targetPlayer, String translationKey, Object... args) {
                ServerPlayer p = server.getPlayerList().getPlayerByName(targetPlayer);
                if (p != null) p.sendSystemMessage(Component.translatable(translationKey, args));
            }

            @Override
            public void sendRoomCommand(String targetPlayer, MusicRoom.Command command, String payload) {
                ServerPlayer p = server.getPlayerList().getPlayerByName(targetPlayer);
                if (p != null) serverSender(command.name(), payload, p);
            }

            @Override
            public boolean hasExternalPermission(String player, int level) {
                ServerPlayer p = server.getPlayerList().getPlayerByName(player);
                Permission permission = new Permission.HasCommandLevel(PermissionLevel.byId(
                        ServerConfig.INSTANCE.options.musicRoomCommandPermission));
                return p != null && p.permissions().hasPermission(permission);
            }
        };
    }

    public static void serverReceiver(ConcertoPayload payload, MinecraftServerBridge.NetworkingContext context) {
        ServerPlayer player = context.player();
        MinecraftServer server = context.server();
        String[] args = payload.string.split(":", 3);
        if (args.length != 3) {
            ConcertoServer.LOGGER.error("Invalid arguments for server receiver: {}", payload);
            return;
        }

        MusicRoom.handleServerCommand(args[0], args[1], args[2],
                player.getName().getString(), createServerBridge(server));
    }

    private static final MusicRoom.ClientNetworkBridge CLIENT_BRIDGE = new MusicRoom.ClientNetworkBridge() {
        @Override
        public void sendRoomCommand(String uuid, MusicRoom.Command command, String payloadString) {
            ConcertoPayload payload = new ConcertoPayload(
                    ConcertoPayload.Channel.MUSIC_ROOM, uuid + ":" + command + ":" + payloadString);
            ConcertoClient.getBridge().sendPayload(payload);
        }

        @Override
        public void onErrorMessageUpdate(String message) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(Component.literal(message));
            }
        }
    };

    public static void clientReceiver(ConcertoPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            ConcertoClient.LOGGER.error("Client player not found.");
            return;
        }
        String[] args = payload.string.split(":", 2);
        if (args.length != 2) {
            ConcertoClient.LOGGER.error("Invalid arguments for client receiver: {}", payload);
            return;
        }

        MusicRoom.handleClientCommand(args[0], args[1], CLIENT_BRIDGE);
    }

    public static void clientCreate() {
        MusicRoom.clientCreate(CLIENT_BRIDGE);
    }

    public static void clientCreate(String roomName) {
        MusicRoom.clientCreate(roomName, CLIENT_BRIDGE);
    }

    public static void clientRequestList() {
        MusicRoom.clientRequestList(CLIENT_BRIDGE);
    }

    public static void clientJoin(String uuid) {
        MusicRoom.clientJoin(uuid, CLIENT_BRIDGE);
    }

    public static void clientRemove() {
        MusicRoom.clientRemove(CLIENT_BRIDGE);
    }

    public static void clientQuit() {
        MusicRoom.clientQuit(CLIENT_BRIDGE);
    }

    public static void clientSetOp(String target) {
        MusicRoom.clientSetOp(target, CLIENT_BRIDGE);
    }
}