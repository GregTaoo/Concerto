package top.gregtao.concerto.experimental.screen.qq;

import com.mojang.datafixers.util.Pair;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import top.gregtao.concerto.experimental.http.qq.QQMusicApiClient;
import top.gregtao.concerto.screen.ConcertoScreen;
import top.gregtao.concerto.screen.login.QRCodeLoginScreen;

public class QQMusicLoginScreens extends ConcertoScreen {

    public static Text SOURCE_TEXT = Text.translatable("concerto.source.qq_music");

    public QQMusicLoginScreens(Screen parent) {
        super(Text.translatable("concerto.screen.login").append(SOURCE_TEXT), parent);
    }

    @Override
    protected void init() {
        super.init();
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.login.type.qrcode"),
                button -> MinecraftClient.getInstance().setScreen(this.weChatQRLogin())
        ).size(100, 20).position(this.width / 2 - 50, 40).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.login.type.qrcode"),
                button -> MinecraftClient.getInstance().setScreen(this.qqQRLogin())
        ).size(100, 20).position(this.width / 2 - 50, 70).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.login.type.qrcode"),
                button -> QQMusicApiClient.LOCAL_USER.logout()
        ).size(100, 20).position(this.width / 2 - 50, 100).build());
    }

    private static boolean loginChecker() {
        return QQMusicApiClient.LOCAL_USER.loggedIn;
    }

    public QRCodeLoginScreen weChatQRLogin() {
        return new QRCodeLoginScreen(
                () -> {
                    try {
                        return QQMusicApiClient.U.getWeChatQRKey();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                key -> QQMusicApiClient.U.combineWeChatQRLink(key),
                null,
                key -> {
                    try {
                        Pair<Integer, String> pair = QQMusicApiClient.U.getWeChatQRStatus(key);
                        int code = pair.getFirst();
                        if (code == 408 || code == 404) {
                            return QRCodeLoginScreen.Status.WAITING;
                        } else if (code == 402) {
                            return QRCodeLoginScreen.Status.EXPIRED;
                        } else if (code == 405) {
                            QQMusicApiClient.U.setWxLoginCookies(pair.getSecond());
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
                () -> QQMusicApiClient.U.getQQLoginQRLink(),
                null,
                url -> {
                    try {
                        return QQMusicApiClient.U.getInBytes(url.toString());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                key -> {
                    try {
                        Pair<Integer, String> pair = QQMusicApiClient.U.getQQLoginQRStatus();
                        int code = pair.getFirst();
                        if (code == 66 || code == 67) {
                            return QRCodeLoginScreen.Status.WAITING;
                        } else if (code == 68) {
                            return QRCodeLoginScreen.Status.EXPIRED;
                        } else if (code == 0) {
                            QQMusicApiClient.U.get(pair.getSecond());
                            QQMusicApiClient.U.authorizeQQLogin();
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
