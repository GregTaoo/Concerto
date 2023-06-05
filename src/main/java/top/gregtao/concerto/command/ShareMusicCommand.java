package top.gregtao.concerto.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.UuidArgumentType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import top.gregtao.concerto.music.UnsafeMusicException;
import top.gregtao.concerto.command.argument.ShareMusicTargetArgumentType;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.network.ClientMusicNetworkHandler;
import top.gregtao.concerto.network.MusicDataPacket;
import top.gregtao.concerto.player.MusicPlayer;
import top.gregtao.concerto.player.MusicPlayerStatus;
import top.gregtao.concerto.util.TextUtil;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class ShareMusicCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        dispatcher.register(
                ClientCommandManager.literal("sharemusic").then(
                        ClientCommandManager.literal("to").then(
                                ClientCommandManager.argument("target", ShareMusicTargetArgumentType.create()).executes(context -> {
                                    String target = ShareMusicTargetArgumentType.get(context, "target");
                                    MusicPlayer.executeThread(() -> {
                                        Music current = MusicPlayerStatus.INSTANCE.getCurrentMusic();
                                        if (current != null) {
                                            TextUtil.commandMessageClient(context, Text.translatable("concerto.share.sent"));
                                            try {
                                                ClientMusicNetworkHandler.sendC2SMusicData(new MusicDataPacket(current, target, false));
                                            } catch (UnsafeMusicException e) {
                                                TextUtil.commandMessageClient(context, Text.translatable("concerto.share.unsafe"));
                                            }
                                        } else {
                                            TextUtil.commandMessageClient(context, Text.translatable("concerto.share.no_music"));
                                        }
                                    });
                                    return 0;
                                })
                        )
                ).then(
                        ClientCommandManager.literal("accept").then(
                                ClientCommandManager.argument("uuid", UuidArgumentType.uuid()).executes(context -> {
                                    UUID uuid = context.getArgument("uuid", UUID.class);
                                    ClientMusicNetworkHandler.accept(context.getSource().getPlayer(), uuid, MinecraftClient.getInstance());
                                    return 0;
                                })
                        )
                ).then(
                        ClientCommandManager.literal("reject").then(
                                ClientCommandManager.argument("uuid", UuidArgumentType.uuid()).executes(context -> {
                                    UUID uuid = context.getArgument("uuid", UUID.class);
                                    ClientMusicNetworkHandler.reject(context.getSource().getPlayer(), uuid, MinecraftClient.getInstance());
                                    return 0;
                                })
                        ).then(ClientCommandManager.literal("all").executes(context -> {
                            ClientMusicNetworkHandler.rejectAll(context.getSource().getPlayer(), MinecraftClient.getInstance());
                            return 0;
                        }))
                ).then(
                        ClientCommandManager.literal("list").then(
                                ClientCommandManager.argument("page", IntegerArgumentType.integer(1)).executes(context -> {
                                    MusicPlayer.executeThread(() -> {
                                        int page = IntegerArgumentType.getInteger(context, "page");
                                        Map<UUID, MusicDataPacket> map = ClientMusicNetworkHandler.WAIT_CONFIRMATION;
                                        Iterator<Map.Entry<UUID, MusicDataPacket>> iterator = map.entrySet().iterator();
                                        page = Math.min(page, (int) Math.ceil(map.size() / 10f));
                                        TextUtil.commandMessageClient(context, TextUtil.PAGE_SPLIT);
                                        for (int i = 1; i < 10 * (page - 1); ++i) {
                                            if (iterator.hasNext()) iterator.next();
                                        }
                                        for (int i = 10 * (page - 1); i < Math.min(10 * page, map.size()) && iterator.hasNext(); ++i) {
                                            Map.Entry<UUID, MusicDataPacket> entry = iterator.next();
                                            MusicDataPacket packet = entry.getValue();
                                            TextUtil.commandMessageClient(context, Text.literal((i + 1) + ". ").append(chatMessageBuilder(
                                                    entry.getKey(), packet.from, packet.music.getMeta().title()
                                            )));
                                        }
                                        TextUtil.commandMessageClient(context, TextUtil.PAGE_SPLIT);
                                    });
                                    return 0;
                                })
                        )
                )
        );
    }

    public static Text chatMessageBuilder(UUID uuid, String name, String title) {
        return Text.translatable("concerto.share.wait_confirmation", name, title)
                .append(Text.literal("  ["))
                .append(Text.translatable("concerto.accept").setStyle(
                        TextUtil.getRunCommandStyle("/sharemusic accept " + uuid).withColor(Formatting.GREEN)))
                .append(Text.literal("]"))
                .append(Text.literal("  ["))
                .append(Text.translatable("concerto.reject").setStyle(
                        TextUtil.getRunCommandStyle("/sharemusic reject " + uuid).withColor(Formatting.RED)))
                .append(Text.literal("]"));
    }
}
