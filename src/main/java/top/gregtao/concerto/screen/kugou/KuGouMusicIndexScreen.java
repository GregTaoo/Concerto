package top.gregtao.concerto.screen.kugou;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import top.gregtao.concerto.config.ClientConfig;
import top.gregtao.concerto.http.kugou.KuGouMusicApiClient;
import top.gregtao.concerto.http.kugou.KuGouMusicUser;
import top.gregtao.concerto.screen.ConcertoScreen;
import top.gregtao.concerto.screen.widget.ModifiablePressableTextWidget;
import top.gregtao.concerto.screen.widget.URLImageWidget;
import top.gregtao.concerto.util.ConcertoRunner;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.LocalDateTime;

public class KuGouMusicIndexScreen extends ConcertoScreen {
    private URLImageWidget avatar;

    private ModifiablePressableTextWidget vipStatusWidget;

    public KuGouMusicIndexScreen(Screen parent) {
        super(new TranslatableText("concerto.screen.index.kugou"), parent);
    }

    @Override
    protected void init() {
        super.init();
        this.addDrawableChild(new ButtonWidget(this.width / 2 - 50, 40, 100, 20, new TranslatableText("concerto.screen.user"),
                button -> MinecraftClient.getInstance().setScreen(new KuGouMusicUserScreen(this))
        ));
        this.addDrawableChild(new ButtonWidget(this.width / 2 - 50, 65, 100, 20, new TranslatableText("concerto.screen.search"),
                button -> MinecraftClient.getInstance().setScreen(new KuGouMusicSearchScreen(this))
        ));

        URL avatarUrl;
        try {
            avatarUrl = (!this.loggedIn() || KuGouMusicApiClient.LOCAL_USER.getAvatarUrl().isEmpty()) ? null :
                    URI.create(KuGouMusicApiClient.LOCAL_USER.getAvatarUrl()).toURL();
        } catch (MalformedURLException e) {
            avatarUrl = null;
        }
        this.avatar = new URLImageWidget(64, 64, this.width / 2 - 32, 110,
                avatarUrl == null ? null : avatarUrl.toString(), false);
        ConcertoRunner.run(() -> {
            this.avatar.loadImage(true, true);
        });

        if (loggedIn() && isVersionSame()) {
            this.vipStatusWidget = this.addSelectableChild(new ModifiablePressableTextWidget(
                    0, 0, 0, textRenderer.fontHeight,
                    LiteralText.EMPTY,
                    button -> {
                        ConcertoRunner.run(() -> {
                            // 更新 VIP 状态
                            KuGouMusicUser localUser = KuGouMusicApiClient.LOCAL_USER;
                            if (localUser.isLoggedIn() && localUser.isVersionSame()) {
                                Text tip;
                                if (localUser.updateVIPStatus()) {
                                    tip = new TranslatableText("concerto.screen.kugou.vip.update_success");
                                } else {
                                    tip = new TranslatableText("concerto.screen.kugou.vip.update_failed");
                                }
                                displayAlert(tip);
                            }
                        });
                    },
                    textRenderer
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
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        Text text = this.loggedIn() ? new TranslatableText("concerto.screen.kugou.welcome", KuGouMusicApiClient.LOCAL_USER.getUserName()) :
                new TranslatableText("concerto.screen.kugou.not_login");
        DrawableHelper.drawCenteredTextWithShadow(matrices, this.textRenderer, text.asOrderedText(), this.width / 2, 90, 0xffffffff);

        if (this.loggedIn()) {
            boolean isVersionSame = isVersionSame();
            int fontHeight = this.textRenderer.fontHeight;
            int x = 5;
            int bottom = this.height - 5;

            Text currentVersion = new TranslatableText("concerto.screen.kugou.version.current", getVersionName(KuGouMusicApiClient.LOCAL_USER.isLite()));
            Text apiVersion = new TranslatableText("concerto.screen.kugou.version.options", getVersionName(ClientConfig.INSTANCE.options.kuGouMusicLite));
            Text versionStatus = isVersionSame ?
                    new TranslatableText("concerto.screen.kugou.version.correct") :
                    new TranslatableText("concerto.screen.kugou.version.warning");

            // 避免 VIP 适用平台歧义, 只有版本匹配时才显示
            if (isVersionSame) {
                KuGouMusicUser.VIPLevel vipLevel = KuGouMusicApiClient.LOCAL_USER.getVipLevel();

                LocalDateTime vipExpireTime = KuGouMusicApiClient.LOCAL_USER.getVipExpireTime();
                if (vipLevel != KuGouMusicUser.VIPLevel.NONE && vipExpireTime != null) {
                    Text expireTime = new TranslatableText("concerto.screen.kugou.vip.expire_time", KuGouMusicUser.FORMATTER.format(vipExpireTime));
                    bottom -= fontHeight;
                    DrawableHelper.drawTextWithShadow(matrices, this.textRenderer, expireTime, x, bottom, 0xffffffff);
                    bottom -= 1;
                }

                String levelPrefix = "concerto.screen.kugou.vip.level.";
                String  levelText = new TranslatableText(levelPrefix + vipLevel.name().toLowerCase()).getString();
                Text vipStatus = new TranslatableText("concerto.screen.kugou.vip.vip_level", levelText);
                bottom -= fontHeight;
                if (vipStatusWidget != null) {
                    vipStatusWidget.x = x;
                    vipStatusWidget.y = bottom;
                    vipStatusWidget.setText(vipStatus);
                    vipStatusWidget.render(matrices, mouseX, mouseY, delta);
                }
                bottom -= 1;
            }

            DrawableHelper.drawTextWithShadow(matrices, this.textRenderer, versionStatus, x, bottom - fontHeight, isVersionSame ? 5635925 : 16733525);
            DrawableHelper.drawTextWithShadow(matrices, this.textRenderer, apiVersion, x, bottom - fontHeight * 2 - 1, 0xffffffff);
            DrawableHelper.drawTextWithShadow(matrices, this.textRenderer, currentVersion, x, bottom - fontHeight * 3 - 2, 0xffffffff);
        }

        this.avatar.render(matrices, mouseX, mouseY, delta);
    }

    public String getVersionName(boolean isLite) {
        return new TranslatableText("concerto.screen.kugou.version." + (isLite ? "lite" : "normal")).getString();
    }
}
