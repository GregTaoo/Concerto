package top.gregtao.concerto.paper.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import top.gregtao.concerto.core.config.CacheManager;
import top.gregtao.concerto.core.http.kugou.KuGouMusicApiClient;
import top.gregtao.concerto.core.http.netease.NeteaseCloudApiClient;
import top.gregtao.concerto.core.http.qq.QQMusicApiClient;
import top.gregtao.concerto.core.room.agent.ServerMusicAgent;
import top.gregtao.concerto.core.util.ConcertoRunner;
import top.gregtao.concerto.paper.util.ComponentUtil;
import top.gregtao.concerto.paper.ConcertoPaperPlugin;
import top.gregtao.concerto.paper.network.MusicDataPacket;
import top.gregtao.concerto.paper.network.ServerMusicNetworkHandler;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class ConcertoServerCommand {

    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("concerto-server").then(
                Commands.literal("audit").requires(source -> source.getExecutor() != null && source.getExecutor().isOp()).then(
                        Commands.argument("uuid", ArgumentTypes.uuid()).executes(context -> {
                            UUID uuid = context.getArgument("uuid", UUID.class);
                            ServerMusicNetworkHandler.passAudition(context.getSource().getSender(), uuid);
                            return 0;
                        })
                ).then(
                        Commands.literal("reject").then(
                                Commands.argument("uuid", ArgumentTypes.uuid()).executes(context -> {
                                    UUID uuid = context.getArgument("uuid", UUID.class);
                                    ServerMusicNetworkHandler.rejectAudition(context.getSource().getSender(), uuid);
                                    return 0;
                                })
                        ).then(Commands.literal("all").executes(context -> {
                            ServerMusicNetworkHandler.rejectAll(context.getSource().getSender());
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
                                        context.getSource().getSender().sendMessage(ComponentUtil.PAGE_SPLIT);
                                        for (int i = 1; i < 10 * (page - 1); ++i) {
                                            if (iterator.hasNext()) iterator.next();
                                        }
                                        for (int i = 10 * (page - 1); i < Math.min(10 * page, map.size()) && iterator.hasNext(); ++i) {
                                            Map.Entry<UUID, MusicDataPacket> entry = iterator.next();
                                            MusicDataPacket packet = entry.getValue();
                                            context.getSource().getSender().sendMessage(Component.text((i + 1) + ". ").append(chatMessageBuilder(
                                                    entry.getKey(), packet.from, packet.music.getMeta().title()
                                            )));
                                        }
                                        context.getSource().getSender().sendMessage(ComponentUtil.PAGE_SPLIT);
                                    });
                                    return 0;
                                })
                        )
                )
        ).then(
                Commands.literal("reload").requires(source -> source.getExecutor() != null && source.getExecutor().isOp())
                        .executes(context -> {
                            ConcertoPaperPlugin.reload();
                            return 0;
                        })
        ).then(
                Commands.literal("reload-cookie").requires(source -> source.getExecutor() != null && source.getExecutor().isOp())
                        .executes(context -> {
                            NeteaseCloudApiClient.INSTANCE.readCookie();
                            QQMusicApiClient.INSTANCE.readCookie();
                            KuGouMusicApiClient.INSTANCE.readCookie();
                            return 0;
                        })
        ).then(
                Commands.literal("clean-cache").requires(source -> source.getExecutor() != null && source.getExecutor().isOp())
                        .executes(context -> {
                            CacheManager.cleanAllCache();
                            return 0;
                        })
        ).then(
                Commands.literal("fetch-radios")
                        .requires(source -> true).executes(context -> {
                            Player player = Bukkit.getPlayer(context.getSource().getSender().getName());
                            ServerMusicNetworkHandler.sendS2CPresetRadiosPacket(player);
                            return 0;
                        })
        ).then(
                Commands.literal("agent").requires(source -> source.getExecutor() != null && source.getExecutor().isOp()).then(
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
        ).build();
    }

    public static Component chatMessageBuilder(UUID uuid, String name, String title) {
        return Component.translatable("concerto.audit.message", Component.text(name), Component.text(title))
                .append(Component.text("  ["))
                .append(Component.translatable("concerto.accept")
                        .clickEvent(ClickEvent.runCommand("/concerto-server audit " + uuid))
                        .color(TextColor.color(5635925)))
                .append(Component.text("]"))
                .append(Component.text("  ["))
                .append(Component.translatable("concerto.reject")
                        .clickEvent(ClickEvent.runCommand("/concerto-server audit reject " + uuid))
                        .color(TextColor.color(16733525)))
                .append(Component.text("]"));
    }
}
