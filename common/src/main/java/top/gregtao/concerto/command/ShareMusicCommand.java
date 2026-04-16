package top.gregtao.concerto.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import top.gregtao.concerto.core.api.UnsafeMusicException;
import top.gregtao.concerto.command.argument.ShareMusicTargetArgumentType;
import top.gregtao.concerto.core.music.Music;
import top.gregtao.concerto.network.ClientMusicNetworkHandler;
import top.gregtao.concerto.network.MusicDataPacket;
import top.gregtao.concerto.core.player.MusicPlayerHandler;
import top.gregtao.concerto.core.util.ConcertoRunner;
import top.gregtao.concerto.util.CommandUtil;
import top.gregtao.concerto.util.ComponentUtil;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class ShareMusicCommand {

    public static <S extends SharedSuggestionProvider> void register(CommandDispatcher<S> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(
                LiteralArgumentBuilder.<S>literal("sharemusic").then(
                        LiteralArgumentBuilder.<S>literal("to").then(
                                RequiredArgumentBuilder.<S, String>argument("target", ShareMusicTargetArgumentType.create()).executes(context -> {
                                    String target = ShareMusicTargetArgumentType.get(context, "target");
                                    ConcertoRunner.run(() -> {
                                        Music current = MusicPlayerHandler.INSTANCE.getCurrentMusic();
                                        if (current != null) {
                                            CommandUtil.commandMessageClient(Component.translatable("concerto.share.sent"));
                                            try {
                                                ClientMusicNetworkHandler.sendC2SMusicData(new MusicDataPacket(current, target, false));
                                            } catch (UnsafeMusicException e) {
                                                CommandUtil.commandMessageClient(Component.translatable("concerto.share.unsafe"));
                                            }
                                        } else {
                                            CommandUtil.commandMessageClient(Component.translatable("concerto.share.no_music"));
                                        }
                                    });
                                    return 0;
                                })
                        )
                ).then(
                        LiteralArgumentBuilder.<S>literal("accept").then(
                                RequiredArgumentBuilder.<S, UUID>argument("uuid", UuidArgument.uuid()).executes(context -> {
                                    UUID uuid = context.getArgument("uuid", UUID.class);
                                    ClientMusicNetworkHandler.accept(Minecraft.getInstance().player, uuid, Minecraft.getInstance());
                                    return 0;
                                })
                        )
                ).then(
                        LiteralArgumentBuilder.<S>literal("reject").then(
                                RequiredArgumentBuilder.<S, UUID>argument("uuid", UuidArgument.uuid()).executes(context -> {
                                    UUID uuid = context.getArgument("uuid", UUID.class);
                                    ClientMusicNetworkHandler.reject(Minecraft.getInstance().player, uuid, Minecraft.getInstance());
                                    return 0;
                                })
                        ).then(LiteralArgumentBuilder.<S>literal("all").executes(context -> {
                            if (Minecraft.getInstance().player == null) return -1;
                            ClientMusicNetworkHandler.rejectAll(Minecraft.getInstance().player, Minecraft.getInstance());
                            return 0;
                        }))
                ).then(
                        LiteralArgumentBuilder.<S>literal("list").then(
                                RequiredArgumentBuilder.<S, Integer>argument("page", IntegerArgumentType.integer(1)).executes(context -> {
                                    ConcertoRunner.run(() -> {
                                        int page = IntegerArgumentType.getInteger(context, "page");
                                        Map<UUID, MusicDataPacket> map = ClientMusicNetworkHandler.WAIT_CONFIRMATION;
                                        Iterator<Map.Entry<UUID, MusicDataPacket>> iterator = map.entrySet().iterator();
                                        page = Math.min(page, (int) Math.ceil(map.size() / 10f));
                                        CommandUtil.commandMessageClient(CommandUtil.PAGE_SPLIT);
                                        for (int i = 1; i < 10 * (page - 1); ++i) {
                                            if (iterator.hasNext()) iterator.next();
                                        }
                                        for (int i = 10 * (page - 1); i < Math.min(10 * page, map.size()) && iterator.hasNext(); ++i) {
                                            Map.Entry<UUID, MusicDataPacket> entry = iterator.next();
                                            MusicDataPacket packet = entry.getValue();
                                            CommandUtil.commandMessageClient(Component.literal((i + 1) + ". ").append(chatMessageBuilder(
                                                    entry.getKey(), packet.from, packet.music.getMeta().title()
                                            )));
                                        }
                                        CommandUtil.commandMessageClient(CommandUtil.PAGE_SPLIT);
                                    });
                                    return 0;
                                })
                        )
                )
        );
    }

    public static Component chatMessageBuilder(UUID uuid, String name, String title) {
        return Component.translatable("concerto.share.wait_confirmation", name, title)
                .append(Component.literal("  ["))
                .append(Component.translatable("concerto.accept").setStyle(
                        ComponentUtil.getRunCommandStyle("/sharemusic accept " + uuid).withColor(ChatFormatting.GREEN)))
                .append(Component.literal("]"))
                .append(Component.literal("  ["))
                .append(Component.translatable("concerto.reject").setStyle(
                        ComponentUtil.getRunCommandStyle("/sharemusic reject " + uuid).withColor(ChatFormatting.RED)))
                .append(Component.literal("]"));
    }
}
