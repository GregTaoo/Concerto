package top.gregtao.concerto.screen.qq;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import top.gregtao.concerto.http.qq.QQMusicApiClient;
import top.gregtao.concerto.screen.ConcertoScreen;
import top.gregtao.concerto.screen.login.QRCodeLoginScreen;
import top.gregtao.concerto.util.Pair;

import java.net.http.HttpResponse;

public class QQMusicLoginScreens extends ConcertoScreen {

    public static Text SOURCE_TEXT = new TranslatableText("concerto.source.qq_music");

    public QQMusicLoginScreens(Screen parent) {
        super(new LiteralText(new TranslatableText("concerto.screen.login").getString() + SOURCE_TEXT.getString()), parent);
    }

    @Override
    protected void init() {
        super.init();
        this.addDrawableChild(new ButtonWidget(this.width / 2 - 50, 40, 100, 20,
                new TranslatableText("concerto.screen.login.type.qrcode.wechat"),
                button -> MinecraftClient.getInstance().setScreen(this.weChatQRLogin())
        ));
        this.addDrawableChild(new ButtonWidget(this.width / 2 - 50, 70, 100, 20,
                new TranslatableText("concerto.screen.login.type.qrcode.qq"),
                button -> MinecraftClient.getInstance().setScreen(this.qqQRLogin())
        ));
    }

    private static boolean loginChecker() {
        return QQMusicApiClient.LOCAL_USER.loggedIn;
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
                QQMusicApiClient.INSTANCE::combineWeChatQRLink,
                null,
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
                        throw new RuntimeException(e);
                    }
                },
                false, 110, 110,
                SOURCE_TEXT,
                this
        );
    }

    public QRCodeLoginScreen qqQRLogin() {
        return new QRCodeLoginScreen(
                QQMusicApiClient.INSTANCE::getQQLoginQRLink,
                null,
                url -> {
                    try {
                        return QQMusicApiClient.INSTANCE.openQQLoginApi().url(url.toString()).get(HttpResponse.BodyHandlers.ofByteArray()).body();
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
                        throw new RuntimeException(e);
                    }
                },
                true, 111, 111,
                SOURCE_TEXT,
                this
        );
    }
}
