package top.gregtao.concerto.network;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.api.MusicJsonParsers;
import top.gregtao.concerto.command.ShareMusicCommand;
import top.gregtao.concerto.config.ClientConfig;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.player.MusicPlayer;
import top.gregtao.concerto.screen.MusicAuditionScreen;
import top.gregtao.concerto.util.JsonUtil;
import top.gregtao.concerto.util.TextUtil;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class ClientMusicNetworkHandler {

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ConcertoPayload.ID, ClientMusicNetworkHandler::generalReceiver);
    }

    public static final Map<UUID, MusicDataPacket> WAIT_CONFIRMATION = new HashMap<>();
    public static void removeFirst() {
        Iterator<Map.Entry<UUID, MusicDataPacket>> iterator = WAIT_CONFIRMATION.entrySet().iterator();
        if (!iterator.hasNext()) return;
        iterator.next();
        iterator.remove();
    }

    public static void generalReceiver(ConcertoPayload payload, ClientPlayNetworking.Context context) {
        switch (payload.channel) {
            case MUSIC_DATA -> musicDataReceiver(payload, context);
            case HANDSHAKE -> playerJoinHandshake(payload, context);
            case AUDITION_SYNC -> auditionDataSyncReceiver(payload, context);
            case MUSIC_ROOM -> MusicRoom.clientReceiver(payload, context);
        }
    }

    public static void sendC2SMusicData(MusicDataPacket packet) {
        if (!ConcertoClient.isServerAvailable()) {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            JsonObject object = MusicJsonParsers.to(packet.music, false);
            if (player != null && object != null) {
                String code = "Concerto:Share:" +
                        Base64.getEncoder().encodeToString(object.toString().getBytes(StandardCharsets.UTF_8));
                if (packet.to.equals("@a")) {
                    player.networkHandler.sendChatMessage(code);
                } else {
                    player.networkHandler.sendChatCommand("msg " + packet.to + " \"" + code + "\"");
                }
            }
            return;
        }
        if (packet.isS2C) {
            throw new RuntimeException("Not an C2S music data packet");
        }
        PlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            throw new RuntimeException("You are NULL, bro :)");
        }
        packet.music.load();
        ConcertoPayload buf = packet.toPacket(player.getName().getString());
        ClientPlayNetworking.send(buf);
    }

    public static void accept(PlayerEntity player, UUID uuid, MinecraftClient client) {
        if (!WAIT_CONFIRMATION.containsKey(uuid)) {
            player.sendMessage(Text.translatable("concerto.confirm.not_found"));
        } else {
            MusicDataPacket packet = WAIT_CONFIRMATION.get(uuid);
            MinecraftServer server = client.getServer();
            if (server != null) {
                PlayerEntity from = server.getPlayerManager().getPlayer(packet.from);
                if (from != null) from.sendMessage(Text.translatable("concerto.confirm.accept_response", player.getName().getString()));
            }
            MusicPlayer.INSTANCE.playTempMusic(packet.music);
            WAIT_CONFIRMATION.remove(uuid);
            player.sendMessage(Text.translatable("concerto.confirm.accept"));
        }
    }

    public static void rejectAll(PlayerEntity player, MinecraftClient client) {
        MinecraftServer server = client.getServer();
        WAIT_CONFIRMATION.forEach((uuid, packet) -> {
            if (server != null) {
                PlayerEntity from = server.getPlayerManager().getPlayer(packet.from);
                if (from != null) from.sendMessage(Text.translatable("concerto.confirm.reject_response", player.getName().getString()));
            }
        });
        WAIT_CONFIRMATION.clear();
        player.sendMessage(Text.translatable("concerto.confirm.reject"));
    }

    public static void reject(PlayerEntity player, UUID uuid, MinecraftClient client) {
        if (!WAIT_CONFIRMATION.containsKey(uuid)) {
            player.sendMessage(Text.translatable("concerto.confirm.not_found"));
        } else {
            MusicDataPacket packet = WAIT_CONFIRMATION.get(uuid);
            MinecraftServer server = client.getServer();
            if (server != null) {
                PlayerEntity from = server.getPlayerManager().getPlayer(packet.from);
                if (from != null) from.sendMessage(Text.translatable("concerto.confirm.reject_response", player.getName().getString()));
            }
            WAIT_CONFIRMATION.remove(uuid);
            player.sendMessage(Text.translatable("concerto.confirm.reject"));
        }
    }

    public static void addToWaitList(MinecraftClient client, MusicDataPacket packet, PlayerEntity self) {
        UUID uuid = UUID.randomUUID();
        WAIT_CONFIRMATION.put(uuid, packet);
        if (WAIT_CONFIRMATION.size() > MusicNetworkChannels.WAIT_LIST_MAX_SIZE) {
            removeFirst();
        }
        MusicPlayer.run(() -> {
            if (ClientConfig.INSTANCE.options.confirmAfterReceived) {
                self.sendMessage(TextUtil.PAGE_SPLIT);
                self.sendMessage(ShareMusicCommand.chatMessageBuilder(uuid, packet.from, packet.music.getMeta().title()));
                self.sendMessage(TextUtil.PAGE_SPLIT);
            } else {
                accept(self, uuid, client);
            }
        });
    }

    public static void musicDataReceiver(ConcertoPayload payload, ClientPlayNetworking.Context context) {
        try {
            MusicDataPacket packet = MusicDataPacket.fromPacket(payload, true);
            PlayerEntity self = context.player();
            if (packet != null && packet.music != null && self != null) {
                addToWaitList(context.client(), packet, self);
            } else {
                ConcertoClient.LOGGER.warn("Received an unknown music data packet");
            }
        } catch (Exception e) {
            ConcertoClient.LOGGER.warn("Received an unsafe music data packet");
            // Ignore unsafe music
        }
    }

    public static void playerJoinHandshake(ConcertoPayload payload, ClientPlayNetworking.Context context) {
        String str = payload.string;
        if (!str.startsWith(MusicNetworkChannels.HANDSHAKE_STRING)) return;
        String[] args = str.split(":");
        if (args.length < 3) return;
        if (args[1].equals("CallJoin")) {
            String playerName = args[2];
            ClientPlayerEntity player = context.player();
            if (player != null && playerName.equals(player.getName().getString())) {
                ConcertoClient.serverAvailable = true;
                ConcertoClient.LOGGER.info("Concerto has been installed in this server");
            }
        }
    }

    public static void auditionDataSyncReceiver(ConcertoPayload payload, ClientPlayNetworking.Context context) {
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

}
