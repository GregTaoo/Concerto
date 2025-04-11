package top.gregtao.concerto.screen;

import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import top.gregtao.concerto.api.MusicJsonParsers;
import top.gregtao.concerto.music.LocalFileMusic;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.api.UnsafeMusicException;
import top.gregtao.concerto.music.list.Playlist;
import top.gregtao.concerto.player.MusicPlayer;
import top.gregtao.concerto.player.MusicPlayerHandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public abstract class ApplyDraggedFileScreen extends ConcertoScreen {

    public ApplyDraggedFileScreen(Text title, Screen parent) {
        super(title, parent);
    }

    @Override
    public void filesDragged(List<Path> paths) {
        if (this.client == null) return;
        String message = paths.stream().map(Path::getFileName).map(Path::toString).collect(Collectors.joining(", "));
        this.client.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                AtomicInteger integer = new AtomicInteger(0);
                MusicPlayer.INSTANCE.addMusic(() -> {
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
                            this.displayAlert(Text.literal(e.getMessage()));
                        } catch (UnsafeMusicException e) {
                            this.displayAlert(Text.translatable("concerto.error.invalid_path"));
                        }
                    });
                    integer.set(list.size());
                    return list;
                }, () -> {
                    MusicPlayer.INSTANCE.skipTo(MusicPlayerHandler.INSTANCE.getMusicList().size() - integer.get());
                    if (this instanceof GeneralPlaylistScreen screen) {
                        screen.toggleSearch();
                    }
                });
            }
            this.client.setScreen(this);
        }, Text.translatable("concerto.drag_confirm"), Text.literal(message)));
    }
}
