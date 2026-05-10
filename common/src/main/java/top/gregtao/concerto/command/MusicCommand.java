package top.gregtao.concerto.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import top.gregtao.concerto.core.api.CacheableMusic;
import top.gregtao.concerto.core.api.Likeable;
import top.gregtao.concerto.core.config.CacheManager;
import top.gregtao.concerto.core.config.ClientConfig;
import top.gregtao.concerto.core.config.MusicCacheManager;
import top.gregtao.concerto.core.config.PresetPlaylistsConfig;
import top.gregtao.concerto.core.music.Music;
import top.gregtao.concerto.core.music.list.FixedPlaylist;
import top.gregtao.concerto.core.music.meta.music.list.PlaylistMetaData;
import top.gregtao.concerto.core.player.MusicPlayer;
import top.gregtao.concerto.core.player.MusicPlayerHandler;
import top.gregtao.concerto.core.util.ConcertoRunner;
import top.gregtao.concerto.util.CommandUtil;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MusicCommand {

    public static <S extends SharedSuggestionProvider> void register(CommandDispatcher<S> dispatcher, CommandBuildContext access) {
        LiteralCommandNode<S> node = dispatcher.register(registerPlayerControllers(
                LiteralArgumentBuilder.literal("concerto")
        ));
        if (ClientConfig.INSTANCE.options.registerMusicCommand) {
            dispatcher.register(LiteralArgumentBuilder.<S>literal("music").redirect(node));
        }
    }

    public static <S extends SharedSuggestionProvider> LiteralArgumentBuilder<S> registerPlayerControllers(
            LiteralArgumentBuilder<S> builder) {
        MusicPlayer player = MusicPlayer.INSTANCE;
        MusicPlayerHandler handler = MusicPlayerHandler.INSTANCE;
        return builder.then(
                LiteralArgumentBuilder.<S>literal("pause").executes(context -> {
                    if (handler.isForcePaused()) {
                        handler.tryForcePause(false);
                        CommandUtil.commandMessageClient(Component.translatable("concerto.player.resume"));
                    } else {
                        handler.tryForcePause(true);
                        CommandUtil.commandMessageClient(Component.translatable("concerto.player.pause"));
                    }
                    return 0;
                })
        ).then(
                LiteralArgumentBuilder.<S>literal("start").executes(context -> {
                    if (!player.started) {
                        handler.start();
                        CommandUtil.commandMessageClient(Component.translatable("concerto.player.start"));
                    } else {
                        CommandUtil.commandMessageClient(Component.translatable("concerto.player.already_started"));
                    }
                    return 0;
                })
        ).then(
                LiteralArgumentBuilder.<S>literal("stop").executes(context -> {
                    MusicPlayerHandler.INSTANCE.stop();
                    CommandUtil.commandMessageClient(Component.translatable("concerto.player.stop"));
                    return 0;
                })
        ).then(
                LiteralArgumentBuilder.<S>literal("clear").executes(context -> {
                    MusicPlayerHandler.INSTANCE.clear();
                    MusicPlayer.resetInstance();
                    CommandUtil.commandMessageClient(Component.translatable("concerto.player.clear"));
                    return 0;
                })
        ).then(
                LiteralArgumentBuilder.<S>literal("restart").executes(context -> {
                    MusicPlayer.resetInstance();
                    CommandUtil.commandMessageClient(Component.translatable("concerto.success"));
                    return 0;
                })
        ).then(
                LiteralArgumentBuilder.<S>literal("reload").executes(context -> {
                    MusicPlayerHandler.reloadConfig(() ->
                            CommandUtil.commandMessageClient(Component.translatable("concerto.player.reload")));
                    ClientConfig.INSTANCE.readOptions();
                    PresetPlaylistsConfig.LOCAL_PLAYLISTS.read();
                    MusicPlayer.resetInstance();
                    return 0;
                })
        ).then(
                LiteralArgumentBuilder.<S>literal("save").executes(context -> {
                    LocalPlayer clientPlayer = Minecraft.getInstance().player;
                    if (clientPlayer == null) return -1;
                    if (MusicPlayer.INSTANCE.currentMusic == null) {
                        clientPlayer.displayClientMessage(Component.translatable("concerto.unknown"), false);
                    } else if (MusicPlayer.INSTANCE.currentMusic instanceof CacheableMusic music) {
                        ConcertoRunner.run(() -> {
                            try {
                                MusicCacheManager.INSTANCE.addMusic(music);
                                clientPlayer.displayClientMessage(Component.translatable("concerto.success"), false);
                            } catch (IOException | UnsupportedAudioFileException e) {
                                throw new RuntimeException(e);
                            }
                        });
                    } else {
                        clientPlayer.displayClientMessage(Component.translatable("concerto.not_cacheable"), false);
                    }
                    return 0;
                })
        ).then(
                LiteralArgumentBuilder.<S>literal("like").executes(context -> {
                    LocalPlayer clientPlayer = Minecraft.getInstance().player;
                    if (clientPlayer == null) return -1;
                    Music music = MusicPlayerHandler.INSTANCE.getCurrentMusic();
                    if (music instanceof Likeable likeable) {
                        CompletableFuture.supplyAsync(likeable::likeIt, ConcertoRunner.RUNNERS_POOL).thenAcceptAsync(success ->
                                clientPlayer.displayClientMessage(success ? Component.translatable("concerto.like",
                                        music.getMeta().title(), music.getMeta().getSource()) :
                                        Component.translatable("concerto.fail"), false), ConcertoRunner.RUNNERS_POOL);
                    } else {
                        clientPlayer.displayClientMessage(Component.translatable("concerto.error.unsupported_operation"), false);
                    }
                    return 0;
                })
        ).then(
                LiteralArgumentBuilder.<S>literal("dislike").executes(context -> {
                    LocalPlayer clientPlayer = Minecraft.getInstance().player;
                    if (clientPlayer == null) return -1;
                    Music music = MusicPlayerHandler.INSTANCE.getCurrentMusic();
                    if (music instanceof Likeable likeable) {
                        CompletableFuture.supplyAsync(likeable::dislikeIt, ConcertoRunner.RUNNERS_POOL).thenAcceptAsync(success ->
                                clientPlayer.displayClientMessage(success ? Component.translatable("concerto.dislike",
                                        music.getMeta().title(), music.getMeta().getSource()) :
                                        Component.translatable("concerto.fail"), false), ConcertoRunner.RUNNERS_POOL);
                    } else {
                        clientPlayer.displayClientMessage(Component.translatable("concerto.error.unsupported_operation"), false);
                    }
                    return 0;
                })
        ).then(
                LiteralArgumentBuilder.<S>literal("download-current").executes(context -> {
                    MusicPlayerHandler.downloadMusics(List.of(MusicPlayerHandler.INSTANCE.getCurrentMusic()));
                    LocalPlayer clientPlayer = Minecraft.getInstance().player;
                    if (clientPlayer == null) return -1;
                    clientPlayer.displayClientMessage(Component.translatable("concerto.success"), false);
                    return 0;
                })
        ).then(
                LiteralArgumentBuilder.<S>literal("download-all").executes(context -> {
                    MusicPlayerHandler.downloadMusics(MusicPlayerHandler.INSTANCE.getMusicList().snapshotMusics());
                    LocalPlayer clientPlayer = Minecraft.getInstance().player;
                    if (clientPlayer == null) return -1;
                    clientPlayer.displayClientMessage(Component.translatable("concerto.success"), false);
                    return 0;
                })
        ).then(
                LiteralArgumentBuilder.<S>literal("export-as-playlist").executes(context -> {
                    LocalPlayer clientPlayer = Minecraft.getInstance().player;
                    if (clientPlayer == null) return -1;
                    Component playerName = clientPlayer.getDisplayName();
                    if (PresetPlaylistsConfig.saveToLocalPlaylists(new FixedPlaylist(
                            MusicPlayerHandler.INSTANCE.getMusicList().snapshotMusics(),
                            new PlaylistMetaData(
                                    playerName.getString(),
                                    "Default Playlist",
                                    LocalDateTime.now().toString(),
                                    "Default Playlist"
                            ),
                            false
                    ))) {
                        clientPlayer.displayClientMessage(Component.translatable("concerto.playlist.export.success"), false);
                    } else {
                        clientPlayer.displayClientMessage(Component.translatable("concerto.playlist.export.fail"), false);
                    }
                    return 0;
                })
        ).then(
                LiteralArgumentBuilder.<S>literal("clean-cache").executes(context -> {
                    CacheManager.cleanAllCache();
                    return 0;
                })
        );
    }
}
