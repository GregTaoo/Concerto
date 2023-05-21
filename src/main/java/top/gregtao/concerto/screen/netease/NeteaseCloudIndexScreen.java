package top.gregtao.concerto.screen.netease;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import top.gregtao.concerto.http.netease.NeteaseCloudApiClient;
import top.gregtao.concerto.screen.ConcertoScreen;

public class NeteaseCloudIndexScreen extends ConcertoScreen {
    public NeteaseCloudIndexScreen(Screen parent) {
        super(Text.translatable("concerto.screen.index.163"), parent);
    }

    @Override
    protected void init() {
        super.init();
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.163.user"),
                button -> MinecraftClient.getInstance().setScreen(new NeteaseCloudUserScreen(this))
        ).size(100, 20).position(this.width / 2 - 50, 40).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.163.search"),
                button -> MinecraftClient.getInstance().setScreen(new NeteaseCloudSearchScreen(this))
        ).size(100, 20).position(this.width / 2 - 50, 70).build());
    }

    private boolean loggedIn() {
        return NeteaseCloudApiClient.LOCAL_USER.loggedIn;
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        Text text = this.loggedIn() ? Text.translatable("concerto.screen.163.welcome", NeteaseCloudApiClient.LOCAL_USER.nickname) :
                Text.translatable("concerto.screen.163.not_login");
        DrawableHelper.drawCenteredTextWithShadow(matrices, this.textRenderer, text, this.width / 2, this.height / 2, 0xffffffff);
    }
}
