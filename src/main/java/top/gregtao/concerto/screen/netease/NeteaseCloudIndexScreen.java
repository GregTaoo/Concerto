package top.gregtao.concerto.screen.netease;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import top.gregtao.concerto.http.netease.NeteaseCloudApiClient;
import top.gregtao.concerto.screen.ConcertoScreen;
import top.gregtao.concerto.screen.widget.URLImageWidget;
import top.gregtao.concerto.util.ConcertoRunner;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

public class NeteaseCloudIndexScreen extends ConcertoScreen {
    private URLImageWidget avatar;

    public NeteaseCloudIndexScreen(Screen parent) {
        super(new TranslatableText("concerto.screen.index.163"), parent);
    }

    @Override
    protected void init() {
        super.init();
        this.addButton(new ButtonWidget(this.width / 2 - 50, 40, 100, 20,
                new TranslatableText("concerto.screen.user"),
                button -> MinecraftClient.getInstance().openScreen(new NeteaseCloudUserScreen(this))
        ));
        this.addButton(new ButtonWidget(this.width / 2 - 50, 65, 100, 20, new TranslatableText("concerto.screen.search"),
                button -> MinecraftClient.getInstance().openScreen(new NeteaseCloudSearchScreen(this))
        ));

        URL avatarUrl;
        try {
            avatarUrl = (!this.loggedIn() || NeteaseCloudApiClient.LOCAL_USER.avatarUrl.isEmpty()) ? null :
                    URI.create(NeteaseCloudApiClient.LOCAL_USER.avatarUrl).toURL();
        } catch (MalformedURLException e) {
            avatarUrl = null;
        }
        this.avatar = new URLImageWidget(64, 64, this.width / 2 - 32, 110,
                avatarUrl == null ? null : avatarUrl.toString(), false);
        ConcertoRunner.run(() -> this.avatar.loadImage(true, true));
    }

    @Override
    public void onClose() {
        super.onClose();
        this.avatar.close();
    }

    private boolean loggedIn() {
        return NeteaseCloudApiClient.LOCAL_USER.loggedIn;
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        Text text = this.loggedIn() ? new TranslatableText("concerto.screen.163.welcome", NeteaseCloudApiClient.LOCAL_USER.nickname) :
                new TranslatableText("concerto.screen.163.not_login");
        ConcertoScreen.drawCenteredTextWithShadow(matrices, this.textRenderer, text.asOrderedText(), this.width / 2, 90, 0xffffffff);
        this.avatar.render(matrices, mouseX, mouseY, delta);
    }
}
