package top.gregtao.concerto.screen;

import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.gregtao.concerto.core.api.MusicJsonParsers;
import top.gregtao.concerto.core.music.LocalFileMusic;
import top.gregtao.concerto.core.music.Music;
import top.gregtao.concerto.core.api.UnsafeMusicException;
import top.gregtao.concerto.core.music.list.Playlist;
import top.gregtao.concerto.core.player.MusicPlayerHandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public abstract class ApplyDraggedFileScreen extends ConcertoScreen {

    public ApplyDraggedFileScreen(Component title, Screen parent) {
        super(title, parent);
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        if (this.minecraft == null) return;
        String message = paths.stream().map(Path::getFileName).map(Path::toString).collect(Collectors.joining(", "));
        this.minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                MusicPlayerHandler.INSTANCE.addMusicAsync(() -> {
                    ArrayList<Music> list = new ArrayList<>();
                    paths.forEach(path -> {
                        File file = path.toFile();
                        try {
                            if (file.isDirectory()) {
                                list.addAll(LocalFileMusic.getMusicsInFolder(file));
                            } else if (path.toString().toLowerCase().endsWith(".json")) {
                                try (FileInputStream inputStream = new FileInputStream(file)) {
                                    Playlist playlist = MusicJsonParsers.fromPlaylist(new String(inputStream.readAllBytes()));
                                    if (playlist != null) list.addAll(playlist.getList());
                                }
                            } else {
                                list.add(new LocalFileMusic(file.getAbsolutePath()));
                            }
                        } catch (IOException e) {
                            this.displayAlert(Component.literal(e.getMessage()));
                        } catch (UnsafeMusicException e) {
                            this.displayAlert(Component.translatable("concerto.error.invalid_path"));
                        }
                    });
                    return list;
                }, true, () -> {
                    if (this instanceof GeneralPlaylistScreen screen) {
                        screen.toggleSearch();
                    }
                });
            }
            this.minecraft.setScreen(this);
        }, Component.translatable("concerto.drag_confirm"), Component.literal(message)));
    }
}
