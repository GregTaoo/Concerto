package top.gregtao.concerto.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import top.gregtao.concerto.ConcertoServer;
import top.gregtao.concerto.core.config.CacheManager;
import top.gregtao.concerto.core.http.kugou.KuGouMusicApiClient;
import top.gregtao.concerto.core.http.netease.NeteaseCloudApiClient;
import top.gregtao.concerto.core.http.qq.QQMusicApiClient;
import top.gregtao.concerto.core.room.agent.ServerMusicAgent;
import top.gregtao.concerto.core.util.ConcertoRunner;
import top.gregtao.concerto.network.MusicDataPacket;
import top.gregtao.concerto.network.ServerMusicNetworkHandler;
import top.gregtao.concerto.util.CommandUtil;
import top.gregtao.concerto.util.ComponentUtil;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class ConcertoServerCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext access,
                                Commands.CommandSelection environment) {
        dispatcher.register(
                Commands.literal("concerto-server").then(
                        Commands.literal("audit").requires(source -> source.hasPermission(2)).then(
                                Commands.argument("uuid", UuidArgument.uuid()).executes(context -> {
                                    UUID uuid = UuidArgument.getUuid(context, "uuid");
                                    ServerMusicNetworkHandler.passAudition(context.getSource().getPlayer(), uuid);
                                    return 0;
                                })
                        ).then(
                                Commands.literal("reject").then(
                                        Commands.argument("uuid", UuidArgument.uuid()).executes(context -> {
                                            UUID uuid = UuidArgument.getUuid(context, "uuid");
                                            ServerMusicNetworkHandler.rejectAudition(context.getSource().getPlayer(), uuid);
                                            return 0;
                                        })
                                ).then(Commands.literal("all").executes(context -> {
                                    ServerMusicNetworkHandler.rejectAll(context.getSource().getPlayer());
                                    return 0;
                                }))
                        ).then(
                                Commands.literal("list").then(
                                        Commands.argument("page", IntegerArgumentType.integer(1)).executes(context -> {
                                            ConcertoRunner.run(() -> {
                                                int page = IntegerArgumentType.getInteger(context, "page");
                                                Map<UUID, MusicDataPacket> map = ServerMusicNetworkHandler.WAIT_AUDITION;
                                                Iterator<Map.Entry<UUID, MusicDataPacket>> iterator = map.entrySet().iterator();
                                                page = Math.min(page, (int) Math.ceil(map.size() / 10f));
                                                CommandUtil.commandMessageServer(context, CommandUtil.PAGE_SPLIT);
                                                for (int i = 1; i < 10 * (page - 1); ++i) {
                                                    if (iterator.hasNext()) iterator.next();
                                                }
                                                for (int i = 10 * (page - 1); i < Math.min(10 * page, map.size()) && iterator.hasNext(); ++i) {
                                                    Map.Entry<UUID, MusicDataPacket> entry = iterator.next();
                                                    MusicDataPacket packet = entry.getValue();
                                                    CommandUtil.commandMessageServer(context, Component.literal((i + 1) + ". ").append(chatMessageBuilder(
                                                            entry.getKey(), packet.from, packet.music.getMeta().title()
                                                    )));
                                                }
                                                CommandUtil.commandMessageServer(context, CommandUtil.PAGE_SPLIT);
                                            });
                                            return 0;
                                        })
                                )
                        )
                ).then(
                        Commands.literal("reload").requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    ConcertoServer.reload();
                                    return 0;
                                })
                ).then(
                        Commands.literal("reload-cookie").requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    NeteaseCloudApiClient.INSTANCE.readCookie();
                                    QQMusicApiClient.INSTANCE.readCookie();
                                    KuGouMusicApiClient.INSTANCE.readCookie();
                                    return 0;
                                })
                ).then(
                        Commands.literal("clean-cache").requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    CacheManager.cleanAllCache();
                                    return 0;
                                })
                ).then(
                        Commands.literal("fetch-radios")
                                .requires(source -> source.hasPermission(0)).executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if (player != null) ServerMusicNetworkHandler.sendS2CPresetRadiosPacket(player);
                                    return 0;
                                })
                ).then(
                        Commands.literal("agent").requires(source -> source.hasPermission(2)).then(
                                Commands.literal("reset").executes(context -> {
                                    ServerMusicAgent.INSTANCE.reset();
                                    return 0;
                                })
                        ).then(
                                Commands.literal("cut").executes(context -> {
                                    ServerMusicAgent.INSTANCE.schedulePlayNext(0, false);
                                    return 0;
                                })
                        ).then(
                                Commands.literal("stop").executes(context -> {
                                    ServerMusicAgent.INSTANCE.stop();
                                    return 0;
                                })
                        ).then(
                                Commands.literal("start").executes(context -> {
                                    ServerMusicAgent.INSTANCE.start();
                                    return 0;
                                })
                        )
                )
        );
    }

    public static Component chatMessageBuilder(UUID uuid, String name, String title) {
        return Component.translatable("concerto.audit.message", name, title)
                .append(Component.literal("  ["))
                .append(Component.translatable("concerto.accept").setStyle(
                        ComponentUtil.getRunCommandStyle("/concerto-server audit " + uuid).withColor(ChatFormatting.GREEN)))
                .append(Component.literal("]"))
                .append(Component.literal("  ["))
                .append(Component.translatable("concerto.reject").setStyle(
                        ComponentUtil.getRunCommandStyle("/concerto-server audit reject " + uuid).withColor(ChatFormatting.RED)))
                .append(Component.literal("]"));
    }
}
