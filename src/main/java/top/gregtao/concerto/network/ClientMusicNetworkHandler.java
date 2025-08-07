package top.gregtao.concerto.network;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.api.MusicJsonParsers;
import top.gregtao.concerto.command.ShareMusicCommand;
import top.gregtao.concerto.config.ClientConfig;
import top.gregtao.concerto.config.PresetPlaylistsConfig;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.network.room.MusicRoom;
import top.gregtao.concerto.player.MusicPlayer;
import top.gregtao.concerto.player.MusicPlayerHandler;
import top.gregtao.concerto.screen.MusicAuditionScreen;
import top.gregtao.concerto.screen.PresetRadiosScreen;
import top.gregtao.concerto.util.JsonUtil;
import top.gregtao.concerto.util.TextUtil;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class ClientMusicNetworkHandler {

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ConcertoNetworking.MUSIC_DATA, ClientMusicNetworkHandler::musicDataReceiver);
        ClientPlayNetworking.registerGlobalReceiver(ConcertoNetworking.HANDSHAKE, ClientMusicNetworkHandler::playerJoinHandshake);
        ClientPlayNetworking.registerGlobalReceiver(ConcertoNetworking.AUDITION_SYNC, ClientMusicNetworkHandler::auditionDataSyncReceiver);
        ClientPlayNetworking.registerGlobalReceiver(ConcertoNetworking.MUSIC_ROOM, MusicRoom::clientReceiver);
        ClientPlayNetworking.registerGlobalReceiver(ConcertoNetworking.PRESET_RADIOS, ClientMusicNetworkHandler::presetRadiosReceiver);
        ClientPlayNetworking.registerGlobalReceiver(ConcertoNetworking.MUSIC_AGENT, ClientMusicNetworkHandler::musicAgentMusicReceiver);
    }

    public static final Map<UUID, MusicDataPacket> WAIT_CONFIRMATION = new HashMap<>();
    public static void removeFirst() {
        Iterator<Map.Entry<UUID, MusicDataPacket>> iterator = WAIT_CONFIRMATION.entrySet().iterator();
        if (!iterator.hasNext()) return;
        iterator.next();
        iterator.remove();
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
        PacketByteBuf buf = packet.toPacket(player.getName().getString());
        ClientPlayNetworking.send(ConcertoNetworking.MUSIC_DATA, buf);
    }

    public static void accept(PlayerEntity player, UUID uuid, MinecraftClient client) {
        if (!WAIT_CONFIRMATION.containsKey(uuid)) {
            player.sendMessage(Text.translatable("concerto.confirm.not_found"), false);
        } else {
            MusicDataPacket packet = WAIT_CONFIRMATION.get(uuid);
            MinecraftServer server = client.getServer();
            if (server != null) {
                PlayerEntity from = server.getPlayerManager().getPlayer(packet.from);
                if (from != null) from.sendMessage(Text.translatable("concerto.confirm.accept_response", player.getName().getString()), false);
            }
            MusicPlayer.INSTANCE.playTempMusic(packet.music);
            WAIT_CONFIRMATION.remove(uuid);
            player.sendMessage(Text.translatable("concerto.confirm.accept"), false);
        }
    }

    public static void rejectAll(PlayerEntity player, MinecraftClient client) {
        MinecraftServer server = client.getServer();
        WAIT_CONFIRMATION.forEach((uuid, packet) -> {
            if (server != null) {
                PlayerEntity from = server.getPlayerManager().getPlayer(packet.from);
                if (from != null) from.sendMessage(Text.translatable("concerto.confirm.reject_response", player.getName().getString()), false);
            }
        });
        WAIT_CONFIRMATION.clear();
        player.sendMessage(Text.translatable("concerto.confirm.reject"), false);
    }

    public static void reject(PlayerEntity player, UUID uuid, MinecraftClient client) {
        if (!WAIT_CONFIRMATION.containsKey(uuid)) {
            player.sendMessage(Text.translatable("concerto.confirm.not_found"), false);
        } else {
            MusicDataPacket packet = WAIT_CONFIRMATION.get(uuid);
            MinecraftServer server = client.getServer();
            if (server != null) {
                PlayerEntity from = server.getPlayerManager().getPlayer(packet.from);
                if (from != null) from.sendMessage(Text.translatable("concerto.confirm.reject_response", player.getName().getString()), false);
            }
            WAIT_CONFIRMATION.remove(uuid);
            player.sendMessage(Text.translatable("concerto.confirm.reject"), false);
        }
    }

    public static void addToWaitList(MinecraftClient client, MusicDataPacket packet, PlayerEntity self) {
        UUID uuid = UUID.randomUUID();
        WAIT_CONFIRMATION.put(uuid, packet);
        if (WAIT_CONFIRMATION.size() > ConcertoNetworking.WAIT_LIST_MAX_SIZE) {
            removeFirst();
        }
        MusicPlayer.run(() -> {
            if (ClientConfig.INSTANCE.options.confirmAfterReceived) {
                self.sendMessage(TextUtil.PAGE_SPLIT, false);
                self.sendMessage(ShareMusicCommand.chatMessageBuilder(uuid, packet.from, packet.music.getMeta().title()), false);
                self.sendMessage(TextUtil.PAGE_SPLIT, false);
            } else {
                accept(self, uuid, client);
            }
        });
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
        String str = buf.readString(Short.MAX_VALUE << 4);
        if (!str.startsWith(ConcertoNetworking.HANDSHAKE_STRING)) return;
        String[] args = str.split(":");
        if (args.length < 3) return;
        if (args[1].equals("CallJoin")) {
            String playerName = args[2];
            ClientPlayerEntity player = client.player;
            if (player != null && playerName.equals(player.getName().getString())) {
                ConcertoClient.serverAvailable = true;
                ConcertoClient.LOGGER.info("Concerto has been installed in this server");
                if (args.length > 3 && !client.isInSingleplayer() && args[3].equals("Invite")) {
                    if (ClientConfig.INSTANCE.options.joinAgentWhenInvited) {
                        player.networkHandler.sendChatCommand("musicroom agent join");
                    } else {
                        player.sendMessage(TextUtil.PAGE_SPLIT, false);
                        player.sendMessage(Text.translatable("concerto.agent.invite")
                                .append(Text.literal("  ["))
                                .append(Text.translatable("concerto.accept").setStyle(
                                        TextUtil.getRunCommandStyle("/musicroom agent join").withColor(Formatting.GREEN)))
                                .append(Text.literal("]")), false);
                        player.sendMessage(TextUtil.PAGE_SPLIT, false);
                    }
                }
            }
        }
    }

    public static void auditionDataSyncReceiver(MinecraftClient client, ClientPlayNetworkHandler handler,
                                                PacketByteBuf buf, PacketSender packetSender) {
        String str = buf.readString(Short.MAX_VALUE << 4);
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

    public static void presetRadiosReceiver(MinecraftClient client, ClientPlayNetworkHandler handler,
                                            PacketByteBuf buf, PacketSender packetSender) {
        String str = buf.readString(Short.MAX_VALUE << 4);
        MusicPlayer.run(() -> ConcertoClient.presetRadios = PresetRadioConfig.fromJson(str).stream().filter(playlist ->
                        playlist.getList().stream().allMatch(MusicDataPacket::isMusicSafe)).toList(), () -> {
//                .peek(playlist -> MusicPlayerHandler.loadInThreadPool(playlist.getList())).toList(), () -> {
            if (client != null && client.currentScreen instanceof PresetRadiosScreen screen) {
                screen.reset();
            }
        });
    }

    public static void musicAgentSender(String command) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(command);
        ClientPlayNetworking.send(ConcertoNetworking.MUSIC_AGENT, buf);
    }

    public static void musicAgentJoin() {
        musicAgentSender("Join");
        ConcertoClient.clientState = ConcertoClient.ClientState.MUSIC_AGENT;
    }

    public static void musicAgentQuit() {
        musicAgentSender("Quit");
        ConcertoClient.clientState = ConcertoClient.ClientState.LOCAL;
    }

    public static void musicAgentNewVote() {
        musicAgentSender("Vote:New");
    }

    public static void musicAgentQuery() {
        musicAgentSender("Query");
    }

    public static void musicAgentVote(boolean vote) {
        musicAgentSender("Vote:" + (vote ? "1" : "0"));
    }

    public static boolean musicAgentAddCurrentMusic() {
        return MusicPlayerHandler.INSTANCE.getCurrentMusic() != null &&
                musicAgentAddMusic(MusicPlayerHandler.INSTANCE.getCurrentMusic());
    }

    public static boolean musicAgentAddMusic(Music music) {
        JsonObject object = MusicJsonParsers.to(music);
        if (object == null) return false;
        musicAgentSender("Add:" +  TextUtil.toBase64(object.toString()));
        return true;
    }

    public static void musicAgentMusicReceiver(MinecraftClient client, ClientPlayNetworkHandler handler,
                                               PacketByteBuf buf, PacketSender packetSender) {
        String str = buf.readString(Short.MAX_VALUE << 4);
        if (ConcertoClient.clientState != ConcertoClient.ClientState.MUSIC_AGENT) return;
        MusicPlayer.run(() -> {
            if (str.equals("Stop")) {
                MusicPlayer.INSTANCE.stop();
            } else {
                Music music = MusicJsonParsers.from(TextUtil.fromBase64(str));
                if (music != null) {
                    MusicPlayer.INSTANCE.playTempMusic(music);
                }
            }
        });
    }
}
