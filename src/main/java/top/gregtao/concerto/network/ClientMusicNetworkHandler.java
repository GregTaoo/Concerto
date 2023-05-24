package top.gregtao.concerto.network;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.api.MusicJsonParsers;
import top.gregtao.concerto.command.ShareMusicCommand;
import top.gregtao.concerto.config.ClientConfig;
import top.gregtao.concerto.player.MusicPlayer;
import top.gregtao.concerto.util.TextUtil;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClientMusicNetworkHandler {

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(MusicNetworkChannels.CHANNEL_MUSIC_DATA, ClientMusicNetworkHandler::musicDataReceiver);
        ClientPlayNetworking.registerGlobalReceiver(MusicNetworkChannels.CHANNEL_HANDSHAKE, ClientMusicNetworkHandler::playerJoinHandshake);
    }

    public static Map<UUID, MusicDataPacket> WAIT_CONFIRMATION = new HashMap<>();

    public static void sendC2SMusicData(MusicDataPacket packet) {
        if (!ConcertoClient.isServerAvailable()) {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            JsonObject object = MusicJsonParsers.to(packet.music);
            if (player != null && object != null) {
                JsonObject metaObject = object.getAsJsonObject("meta");
                String src = metaObject.get("src").getAsString();
                metaObject.addProperty("src", src + ", " + player.getEntityName());
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
        PacketByteBuf buf = packet.toPacket(player.getEntityName());
        ClientPlayNetworking.send(MusicNetworkChannels.CHANNEL_MUSIC_DATA, buf);
    }

    public static void accept(PlayerEntity player, UUID uuid, MinecraftClient client) {
        if (!WAIT_CONFIRMATION.containsKey(uuid)) {
            player.sendMessage(Text.translatable("concerto.confirm.not_found"));
        } else {
            MusicDataPacket packet = WAIT_CONFIRMATION.get(uuid);
            MinecraftServer server = client.getServer();
            if (server != null) {
                PlayerEntity from = server.getPlayerManager().getPlayer(packet.from);
                if (from != null) from.sendMessage(Text.translatable("concerto.confirm.accept_response", player.getEntityName()));
            }
            MusicPlayer.INSTANCE.playTempMusic(packet.music);
            WAIT_CONFIRMATION.remove(uuid);
            player.sendMessage(Text.translatable("concerto.confirm.accept"));
        }
    }

    public static void reject(PlayerEntity player, UUID uuid, MinecraftClient client) {
        if (!WAIT_CONFIRMATION.containsKey(uuid)) {
            player.sendMessage(Text.translatable("concerto.confirm.not_found"));
        } else {
            MusicDataPacket packet = WAIT_CONFIRMATION.get(uuid);
            MinecraftServer server = client.getServer();
            if (server != null) {
                PlayerEntity from = server.getPlayerManager().getPlayer(packet.from);
                if (from != null) from.sendMessage(Text.translatable("concerto.confirm.reject_response", player.getEntityName()));
            }
            WAIT_CONFIRMATION.remove(uuid);
            player.sendMessage(Text.translatable("concerto.confirm.reject"));
        }
    }

    public static void addToWaitList(MinecraftClient client, MusicDataPacket packet, PlayerEntity self) {
        UUID uuid = UUID.randomUUID();
        WAIT_CONFIRMATION.put(uuid, packet);
        if (ClientConfig.INSTANCE.options.confirmAfterReceived) {
            self.sendMessage(TextUtil.PAGE_SPLIT);
            self.sendMessage(ShareMusicCommand.chatMessageBuilder(uuid, packet.from, packet.music.getMeta().title()));
            self.sendMessage(TextUtil.PAGE_SPLIT);
        } else {
            accept(self, uuid, client);
        }
    }

    public static void musicDataReceiver(MinecraftClient client, ClientPlayNetworkHandler handler,
                                         PacketByteBuf buf, PacketSender packetSender) {
        try {
            MusicDataPacket packet = MusicDataPacket.fromPacket(buf, true);
            PlayerEntity self = client.player;
            if (packet != null && packet.music != null && self != null) {
                addToWaitList(client, packet, self);
            } else {
                ConcertoClient.LOGGER.warn("Received an unknown music data packet");
            }
        } catch (Exception e) {
            ConcertoClient.LOGGER.warn("Received an unsafe music data packet");
            // Ignore unsafe music
        }
    }

    public static void playerJoinHandshake(MinecraftClient client, ClientPlayNetworkHandler handler,
                                           PacketByteBuf buf, PacketSender packetSender) {
        String str = buf.readString(Short.MAX_VALUE);
        if (!str.startsWith(MusicNetworkChannels.HANDSHAKE_STRING)) return;
        String playerName = str.substring(MusicNetworkChannels.HANDSHAKE_STRING.length());
        if (client.player != null && playerName.equals(client.player.getEntityName())) {
            ConcertoClient.serverAvailable = true;
            ConcertoClient.LOGGER.info("Concerto has been installed in this server");
        }
    }

}
