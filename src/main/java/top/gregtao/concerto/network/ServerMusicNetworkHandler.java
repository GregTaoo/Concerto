package top.gregtao.concerto.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.JukeboxBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import top.gregtao.concerto.ConcertoServer;
import top.gregtao.concerto.api.MusicJsonParsers;
import top.gregtao.concerto.command.AuditCommand;
import top.gregtao.concerto.config.ServerConfig;
import top.gregtao.concerto.music.meta.music.MusicMetaData;
import top.gregtao.concerto.player.exp.ConcertoJukeboxBlockEntity;
import top.gregtao.concerto.util.TextUtil;

import java.util.*;

public class ServerMusicNetworkHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(MusicNetworkChannels.CHANNEL_MUSIC_DATA, ServerMusicNetworkHandler::musicDataReceiver);
    }

    public static Map<UUID, MusicDataPacket> WAIT_AUDITION = new HashMap<>();
    public static void removeFirst() {
        Iterator<Map.Entry<UUID, MusicDataPacket>> iterator = WAIT_AUDITION.entrySet().iterator();
        if (!iterator.hasNext()) return;
        Map.Entry<UUID, MusicDataPacket> entry = iterator.next();
        sendS2CAuditionSyncData(entry.getKey(), entry.getValue(), true);
        iterator.remove();
    }

    public static void passAudition(@Nullable PlayerEntity auditor, UUID uuid) {
        if (WAIT_AUDITION.containsKey(uuid)) {
            MusicDataPacket packet = WAIT_AUDITION.get(uuid);
            WAIT_AUDITION.remove(uuid);
            boolean success = sendS2CMusicData(packet, true);
            if (auditor != null) {
                if (success) {
                    auditor.sendMessage(Text.translatable("concerto.audit.pass", packet.from, packet.music.getMeta().title()));
                } else {
                    auditor.sendMessage(Text.translatable("concerto.share.s2c_failed", uuid));
                }
                ConcertoServer.LOGGER.info("Auditor %s passed request from %s: %s to %s"
                        .formatted(auditor.getEntityName(), packet.from, packet.music.getMeta().title(), packet.to));
            }
            ConcertoServer.LOGGER.info("Auditor ??? passed request from %s: %s to %s"
                    .formatted(packet.from, packet.music.getMeta().title(), packet.to));
            sendS2CAuditionSyncData(uuid, packet, true);
        } else if (auditor != null) {
            auditor.sendMessage(Text.translatable("concerto.audit.uuid_not_found", uuid));
        }
    }

    public static void rejectAll(@Nullable PlayerEntity auditor) {
        WAIT_AUDITION.forEach((uuid, packet) -> {
            PlayerEntity player = packet.server.getPlayerManager().getPlayer(packet.from);
            String title = packet.music.getMeta().title();
            if (player != null) player.sendMessage(Text.translatable("concerto.share.rejected", title));
        });
        if (auditor != null) auditor.sendMessage(Text.translatable("concerto.audit.reject", "all guys'", "Musics"));
        ConcertoServer.LOGGER.info("Auditor " + (auditor == null ? "?" : auditor.getEntityName()) + " rejected all request");
    }

    public static void rejectAudition(@Nullable PlayerEntity auditor, UUID uuid) {
        if (WAIT_AUDITION.containsKey(uuid)) {
            MusicDataPacket packet = WAIT_AUDITION.get(uuid);
            WAIT_AUDITION.remove(uuid);
            PlayerEntity player = packet.server.getPlayerManager().getPlayer(packet.from);
            String title = packet.music.getMeta().title();
            if (player != null) player.sendMessage(Text.translatable("concerto.share.rejected", title));
            if (auditor != null) auditor.sendMessage(Text.translatable(
                    "concerto.audit.reject", player == null ? "an unknown player" : player.getEntityName(), title));
            ConcertoServer.LOGGER.info("Auditor %s rejected request from %s: %s to %s"
                    .formatted(auditor == null ? "???" : auditor.getEntityName(), packet.from, title, packet.to));
            sendS2CAuditionSyncData(uuid, packet, true);
        } else if (auditor != null) {
            auditor.sendMessage(Text.translatable("concerto.audit.uuid_not_found", uuid));
        }
    }

    public static void sendAuditionSyncPacket(UUID uuid, ServerPlayerEntity player, MusicDataPacket packet, boolean isDelete) {
        PacketByteBuf packetByteBuf = PacketByteBufs.create();
        packetByteBuf.writeString((isDelete ? "DEL;" : "ADD;") + uuid + ";" +
                (isDelete ? "QwQ" : Objects.requireNonNull(MusicJsonParsers.to(packet.music)).toString()), Short.MAX_VALUE << 4);
        ServerPlayNetworking.send(player, MusicNetworkChannels.CHANNEL_AUDITION_SYNC, packetByteBuf);
    }

    public static void sendS2CAuditionSyncData(UUID uuid, MusicDataPacket packet, boolean isDelete) {
        PlayerManager playerManager = packet.server.getPlayerManager();
        for (ServerPlayerEntity player : playerManager.getPlayerList()) {
            if (player.hasPermissionLevel(packet.server.getOpPermissionLevel())) {
                sendAuditionSyncPacket(uuid, player, packet, isDelete);
            }
        }
    }

    public static void sendS2CAllAuditionData(ServerPlayerEntity player) {
        WAIT_AUDITION.forEach((uuid, packet) -> sendAuditionSyncPacket(uuid, player, packet, false));
    }

    public static boolean sendS2CMusicData(MusicDataPacket packet, boolean audit) {
        if (!packet.isS2C) {
            throw new RuntimeException("Not an S2C music data packet");
        } else if (packet.server == null || !packet.server.isRunning()) {
            throw new RuntimeException("Server not found or not running");
        }
        PacketByteBuf buf = packet.toPacket();
        PlayerManager playerManager = packet.server.getPlayerManager();
        ConcertoServer.LOGGER.info("Trying to send music request to " + packet.to);
        if (packet.to.equals("@a")) {
            playerManager.getPlayerList().forEach(serverPlayer ->
                    ServerPlayNetworking.send(serverPlayer, MusicNetworkChannels.CHANNEL_MUSIC_DATA, buf));
        } else {
            ServerPlayerEntity target = playerManager.getPlayer(packet.to);
            ServerPlayerEntity from = playerManager.getPlayer(packet.from);
            if (target == null) {
                if (from != null) {
                    from.sendMessage(Text.translatable("concerto.share.s2c_player_not_found", packet.to));
                }
                ConcertoServer.LOGGER.warn("Target not found, failed to send.");
                return false;
            } else {
                ServerPlayNetworking.send(target, MusicNetworkChannels.CHANNEL_MUSIC_DATA, buf);
                if (audit && from != null) {
                    from.sendMessage(Text.translatable("concerto.share.audition_passed",
                            packet.to, packet.music.getMeta().title()));
                }
            }
        }
        ConcertoServer.LOGGER.info("Successfully.");
        return true;
    }

    public static void musicDataReceiver(MinecraftServer server, ServerPlayerEntity player,
                                         ServerPlayNetworkHandler handler, PacketByteBuf buf, PacketSender sender) {
        try {
            MusicDataPacket packet = MusicDataPacket.fromPacket(buf, false);
            if (packet != null && packet.music != null) {
                PlayerManager playerManager = server.getPlayerManager();
                if (!playerExist(playerManager, packet.to)) {
                    player.sendMessage(Text.translatable("concerto.share.c2s_player_not_found", packet.to));
                    ConcertoServer.LOGGER.info("Received a music request from " + player.getEntityName() + " to an unknown player");
                } else {
                    packet.from = player.getEntityName();
                    packet.isS2C = true;
                    packet.server = server;
                    boolean audit = ServerConfig.INSTANCE.options.auditionRequired && packet.to.equals("@a");
                    boolean success = true;
                    if (audit) {
                        UUID uuid = UUID.randomUUID();
                        for (ServerPlayerEntity player1 : playerManager.getPlayerList()) {
                            if (player1.hasPermissionLevel(server.getOpPermissionLevel())) {
                                player1.sendMessage(TextUtil.PAGE_SPLIT);
                                player1.sendMessage(AuditCommand.chatMessageBuilder(
                                        uuid, packet.from, packet.music.getMeta().title()
                                ));
                                player1.sendMessage(TextUtil.PAGE_SPLIT);
                                sendAuditionSyncPacket(uuid, player1, packet, false);
                            }
                        }
                        WAIT_AUDITION.put(uuid, packet);
                        if (WAIT_AUDITION.size() > MusicNetworkChannels.WAIT_LIST_MAX_SIZE) {
                            removeFirst();
                        }
                    } else {
                        success = sendS2CMusicData(packet, false);
                    }
                    player.sendMessage(Text.translatable("concerto.share." + (success ? "success" : "failed")
                            + (audit ? "_audit" : ""), packet.music.getMeta().title()));
                    MusicMetaData meta = packet.music.getMeta();
                    ConcertoServer.LOGGER.info("Received a music request %s - %s from %s to %s"
                            .formatted(meta.getSource(), meta.title(), player.getEntityName(), packet.to));
                }
            } else {
                player.sendMessage(Text.translatable("concerto.share.error"));
                ConcertoServer.LOGGER.warn("Received an unknown music data packet from " + player.getEntityName());
            }
        } catch (Exception e) {
            ConcertoServer.LOGGER.warn("Received an unsafe music data packet from " + player.getEntityName());
            // Ignore unsafe music
        }
    }

    public static void playerJoinHandshake(ServerPlayerEntity player) {
        PacketByteBuf packetByteBuf = PacketByteBufs.create();
        packetByteBuf.writeString(MusicNetworkChannels.HANDSHAKE_STRING + "CallJoin:" + player.getEntityName());
        ServerPlayNetworking.send(player, MusicNetworkChannels.CHANNEL_HANDSHAKE, packetByteBuf);
        sendS2CAllAuditionData(player);
    }

    public static void broadcastJukeboxEvent(ServerWorld serverWorld, ConcertoJukeboxBlockEntity blockEntity, boolean start) {
        BlockPos pos = blockEntity.getPos();
        String posStr = pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
        PacketByteBuf packetByteBuf = PacketByteBufs.create();
        packetByteBuf.writeString(MusicNetworkChannels.HANDSHAKE_STRING + "Jukebox:" + posStr +
                (start ? ":st:" + TextUtil.toBase64(MusicJsonParsers.to(blockEntity.getMusic()).toString()) : ":ed"));
        List<ServerPlayerEntity> playerList = serverWorld.getServer().getPlayerManager().getPlayerList();
        playerList.forEach(player -> {
            if (player.getPos().distanceTo(blockEntity.getPos().toCenterPos()) <= 64) {
                ServerPlayNetworking.send(player, MusicNetworkChannels.CHANNEL_HANDSHAKE, packetByteBuf);
            }
        });
    }

    public static boolean playerExist(PlayerManager manager, String name) {
        return name.equals("@a") || (manager.getPlayer(name) != null);
    }
}
