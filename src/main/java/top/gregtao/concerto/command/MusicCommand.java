package top.gregtao.concerto.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;
import top.gregtao.concerto.music.meta.music.MusicMetaData;
import top.gregtao.concerto.command.argument.OrderTypeArgumentType;
import top.gregtao.concerto.command.builder.MusicAdderBuilder;
import top.gregtao.concerto.config.ClientConfig;
import top.gregtao.concerto.enums.OrderType;
import top.gregtao.concerto.enums.Sources;
import top.gregtao.concerto.music.HttpFileMusic;
import top.gregtao.concerto.music.LocalFileMusic;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.player.MusicPlayer;
import top.gregtao.concerto.player.MusicPlayerHandler;
import top.gregtao.concerto.util.TextUtil;

import java.io.File;
import java.util.List;

public class MusicCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess access) {
        LiteralCommandNode<FabricClientCommandSource> node = dispatcher.register(registerPlayerControllers(
                ClientCommandManager.literal("music")
                        .then(addMusicCommand())
                        .then(insertMusicCommand())
        ));
        dispatcher.register(ClientCommandManager.literal("concerto").redirect(node));
    }

    private static final List<MusicAdderBuilder.MusicGetter<Music>> GETTERS = List.of(
            context -> {
                LocalFileMusic music = new LocalFileMusic(StringArgumentType.getString(context, "path"));
                return Pair.of(music, Text.translatable(Sources.LOCAL_FILE.getKey("add"), music.getRawPath()));
            },
            context -> {
                HttpFileMusic music = new HttpFileMusic(StringArgumentType.getString(context, "path"));
                return Pair.of(music, Text.translatable(Sources.INTERNET.getKey("add"), music.getRawPath()));
            },
            NeteaseCloudMusicCommand::musicGetter
    );

    public static LiteralArgumentBuilder<FabricClientCommandSource> registerPlayerControllers(
            LiteralArgumentBuilder<FabricClientCommandSource> builder) {
        MusicPlayer player = MusicPlayer.INSTANCE;
        return builder.then(
                ClientCommandManager.literal("pause").executes(context -> {
                    if (player.forcePaused) {
                        player.forceResume();
                        TextUtil.commandMessageClient(context, Text.translatable("concerto.player.resume"));
                    } else {
                        player.forcePause();
                        TextUtil.commandMessageClient(context, Text.translatable("concerto.player.pause"));
                    }
                    return 0;
                })
        ).then(
                ClientCommandManager.literal("start").executes(context -> {
                    if (!player.started) {
                        player.start();
                        TextUtil.commandMessageClient(context, Text.translatable("concerto.player.start"));
                    } else {
                        TextUtil.commandMessageClient(context, Text.translatable("concerto.player.already_started"));
                    }
                    return 0;
                })
        ).then(
                ClientCommandManager.literal("stop").executes(context -> {
                    player.started = false;
                    player.playNextLock = true;
                    player.stop();
                    MusicPlayerHandler.INSTANCE.resetInfo();
                    TextUtil.commandMessageClient(context, Text.translatable("concerto.player.stop"));
                    return 0;
                })
        ).then(
                ClientCommandManager.literal("skip").executes(context -> {
                    MusicPlayer.INSTANCE.stop();
                    TextUtil.commandMessageClient(context, Text.translatable("concerto.player.skip"));
                    return 0;
                }).then(
                        ClientCommandManager.argument("index", IntegerArgumentType.integer(1)).executes(context -> {
                            int index = IntegerArgumentType.getInteger(context, "index");
                            MusicPlayer.INSTANCE.skipTo(index - 1);
                            TextUtil.commandMessageClient(context, Text.translatable("concerto.player.skip_to", index));
                            return 0;
                        })
                )
        ).then(
                ClientCommandManager.literal("cut").executes(context -> {
                    MusicPlayer.INSTANCE.cut(() -> {});
                    TextUtil.commandMessageClient(context, Text.translatable("concerto.player.cut"));
                    return 0;
                })
        ).then(
                ClientCommandManager.literal("clear").executes(context -> {
                    MusicPlayer.INSTANCE.clear();
                    TextUtil.commandMessageClient(context, Text.translatable("concerto.player.clear"));
                    return 0;
                })
        ).then(
                ClientCommandManager.literal("mode").then(
                        ClientCommandManager.argument("mode", OrderTypeArgumentType.orderType()).executes((context -> {
                            OrderType type = OrderTypeArgumentType.getOrderType(context, "mode");
                            MusicPlayerHandler.INSTANCE.setOrderType(type);
                            TextUtil.commandMessageClient(context, Text.translatable("concerto.player.mode", type.getName()));
                            return 0;
                        }))
                )
        ).then(
                ClientCommandManager.literal("reload").executes(context -> {
                    MusicPlayer.INSTANCE.reloadConfig(() ->
                            TextUtil.commandMessageClient(context, Text.translatable("concerto.player.reload")));
                    ClientConfig.INSTANCE.readOptions();
                    return 0;
                })
        ).then(
                ClientCommandManager.literal("list").then(
                        ClientCommandManager.argument("page", IntegerArgumentType.integer(1)).executes(context -> {
                            ClientPlayerEntity clientPlayer = context.getSource().getPlayer();
                            MusicPlayer.run(() -> {
                                int page = IntegerArgumentType.getInteger(context, "page");
                                List<Music> list = MusicPlayerHandler.INSTANCE.getMusicList();
                                page = Math.min(page, (int) Math.ceil(list.size() / 10f));
                                clientPlayer.sendMessage(TextUtil.PAGE_SPLIT);
                                for (int i = 10 * (page - 1); i < Math.min(10 * page, list.size()); ++i) {
                                    MusicMetaData meta = list.get(i).getMeta();
                                    clientPlayer.sendMessage(Text.literal(
                                                    (i + 1) + ". " + meta.title() + " | " + meta.author()
                                                            + " | " + meta.getSource() + " | " + meta.getDuration().toShortString())
                                            .setStyle(TextUtil.getRunCommandStyle("/concerto skip " + (i + 1))));
                                }
                                clientPlayer.sendMessage(TextUtil.PAGE_SPLIT);
                            });
                            return 0;
                        })
                )
        );
    }

    public static ArgumentBuilder<FabricClientCommandSource, ?> addMusicCommand() {
        return ClientCommandManager.literal("add").then(
                ClientCommandManager.literal("local").then(
                        ClientCommandManager.argument("path", StringArgumentType.string()).executes(
                                context -> MusicAdderBuilder.execute(context, GETTERS.get(0).get(context), false)
                        )
                ).then(
                        ClientCommandManager.literal("folder").then(
                                ClientCommandManager.argument("path", StringArgumentType.string()).executes(context -> {
                                    String path = StringArgumentType.getString(context, "path");
                                    MusicPlayer.INSTANCE.addMusic(
                                            () -> LocalFileMusic.getMusicsInFolder(new File(path)),
                                            () -> context.getSource().getPlayer().sendMessage(
                                                    Text.translatable(Sources.LOCAL_FILE.getKey("add"), path))
                                    );
                                    return 0;
                                })
                        )
                )
        ).then(
                ClientCommandManager.literal("http").then(
                        ClientCommandManager.argument("path", StringArgumentType.string()).executes(
                                context -> MusicAdderBuilder.execute(context, GETTERS.get(1).get(context), false)
                        )
                )
        ).then(
                ClientCommandManager.literal("163").then(
                        NeteaseCloudMusicCommand.builderWithIdAndLevel(
                                context -> MusicAdderBuilder.execute(context, GETTERS.get(2).get(context), false))
                ).then(
                        ClientCommandManager.literal("playlist").then(
                                NeteaseCloudMusicCommand.builderWithIdAndLevel(NeteaseCloudMusicCommand::addPlaylistExecutor)
                        )
                ).then(
                        ClientCommandManager.literal("album").then(
                                NeteaseCloudMusicCommand.builderWithIdAndLevel(NeteaseCloudMusicCommand::addAlbumExecutor)
                        )
                )
        );
    }

    public static ArgumentBuilder<FabricClientCommandSource, ?> insertMusicCommand() {
        return ClientCommandManager.literal("insert").then(
                ClientCommandManager.literal("local").then(
                        ClientCommandManager.argument("path", StringArgumentType.string()).executes(
                                context -> MusicAdderBuilder.execute(context, GETTERS.get(0).get(context), true)
                        )
                )
        ).then(
                ClientCommandManager.literal("http").then(
                        ClientCommandManager.argument("path", StringArgumentType.string()).executes(
                                context -> MusicAdderBuilder.execute(context, GETTERS.get(1).get(context), true)
                        )
                )
        ).then(
                ClientCommandManager.literal("163").then(
                        ClientCommandManager.argument("id", StringArgumentType.string()).executes(
                                context -> MusicAdderBuilder.execute(context, GETTERS.get(2).get(context), true)
                        )
                )
        );
    }
}
