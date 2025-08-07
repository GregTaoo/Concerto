package top.gregtao.concerto.screen.qq;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import top.gregtao.concerto.http.qq.QQMusicApiClient;
import top.gregtao.concerto.player.MusicPlayer;
import top.gregtao.concerto.screen.ConcertoScreen;
import top.gregtao.concerto.screen.widget.URLImageWidget;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

public class QQMusicIndexScreen extends ConcertoScreen {
    private URLImageWidget avatar;

    public QQMusicIndexScreen(Screen parent) {
        super(new TranslatableText("concerto.screen.index.qq"), parent);
    }

    @Override
    protected void init() {
        super.init();
        this.addDrawableChild(new ButtonWidget(this.width / 2 - 50, 40, 100, 20, new TranslatableText("concerto.screen.user"),
                button -> MinecraftClient.getInstance().setScreen(this.loggedIn() ? new QQMusicUserScreen(this) : new QQMusicLoginScreens(this))
        ));
        this.addDrawableChild(new ButtonWidget(this.width / 2 - 50, 65, 100, 20, new TranslatableText("concerto.screen.search"),
                button -> MinecraftClient.getInstance().setScreen(new QQMusicSearchScreen(this))
        ));

        URL avatarUrl;
        try {
            avatarUrl = (!this.loggedIn() || QQMusicApiClient.LOCAL_USER.avatarUrl.isEmpty()) ? null :
                    URI.create(QQMusicApiClient.LOCAL_USER.avatarUrl).toURL();
        } catch (MalformedURLException e) {
            avatarUrl = null;
        }
        this.avatar = new URLImageWidget(64, 64, this.width / 2 - 32, 110,
                avatarUrl == null ? null : avatarUrl.toString(), false);
        MusicPlayer.run(() -> this.avatar.loadImage(true, true));
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
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        Text text = this.loggedIn() ? new TranslatableText("concerto.screen.qq.welcome", QQMusicApiClient.LOCAL_USER.nickname) :
                new TranslatableText("concerto.screen.qq.not_login");
        DrawableHelper.drawCenteredTextWithShadow(matrices, this.textRenderer, text.asOrderedText(), this.width / 2, 90, 0xffffffff);
        this.avatar.render(matrices, mouseX, mouseY, delta);
    }
}
