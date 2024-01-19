package top.gregtao.concerto.screen.qq;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import top.gregtao.concerto.http.qq.QQMusicApiClient;
import top.gregtao.concerto.player.MusicPlayer;
import top.gregtao.concerto.screen.ConcertoScreen;
import top.gregtao.concerto.screen.widget.URLImageWidget;

import java.net.MalformedURLException;
import java.net.URL;

public class QQMusicIndexScreen extends ConcertoScreen {
    private URLImageWidget avatar;

    public QQMusicIndexScreen(Screen parent) {
        super(Text.translatable("concerto.screen.index.qq"), parent);
    }

    @Override
    protected void init() {
        super.init();
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.user"),
                button -> MinecraftClient.getInstance().setScreen(this.loggedIn() ? new QQMusicUserScreen(this) : new QQMusicLoginScreens(this))
        ).size(100, 20).position(this.width / 2 - 50, 40).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.search"),
                button -> MinecraftClient.getInstance().setScreen(new QQMusicSearchScreen(this))
        ).size(100, 20).position(this.width / 2 - 50, 65).build());

        URL avatarUrl;
        try {
            avatarUrl = (!this.loggedIn() || QQMusicApiClient.LOCAL_USER.avatarUrl.isEmpty()) ? null :
                    new URL(QQMusicApiClient.LOCAL_USER.avatarUrl);
        } catch (MalformedURLException e) {
            avatarUrl = null;
        }
        this.avatar = new URLImageWidget(64, 64, this.width / 2 - 32, 110, avatarUrl);
        MusicPlayer.run(() -> this.avatar.loadImage());
    }

    @Override
    public void close() {
        super.close();
        this.avatar.close();
    }

    private boolean loggedIn() {
        return QQMusicApiClient.LOCAL_USER.loggedIn;
    }

    @Override
    public void render(DrawContext matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        Text text = this.loggedIn() ? Text.translatable("concerto.screen.qq.welcome", QQMusicApiClient.LOCAL_USER.nickname) :
                Text.translatable("concerto.screen.qq.not_login");
        matrices.drawCenteredTextWithShadow(this.textRenderer, text, this.width / 2, 90, 0xffffffff);
        this.avatar.render(matrices, mouseX, mouseY, delta);
    }
}
