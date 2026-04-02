package top.gregtao.concerto.command;

import com.mojang.brigadier.CommandDispatcher;
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
import top.gregtao.concerto.core.config.ClientConfig;
import top.gregtao.concerto.core.music.Music;
import top.gregtao.concerto.core.music.meta.music.list.PlaylistMetaData;
import top.gregtao.concerto.core.player.MusicPlayer;
import top.gregtao.concerto.core.player.MusicPlayerHandler;
import top.gregtao.concerto.core.util.ConcertoRunner;
import top.gregtao.concerto.util.MinecraftTextUtil;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MusicCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess access) {
        LiteralCommandNode<FabricClientCommandSource> node = dispatcher.register(registerPlayerControllers(
                ClientCommandManager.literal("concerto")
        ));
        if (ClientConfig.INSTANCE.options.registerMusicCommand) {
            dispatcher.register(ClientCommandManager.literal("music").redirect(node));
        }
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> registerPlayerControllers(
            LiteralArgumentBuilder<FabricClientCommandSource> builder) {
        MusicPlayer player = MusicPlayer.INSTANCE;
        MusicPlayerHandler handler = MusicPlayerHandler.INSTANCE;
        return builder.then(
                ClientCommandManager.literal("pause").executes(context -> {
                    if (handler.isForcePaused()) {
                        handler.tryForcePause(false);
                        MinecraftTextUtil.commandMessageClient(context, Text.translatable("concerto.player.resume"));
                    } else {
                        handler.tryForcePause(true);
                        MinecraftTextUtil.commandMessageClient(context, Text.translatable("concerto.player.pause"));
                    }
                    return 0;
                })
        ).then(
                ClientCommandManager.literal("start").executes(context -> {
                    if (!player.started) {
                        handler.start();
                        MinecraftTextUtil.commandMessageClient(context, Text.translatable("concerto.player.start"));
                    } else {
                        MinecraftTextUtil.commandMessageClient(context, Text.translatable("concerto.player.already_started"));
                    }
                    return 0;
                })
        ).then(
                ClientCommandManager.literal("stop").executes(context -> {
                    MusicPlayerHandler.INSTANCE.stop();
                    MinecraftTextUtil.commandMessageClient(context, Text.translatable("concerto.player.stop"));
                    return 0;
                })
        ).then(
                ClientCommandManager.literal("clear").executes(context -> {
                    MusicPlayerHandler.INSTANCE.clear();
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
                    MusicPlayerHandler.reloadConfig(() ->
                            MinecraftTextUtil.commandMessageClient(context, Text.translatable("concerto.player.reload")));
                    ClientConfig.INSTANCE.readOptions();
                    PresetPlaylistsConfig.LOCAL_PLAYLISTS.read();
                    MusicPlayer.resetInstance();
                    return 0;
                })
        ).then(
                ClientCommandManager.literal("save").executes(context -> {
                    ClientPlayerEntity clientPlayer = context.getSource().getPlayer();
                    if (MusicPlayer.INSTANCE.currentMusic == null) {
                        clientPlayer.sendMessage(Text.translatable("concerto.unknown"), false);
                    } else if (MusicPlayer.INSTANCE.currentMusic instanceof CacheableMusic music) {
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
                    MusicPlayerHandler.downloadMusics(MusicPlayerHandler.INSTANCE.getMusicList().snapshotMusics());
                    context.getSource().getPlayer().sendMessage(Text.translatable("concerto.success"), false);
                    return 0;
                })
        ).then(
                ClientCommandManager.literal("export-as-playlist").executes(context -> {
                    ClientPlayerEntity clientPlayer = context.getSource().getPlayer();
                    Text playerName = clientPlayer.getDisplayName();
                    if (PresetPlaylistsConfig.saveToLocalPlaylists(new FixedPlaylist(
                            MusicPlayerHandler.INSTANCE.getMusicList().snapshotMusics(),
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
}
