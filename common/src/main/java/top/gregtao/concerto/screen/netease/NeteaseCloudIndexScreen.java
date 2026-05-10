package top.gregtao.concerto.screen.netease;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.gregtao.concerto.core.http.netease.NeteaseCloudApiClient;
import top.gregtao.concerto.core.util.ConcertoRunner;
import top.gregtao.concerto.screen.ConcertoScreen;
import top.gregtao.concerto.screen.widget.URLImageWidget;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

public class NeteaseCloudIndexScreen extends ConcertoScreen {
    private URLImageWidget avatar;

    public NeteaseCloudIndexScreen(Screen parent) {
        super(Component.translatable("concerto.screen.index.163"), parent);
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.user"),
                button -> Minecraft.getInstance().setScreen(new NeteaseCloudUserScreen(this))
        ).size(100, 20).pos(this.width / 2 - 50, 40).build());
        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.search"),
                button -> Minecraft.getInstance().setScreen(new NeteaseCloudSearchScreen(this))
        ).size(100, 20).pos(this.width / 2 - 50, 65).build());

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
    public void render(GuiGraphics matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        Component text = this.loggedIn() ? Component.translatable("concerto.screen.163.welcome", NeteaseCloudApiClient.LOCAL_USER.nickname) :
                Component.translatable("concerto.screen.163.not_login");
        matrices.drawCenteredString(this.font, text, this.width / 2, 90, 0xffffffff);
        this.avatar.render(matrices, mouseX, mouseY, delta);
    }
}
