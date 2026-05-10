package top.gregtao.concerto.paper.network;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import top.gregtao.concerto.core.api.MusicJsonParsers;
import top.gregtao.concerto.core.config.PresetPlaylistsConfig;
import top.gregtao.concerto.core.config.ServerConfig;
import top.gregtao.concerto.core.music.meta.music.MusicMetaData;
import top.gregtao.concerto.paper.ConcertoPaperPlugin;
import top.gregtao.concerto.paper.command.ConcertoServerCommand;
import top.gregtao.concerto.paper.network.room.MusicRoomManager;
import top.gregtao.concerto.paper.network.room.ServerMusicAgentManager;
import top.gregtao.concerto.paper.util.ComponentUtil;

import java.util.*;

public class ServerMusicNetworkHandler {

    public static final int WAIT_LIST_MAX_SIZE = 300;
    public static final Map<UUID, MusicDataPacket> WAIT_AUDITION = new HashMap<>();

    public static void removeFirst() {
        Iterator<Map.Entry<UUID, MusicDataPacket>> iterator = WAIT_AUDITION.entrySet().iterator();
        if (!iterator.hasNext()) return;
        Map.Entry<UUID, MusicDataPacket> entry = iterator.next();
        sendS2CAuditionSyncData(entry.getKey(), entry.getValue(), true);
        iterator.remove();
    }

    public static void generalReceiver(ConcertoPayload payload, Player sender) {
        switch (payload.channel) {
            case MUSIC_DATA -> musicDataReceiver(payload, sender);
            case MUSIC_ROOM -> MusicRoomManager.serverReceiver(payload, sender);
            case MUSIC_AGENT -> ServerMusicAgentManager.serverReceiver(payload, sender);
        }
    }

    public static void passAudition(@Nullable CommandSender auditor, UUID uuid) {
        if (WAIT_AUDITION.containsKey(uuid)) {
            MusicDataPacket packet = WAIT_AUDITION.get(uuid);
            WAIT_AUDITION.remove(uuid);
            boolean success = sendS2CMusicData(packet, true);
            if (auditor != null) {
                if (success) {
                    auditor.sendMessage(Component.translatable("concerto.audit.pass",
                            Component.text(packet.from), Component.text(packet.music.getMeta().title())));
                } else {
                    auditor.sendMessage(Component.translatable("concerto.share.s2c_failed", uuid.toString()));
                }
                ConcertoPaperPlugin.LOGGER.info("Auditor {} passed request from {}: {} to {}",
                        auditor.getName(), packet.from, packet.music.getMeta().title(), packet.to);
            }
            ConcertoPaperPlugin.LOGGER.info("Auditor ??? passed request from {}: {} to {}",
                    packet.from, packet.music.getMeta().title(), packet.to);
            sendS2CAuditionSyncData(uuid, packet, true);
        } else if (auditor != null) {
            auditor.sendMessage(Component.translatable("concerto.audit.uuid_not_found"));
        }
    }

    public static void rejectAll(@Nullable CommandSender auditor) {
        WAIT_AUDITION.forEach((uuid, packet) -> {
            Player player = Bukkit.getPlayerExact(packet.from);
            String title = packet.music.getMeta().title();
            if (player != null) player.sendMessage(Component.translatable("concerto.share.rejected", title));
        });
        WAIT_AUDITION.clear();
        if (auditor != null)
            auditor.sendMessage(Component.translatable("concerto.audit.reject", Component.text("ALL"), Component.text("ALL")));
        ConcertoPaperPlugin.LOGGER.info("Auditor {} rejected all request",
                auditor == null ? "?" : auditor.getName());
    }

    public static void rejectAudition(@Nullable CommandSender auditor, UUID uuid) {
        if (WAIT_AUDITION.containsKey(uuid)) {
            MusicDataPacket packet = WAIT_AUDITION.get(uuid);
            WAIT_AUDITION.remove(uuid);
            Player player = Bukkit.getPlayerExact(packet.from);
            String title = packet.music.getMeta().title();
            if (player != null) player.sendMessage(Component.translatable("concerto.share.rejected", title));
            if (auditor != null) auditor.sendMessage(Component.translatable(
                    "concerto.audit.reject", player == null ? "an unknown player" : player.getName(), Component.text(title)));
            ConcertoPaperPlugin.LOGGER.info("Auditor {} rejected request from {}: {} to {}",
                    auditor == null ? "???" : auditor.getName(), packet.from, title, packet.to);
            sendS2CAuditionSyncData(uuid, packet, true);
        } else if (auditor != null) {
            auditor.sendMessage(Component.translatable("concerto.audit.uuid_not_found"));
        }
    }

    public static void sendAuditionSyncPacket(UUID uuid, Player player, MusicDataPacket packet, boolean isDelete) {
        ConcertoPayload payload = new ConcertoPayload(ConcertoPayload.Channel.AUDITION_SYNC,
                (isDelete ? "DEL;" : "ADD;") + uuid + ";" +
                        (isDelete ? "QwQ" : Objects.requireNonNull(MusicJsonParsers.to(packet.music)).toString()));
        sendPluginMessage(player, payload);
    }

