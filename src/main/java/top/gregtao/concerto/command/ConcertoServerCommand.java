package top.gregtao.concerto.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.UuidArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import top.gregtao.concerto.ConcertoServer;
import top.gregtao.concerto.config.CacheManager;
import top.gregtao.concerto.http.kugou.KuGouMusicApiClient;
import top.gregtao.concerto.http.netease.NeteaseCloudApiClient;
import top.gregtao.concerto.http.qq.QQMusicApiClient;
import top.gregtao.concerto.network.MusicDataPacket;
import top.gregtao.concerto.network.room.ServerMusicAgent;
import top.gregtao.concerto.network.ServerMusicNetworkHandler;
import top.gregtao.concerto.util.ConcertoRunner;
import top.gregtao.concerto.util.TextUtil;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class ConcertoServerCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess access,
                                CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
                CommandManager.literal("concerto-server").then(
                        CommandManager.literal("audit").requires(source -> source.hasPermissionLevel(2)).then(
                                CommandManager.argument("uuid", UuidArgumentType.uuid()).executes(context -> {
                                    UUID uuid = UuidArgumentType.getUuid(context, "uuid");
                                    ServerMusicNetworkHandler.passAudition(context.getSource().getPlayer(), uuid);
                                    return 0;
                                })
                        ).then(
                                CommandManager.literal("reject").then(
                                        CommandManager.argument("uuid", UuidArgumentType.uuid()).executes(context -> {
                                            UUID uuid = UuidArgumentType.getUuid(context, "uuid");
                                            ServerMusicNetworkHandler.rejectAudition(context.getSource().getPlayer(), uuid);
                                            return 0;
                                        })
                                ).then(CommandManager.literal("all").executes(context -> {
                                    ServerMusicNetworkHandler.rejectAll(context.getSource().getPlayer());
                                    return 0;
                                }))
                        ).then(
                                CommandManager.literal("list").then(
                                        CommandManager.argument("page", IntegerArgumentType.integer(1)).executes(context -> {
                                            ConcertoRunner.run(() -> {
                                                int page = IntegerArgumentType.getInteger(context, "page");
                                                Map<UUID, MusicDataPacket> map = ServerMusicNetworkHandler.WAIT_AUDITION;
                                                Iterator<Map.Entry<UUID, MusicDataPacket>> iterator = map.entrySet().iterator();
                                                page = Math.min(page, (int) Math.ceil(map.size() / 10f));
                                                TextUtil.commandMessageServer(context, TextUtil.PAGE_SPLIT);
                                                for (int i = 1; i < 10 * (page - 1); ++i) {
                                                    if (iterator.hasNext()) iterator.next();
                                                }
                                                for (int i = 10 * (page - 1); i < Math.min(10 * page, map.size()) && iterator.hasNext(); ++i) {
                                                    Map.Entry<UUID, MusicDataPacket> entry = iterator.next();
                                                    MusicDataPacket packet = entry.getValue();
                                                    TextUtil.commandMessageServer(context, Text.literal((i + 1) + ". ").append(chatMessageBuilder(
                                                            entry.getKey(), packet.from, packet.music.getMeta().title()
                                                    )));
                                                }
                                                TextUtil.commandMessageServer(context, TextUtil.PAGE_SPLIT);
                                            });
                                            return 0;
                                        })
                                )
                        )
                ).then(
                        CommandManager.literal("reload").requires(source -> source.hasPermissionLevel(2))
                                .executes(context -> {
                                    ConcertoServer.reload();
                                    return 0;
                                })
                ).then(
                        CommandManager.literal("reload-cookie").requires(source -> source.hasPermissionLevel(2))
                                .executes(context -> {
                                    NeteaseCloudApiClient.INSTANCE.readCookie();
                                    QQMusicApiClient.INSTANCE.readCookie();
                                    KuGouMusicApiClient.INSTANCE.readCookie();
                                    return 0;
                                })
                ).then(
                        CommandManager.literal("clean-cache").requires(source -> source.hasPermissionLevel(2))
                                .executes(context -> {
                                    CacheManager.cleanAllCache();
                                    return 0;
                                })
                ).then(
                        CommandManager.literal("fetch-radios")
                                .requires(source -> source.hasPermissionLevel(0)).executes(context -> {
                                    ServerPlayerEntity player = context.getSource().getPlayer();
                                    if (player != null) ServerMusicNetworkHandler.sendS2CPresetRadiosPacket(player);
                                    return 0;
                                })
                ).then(
                        CommandManager.literal("agent").requires(source -> source.hasPermissionLevel(2)).then(
                                CommandManager.literal("reset").executes(context -> {
                                    ServerMusicAgent.INSTANCE.reset();
                                    return 0;
                                })
                        ).then(
                                CommandManager.literal("cut").executes(context -> {
                                    ServerMusicAgent.INSTANCE.schedulePlayNext(0, false);
                                    return 0;
                                })
                        ).then(
                                CommandManager.literal("stop").executes(context -> {
                                    ServerMusicAgent.INSTANCE.stop();
                                    return 0;
                                })
                        ).then(
                                CommandManager.literal("start").executes(context -> {
                                    ServerMusicAgent.INSTANCE.start();
                                    return 0;
                                })
                        )
                )
        );
    }

    public static Text chatMessageBuilder(UUID uuid, String name, String title) {
        return Text.translatable("concerto.audit.message", name, title)
                .append(Text.literal("  ["))
                .append(Text.translatable("concerto.accept").setStyle(
                        TextUtil.getRunCommandStyle("/concerto-server audit " + uuid).withColor(Formatting.GREEN)))
                .append(Text.literal("]"))
                .append(Text.literal("  ["))
                .append(Text.translatable("concerto.reject").setStyle(
                        TextUtil.getRunCommandStyle("/concerto-server audit reject " + uuid).withColor(Formatting.RED)))
                .append(Text.literal("]"));
    }
}
