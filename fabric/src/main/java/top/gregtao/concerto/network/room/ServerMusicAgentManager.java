package top.gregtao.concerto.network.room;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import top.gregtao.concerto.ConcertoServer;
import top.gregtao.concerto.core.music.Music;
import top.gregtao.concerto.core.player.MusicPlayerHandler;
import top.gregtao.concerto.core.room.agent.ServerMusicAgent;
import top.gregtao.concerto.network.ConcertoPayload;
import top.gregtao.concerto.util.MinecraftTextUtil;

public class ServerMusicAgentManager {

    public static void sendVote2Member(ServerPlayer player) {
        player.sendSystemMessage(MinecraftTextUtil.PAGE_SPLIT);
        player.sendSystemMessage(Component.translatable("concerto.agent.vote")
                .append(Component.literal("  ["))
                .append(Component.translatable("concerto.accept").setStyle(
                        MinecraftTextUtil.getRunCommandStyle("/musicroom agent vote true").withColor(ChatFormatting.GREEN)))
                .append(Component.literal("]"))
                .append(Component.literal("  ["))
                .append(Component.translatable("concerto.reject").setStyle(
                        MinecraftTextUtil.getRunCommandStyle("/musicroom agent vote false").withColor(ChatFormatting.RED)))
                .append(Component.literal("]")));
        player.sendSystemMessage(MinecraftTextUtil.PAGE_SPLIT);
    }

    public static void serverReceiver(ConcertoPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        String[] args = payload.string.split(":", 2);
        if (args.length != 2) {
            ConcertoServer.LOGGER.error("Invalid arguments for server receiver: {}", payload);
            return;
        }

        ServerMusicAgent.handleServerCommand(args[0], args[1], player.getName().getString());
    }

    public static final ServerMusicAgent.ClientNetworkBridge CLIENT_BRIDGE = (command, payload) ->
            ClientPlayNetworking.send(new ConcertoPayload(ConcertoPayload.Channel.MUSIC_AGENT, command.name() + ":" + payload));

    public static void clientJoin() {
        MusicRoomManager.clientJoin(ServerMusicAgent.ROOM_UUID.toString());
    }

    public static void clientQuit() {
        MusicRoomManager.clientQuit();
    }

    public static void clientNewVote() {
        ServerMusicAgent.clientNewVote(CLIENT_BRIDGE);
    }

    public static void clientVote(boolean vote) {
        ServerMusicAgent.clientVote(CLIENT_BRIDGE, vote);
    }

    public static boolean clientAddCurrentMusic() {
        if (MusicPlayerHandler.INSTANCE.getCurrentMusic() != null) {
            clientAddMusic(MusicPlayerHandler.INSTANCE.getCurrentMusic());
            return true;
        }
        return false;
    }

    public static void clientAddMusic(Music music) {
        ServerMusicAgent.clientAddMusic(CLIENT_BRIDGE, music);
    }

    public static void init(MinecraftServer server) {
        ServerMusicAgent.init(playerName -> {
            ServerPlayer entity = server.getPlayerList().getPlayerByName(playerName);
            if (entity != null) {
                sendVote2Member(entity);
            }
        }, MusicRoomManager.createServerBridge(server));
    }
}