    public static void sendS2CAuditionSyncData(UUID uuid, MusicDataPacket packet, boolean isDelete) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOp()) {
                sendAuditionSyncPacket(uuid, player, packet, isDelete);
            }
        }
    }

    public static void sendS2CAllAuditionData(Player player) {
        WAIT_AUDITION.forEach((uuid, packet) -> sendAuditionSyncPacket(uuid, player, packet, false));
    }

    public static void sendS2CPresetRadiosPacket(Player player) {
        ConcertoPayload payload = new ConcertoPayload(ConcertoPayload.Channel.PRESET_RADIOS,
                PresetPlaylistsConfig.PRESET_RADIOS.toString());
        sendPluginMessage(player, payload);
    }

    public static boolean sendS2CMusicData(MusicDataPacket packet, boolean audit) {
        if (!packet.isS2C) {
            throw new RuntimeException("Not an S2C music data packet");
        }

        ConcertoPayload payload = packet.toPacket();
        ConcertoPaperPlugin.LOGGER.info("Trying to send music request to {}", packet.to);

        if (packet.to.equals("@a")) {
            for (Player target : Bukkit.getOnlinePlayers()) {
                sendPluginMessage(target, payload);
            }
        } else {
            Player target = Bukkit.getPlayerExact(packet.to);
            Player from = Bukkit.getPlayerExact(packet.from);
            if (target == null) {
                if (from != null) {
                    from.sendMessage(Component.translatable("concerto.share.s2c_player_not_found", packet.to));
                }
                ConcertoPaperPlugin.LOGGER.warn("Target not found, failed to send.");
                return false;
            } else {
                sendPluginMessage(target, payload);
                if (audit && from != null) {
                    from.sendMessage(Component.translatable("concerto.share.audition_passed",
                            Component.text(packet.to), Component.text(packet.music.getMeta().title())));
                }
            }
        }
        ConcertoPaperPlugin.LOGGER.info("Successfully.");
        return true;
    }

    public static void musicDataReceiver(ConcertoPayload payload, Player sender) {
        try {
            MusicDataPacket packet = MusicDataPacket.fromPacket(payload, false);
            if (packet != null && packet.music != null) {
                if (!playerExist(packet.to)) {
                    sender.sendMessage(Component.translatable("concerto.share.c2s_player_not_found", packet.to));
                    ConcertoPaperPlugin.LOGGER.info("Received a music request from {} to an unknown player",
                            sender.getName());
                } else {
                    packet.from = sender.getName();
                    packet.isS2C = true;

                    boolean audit = ServerConfig.INSTANCE.options.auditionRequired && packet.to.equals("@a");
                    boolean success = true;

                    if (audit) {
                        UUID uuid = UUID.randomUUID();
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            if (player.isOp() || player.hasPermission("concerto.admin")) {
                                player.sendMessage(ComponentUtil.PAGE_SPLIT);
                                player.sendMessage(ConcertoServerCommand.chatMessageBuilder(
                                        uuid, packet.from, packet.music.getMeta().title()));
                                player.sendMessage(ComponentUtil.PAGE_SPLIT);
                                sendAuditionSyncPacket(uuid, player, packet, false);
                            }
                        }
                        WAIT_AUDITION.put(uuid, packet);
                        if (WAIT_AUDITION.size() > WAIT_LIST_MAX_SIZE) {
                            removeFirst();
                        }
                    } else {
                        success = sendS2CMusicData(packet, false);
                    }

                    sender.sendMessage(Component.translatable("concerto.share." + (success ? "success" : "failed")
                            + (audit ? "_audit" : ""), packet.music.getMeta().title()));

                    MusicMetaData meta = packet.music.getMeta();
                    ConcertoPaperPlugin.LOGGER.info("Received a music request {} - {} from {} to {}",
                            meta.getSource(), meta.title(), sender.getName(), packet.to);
                }
            } else {
                sender.sendMessage(Component.translatable("concerto.share.error"));
                ConcertoPaperPlugin.LOGGER.warn("Received an unknown music data packet from {}",
                        sender.getName());
            }
        } catch (Exception e) {
            ConcertoPaperPlugin.LOGGER.warn("Received an unsafe music data packet from {}",
                    sender.getName());
        }
    }

    public static void playerJoinHandshake(Player player) {
        ConcertoPayload payload = new ConcertoPayload(ConcertoPayload.Channel.HANDSHAKE,
                ConcertoPayload.HANDSHAKE_STRING + "CallJoin:" + player.getName()
                        + (ServerConfig.INSTANCE.options.serverMusicAgent
                        && ServerConfig.INSTANCE.options.agentInviteWhenJoin ? ":Invite" : ""));
        sendPluginMessage(player, payload);
        sendS2CAllAuditionData(player);
        sendS2CPresetRadiosPacket(player);
    }

    public static boolean playerExist(String name) {
        return name.equals("@a") || (Bukkit.getPlayerExact(name) != null);
    }

    public static void sendPluginMessage(Player player, ConcertoPayload payload) {
        player.sendPluginMessage(
                ConcertoPaperPlugin.INSTANCE,
                ConcertoPayload.ID,
                payload.encode()
        );
    }
}