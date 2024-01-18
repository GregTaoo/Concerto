package top.gregtao.concerto.screen.qq;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import top.gregtao.concerto.http.qq.QQMusicApiClient;
import top.gregtao.concerto.screen.ConcertoScreen;
import top.gregtao.concerto.screen.netease.NeteaseCloudSearchScreen;

public class QQMusicIndexScreen extends ConcertoScreen {
    public QQMusicIndexScreen(Screen parent) {
        super(Text.translatable("concerto.screen.index.qq"), parent);
    }

    @Override
    protected void init() {
        super.init();
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.qq.user"),
                button -> MinecraftClient.getInstance().setScreen(new QQMusicUserScreen(this))
        ).size(100, 20).position(this.width / 2 - 50, 40).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.qq.search"),
                button -> MinecraftClient.getInstance().setScreen(new QQMusicSearchScreen(this))
        ).size(100, 20).position(this.width / 2 - 50, 65).build());
    }

    private boolean loggedIn() {
        return QQMusicApiClient.LOCAL_USER.loggedIn;
    }

    @Override
    public void render(DrawContext matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        Text text = this.loggedIn() ? Text.translatable("concerto.screen.163.welcome", QQMusicApiClient.LOCAL_USER.nickname) :
                Text.translatable("concerto.screen.163.not_login");
        matrices.drawCenteredTextWithShadow(this.textRenderer, text, this.width / 2, this.height / 2, 0xffffffff);
    }
}
