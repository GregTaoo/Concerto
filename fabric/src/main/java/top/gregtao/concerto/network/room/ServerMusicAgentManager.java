package top.gregtao.concerto.network.room;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import top.gregtao.concerto.core.room.agent.ServerMusicAgent;
import top.gregtao.concerto.network.ServerMusicNetworkHandler;

public class ServerMusicAgentManager {

    public static ServerMusicAgent INSTANCE;

    public static void init(MinecraftServer server) {
        INSTANCE = new ServerMusicAgent(new ServerMusicAgent.AgentBridge() {
            @Override
            public void sendMessage(String playerName, String translationKey, Object... args) {
                ServerPlayerEntity entity = server.getPlayerManager().getPlayer(playerName);
                if (entity != null) {
                    entity.sendMessage(Text.translatable(translationKey, args), false);
                }
            }

            @Override
            public void sendVoteRequest(String playerName) {
                ServerPlayerEntity entity = server.getPlayerManager().getPlayer(playerName);
                if (entity != null) {
                    ServerMusicNetworkHandler.sendVote2Member(entity);
                }
            }

        }, MusicRoomManager.createServerBridge(server));
    }
}
