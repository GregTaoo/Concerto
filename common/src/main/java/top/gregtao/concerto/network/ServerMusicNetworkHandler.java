package top.gregtao.concerto.network;

import net.minecraft.world.entity.player.Player;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import top.gregtao.concerto.ConcertoServer;
import top.gregtao.concerto.bridge.MinecraftServerBridge;
import top.gregtao.concerto.core.api.MusicJsonParsers;
import top.gregtao.concerto.command.ConcertoServerCommand;
import top.gregtao.concerto.core.config.PresetPlaylistsConfig;
import top.gregtao.concerto.core.config.ServerConfig;
import top.gregtao.concerto.core.music.meta.music.MusicMetaData;
import top.gregtao.concerto.network.room.MusicRoomManager;
import top.gregtao.concerto.network.room.ServerMusicAgentManager;
import top.gregtao.concerto.util.CommandUtil;

import java.util.*;

public class ServerMusicNetworkHandler {

    public static final int WAIT_LIST_MAX_SIZE = 300;

    public static void register(MinecraftServerBridge bridge) {
        bridge.registerServerPayloadReceiver(ConcertoPayload.ID, ConcertoPayload.CODEC, ServerMusicNetworkHandler::generalReceiver);
    }

    public static Map<UUID, MusicDataPacket> WAIT_AUDITION = new HashMap<>();
    public static void removeFirst() {
        Iterator<Map.Entry<UUID, MusicDataPacket>> iterator = WAIT_AUDITION.entrySet().iterator();
        if (!iterator.hasNext()) return;
        Map.Entry<UUID, MusicDataPacket> entry = iterator.next();
        sendS2CAuditionSyncData(entry.getKey(), entry.getValue(), true);
        iterator.remove();
    }
    
    public static void generalReceiver(ConcertoPayload payload, MinecraftServerBridge.NetworkingContext context) {
        switch (payload.channel) {
            case MUSIC_DATA -> musicDataReceiver(payload, context);
            case MUSIC_ROOM -> MusicRoomManager.serverReceiver(payload, context);
            case MUSIC_AGENT -> ServerMusicAgentManager.serverReceiver(payload, context);
        }
    }

    public static void passAudition(@Nullable Player auditor, UUID uuid) {
        if (WAIT_AUDITION.containsKey(uuid)) {
            MusicDataPacket packet = WAIT_AUDITION.get(uuid);
            WAIT_AUDITION.remove(uuid);
            boolean success = sendS2CMusicData(packet, true);
            if (auditor != null) {
                if (success) {
                    auditor.displayClientMessage(Component.translatable("concerto.audit.pass", packet.from, packet.music.getMeta().title()), false);
                } else {
                    auditor.displayClientMessage(Component.translatable("concerto.share.s2c_failed", uuid.toString()), false);
                }
                ConcertoServer.LOGGER.info("Auditor {} passed request from {}: {} to {}",
                        auditor.getName().getString(), packet.from, packet.music.getMeta().title(), packet.to);
            }
            ConcertoServer.LOGGER.info("Auditor ??? passed request from {}: {} to {}",
                    packet.from, packet.music.getMeta().title(), packet.to);
            sendS2CAuditionSyncData(uuid, packet, true);
        } else if (auditor != null) {
            auditor.displayClientMessage(Component.translatable("concerto.audit.uuid_not_found"), false);
        }
    }

    public static void rejectAll(@Nullable Player auditor) {
        WAIT_AUDITION.forEach((uuid, packet) -> {
            Player player = packet.server.getPlayerList().getPlayerByName(packet.from);
            String title = packet.music.getMeta().title();
            if (player != null) player.displayClientMessage(Component.translatable("concerto.share.rejected", title), false);
        });
        WAIT_AUDITION.clear();
        if (auditor != null) auditor.displayClientMessage(Component.translatable("concerto.audit.reject", "ALL", "ALL"), false);
        ConcertoServer.LOGGER.info("Auditor {} rejected all request", auditor == null ? "?" : auditor.getName().getString());
    }

    public static void rejectAudition(@Nullable Player auditor, UUID uuid) {
        if (WAIT_AUDITION.containsKey(uuid)) {
            MusicDataPacket packet = WAIT_AUDITION.get(uuid);
            WAIT_AUDITION.remove(uuid);
            Player player = packet.server.getPlayerList().getPlayerByName(packet.from);
            String title = packet.music.getMeta().title();
            if (player != null) player.displayClientMessage(Component.translatable("concerto.share.rejected", title), false);
            if (auditor != null) auditor.displayClientMessage(Component.translatable(
                    "concerto.audit.reject", player == null ? "an unknown player" : player.getName().getString(), title), false);
            ConcertoServer.LOGGER.info("Auditor {} rejected request from {}: {} to {}",
                    auditor == null ? "???" : auditor.getName().getString(), packet.from, title, packet.to);
            sendS2CAuditionSyncData(uuid, packet, true);
        } else if (auditor != null) {
            auditor.displayClientMessage(Component.translatable("concerto.audit.uuid_not_found"), false);
        }
    }

    public static void sendAuditionSyncPacket(UUID uuid, ServerPlayer player, MusicDataPacket packet, boolean isDelete) {
        ConcertoPayload payload = new ConcertoPayload(ConcertoPayload.Channel.AUDITION_SYNC, (isDelete ? "DEL;" : "ADD;") + uuid + ";" +
                (isDelete ? "QwQ" : Objects.requireNonNull(MusicJsonParsers.to(packet.music)).toString()));
        ConcertoServer.getBridge().sendPayload(player, payload);
    }

