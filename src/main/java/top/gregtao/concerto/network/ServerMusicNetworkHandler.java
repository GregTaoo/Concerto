package top.gregtao.concerto.network;

import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import top.gregtao.concerto.ConcertoServer;
import top.gregtao.concerto.music.meta.music.MusicMeta;
import top.gregtao.concerto.command.AuditCommand;
import top.gregtao.concerto.config.ServerConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ServerMusicNetworkHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(MusicNetworkChannels.CHANNEL_MUSIC_DATA, ServerMusicNetworkHandler::musicDataReceiver);
    }

    public static Map<UUID, MusicDataPacket> WAIT_AUDITION = new HashMap<>();

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
        } else if (auditor != null) {
            auditor.sendMessage(Text.translatable("concerto.audit.uuid_not_found", uuid));
        }
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
        } else if (auditor != null) {
            auditor.sendMessage(Text.translatable("concerto.audit.uuid_not_found", uuid));
        }
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
                        for (PlayerEntity player1 : playerManager.getPlayerList()) {
                            if (player1.hasPermissionLevel(server.getOpPermissionLevel())) {
                                player1.sendMessage(AuditCommand.chatMessageBuilder(
                                        uuid, packet.from, packet.music.getMeta().title()
                                ));
                            }
                        }
                        WAIT_AUDITION.put(uuid, packet);
                    } else {
                        success = sendS2CMusicData(packet, false);
                    }
                    player.sendMessage(Text.translatable("concerto.share." + (success ? "success" : "failed")
                            + (audit ? "_audit" : ""), packet.music.getMeta().title()));
                    MusicMeta meta = packet.music.getMeta();
                    ConcertoServer.LOGGER.info("Received a music request %s - %s from %s to %s"
                            .formatted(meta.getSource(), meta.title(), player.getEntityName(), packet.to));
                }
            } else {
                player.sendMessage(Text.translatable("concerto.share.error"));
                ConcertoServer.LOGGER.warn("Received an unknown music data packet from " + player.getEntityName());
            }
        } catch (Exception ignored) {
            ConcertoServer.LOGGER.warn("Received an unsafe music data packet from " + player.getEntityName());
            // Ignore unsafe music
        }
    }

    public static boolean playerExist(PlayerManager manager, String name) {
        return name.equals("@a") || (manager.getPlayer(name) != null);
    }
}
