package top.gregtao.concerto.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;
import top.gregtao.concerto.core.api.CacheableMusic;
import top.gregtao.concerto.core.api.Likeable;
import top.gregtao.concerto.core.config.CacheManager;
import top.gregtao.concerto.core.config.MusicCacheManager;
import top.gregtao.concerto.config.PresetPlaylistsConfig;
import top.gregtao.concerto.core.music.list.FixedPlaylist;
import top.gregtao.concerto.core.music.meta.music.MusicMetaData;
import top.gregtao.concerto.command.builder.MusicAdderBuilder;
import top.gregtao.concerto.core.config.ClientConfig;
import top.gregtao.concerto.core.enums.Sources;
import top.gregtao.concerto.core.music.HttpFileMusic;
import top.gregtao.concerto.core.music.LocalFileMusic;
import top.gregtao.concerto.core.music.Music;
import top.gregtao.concerto.core.music.meta.music.list.PlaylistMetaData;
import top.gregtao.concerto.core.player.MusicPlayer;
import top.gregtao.concerto.core.player.MusicPlayerHandler;
import top.gregtao.concerto.core.util.ConcertoRunner;
import top.gregtao.concerto.core.util.Pair;
import top.gregtao.concerto.util.MinecraftTextUtil;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MusicCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess access) {
        LiteralCommandNode<FabricClientCommandSource> node = dispatcher.register(registerPlayerControllers(
                ClientCommandManager.literal("concerto")
                        .then(addMusicCommand())
                        .then(insertMusicCommand())
        ));
        if (ClientConfig.INSTANCE.options.registerMusicCommand) {
            dispatcher.register(ClientCommandManager.literal("music").redirect(node));
        }
    }

    private static final List<MusicAdderBuilder.MusicGetter<Music>> GETTERS = List.of(
            context -> {
                LocalFileMusic music = new LocalFileMusic(StringArgumentType.getString(context, "path"));
                return Pair.of(music, Text.translatable(Sources.LOCAL_FILE.getKey("add"), music.getRawPath()));
            },
            context -> {
                HttpFileMusic music = new HttpFileMusic(StringArgumentType.getString(context, "path"));
                return Pair.of(music, Text.translatable(Sources.INTERNET.getKey("add"), music.getRawPath()));
            }
    );

    public static LiteralArgumentBuilder<FabricClientCommandSource> registerPlayerControllers(
            LiteralArgumentBuilder<FabricClientCommandSource> builder) {
        MusicPlayer player = MusicPlayer.INSTANCE;
        return builder.then(
                ClientCommandManager.literal("pause").executes(context -> {
                    if (player.forcePaused) {
                        player.forceResume();
                        MinecraftTextUtil.commandMessageClient(context, Text.translatable("concerto.player.resume"));
                    } else {
                        player.forcePause();
                        MinecraftTextUtil.commandMessageClient(context, Text.translatable("concerto.player.pause"));
                    }
                    return 0;
                })
        ).then(
                ClientCommandManager.literal("start").executes(context -> {
                    if (!player.started) {
                        player.start();
                        MinecraftTextUtil.commandMessageClient(context, Text.translatable("concerto.player.start"));
                    } else {
                        MinecraftTextUtil.commandMessageClient(context, Text.translatable("concerto.player.already_started"));
                    }
                    return 0;
                })
        ).then(
                ClientCommandManager.literal("stop").executes(context -> {
                    player.started = false;
                    player.playNextLock.set(true);
                    player.stop();
                    MusicPlayerHandler.INSTANCE.resetInfo();
                    MinecraftTextUtil.commandMessageClient(context, Text.translatable("concerto.player.stop"));
                    return 0;
                })
        ).then(
                ClientCommandManager.literal("skip").executes(context -> {
                    MusicPlayer.INSTANCE.stop();
                    MinecraftTextUtil.commandMessageClient(context, Text.translatable("concerto.player.skip"));
                    return 0;
                }).then(
                        ClientCommandManager.argument("index", IntegerArgumentType.integer(1)).executes(context -> {
                            int index = IntegerArgumentType.getInteger(context, "index");
                            MusicPlayer.INSTANCE.skipTo(index - 1);
                            MinecraftTextUtil.commandMessageClient(context, Text.translatable("concerto.player.skip_to", index));
                            return 0;
                        })
                )
        ).then(
                ClientCommandManager.literal("cut").executes(context -> {
                    MusicPlayer.INSTANCE.cut(() -> {});
                    MinecraftTextUtil.commandMessageClient(context, Text.translatable("concerto.player.cut"));
                    return 0;
                })
        ).then(
                ClientCommandManager.literal("clear").executes(context -> {
                    MusicPlayer.INSTANCE.clear();
                    MusicPlayer.resetInstance();
                    MinecraftTextUtil.commandMessageClient(context, Text.translatable("concerto.player.clear"));
                    return 0;
                })
        ).then(
                ClientCommandManager.literal("restart").executes(context -> {
                    MusicPlayer.resetInstance();
                    MinecraftTextUtil.commandMessageClient(context, Text.translatable("concerto.success"));
                    return 0;
                })
        ).then(
                ClientCommandManager.literal("reload").executes(context -> {
                    MusicPlayer.INSTANCE.reloadConfig(() ->
                            MinecraftTextUtil.commandMessageClient(context, Text.translatable("concerto.player.reload")));
                    ClientConfig.INSTANCE.readOptions();
                    PresetPlaylistsConfig.LOCAL_PLAYLISTS.read();
                    MusicPlayer.resetInstance();
                    return 0;
                })
        ).then(
                ClientCommandManager.literal("list").then(
                        ClientCommandManager.argument("page", IntegerArgumentType.integer(1)).executes(context -> {
                            ClientPlayerEntity clientPlayer = context.getSource().getPlayer();
                            ConcertoRunner.run(() -> {
                                int page = IntegerArgumentType.getInteger(context, "page");
                                List<Music> list = MusicPlayerHandler.INSTANCE.getMusicList();
                                page = Math.min(page, (int) Math.ceil(list.size() / 10f));
                                clientPlayer.sendMessage(MinecraftTextUtil.PAGE_SPLIT, false);
                                for (int i = 10 * (page - 1); i < Math.min(10 * page, list.size()); ++i) {
                                    MusicMetaData meta = list.get(i).getMeta();
                                    clientPlayer.sendMessage(Text.literal(
                                                    (i + 1) + ". " + meta.title() + " | " + meta.author()
                                                            + " | " + meta.getSource() + " | " + meta.getDuration().toShortString())
                                            .setStyle(MinecraftTextUtil.getRunCommandStyle("/concerto skip " + (i + 1))), false);
                                }
                                clientPlayer.sendMessage(MinecraftTextUtil.PAGE_SPLIT, false);
                            });
                            return 0;
                        })
                )
        ).then(
                ClientCommandManager.literal("save").executes(context -> {
                    ClientPlayerEntity clientPlayer = context.getSource().getPlayer();
                    if (MusicPlayerHandler.INSTANCE.currentMusic == null) {
                        clientPlayer.sendMessage(Text.translatable("concerto.unknown"), false);
                    } else if (MusicPlayerHandler.INSTANCE.currentMusic instanceof CacheableMusic music) {
                        ConcertoRunner.run(() -> {
                            try {
                                MusicCacheManager.INSTANCE.addMusic(music);
                                clientPlayer.sendMessage(Text.translatable("concerto.success"), false);
                            } catch (IOException | UnsupportedAudioFileException e) {
                                throw new RuntimeException(e);
                            }
                        });
                    } else {
                        clientPlayer.sendMessage(Text.translatable("concerto.not_cacheable"), false);
                    }
                    return 0;
                })
        ).then(
                ClientCommandManager.literal("like").executes(context -> {
                    ClientPlayerEntity clientPlayer = context.getSource().getPlayer();
                    Music music = MusicPlayerHandler.INSTANCE.getCurrentMusic();
                    if (music instanceof Likeable likeable) {
                        CompletableFuture.supplyAsync(likeable::likeIt, ConcertoRunner.RUNNERS_POOL).thenAcceptAsync(success ->
                                clientPlayer.sendMessage(success ? Text.translatable("concerto.like",
                                        music.getMeta().title(), music.getMeta().getSource()) :
                                        Text.translatable("concerto.fail"), false), ConcertoRunner.RUNNERS_POOL);
                    } else {
                        clientPlayer.sendMessage(Text.translatable("concerto.error.unsupported_operation"), false);
                    }
                    return 0;
                })
        ).then(
                ClientCommandManager.literal("dislike").executes(context -> {
                    ClientPlayerEntity clientPlayer = context.getSource().getPlayer();
                    Music music = MusicPlayerHandler.INSTANCE.getCurrentMusic();
                    if (music instanceof Likeable likeable) {
                        CompletableFuture.supplyAsync(likeable::dislikeIt, ConcertoRunner.RUNNERS_POOL).thenAcceptAsync(success ->
                                clientPlayer.sendMessage(success ? Text.translatable("concerto.dislike",
                                        music.getMeta().title(), music.getMeta().getSource()) :
                                        Text.translatable("concerto.fail"), false), ConcertoRunner.RUNNERS_POOL);
                    } else {
                        clientPlayer.sendMessage(Text.translatable("concerto.error.unsupported_operation"), false);
                    }
                    return 0;
                })
        ).then(
                ClientCommandManager.literal("download-current").executes(context -> {
                    MusicPlayerHandler.downloadMusics(List.of(MusicPlayerHandler.INSTANCE.getCurrentMusic()));
                    context.getSource().getPlayer().sendMessage(Text.translatable("concerto.success"), false);
                    return 0;
                })
        ).then(
                ClientCommandManager.literal("download-all").executes(context -> {
                    MusicPlayerHandler.downloadMusics(MusicPlayerHandler.INSTANCE.getMusicList());
                    context.getSource().getPlayer().sendMessage(Text.translatable("concerto.success"), false);
                    return 0;
                })
        ).then(
                ClientCommandManager.literal("export-as-playlist").executes(context -> {
                    ClientPlayerEntity clientPlayer = context.getSource().getPlayer();
                    Text playerName = clientPlayer.getDisplayName();
                    if (PresetPlaylistsConfig.saveToLocalPlaylists(new FixedPlaylist(
                            MusicPlayerHandler.INSTANCE.getMusicList(),
                            new PlaylistMetaData(
                                    playerName == null ? "Unknown" : playerName.getString(),
                                    "Default Playlist",
                                    LocalDateTime.now().toString(),
                                    "Default Playlist"
                            ),
                            false
                    ))) {
                        clientPlayer.sendMessage(Text.translatable("concerto.playlist.export.success"), false);
                    } else {
                        clientPlayer.sendMessage(Text.translatable("concerto.playlist.export.fail"), false);
                    }
                    return 0;
                })
        ).then(
                ClientCommandManager.literal("clean-cache").executes(context -> {
                    CacheManager.cleanAllCache();
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
                                                    Text.translatable(Sources.LOCAL_FILE.getKey("add"), path), false)
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
        );
    }
}
