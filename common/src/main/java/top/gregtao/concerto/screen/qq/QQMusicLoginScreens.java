package top.gregtao.concerto.screen.qq;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.core.http.qq.QQMusicApiClient;
import top.gregtao.concerto.screen.ConcertoScreen;
import top.gregtao.concerto.screen.login.CookieLoginScreen;
import top.gregtao.concerto.util.QRCodeRenderer;
import top.gregtao.concerto.screen.login.QRCodeLoginScreen;
import top.gregtao.concerto.core.util.Pair;

import java.net.http.HttpResponse;
import java.util.List;

public class QQMusicLoginScreens extends ConcertoScreen {

    public static Component SOURCE_TEXT = Component.translatable("concerto.source.qq_music");

    public QQMusicLoginScreens(Screen parent) {
        super(Component.literal(Component.translatable("concerto.screen.login").getString() + SOURCE_TEXT.getString()), parent);
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.login.type.qrcode.wechat"),
                button -> Minecraft.getInstance().setScreen(this.weChatQRLogin())
        ).size(150, 20).pos(this.width / 2 - 75, 40).build());
        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.login.type.qrcode.qq"),
                button -> Minecraft.getInstance().setScreen(this.qqQRLogin())
        ).size(150, 20).pos(this.width / 2 - 75, 70).build());
        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.login.type.cookie"),
                button -> Minecraft.getInstance().setScreen(this.cookieLogin())
        ).size(150, 20).pos(this.width / 2 - 75, 100).build());
    }

    public QRCodeLoginScreen weChatQRLogin() {
        return new QRCodeLoginScreen(
                () -> {
                    try {
                        return QQMusicApiClient.INSTANCE.getWeChatQRKey();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                url -> QRCodeRenderer.generateQRCode(QQMusicApiClient.INSTANCE.combineWeChatQRLink(url)),
                key -> {
                    try {
                        Pair<Integer, String> pair = QQMusicApiClient.INSTANCE.getWeChatQRStatus(key);
                        int code = pair.getFirst();
                        if (code == 408 || code == 404) {
                            return QRCodeLoginScreen.Status.WAITING;
                        } else if (code == 402) {
                            return QRCodeLoginScreen.Status.EXPIRED;
                        } else if (code == 405) {
                            QQMusicApiClient.INSTANCE.setWxLoginCookies(pair.getSecond());
                            QQMusicApiClient.LOCAL_USER.updateLoginStatus();
                            return QRCodeLoginScreen.Status.SUCCESS;
                        } else {
                            return QRCodeLoginScreen.Status.EMPTY;
                        }
                    } catch (Exception e) {
                        ConcertoClient.LOGGER.error("Error in WeChat QR Login", e);
                        throw new RuntimeException(e);
                    }
                },
                110, 110,
                SOURCE_TEXT,
                this
        );
    }

    public QRCodeLoginScreen qqQRLogin() {
        return new QRCodeLoginScreen(
                QQMusicApiClient.INSTANCE::getQQLoginQRLink,
                url -> {
                    try {
                        return QQMusicApiClient.INSTANCE.openQQLoginApi().url(url).get(HttpResponse.BodyHandlers.ofByteArray()).body();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                key -> {
                    try {
                        Pair<Integer, String> pair = QQMusicApiClient.INSTANCE.getQQLoginQRStatus();
                        int code = pair.getFirst();
                        if (code == 66 || code == 67) {
                            return QRCodeLoginScreen.Status.WAITING;
                        } else if (code == 68) {
                            return QRCodeLoginScreen.Status.EXPIRED;
                        } else if (code == 0) {
                            QQMusicApiClient.INSTANCE.openQQLoginApi().url(pair.getSecond()).get();
                            QQMusicApiClient.INSTANCE.authorizeQQLogin();
                            QQMusicApiClient.LOCAL_USER.updateLoginStatus();
                            return QRCodeLoginScreen.Status.SUCCESS;
                        } else {
                            return QRCodeLoginScreen.Status.EMPTY;
                        }
                    } catch (Exception e) {
                        ConcertoClient.LOGGER.error("Error in QQ QR Login", e);
                        throw new RuntimeException(e);
                    }
                },
                111, 111,
                SOURCE_TEXT,
                this
        );
    }

    public CookieLoginScreen cookieLogin() {
        return new CookieLoginScreen(
                QQMusicApiClient.LOCAL_USER::updateLoginStatus,
                List.of("http://ssl.ptlogin2.qq.com", "http://u.y.qq.com"),
                QQMusicApiClient.INSTANCE,
                SOURCE_TEXT,
                this
        );
    }
}
