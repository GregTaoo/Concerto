package top.gregtao.concerto.screen.qq;

import com.mojang.datafixers.util.Pair;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import top.gregtao.concerto.http.qq.QQMusicApiClient;
import top.gregtao.concerto.screen.ConcertoScreen;
import top.gregtao.concerto.screen.login.QRCodeLoginScreen;

import java.net.http.HttpResponse;

public class QQMusicLoginScreens extends ConcertoScreen {

    public static Text SOURCE_TEXT = Text.translatable("concerto.source.qq_music");

    public QQMusicLoginScreens(Screen parent) {
        super(Text.literal(Text.translatable("concerto.screen.login").getString() + SOURCE_TEXT.getString()), parent);
    }

    @Override
    protected void init() {
        super.init();
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.login.type.qrcode.wechat"),
                button -> MinecraftClient.getInstance().setScreen(this.weChatQRLogin())
        ).size(100, 20).position(this.width / 2 - 50, 40).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.login.type.qrcode.qq"),
                button -> MinecraftClient.getInstance().setScreen(this.qqQRLogin())
        ).size(100, 20).position(this.width / 2 - 50, 70).build());
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
