package top.gregtao.concerto.network.room;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.ConcertoServer;
import top.gregtao.concerto.core.config.ServerConfig;
import top.gregtao.concerto.network.ConcertoPayload;

import top.gregtao.concerto.core.room.MusicRoom;

import java.util.*;

public class MusicRoomManager {

    public static void serverSender(String command, String payloadString, ServerPlayerEntity player) {
        if (player == null) return;
        ConcertoPayload payload = new ConcertoPayload(ConcertoPayload.Channel.MUSIC_ROOM, command + ":" + payloadString);
        ServerPlayNetworking.send(player, payload);
    }
    
    public static MusicRoom.ServerNetworkBridge createServerBridge(MinecraftServer server) {
        return new MusicRoom.ServerNetworkBridge() {
            @Override
            public void sendMessage(String targetPlayer, String translationKey, Object... args) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(targetPlayer);
                if (p != null) p.sendMessage(Text.translatable(translationKey, args));
            }

            @Override
            public void sendRoomCommand(String targetPlayer, MusicRoom.Command command, String payload) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(targetPlayer);
                if (p != null) serverSender(command.name(), payload, p);
            }

            @Override
            public boolean hasExternalPermission(String player, int level) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(player);
                return p != null && p.hasPermissionLevel(ServerConfig.INSTANCE.options.musicRoomCommandPermission);
            }
        };
    }

    public static void serverReceiver(ConcertoPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayerEntity player = context.player();
        MinecraftServer server = context.player().getServer();
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
            ClientPlayNetworking.send(payload);
        }

        @Override
        public void onErrorMessageUpdate(String message) {
            if (MinecraftClient.getInstance().player != null) {
                MinecraftClient.getInstance().player.sendMessage(Text.literal(message), false);
            }
        }
    };

    public static void clientReceiver(ConcertoPayload payload, ClientPlayNetworking.Context context) {
        MinecraftClient client = context.client();
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