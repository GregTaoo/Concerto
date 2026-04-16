package top.gregtao.concerto.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.core.room.MusicRoom;
import top.gregtao.concerto.network.room.MusicRoomManager;
import top.gregtao.concerto.network.room.ServerMusicAgentManager;

public class MusicRoomCommand {

    public static <S extends SharedSuggestionProvider> void register(CommandDispatcher<S> dispatcher, CommandBuildContext access) {
        dispatcher.register(
                LiteralArgumentBuilder.<S>literal("musicroom")
                        .then(LiteralArgumentBuilder.<S>literal("create").executes(context -> {
                            LocalPlayer player = Minecraft.getInstance().player;
                            if (player == null) return -1;
                            if (checkServerAvailable(player) && checkLocal(player)) {
                                MusicRoomManager.clientCreate();
                            }
                            return 0;
                        })).then(LiteralArgumentBuilder.<S>literal("join").then(
                                RequiredArgumentBuilder.<S, String>argument("uuid", StringArgumentType.string()).executes(context -> {
                                    LocalPlayer player = Minecraft.getInstance().player;
                                    if (player == null) return -1;
                                    if (checkServerAvailable(player) && checkLocal(player)) {
                                        MusicRoomManager.clientJoin(StringArgumentType.getString(context, "uuid"));
                                    }
                                    return 0;
                                })
                        )).then(LiteralArgumentBuilder.<S>literal("quit").executes(context -> {
                            MusicRoomManager.clientQuit();
                            return 0;
                        })).then(LiteralArgumentBuilder.<S>literal("remove").executes(context -> {
                            MusicRoomManager.clientRemove();
                            return 0;
                        })).then(LiteralArgumentBuilder.<S>literal("members").executes(context -> {
                            LocalPlayer player = Minecraft.getInstance().player;
                            if (player == null) return -1;
                            if (MusicRoom.CLIENT_ROOM != null) {
                                player.displayClientMessage(Component.translatable(
                                        "concerto.room.members", MusicRoom.CLIENT_ROOM.clientGetOwner(),
                                        String.join(",", MusicRoom.CLIENT_ROOM.clientGetMembers().keySet())
                                ), false);
                            }
                            return 0;
                        })).then(LiteralArgumentBuilder.<S>literal("op").then(
                                RequiredArgumentBuilder.<S, String>argument("player", StringArgumentType.string()).executes(context -> {
                                    MusicRoomManager.clientSetOp(StringArgumentType.getString(context, "player"));
                                    return 0;
                                })
                        )).then(
                                LiteralArgumentBuilder.<S>literal("agent").then(
                                        LiteralArgumentBuilder.<S>literal("join").executes(context -> {
                                            LocalPlayer player = Minecraft.getInstance().player;
                                            if (player == null) return -1;
                                            if (checkServerAvailable(player) && checkLocal(player)) {
                                                ServerMusicAgentManager.clientJoin();
                                            }
                                            return 0;
                                        })
                                ).then(
                                        LiteralArgumentBuilder.<S>literal("quit").executes(context -> {
                                            LocalPlayer player = Minecraft.getInstance().player;
                                            if (player == null) return -1;
                                            if (checkServerAvailable(player) && checkAgent(player)) {
                                                ServerMusicAgentManager.clientQuit();
                                            }
                                            return 0;
                                        })
                                ).then(
                                        LiteralArgumentBuilder.<S>literal("add").executes(context -> {
                                            LocalPlayer player = Minecraft.getInstance().player;
                                            if (player == null) return -1;
                                            if (checkServerAvailable(player) && checkAgent(player)) {
                                                if (!ServerMusicAgentManager.clientAddCurrentMusic()) {
                                                    player.displayClientMessage(Component.translatable("concerto.not_playing"), false);
                                                }
                                            }
                                            return 0;
                                        })
                                ).then(
                                        LiteralArgumentBuilder.<S>literal("vote").executes(context -> {
                                            LocalPlayer player = Minecraft.getInstance().player;
                                            if (player == null) return -1;
                                            if (checkServerAvailable(player) && checkAgent(player)) {
                                                ServerMusicAgentManager.clientNewVote();
                                            }
                                            return 0;
                                        }).then(
                                                RequiredArgumentBuilder.<S, Boolean>argument("vote", BoolArgumentType.bool()).executes(context -> {
                                                    LocalPlayer player = Minecraft.getInstance().player;
                                                    if (player == null) return -1;
                                                    if (checkServerAvailable(player) && checkAgent(player)) {
                                                        ServerMusicAgentManager.clientVote(BoolArgumentType.getBool(context, "vote"));
                                                    }
                                                    return 0;
                                                })
                                        )
                                )
                        )
        );
    }

    public static boolean checkServerAvailable(LocalPlayer player) {
        if (!ConcertoClient.isServerAvailable()) {
            player.displayClientMessage(Component.translatable("concerto.not_available"), false);
            return false;
        }
        return true;
    }

    public static boolean checkLocal(LocalPlayer player) {
        if (MusicRoom.clientGetState() == MusicRoom.ClientState.LOCAL) {
            return true;
        } else {
            player.displayClientMessage(Component.translatable("concerto.agent.occupied"), false);
            return false;
        }
    }

    public static boolean checkAgent(LocalPlayer player) {
        if (MusicRoom.clientGetState() == MusicRoom.ClientState.MUSIC_AGENT) {
            return true;
        } else {
            player.displayClientMessage(Component.translatable("concerto.agent.not_in"), false);
            return false;
        }
    }
}
