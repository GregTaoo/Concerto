package top.gregtao.concerto.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.TranslatableText;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.network.ClientMusicNetworkHandler;
import top.gregtao.concerto.network.room.MusicRoom;

public class MusicRoomCommand {

    public static void register() {
        LiteralCommandNode<FabricClientCommandSource> node = ClientCommandManager.DISPATCHER.register(
                ClientCommandManager.literal("musicroom")
                        .then(ClientCommandManager.literal("create").executes(context -> {
                            ClientPlayerEntity player = context.getSource().getPlayer();
                            if (checkServerAvailable(player) && checkLocal(player)) {
                                MusicRoom.clientCreate();
                            }
                            return 0;
                        })).then(ClientCommandManager.literal("join").then(
                                ClientCommandManager.argument("uuid", StringArgumentType.string()).executes(context -> {
                                    ClientPlayerEntity player = context.getSource().getPlayer();
                                    if (checkServerAvailable(player) && checkLocal(player)) {
                                        MusicRoom.clientJoin(StringArgumentType.getString(context, "uuid"));
                                    }
                                    return 0;
                                })
                        )).then(ClientCommandManager.literal("quit").executes(context -> {
                            MusicRoom.clientQuit();
                            return 0;
                        })).then(ClientCommandManager.literal("members").executes(context -> {
                            if (MusicRoom.CLIENT_ROOM != null) {
                                context.getSource().getPlayer().sendMessage(new TranslatableText(
                                        "concerto.room.members", MusicRoom.CLIENT_ROOM.owner,
                                        String.join(",", MusicRoom.CLIENT_ROOM.members.keySet())
                                ), false);
                            }
                            return 0;
                        })).then(ClientCommandManager.literal("op").then(
                                ClientCommandManager.argument("player", StringArgumentType.string()).executes(context -> {
                                    MusicRoom.clientSetOp(StringArgumentType.getString(context, "player"));
                                    return 0;
                                })
                        )).then(
                                ClientCommandManager.literal("agent").then(
                                        ClientCommandManager.literal("join").executes(context -> {
                                            ClientPlayerEntity player = context.getSource().getPlayer();
                                            if (checkServerAvailable(player) && checkLocal(player)) {
                                                ClientMusicNetworkHandler.musicAgentJoin();
                                            }
                                            return 0;
                                        })
                                ).then(
                                        ClientCommandManager.literal("quit").executes(context -> {
                                            ClientPlayerEntity player = context.getSource().getPlayer();
                                            if (checkServerAvailable(player) && checkAgent(player)) {
                                                ClientMusicNetworkHandler.musicAgentQuit();
                                            }
                                            return 0;
                                        })
                                ).then(
                                        ClientCommandManager.literal("query").executes(context -> {
                                            ClientPlayerEntity player = context.getSource().getPlayer();
                                            if (checkServerAvailable(player) && checkAgent(player)) {
                                                ClientMusicNetworkHandler.musicAgentQuery();
                                            }
                                            return 0;
                                        })
                                ).then(
                                        ClientCommandManager.literal("add").executes(context -> {
                                            ClientPlayerEntity player = context.getSource().getPlayer();
                                            if (checkServerAvailable(player) && checkAgent(player)) {
                                                if (!ClientMusicNetworkHandler.musicAgentAddCurrentMusic()) {
                                                    player.sendMessage(new TranslatableText("concerto.not_playing_music"), false);
                                                }
                                            }
                                            return 0;
                                        })
                                ).then(
                                        ClientCommandManager.literal("vote").executes(context -> {
                                            ClientPlayerEntity player = context.getSource().getPlayer();
                                            if (checkServerAvailable(player) && checkAgent(player)) {
                                                ClientMusicNetworkHandler.musicAgentNewVote();
                                            }
                                            return 0;
                                        }).then(
                                                ClientCommandManager.argument("vote", BoolArgumentType.bool()).executes(context -> {
                                                    ClientPlayerEntity player = context.getSource().getPlayer();
                                                    if (checkServerAvailable(player) && checkAgent(player)) {
                                                        ClientMusicNetworkHandler.musicAgentVote(BoolArgumentType.getBool(context, "vote"));
                                                    }
                                                    return 0;
                                                })
                                        )
                                )
                        )
        );
        ClientCommandManager.DISPATCHER.register(ClientCommandManager.literal("concerto").redirect(node));
    }

    public static boolean checkServerAvailable(ClientPlayerEntity player) {
        if (!ConcertoClient.serverAvailable) {
            player.sendMessage(new TranslatableText("concerto.not_available"), false);
            return false;
        }
        return true;
    }

    public static boolean checkLocal(ClientPlayerEntity player) {
        if (ConcertoClient.clientState == ConcertoClient.ClientState.LOCAL) {
            return true;
        } else {
            player.sendMessage(new TranslatableText("concerto.agent.occupied"), false);
            return false;
        }
    }

    public static boolean checkAgent(ClientPlayerEntity player) {
        if (ConcertoClient.clientState == ConcertoClient.ClientState.MUSIC_AGENT) {
            return true;
        } else {
            player.sendMessage(new TranslatableText("concerto.not_available"), false);
            return false;
        }
    }
}
