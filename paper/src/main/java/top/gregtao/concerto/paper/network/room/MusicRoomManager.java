package top.gregtao.concerto.paper.network.room;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import top.gregtao.concerto.core.room.MusicRoom;
import top.gregtao.concerto.paper.ConcertoPaperPlugin;
import top.gregtao.concerto.paper.network.ConcertoPayload;
import top.gregtao.concerto.paper.network.ServerMusicNetworkHandler;
import top.gregtao.concerto.paper.util.ComponentUtil;

public class MusicRoomManager {

    public static void serverSender(String command, String payloadString, Player player) {
        if (player == null) return;
        ConcertoPayload payload = new ConcertoPayload(ConcertoPayload.Channel.MUSIC_ROOM, command + ":" + payloadString);
        ServerMusicNetworkHandler.sendPluginMessage(player, payload);
    }

    public static MusicRoom.ServerNetworkBridge createServerBridge() {
        return new MusicRoom.ServerNetworkBridge() {
            @Override
            public void sendMessage(String targetPlayer, String translationKey, Object... args) {
                Player p = Bukkit.getPlayer(targetPlayer);
                if (p != null) p.sendMessage(ComponentUtil.translatable(translationKey, args));
            }

            @Override
            public void sendRoomCommand(String targetPlayer, MusicRoom.Command command, String payload) {
                Player p = Bukkit.getPlayer(targetPlayer);
                if (p != null) serverSender(command.name(), payload, p);
            }

            @Override
            public boolean hasExternalPermission(String player, int level) {
                Player p = Bukkit.getPlayer(player);
                return p != null && p.hasPermission("concerto.room.create");
            }
        };
    }

    public static void serverReceiver(ConcertoPayload payload, Player player) {
        String[] args = payload.string.split(":", 3);
        if (args.length != 3) {
            ConcertoPaperPlugin.LOGGER.error("Invalid arguments for server receiver: {}", payload);
            return;
        }

        MusicRoom.handleServerCommand(args[0], args[1], args[2], player.getName(), createServerBridge());
    }
}