    public static void sendS2CAuditionSyncData(UUID uuid, MusicDataPacket packet, boolean isDelete) {
        PlayerList playerManager = packet.server.getPlayerList();
        for (ServerPlayer player : playerManager.getPlayers()) {
            if (player.hasPermissions(packet.server.getOperatorUserPermissionLevel())) {
                sendAuditionSyncPacket(uuid, player, packet, isDelete);
            }
        }
    }

    public static void sendS2CAllAuditionData(ServerPlayer player) {
        WAIT_AUDITION.forEach((uuid, packet) -> sendAuditionSyncPacket(uuid, player, packet, false));
    }

    public static void sendS2CPresetRadiosPacket(ServerPlayer player) {
        ConcertoPayload payload = new ConcertoPayload(ConcertoPayload.Channel.PRESET_RADIOS,
                PresetPlaylistsConfig.PRESET_RADIOS.toString());
        ConcertoServer.getBridge().sendPayload(player, payload);
    }

    public static boolean sendS2CMusicData(MusicDataPacket packet, boolean audit) {
        if (!packet.isS2C) {
            throw new RuntimeException("Not an S2C music data packet");
        } else if (packet.server == null || !packet.server.isRunning()) {
            throw new RuntimeException("Server not found or not running");
        }
        ConcertoPayload payload = packet.toPacket();
        PlayerList playerManager = packet.server.getPlayerList();
        ConcertoServer.LOGGER.info("Trying to send music request to {}", packet.to);
        if (packet.to.equals("@a")) {
            playerManager.getPlayers().forEach(serverPlayer ->
                    ConcertoServer.getBridge().sendPayload(serverPlayer, payload));
        } else {
            ServerPlayer target = playerManager.getPlayerByName(packet.to);
            ServerPlayer from = playerManager.getPlayerByName(packet.from);
            if (target == null) {
                if (from != null) {
                    from.sendSystemMessage(Component.translatable("concerto.share.s2c_player_not_found", packet.to));
                }
                ConcertoServer.LOGGER.warn("Target not found, failed to send.");
                return false;
            } else {
                ConcertoServer.getBridge().sendPayload(target, payload);
                if (audit && from != null) {
                    from.sendSystemMessage(Component.translatable("concerto.share.audition_passed",
                            packet.to, packet.music.getMeta().title()));
                }
            }
        }
        ConcertoServer.LOGGER.info("Successfully.");
        return true;
    }

    public static void musicDataReceiver(ConcertoPayload payload, MinecraftServerBridge.NetworkingContext context) {
        ServerPlayer player = context.player();
        MinecraftServer server = context.server();
        try {
            MusicDataPacket packet = MusicDataPacket.fromPacket(payload, false);
            if (packet != null && packet.music != null && server != null) {
                PlayerList playerManager = server.getPlayerList();
                if (!playerExist(playerManager, packet.to)) {
                    player.sendSystemMessage(Component.translatable("concerto.share.c2s_player_not_found", packet.to));
                    ConcertoServer.LOGGER.info("Received a music request from {} to an unknown player", player.getName().getString());
                } else {
                    packet.from = player.getName().getString();
                    packet.isS2C = true;
                    packet.server = server;
                    boolean audit = ServerConfig.INSTANCE.options.auditionRequired && packet.to.equals("@a");
                    boolean success = true;
                    if (audit) {
                        UUID uuid = UUID.randomUUID();
                        for (ServerPlayer player1 : playerManager.getPlayers()) {
                            if (player1.hasPermissions(server.getOperatorUserPermissionLevel())) {
                                player1.sendSystemMessage(CommandUtil.PAGE_SPLIT);
                                player1.sendSystemMessage(ConcertoServerCommand.chatMessageBuilder(
                                        uuid, packet.from, packet.music.getMeta().title()
                                ));
                                player1.sendSystemMessage(CommandUtil.PAGE_SPLIT);
                                sendAuditionSyncPacket(uuid, player1, packet, false);
                            }
                        }
                        WAIT_AUDITION.put(uuid, packet);
                        if (WAIT_AUDITION.size() > WAIT_LIST_MAX_SIZE) {
                            removeFirst();
                        }
                    } else {
                        success = sendS2CMusicData(packet, false);
                    }
                    player.sendSystemMessage(Component.translatable("concerto.share." + (success ? "success" : "failed")
                            + (audit ? "_audit" : ""), packet.music.getMeta().title()));
                    MusicMetaData meta = packet.music.getMeta();
                    ConcertoServer.LOGGER.info("Received a music request {} - {} from {} to {}",
                            meta.getSource(), meta.title(), player.getName().getString(), packet.to);
                }
            } else {
                player.sendSystemMessage(Component.translatable("concerto.share.error"));
                ConcertoServer.LOGGER.warn("Received an unknown music data packet from {}", player.getName().getString());
            }
        } catch (Exception e) {
            ConcertoServer.LOGGER.warn("Received an unsafe music data packet from {}", player.getName().getString());
            // Ignore unsafe music
        }
    }

    public static void playerJoinHandshake(ServerPlayer player) {
        ConcertoPayload payload = new ConcertoPayload(ConcertoPayload.Channel.HANDSHAKE,
                ConcertoPayload.HANDSHAKE_STRING + "CallJoin:" + player.getName().getString()
                        + (ServerConfig.INSTANCE.options.serverMusicAgent && ServerConfig.INSTANCE.options.agentInviteWhenJoin ? ":Invite" : ""));
        ConcertoServer.getBridge().sendPayload(player, payload);
        sendS2CAllAuditionData(player);
        sendS2CPresetRadiosPacket(player);
    }

    public static boolean playerExist(PlayerList manager, String name) {
        return name.equals("@a") || (manager.getPlayerByName(name) != null);
    }
}
