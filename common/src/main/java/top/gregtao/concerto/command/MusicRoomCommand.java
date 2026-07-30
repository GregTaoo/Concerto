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
                        }).then(RequiredArgumentBuilder.<S, String>argument("name", StringArgumentType.greedyString()).executes(context -> {
                            LocalPlayer player = Minecraft.getInstance().player;
                            if (player == null) return -1;
                            if (checkServerAvailable(player) && checkLocal(player)) {
                                MusicRoomManager.clientCreate(StringArgumentType.getString(context, "name"));
                            }
                            return 0;
                        }))).then(LiteralArgumentBuilder.<S>literal("join").then(
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
                                player.sendSystemMessage(Component.translatable(
                                        "concerto.room.members", MusicRoom.CLIENT_ROOM.clientGetOwner(),
                                        String.join(",", MusicRoom.CLIENT_ROOM.clientGetMembers().keySet())
                                ));
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
                                                    player.sendSystemMessage(Component.translatable("concerto.not_playing"));
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
            player.sendSystemMessage(Component.translatable("concerto.not_available"));
            return false;
        }
        return true;
    }

    public static boolean checkLocal(LocalPlayer player) {
        if (MusicRoom.clientGetState() == MusicRoom.ClientState.LOCAL) {
            return true;
        } else {
            player.sendSystemMessage(Component.translatable("concerto.agent.occupied"));
            return false;
        }
    }

    public static boolean checkAgent(LocalPlayer player) {
        if (MusicRoom.clientGetState() == MusicRoom.ClientState.MUSIC_AGENT) {
            return true;
        } else {
            player.sendSystemMessage(Component.translatable("concerto.agent.not_in"));
            return false;
        }
    }
}
