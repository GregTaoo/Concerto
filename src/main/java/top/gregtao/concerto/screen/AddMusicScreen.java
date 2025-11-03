package top.gregtao.concerto.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import top.gregtao.concerto.api.UnsafeMusicException;
import top.gregtao.concerto.config.ClientConfig;
import top.gregtao.concerto.music.*;
import top.gregtao.concerto.music.list.NeteaseCloudPlaylist;
import top.gregtao.concerto.player.MusicPlayer;
import top.gregtao.concerto.player.MusicPlayerHandler;
import top.gregtao.concerto.screen.widget.TextWidget;
import top.gregtao.concerto.util.ConcertoRunner;

import java.io.File;
import java.util.ArrayList;
import java.util.function.Consumer;

public class AddMusicScreen extends ApplyDraggedFileScreen {

    public AddMusicScreen(Screen parent) {
        super(new TranslatableText("concerto.screen.manual_add"), parent);
    }

    private void addLabel(Text text, int centerX, int y, Consumer<String> onClick) {
        TextFieldWidget widget = new TextFieldWidget(this.textRenderer, centerX - 15, y, 90, 20, text);
        widget.setMaxLength(1024);
        TextWidget textWidget = new TextWidget(centerX - 135, y + 2, 120, 20, text, this.textRenderer);
        textWidget.alignLeft();
        this.addDrawableChild(widget);
        this.addDrawableChild(textWidget);
        this.addDrawableChild(new ButtonWidget(centerX + 80, y, 60, 20, new TranslatableText("concerto.screen.add"),
                button -> onClick.accept(widget.getText())));
        this.addSelectableChild(widget);
    }

    private Consumer<String> methodSafeWrapper(Consumer<String> method) {
        return str -> {
            try {
                method.accept(str);
            } catch (Exception e) {
                AddMusicScreen.this.displayAlert(new TranslatableText("concerto.fail"));
            }
        };
    }

    @Override
    protected void init() {
        super.init();
        this.addLabel(new TranslatableText("concerto.screen.add.local_file"), this.width / 2, 20,
                methodSafeWrapper(str -> {
                    try {
                        MusicPlayer.INSTANCE.addMusicHere(new LocalFileMusic(str), true);
                    } catch (UnsafeMusicException e) {
                        this.displayAlert(new TranslatableText("concerto.error.invalid_path"));
                    }
                }));
        this.addLabel(new TranslatableText("concerto.screen.add.local_file.folder"), this.width / 2, 45, str -> ConcertoRunner.run(() -> {
            ArrayList<Music> list = LocalFileMusic.getMusicsInFolder(new File(str));
            MusicPlayer.INSTANCE.addMusic(list, () -> MusicPlayer.INSTANCE.skipTo(MusicPlayerHandler.INSTANCE.getMusicList().size() - list.size()));
        }));
        this.addLabel(new TranslatableText("concerto.screen.add.internet"), this.width / 2, 70,
                methodSafeWrapper(str -> MusicPlayer.INSTANCE.addMusicHere(new HttpFileMusic(str), true)));
        this.addLabel(new TranslatableText("concerto.screen.add.netease_cloud"), this.width / 2, 95,
                methodSafeWrapper(str -> MusicPlayer.INSTANCE.addMusicHere(new NeteaseCloudMusic(str, ClientConfig.INSTANCE.options.neteaseMusicQuality), true)));
        this.addLabel(new TranslatableText("concerto.screen.add.netease_cloud.playlist"), this.width / 2, 120, methodSafeWrapper(str -> {
            NeteaseCloudPlaylist playlist = new NeteaseCloudPlaylist(str, false);
            playlist.load(() -> MinecraftClient.getInstance().setScreen(new PlaylistPreviewScreen(playlist, this)));
        }));
        this.addLabel(new TranslatableText("concerto.screen.add.netease_cloud.album"), this.width / 2, 145, methodSafeWrapper(str -> {
            NeteaseCloudPlaylist playlist = new NeteaseCloudPlaylist(str, false);
            playlist.load(() -> MinecraftClient.getInstance().setScreen(new PlaylistPreviewScreen(playlist, this)));
        }));
        this.addLabel(new TranslatableText("concerto.screen.add.qq"), this.width / 2, 170,
                methodSafeWrapper(str -> MusicPlayer.INSTANCE.addMusicHere(new QQMusic(str), true, () -> {
                    if (!MusicPlayer.INSTANCE.started) MusicPlayer.INSTANCE.start();
                })));

        this.addLabel(
                new TranslatableText("concerto.screen.add.kugou"), this.width / 2, 195,
                methodSafeWrapper(str -> MusicPlayer.INSTANCE.addMusicHere(new KuGouMusic(str, true), true))
        );
//        this.addLabel(new TranslatableText("concerto.screen.add.bilibili"), this.width / 2, 195,
//                str -> MusicPlayer.INSTANCE.addMusicHere(new BilibiliMusic(str), true));
    }
}
