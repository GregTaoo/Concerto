package top.gregtao.concerto.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.UuidArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import top.gregtao.concerto.config.ServerConfig;
import top.gregtao.concerto.network.MusicDataPacket;
import top.gregtao.concerto.network.ServerMusicNetworkHandler;
import top.gregtao.concerto.player.MusicPlayer;
import top.gregtao.concerto.util.TextUtil;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class AuditCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess access,
                                CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
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
                        )
                ).then(
                        CommandManager.literal("list").then(
                                CommandManager.argument("page", IntegerArgumentType.integer(1)).executes(context -> {
                                    MusicPlayer.executeThread(() -> {
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
                ).then(
                        CommandManager.literal("reload").executes(context -> {
                            ServerConfig.INSTANCE.readOptions();
                            return 0;
                        })
                )
        );
    }

    public static Text chatMessageBuilder(UUID uuid, String name, String title) {
        return Text.translatable("concerto.audit.message", name, title)
                .append(Text.literal("  ["))
                .append(Text.translatable("concerto.accept").setStyle(
                        TextUtil.getRunCommandStyle("/audit " + uuid).withColor(Formatting.GREEN)))
                .append(Text.literal("]"))
                .append(Text.literal("  ["))
                .append(Text.translatable("concerto.reject").setStyle(
                        TextUtil.getRunCommandStyle("/audit reject " + uuid).withColor(Formatting.RED)))
                .append(Text.literal("]"));
    }
}
