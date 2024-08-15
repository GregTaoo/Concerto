package top.gregtao.concerto.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;
import top.gregtao.concerto.network.MusicRoom;

public class MusicRoomCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess access) {
        LiteralCommandNode<FabricClientCommandSource> node = dispatcher.register(
                ClientCommandManager.literal("musicroom")
                        .then(ClientCommandManager.literal("create").executes(context -> {
                            MusicRoom.clientCreate();
                            return 0;
                        })).then(ClientCommandManager.literal("join").then(
                                ClientCommandManager.argument("uuid", StringArgumentType.string()).executes(context -> {
                                    MusicRoom.clientJoin(StringArgumentType.getString(context, "uuid"));
                                    return 0;
                                })
                        )).then(ClientCommandManager.literal("quit").executes(context -> {
                            MusicRoom.clientQuit();
                            return 0;
                        })).then(ClientCommandManager.literal("members").executes(context -> {
                            if (MusicRoom.CLIENT_ROOM != null) {
                                context.getSource().getPlayer().sendMessage(Text.of(
                                        "Admin: " + MusicRoom.CLIENT_ROOM.admin + "; Members: " + String.join(",", MusicRoom.CLIENT_ROOM.members.keySet())
                                ));
                            }
                            return 0;
                        })).then(ClientCommandManager.literal("op").then(
                                ClientCommandManager.argument("player", StringArgumentType.string()).executes(context -> {
                                    MusicRoom.clientSetOp(StringArgumentType.getString(context, "player"));
                                    return 0;
                                })
                        ))
        );
        dispatcher.register(ClientCommandManager.literal("concerto").redirect(node));
    }
}
