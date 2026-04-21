package top.gregtao.concerto.paper.network.room;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import top.gregtao.concerto.core.room.agent.ServerMusicAgent;
import top.gregtao.concerto.paper.util.ComponentUtil;
import top.gregtao.concerto.paper.ConcertoPaperPlugin;
import top.gregtao.concerto.paper.network.ConcertoPayload;

public class ServerMusicAgentManager {

    public static void sendVote2Member(Player player) {
        player.sendMessage(ComponentUtil.PAGE_SPLIT);
        player.sendMessage(Component.translatable("concerto.agent.vote")
                .append(Component.text("  ["))
                .append(Component.translatable("concerto.accept")
                        .clickEvent(ClickEvent.runCommand("/musicroom agent vote true"))
                        .color(TextColor.color(5635925)))
                .append(Component.text("]"))
                .append(Component.text("  ["))
                .append(Component.translatable("concerto.reject")
                        .clickEvent(ClickEvent.runCommand("/musicroom agent vote false"))
                        .color(TextColor.color(16733525)))
                .append(Component.text("]")));
        player.sendMessage(ComponentUtil.PAGE_SPLIT);
    }

    public static void serverReceiver(ConcertoPayload payload, Player player) {
        String[] args = payload.string.split(":", 2);
        if (args.length != 2) {
            ConcertoPaperPlugin.LOGGER.error("Invalid arguments for server receiver: {}", payload);
            return;
        }

        ServerMusicAgent.handleServerCommand(args[0], args[1], player.getName());
    }

    public static void init() {
        ServerMusicAgent.init(playerName -> {
            Player player = Bukkit.getPlayer(playerName);
            if (player != null) {
                sendVote2Member(player);
            }
        }, MusicRoomManager.createServerBridge());
    }
}
