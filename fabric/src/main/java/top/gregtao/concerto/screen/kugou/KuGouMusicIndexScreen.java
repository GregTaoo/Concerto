package top.gregtao.concerto.screen.kugou;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import top.gregtao.concerto.core.config.ClientConfig;
import top.gregtao.concerto.core.http.kugou.KuGouMusicApiClient;
import top.gregtao.concerto.core.http.kugou.KuGouMusicUser;
import top.gregtao.concerto.screen.ConcertoScreen;
import top.gregtao.concerto.screen.widget.ModifiablePressableTextWidget;
import top.gregtao.concerto.screen.widget.URLImageWidget;
import top.gregtao.concerto.core.util.ConcertoRunner;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.LocalDateTime;

public class KuGouMusicIndexScreen extends ConcertoScreen {
    private URLImageWidget avatar;

    private ModifiablePressableTextWidget vipStatusWidget;

    public KuGouMusicIndexScreen(Screen parent) {
        super(Component.translatable("concerto.screen.index.kugou"), parent);
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.user"),
                button -> Minecraft.getInstance().setScreen(new KuGouMusicUserScreen(this))
        ).size(100, 20).pos(this.width / 2 - 50, 40).build());
        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.search"),
                button -> Minecraft.getInstance().setScreen(new KuGouMusicSearchScreen(this))
        ).size(100, 20).pos(this.width / 2 - 50, 65).build());

        URL avatarUrl;
        try {
            avatarUrl = (!this.loggedIn() || KuGouMusicApiClient.LOCAL_USER.getAvatarUrl().isEmpty()) ? null :
                    URI.create(KuGouMusicApiClient.LOCAL_USER.getAvatarUrl()).toURL();
        } catch (MalformedURLException e) {
            avatarUrl = null;
        }
        this.avatar = new URLImageWidget(64, 64, this.width / 2 - 32, 110,
                avatarUrl == null ? null : avatarUrl.toString(), false);
        ConcertoRunner.run(() -> this.avatar.loadImage(true, true));

        if (loggedIn() && isVersionSame()) {
            this.vipStatusWidget = this.addWidget(new ModifiablePressableTextWidget(
                    0, 0, 0, 0,
                    Component.empty(),
                    button -> ConcertoRunner.run(() -> {
                        // 更新 VIP 状态
                        KuGouMusicUser localUser = KuGouMusicApiClient.LOCAL_USER;
                        if (localUser.isLoggedIn() && localUser.isVersionSame()) {
                            Component tip;
                            if (localUser.updateVIPStatus()) {
                                tip = Component.translatable("concerto.screen.kugou.vip.update_success");
                            } else {
                                tip = Component.translatable("concerto.screen.kugou.vip.update_failed");
                            }
                            displayAlert(tip);
                        }
                    }),
                    font
            ));
        }
    }

    private boolean loggedIn() {
        return KuGouMusicApiClient.LOCAL_USER.isLoggedIn();
    }

    private boolean isVersionSame() {
        return KuGouMusicApiClient.LOCAL_USER.isVersionSame();
    }

    @Override
    public void render(GuiGraphics matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        Component text = this.loggedIn() ? Component.translatable("concerto.screen.kugou.welcome", KuGouMusicApiClient.LOCAL_USER.getUserName()) :
                Component.translatable("concerto.screen.kugou.not_login");
        matrices.drawCenteredString(this.font, text, this.width / 2, 90, 0xffffffff);

        if (this.loggedIn()) {
            boolean isVersionSame = isVersionSame();
            int fontHeight = this.font.lineHeight;
            int x = 5;
            int bottom = this.height - 5;

            Component currentVersion = Component.translatable("concerto.screen.kugou.version.current", getVersionName(KuGouMusicApiClient.LOCAL_USER.isLite()));
            Component apiVersion = Component.translatable("concerto.screen.kugou.version.options", getVersionName(ClientConfig.INSTANCE.options.kuGouMusicLite));
            Component versionStatus = isVersionSame ?
                    Component.translatable("concerto.screen.kugou.version.correct") :
                    Component.translatable("concerto.screen.kugou.version.warning");

            // 避免 VIP 适用平台歧义, 只有版本匹配时才显示
            if (isVersionSame) {
                KuGouMusicUser.VIPLevel vipLevel = KuGouMusicApiClient.LOCAL_USER.getVipLevel();

                LocalDateTime vipExpireTime = KuGouMusicApiClient.LOCAL_USER.getVipExpireTime();
                if (vipLevel != KuGouMusicUser.VIPLevel.NONE && vipExpireTime != null) {
                    Component expireTime = Component.translatable("concerto.screen.kugou.vip.expire_time", KuGouMusicUser.FORMATTER.format(vipExpireTime));
                    bottom -= fontHeight;
                    matrices.drawString(this.font, expireTime, x, bottom, 0xffffffff, true);
                    bottom -= 1;
                }

                String levelPrefix = "concerto.screen.kugou.vip.level.";
                String  levelText = Component.translatable(levelPrefix + vipLevel.name().toLowerCase()).getString();
                Component vipStatus = Component.translatable("concerto.screen.kugou.vip.vip_level", levelText);
                bottom -= fontHeight;
                if (vipStatusWidget != null) {
                    vipStatusWidget.setX(x);
                    vipStatusWidget.setY(bottom);
                    vipStatusWidget.setText(vipStatus);
                    vipStatusWidget.render(matrices, mouseX, mouseY, delta);
                }
                bottom -= 1;
            }

            matrices.drawString(this.font, versionStatus, x, bottom - fontHeight, isVersionSame ? 5635925 : 16733525, true);
            matrices.drawString(this.font, apiVersion, x, bottom - fontHeight * 2 - 1, 0xffffffff, true);
            matrices.drawString(this.font, currentVersion, x, bottom - fontHeight * 3 - 2, 0xffffffff, true);
        }

        this.avatar.render(matrices, mouseX, mouseY, delta);
    }

    public String getVersionName(boolean isLite) {
        return Component.translatable("concerto.screen.kugou.version." + (isLite ? "lite" : "normal")).getString();
    }
}
