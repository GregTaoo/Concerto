package top.gregtao.concerto.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import top.gregtao.concerto.api.CacheableMusic;
import top.gregtao.concerto.api.Likeable;
import top.gregtao.concerto.config.MusicCacheManager;
import top.gregtao.concerto.config.PresetRadioConfig;
import top.gregtao.concerto.music.list.FixedPlaylist;
import top.gregtao.concerto.music.meta.music.MusicMetaData;
import top.gregtao.concerto.command.argument.OrderTypeArgumentType;
import top.gregtao.concerto.command.builder.MusicAdderBuilder;
import top.gregtao.concerto.config.ClientConfig;
import top.gregtao.concerto.enums.OrderType;
import top.gregtao.concerto.enums.Sources;
import top.gregtao.concerto.music.HttpFileMusic;
import top.gregtao.concerto.music.LocalFileMusic;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.music.meta.music.list.PlaylistMetaData;
import top.gregtao.concerto.player.MusicPlayer;
import top.gregtao.concerto.player.MusicPlayerHandler;
import top.gregtao.concerto.util.Pair;
import top.gregtao.concerto.util.TextUtil;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MusicCommand {

    public static void register() {
        LiteralCommandNode<FabricClientCommandSource> node = ClientCommandManager.DISPATCHER.register(registerPlayerControllers(
                ClientCommandManager.literal("concerto")
                        .then(addMusicCommand())
                        .then(insertMusicCommand())
        ));
        if (ClientConfig.INSTANCE.options.registerMusicCommand) {
            ClientCommandManager.DISPATCHER.register(ClientCommandManager.literal("music").redirect(node));
        }
    }

    private static final List<MusicAdderBuilder.MusicGetter<Music>> GETTERS = List.of(
            context -> {
                LocalFileMusic music = new LocalFileMusic(StringArgumentType.getString(context, "path"));
                return Pair.of(music, new TranslatableText(Sources.LOCAL_FILE.getKey("add"), music.getRawPath()));
            },
            context -> {
                HttpFileMusic music = new HttpFileMusic(StringArgumentType.getString(context, "path"));
                return Pair.of(music, new TranslatableText(Sources.INTERNET.getKey("add"), music.getRawPath()));
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
                        TextUtil.commandMessageClient(context, new TranslatableText("concerto.player.resume"));
                    } else {
                        player.forcePause();
                        TextUtil.commandMessageClient(context, new TranslatableText("concerto.player.pause"));
                    }
                    return 0;
                })
        ).then(
                ClientCommandManager.literal("start").executes(context -> {
                    if (!player.started) {
                        player.start();
                        TextUtil.commandMessageClient(context, new TranslatableText("concerto.player.start"));
                    } else {
                        TextUtil.commandMessageClient(context, new TranslatableText("concerto.player.already_started"));
                    }
                    return 0;
                })
        ).then(
                ClientCommandManager.literal("stop").executes(context -> {
                    player.started = false;
                    player.playNextLock = true;
                    player.stop();
                    MusicPlayerHandler.INSTANCE.resetInfo();
                    TextUtil.commandMessageClient(context, new TranslatableText("concerto.player.stop"));
                    return 0;
                })
        ).then(
                ClientCommandManager.literal("skip").executes(context -> {
                    MusicPlayer.INSTANCE.stop();
                    TextUtil.commandMessageClient(context, new TranslatableText("concerto.player.skip"));
                    return 0;
                }).then(
                        ClientCommandManager.argument("index", IntegerArgumentType.integer(1)).executes(context -> {
                            int index = IntegerArgumentType.getInteger(context, "index");
                            MusicPlayer.INSTANCE.skipTo(index - 1);
                            TextUtil.commandMessageClient(context, new TranslatableText("concerto.player.skip_to", index));
                            return 0;
                        })
                )
        ).then(
                ClientCommandManager.literal("cut").executes(context -> {
                    MusicPlayer.INSTANCE.cut(() -> {});
                    TextUtil.commandMessageClient(context, new TranslatableText("concerto.player.cut"));
                    return 0;
                })
        ).then(
                ClientCommandManager.literal("clear").executes(context -> {
                    MusicPlayer.INSTANCE.clear();
                    MusicPlayer.resetInstance();
                    TextUtil.commandMessageClient(context, new TranslatableText("concerto.player.clear"));
                    return 0;
                })
        ).then(
                ClientCommandManager.literal("mode").then(
                        ClientCommandManager.argument("mode", OrderTypeArgumentType.orderType()).executes((context -> {
                            OrderType type = OrderTypeArgumentType.getOrderType(context, "mode");
                            MusicPlayerHandler.INSTANCE.setOrderType(type);
                            TextUtil.commandMessageClient(context, new TranslatableText("concerto.player.mode", type.getName().getString()));
                            return 0;
                        }))
                )
        ).then(
                ClientCommandManager.literal("reload").executes(context -> {
                    MusicPlayer.INSTANCE.reloadConfig(() ->
                            TextUtil.commandMessageClient(context, new TranslatableText("concerto.player.reload")));
                    ClientConfig.INSTANCE.readOptions();
                    MusicPlayer.resetInstance();
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
                                clientPlayer.sendMessage(TextUtil.PAGE_SPLIT, false);
                                for (int i = 10 * (page - 1); i < Math.min(10 * page, list.size()); ++i) {
                                    MusicMetaData meta = list.get(i).getMeta();
                                    clientPlayer.sendMessage(new LiteralText(
                                                    (i + 1) + ". " + meta.title() + " | " + meta.author()
                                                            + " | " + meta.getSource() + " | " + meta.getDuration().toShortString())
                                            .setStyle(TextUtil.getRunCommandStyle("/concerto skip " + (i + 1))), false);
                                }
                                clientPlayer.sendMessage(TextUtil.PAGE_SPLIT, false);
                            });
                            return 0;
                        })
                )
        ).then(
                ClientCommandManager.literal("save").executes(context -> {
                    ClientPlayerEntity clientPlayer = context.getSource().getPlayer();
                    if (MusicPlayerHandler.INSTANCE.currentMusic == null) {
                        clientPlayer.sendMessage(new TranslatableText("concerto.unknown"), false);
                    } else if (MusicPlayerHandler.INSTANCE.currentMusic instanceof CacheableMusic music) {
                        MusicPlayer.run(() -> {
                            try {
                                MusicCacheManager.INSTANCE.addMusic(music);
                                clientPlayer.sendMessage(new TranslatableText("concerto.success"), false);
                            } catch (IOException | UnsupportedAudioFileException e) {
                                throw new RuntimeException(e);
                            }
                        });
                    } else {
                        clientPlayer.sendMessage(new TranslatableText("concerto.not_cacheable"), false);
                    }
                    return 0;
                })
        ).then(
                ClientCommandManager.literal("like").executes(context -> {
                    ClientPlayerEntity clientPlayer = context.getSource().getPlayer();
                    Music music = MusicPlayerHandler.INSTANCE.getCurrentMusic();
                    if (music instanceof Likeable likeable) {
                        CompletableFuture.supplyAsync(likeable::likeIt, MusicPlayer.RUNNERS_POOL).thenAcceptAsync(success ->
                                clientPlayer.sendMessage(success ? new TranslatableText("concerto.like",
                                        music.getMeta().title(), music.getMeta().getSource()) :
                                        new TranslatableText("concerto.fail"), false), MusicPlayer.RUNNERS_POOL);
                    } else {
                        clientPlayer.sendMessage(new TranslatableText("concerto.error.unsupported_operation"), false);
                    }
                    return 0;
                })
        ).then(
                ClientCommandManager.literal("dislike").executes(context -> {
                    ClientPlayerEntity clientPlayer = context.getSource().getPlayer();
                    Music music = MusicPlayerHandler.INSTANCE.getCurrentMusic();
                    if (music instanceof Likeable likeable) {
                        CompletableFuture.supplyAsync(likeable::dislikeIt, MusicPlayer.RUNNERS_POOL).thenAcceptAsync(success ->
                                clientPlayer.sendMessage(success ? new TranslatableText("concerto.dislike",
                                        music.getMeta().title(), music.getMeta().getSource()) :
                                        new TranslatableText("concerto.fail"), false), MusicPlayer.RUNNERS_POOL);
                    } else {
                        clientPlayer.sendMessage(new TranslatableText("concerto.error.unsupported_operation"), false);
                    }
                    return 0;
                })
        ).then(
                ClientCommandManager.literal("download-current").executes(context -> {
                    MusicPlayerHandler.downloadMusics(List.of(MusicPlayerHandler.INSTANCE.getCurrentMusic()));
                    return 0;
                })
        ).then(
                ClientCommandManager.literal("download-all").executes(context -> {
                    MusicPlayerHandler.downloadMusics(MusicPlayerHandler.INSTANCE.getMusicList());
                    return 0;
                })
        ).then(
                ClientCommandManager.literal("export-as-playlist").executes(context -> {
                    ClientPlayerEntity clientPlayer = context.getSource().getPlayer();
                    Text playerName = clientPlayer.getDisplayName();
                    if (PresetRadioConfig.saveToTmpFile(new FixedPlaylist(
                            MusicPlayerHandler.INSTANCE.getMusicList(),
                            new PlaylistMetaData(
                                    playerName == null ? "Unknown" : playerName.getString(),
                                    "Default Playlist",
                                    LocalDateTime.now().toString(),
                                    "Default Playlist"
                            ),
                            false
                    ))) {
                        clientPlayer.sendMessage(new TranslatableText("concerto.playlist.export.success"), false);
                    } else {
                        clientPlayer.sendMessage(new TranslatableText("concerto.playlist.export.fail"), false);
                    }
                    return 0;
                })
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
                                                    new TranslatableText(Sources.LOCAL_FILE.getKey("add"), path), false)
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
