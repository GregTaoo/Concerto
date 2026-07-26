package top.gregtao.concerto.network;

import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.bridge.MinecraftClientBridge;
import top.gregtao.concerto.command.ShareMusicCommand;
import top.gregtao.concerto.core.api.MusicJsonParsers;
import top.gregtao.concerto.core.config.ClientConfig;
import top.gregtao.concerto.core.config.PresetPlaylistsConfig;
import top.gregtao.concerto.core.music.Music;
import top.gregtao.concerto.core.player.MusicPlayer;
import top.gregtao.concerto.core.util.ConcertoRunner;
import top.gregtao.concerto.core.util.JsonUtil;
import top.gregtao.concerto.network.room.MusicRoomManager;
import top.gregtao.concerto.screen.MusicAuditionScreen;
import top.gregtao.concerto.screen.PresetRadiosScreen;
import top.gregtao.concerto.util.CommandUtil;
import top.gregtao.concerto.util.ComponentUtil;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class ClientMusicNetworkHandler {

    public static void register(MinecraftClientBridge bridge) {
        bridge.registerClientPayloadReceiver(ConcertoPayload.ID, ConcertoPayload.CODEC, ClientMusicNetworkHandler::generalReceiver);
    }

    public static final Map<UUID, MusicDataPacket> WAIT_CONFIRMATION = new HashMap<>();

    public static void removeFirst() {
        Iterator<Map.Entry<UUID, MusicDataPacket>> iterator = WAIT_CONFIRMATION.entrySet().iterator();
        if (!iterator.hasNext()) return;
        iterator.next();
        iterator.remove();
    }

    public static void generalReceiver(ConcertoPayload payload) {
        switch (payload.channel) {
            case MUSIC_DATA -> musicDataReceiver(payload);
            case HANDSHAKE -> playerJoinHandshake(payload);
            case AUDITION_SYNC -> auditionDataSyncReceiver(payload);
            case MUSIC_ROOM -> MusicRoomManager.clientReceiver(payload);
            case PRESET_RADIOS -> presetRadiosReceiver(payload);
        }
    }

    public static void sendC2SMusicData(MusicDataPacket packet) {
        if (!ConcertoClient.isServerAvailable()) {
            LocalPlayer player = Minecraft.getInstance().player;
            JsonObject object = MusicJsonParsers.to(packet.music, false);
            if (player != null && object != null) {
                String code = "Concerto:Share:" +
                        Base64.getEncoder().encodeToString(object.toString().getBytes(StandardCharsets.UTF_8));
                if (packet.to.equals("@a")) {
                    player.connection.sendChat(code);
                } else {
                    player.connection.sendCommand("msg " + packet.to + " \"" + code + "\"");
                }
            }
            return;
        }
        if (packet.isS2C) {
            throw new RuntimeException("Not an C2S music data packet");
        }
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            throw new RuntimeException("You are NULL, bro :)");
        }
        packet.music.load();
        ConcertoPayload buf = packet.toPacket(player.getName().getString());
        ConcertoClient.getBridge().sendPayload(buf);
    }

    public static void accept(Player player, UUID uuid, Minecraft client) {
        if (!WAIT_CONFIRMATION.containsKey(uuid)) {
            player.sendSystemMessage(Component.translatable("concerto.confirm.not_found"));
        } else {
            MusicDataPacket packet = WAIT_CONFIRMATION.get(uuid);
            MinecraftServer server = client.getSingleplayerServer();
            if (server != null) {
                Player from = server.getPlayerList().getPlayerByName(packet.from);
                if (from != null)
                    from.sendSystemMessage(Component.translatable("concerto.confirm.accept_response", player.getName().getString()));
            }
            MusicPlayer.INSTANCE.playTempMusic(packet.music);
            WAIT_CONFIRMATION.remove(uuid);
            player.sendSystemMessage(Component.translatable("concerto.confirm.accept"));
        }
    }

    public static void rejectAll(Player player, Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        WAIT_CONFIRMATION.forEach((uuid, packet) -> {
            if (server != null) {
                Player from = server.getPlayerList().getPlayerByName(packet.from);
                if (from != null)
                    from.sendSystemMessage(Component.translatable("concerto.confirm.reject_response", player.getName().getString()));
            }
        });
        WAIT_CONFIRMATION.clear();
        player.sendSystemMessage(Component.translatable("concerto.confirm.reject"));
    }

    public static void reject(Player player, UUID uuid, Minecraft client) {
        if (!WAIT_CONFIRMATION.containsKey(uuid)) {
            player.sendSystemMessage(Component.translatable("concerto.confirm.not_found"));
        } else {
            MusicDataPacket packet = WAIT_CONFIRMATION.get(uuid);
            MinecraftServer server = client.getSingleplayerServer();
            if (server != null) {
                Player from = server.getPlayerList().getPlayerByName(packet.from);
                if (from != null)
                    from.sendSystemMessage(Component.translatable("concerto.confirm.reject_response", player.getName().getString()));
            }
            WAIT_CONFIRMATION.remove(uuid);
            player.sendSystemMessage(Component.translatable("concerto.confirm.reject"));
        }
    }

    public static void addToWaitList(Minecraft client, MusicDataPacket packet, Player self) {
        UUID uuid = UUID.randomUUID();
        WAIT_CONFIRMATION.put(uuid, packet);
        if (WAIT_CONFIRMATION.size() > ServerMusicNetworkHandler.WAIT_LIST_MAX_SIZE) {
            removeFirst();
        }
        ConcertoRunner.run(() -> {
            if (ClientConfig.INSTANCE.options.confirmAfterReceived) {
                self.sendSystemMessage(CommandUtil.PAGE_SPLIT);
                self.sendSystemMessage(ShareMusicCommand.chatMessageBuilder(uuid, packet.from, packet.music.getMeta().title()));
                self.sendSystemMessage(CommandUtil.PAGE_SPLIT);
            } else {
                accept(self, uuid, client);
            }
        });
    }

    public static void musicDataReceiver(ConcertoPayload payload) {
        try {
            MusicDataPacket packet = MusicDataPacket.fromPacket(payload, true);
            Player self = Minecraft.getInstance().player;
            if (packet != null && packet.music != null && self != null) {
                addToWaitList(Minecraft.getInstance(), packet, self);
            } else {
                ConcertoClient.LOGGER.warn("Received an unknown music data packet");
            }
        } catch (Exception e) {
            ConcertoClient.LOGGER.warn("Received an unsafe music data packet");
            // Ignore unsafe music
        }
    }

    public static void playerJoinHandshake(ConcertoPayload payload) {
        String str = payload.string;
        LocalPlayer player = Minecraft.getInstance().player;
        if (!str.startsWith(ConcertoPayload.HANDSHAKE_STRING) || player == null) return;
        String[] args = str.split(":");
        if (args.length < 4) {
            player.sendSystemMessage(Component.translatable("concerto.server_invalid_version"));
            ConcertoClient.LOGGER.warn("Server handshake with an invalid version");
            return;
        }
        String version = args[1];
        if (!version.equals(ConcertoPayload.VERSION)) {
            player.sendSystemMessage(Component.translatable("concerto.invalid_version", version));
            ConcertoClient.LOGGER.warn("Server/Client handshake with an invalid version");
            return;
        }
        if (args[2].equals("CallJoin")) {
            String playerName = args[3];
            if (playerName.equals(player.getName().getString())) {
                ConcertoClient.serverAvailable = true;
                ConcertoClient.LOGGER.info("Concerto has been installed in this server");
                if (args.length > 4 && !Minecraft.getInstance().isLocalServer() && args[4].equals("Invite")) {
                    if (ClientConfig.INSTANCE.options.joinAgentWhenInvited) {
                        player.connection.sendCommand("musicroom agent join");
                    } else {
                        player.sendSystemMessage(CommandUtil.PAGE_SPLIT);
                        player.sendSystemMessage(Component.translatable("concerto.agent.invite")
                                .append(Component.literal("  ["))
                                .append(Component.translatable("concerto.accept").setStyle(
                                        ComponentUtil.getRunCommandStyle("/musicroom agent join").withColor(ChatFormatting.GREEN)))
                                .append(Component.literal("]")));
                        player.sendSystemMessage(CommandUtil.PAGE_SPLIT);
                    }
                }
            }
        }
    }

    public static void auditionDataSyncReceiver(ConcertoPayload payload) {
        String str = payload.string;
        String[] args = str.split(";");
        if (args.length != 3) return;
        try {
            if (args[0].equals("DEL")) {
                MusicAuditionScreen.WAIT_AUDITION.remove(UUID.fromString(args[1]));
            } else if (args[0].equals("ADD")) {
                Music music = MusicJsonParsers.from(JsonUtil.from(args[2]));
                if (music != null) MusicAuditionScreen.WAIT_AUDITION.put(UUID.fromString(args[1]), music);
            }
        } catch (IllegalArgumentException e) {
            ConcertoClient.LOGGER.error("Received an AuditionSyncDataPacket with illegal UUID: {}", args[1]);
        }
    }

    public static void presetRadiosReceiver(ConcertoPayload payload) {
        ConcertoRunner.run(() -> ConcertoClient.presetRadios = PresetPlaylistsConfig.fromJson(payload.string).stream().filter(playlist ->
                playlist.getList().stream().allMatch(MusicDataPacket::isMusicSafe)).toList(), () -> {
            Minecraft client = Minecraft.getInstance();
            if (client.gui.screen() instanceof PresetRadiosScreen screen) {
                screen.reset();
            }
        });
    }
}
