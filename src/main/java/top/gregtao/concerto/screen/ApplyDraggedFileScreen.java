package top.gregtao.concerto.screen;

import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import top.gregtao.concerto.music.LocalFileMusic;
import top.gregtao.concerto.music.Music;
import top.gregtao.concerto.music.UnsafeMusicException;
import top.gregtao.concerto.player.MusicPlayer;
import top.gregtao.concerto.player.MusicPlayerHandler;

import java.io.File;
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
    public void onFilesDropped(List<Path> paths) {
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
                            } else {
                                list.add(new LocalFileMusic(file.getAbsolutePath()));
                            }
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
