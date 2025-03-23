package top.gregtao.concerto.network;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;
import top.gregtao.concerto.ConcertoServer;
import top.gregtao.concerto.api.MusicJsonParsers;
import top.gregtao.concerto.command.ConcertoServerCommand;
import top.gregtao.concerto.config.PresetRadioConfig;
import top.gregtao.concerto.config.ServerConfig;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.music.meta.music.MusicMetaData;
import top.gregtao.concerto.network.room.MusicRoom;
import top.gregtao.concerto.network.room.ServerMusicAgent;
import top.gregtao.concerto.util.TextUtil;

import java.util.*;

public class ServerMusicNetworkHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ConcertoPayload.ID, ServerMusicNetworkHandler::generalReceiver);
    }

    public static Map<UUID, MusicDataPacket> WAIT_AUDITION = new HashMap<>();
    public static void removeFirst() {
        Iterator<Map.Entry<UUID, MusicDataPacket>> iterator = WAIT_AUDITION.entrySet().iterator();
        if (!iterator.hasNext()) return;
        Map.Entry<UUID, MusicDataPacket> entry = iterator.next();
        sendS2CAuditionSyncData(entry.getKey(), entry.getValue(), true);
        iterator.remove();
    }
    
    public static void generalReceiver(ConcertoPayload payload, ServerPlayNetworking.Context context) {
        switch (payload.channel) {
            case MUSIC_DATA -> musicDataReceiver(payload, context);
            case MUSIC_ROOM -> MusicRoom.serverReceiver(payload, context);
            case MUSIC_AGENT -> musicAgentReceiver(payload, context);
        }
    }

    public static void passAudition(@Nullable PlayerEntity auditor, UUID uuid) {
        if (WAIT_AUDITION.containsKey(uuid)) {
            MusicDataPacket packet = WAIT_AUDITION.get(uuid);
            WAIT_AUDITION.remove(uuid);
            boolean success = sendS2CMusicData(packet, true);
            if (auditor != null) {
                if (success) {
                    auditor.sendMessage(Text.translatable("concerto.audit.pass", packet.from, packet.music.getMeta().title()), false);
                } else {
                    auditor.sendMessage(Text.translatable("concerto.share.s2c_failed", uuid.toString()), false);
                }
                ConcertoServer.LOGGER.info("Auditor {} passed request from {}: {} to {}",
                        auditor.getName().getString(), packet.from, packet.music.getMeta().title(), packet.to);
            }
            ConcertoServer.LOGGER.info("Auditor ??? passed request from {}: {} to {}",
                    packet.from, packet.music.getMeta().title(), packet.to);
            sendS2CAuditionSyncData(uuid, packet, true);
        } else if (auditor != null) {
            auditor.sendMessage(Text.translatable("concerto.audit.uuid_not_found"), false);
        }
    }

    public static void rejectAll(@Nullable PlayerEntity auditor) {
        WAIT_AUDITION.forEach((uuid, packet) -> {
            PlayerEntity player = packet.server.getPlayerManager().getPlayer(packet.from);
            String title = packet.music.getMeta().title();
            if (player != null) player.sendMessage(Text.translatable("concerto.share.rejected", title), false);
        });
        WAIT_AUDITION.clear();
        if (auditor != null) auditor.sendMessage(Text.translatable("concerto.audit.reject", "ALL", "ALL"), false);
        ConcertoServer.LOGGER.info("Auditor {} rejected all request", auditor == null ? "?" : auditor.getName().getString());
    }

    public static void rejectAudition(@Nullable PlayerEntity auditor, UUID uuid) {
        if (WAIT_AUDITION.containsKey(uuid)) {
            MusicDataPacket packet = WAIT_AUDITION.get(uuid);
            WAIT_AUDITION.remove(uuid);
            PlayerEntity player = packet.server.getPlayerManager().getPlayer(packet.from);
            String title = packet.music.getMeta().title();
            if (player != null) player.sendMessage(Text.translatable("concerto.share.rejected", title), false);
            if (auditor != null) auditor.sendMessage(Text.translatable(
                    "concerto.audit.reject", player == null ? "an unknown player" : player.getName().getString(), title), false);
            ConcertoServer.LOGGER.info("Auditor {} rejected request from {}: {} to {}",
                    auditor == null ? "???" : auditor.getName().getString(), packet.from, title, packet.to);
            sendS2CAuditionSyncData(uuid, packet, true);
        } else if (auditor != null) {
            auditor.sendMessage(Text.translatable("concerto.audit.uuid_not_found"), false);
        }
    }

    public static void sendAuditionSyncPacket(UUID uuid, ServerPlayerEntity player, MusicDataPacket packet, boolean isDelete) {
        ConcertoPayload payload = new ConcertoPayload(ConcertoPayload.Channel.AUDITION_SYNC, (isDelete ? "DEL;" : "ADD;") + uuid + ";" +
                (isDelete ? "QwQ" : Objects.requireNonNull(MusicJsonParsers.to(packet.music)).toString()));
        ServerPlayNetworking.send(player, payload);
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

    public static void sendS2CPresetRadiosPacket(ServerPlayerEntity player) {
        ConcertoPayload payload = new ConcertoPayload(ConcertoPayload.Channel.PRESET_RADIOS,
                PresetRadioConfig.INSTANCE.toString());
        ServerPlayNetworking.send(player, payload);
    }

    public static boolean sendS2CMusicData(MusicDataPacket packet, boolean audit) {
        if (!packet.isS2C) {
            throw new RuntimeException("Not an S2C music data packet");
        } else if (packet.server == null || !packet.server.isRunning()) {
            throw new RuntimeException("Server not found or not running");
        }
        ConcertoPayload payload = packet.toPacket();
        PlayerManager playerManager = packet.server.getPlayerManager();
        ConcertoServer.LOGGER.info("Trying to send music request to {}", packet.to);
        if (packet.to.equals("@a")) {
            playerManager.getPlayerList().forEach(serverPlayer ->
                    ServerPlayNetworking.send(serverPlayer, payload));
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
                ServerPlayNetworking.send(target, payload);
                if (audit && from != null) {
                    from.sendMessage(Text.translatable("concerto.share.audition_passed",
                            packet.to, packet.music.getMeta().title()));
                }
            }
        }
        ConcertoServer.LOGGER.info("Successfully.");
        return true;
    }

    public static void musicDataReceiver(ConcertoPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayerEntity player = context.player();
        MinecraftServer server = context.player().getServer();
        try {
            MusicDataPacket packet = MusicDataPacket.fromPacket(payload, false);
            if (packet != null && packet.music != null && server != null) {
                PlayerManager playerManager = server.getPlayerManager();
                if (!playerExist(playerManager, packet.to)) {
                    player.sendMessage(Text.translatable("concerto.share.c2s_player_not_found", packet.to));
                    ConcertoServer.LOGGER.info("Received a music request from {} to an unknown player", player.getName().getString());
                } else {
                    packet.from = player.getName().getString();
                    packet.isS2C = true;
                    packet.server = server;
                    boolean audit = ServerConfig.INSTANCE.options.auditionRequired && packet.to.equals("@a");
                    boolean success = true;
                    if (audit) {
                        UUID uuid = UUID.randomUUID();
                        for (ServerPlayerEntity player1 : playerManager.getPlayerList()) {
                            if (player1.hasPermissionLevel(server.getOpPermissionLevel())) {
                                player1.sendMessage(TextUtil.PAGE_SPLIT);
                                player1.sendMessage(ConcertoServerCommand.chatMessageBuilder(
                                        uuid, packet.from, packet.music.getMeta().title()
                                ));
                                player1.sendMessage(TextUtil.PAGE_SPLIT);
                                sendAuditionSyncPacket(uuid, player1, packet, false);
                            }
                        }
                        WAIT_AUDITION.put(uuid, packet);
                        if (WAIT_AUDITION.size() > ConcertoNetworking.WAIT_LIST_MAX_SIZE) {
                            removeFirst();
                        }
                    } else {
                        success = sendS2CMusicData(packet, false);
                    }
                    player.sendMessage(Text.translatable("concerto.share." + (success ? "success" : "failed")
                            + (audit ? "_audit" : ""), packet.music.getMeta().title()));
                    MusicMetaData meta = packet.music.getMeta();
                    ConcertoServer.LOGGER.info("Received a music request {} - {} from {} to {}",
                            meta.getSource(), meta.title(), player.getName().getString(), packet.to);
                }
            } else {
                player.sendMessage(Text.translatable("concerto.share.error"));
                ConcertoServer.LOGGER.warn("Received an unknown music data packet from {}", player.getName().getString());
            }
        } catch (Exception e) {
            ConcertoServer.LOGGER.warn("Received an unsafe music data packet from {}", player.getName().getString());
            // Ignore unsafe music
        }
    }

    public static void playerJoinHandshake(ServerPlayerEntity player) {
        ConcertoPayload payload = new ConcertoPayload(ConcertoPayload.Channel.HANDSHAKE,
                ConcertoNetworking.HANDSHAKE_STRING + "CallJoin:" + player.getName().getString()
                        + (ServerConfig.INSTANCE.options.agentInviteWhenJoin ? ":Invite" : ""));
        ServerPlayNetworking.send(player, payload);
        sendS2CAllAuditionData(player);
        sendS2CPresetRadiosPacket(player);
    }

    public static boolean playerExist(PlayerManager manager, String name) {
        return name.equals("@a") || (manager.getPlayer(name) != null);
    }

    public static void musicAgentSendMusic(ServerPlayerEntity player, Music music) {
        JsonObject object = MusicJsonParsers.to(music, true);
        if (object == null) return;
        musicAgentSendMusic(player, object.toString());
    }

    public static void musicAgentSendMusic(ServerPlayerEntity player, String music) {
        ConcertoPayload payload = new ConcertoPayload(ConcertoPayload.Channel.MUSIC_AGENT,
                TextUtil.toBase64(music));
        ServerPlayNetworking.send(player, payload);
    }

    public static void musicAgentSendStop(ServerPlayerEntity player) {
        ConcertoPayload payload = new ConcertoPayload(ConcertoPayload.Channel.MUSIC_AGENT, "Stop");
        ServerPlayNetworking.send(player, payload);
    }

    public static void musicAgentSendMusic(List<ServerPlayerEntity> players, Music music) {
        JsonObject object = MusicJsonParsers.to(music, true);
        if (object == null) return;
        players.forEach(player -> musicAgentSendMusic(player, object.toString()));
    }

    public static void musicAgentReceiver(ConcertoPayload payload, ServerPlayNetworking.Context context) {
        if (!ServerConfig.INSTANCE.options.serverMusicAgent) {
            context.player().sendMessage(Text.translatable("concerto.agent.not_available"));
            return;
        }
        String[] args = payload.string.split(":");
        if (args[0].equals("Join")) {
            ServerMusicAgent.INSTANCE.playerJoin(context.player());
            context.player().sendMessage(Text.translatable("concerto.agent.join"));
        } else if (args[0].equals("Quit")) {
            ServerMusicAgent.INSTANCE.playerQuit(context.player());
            context.player().sendMessage(Text.translatable("concerto.agent.quit"));
        } else if (args[0].equals("Query")) {
            List<Music> list = ServerMusicAgent.INSTANCE.getMusicQueue();
            context.player().sendMessage(TextUtil.PAGE_SPLIT);
            list.forEach(music -> context.player().sendMessage(Text.literal(
                    music.getMeta().title() + " - " + music.getMeta().author())));
            context.player().sendMessage(TextUtil.PAGE_SPLIT);
        } else if (args.length < 2 || !ServerMusicAgent.INSTANCE.isMember(context.player())) {
            context.player().sendMessage(Text.translatable("concerto.agent.error"));
        } else if (args[0].equals("Vote")) {
            if (args[1].equals("New")) {
                ServerMusicAgent.INSTANCE.receiveVoteRequest(context.player());
            } else if (args[1].length() == 1) {
                ServerMusicAgent.INSTANCE.receiveVote(context.player(), args[1].equals("1"));
            } else {
                context.player().sendMessage(Text.translatable("concerto.agent.error"));
            }
        } else if (args[0].equals("Add")) {
            Music music = MusicJsonParsers.from(TextUtil.fromBase64(args[1]), false);
            if (music != null && MusicDataPacket.isMusicSafe(music)) {
                ServerMusicAgent.INSTANCE.addMusic(context.player(), music);
            } else {
                context.player().sendMessage(Text.translatable("concerto.agent.error"));
            }
        }
    }

    public static void sendVote2Member(ServerPlayerEntity player) {
        player.sendMessage(TextUtil.PAGE_SPLIT);
        player.sendMessage(Text.translatable("concerto.agent.vote")
                .append(Text.literal("  ["))
                .append(Text.translatable("concerto.accept").setStyle(
                        TextUtil.getRunCommandStyle("/musicroom agent vote true").withColor(Formatting.GREEN)))
                .append(Text.literal("]"))
                .append(Text.literal("  ["))
                .append(Text.translatable("concerto.reject").setStyle(
                        TextUtil.getRunCommandStyle("/musicroom agent vote false").withColor(Formatting.RED)))
                .append(Text.literal("]")));
        player.sendMessage(TextUtil.PAGE_SPLIT);
    }
}
