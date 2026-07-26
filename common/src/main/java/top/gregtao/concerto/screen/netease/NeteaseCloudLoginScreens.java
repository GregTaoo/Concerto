package top.gregtao.concerto.screen.netease;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.core.http.netease.NeteaseCloudApiClient;
import top.gregtao.concerto.core.util.Pair;
import top.gregtao.concerto.core.util.TextUtil;
import top.gregtao.concerto.screen.ConcertoScreen;
import top.gregtao.concerto.screen.login.CaptchaLoginScreen;
import top.gregtao.concerto.screen.login.CookieLoginScreen;
import top.gregtao.concerto.screen.login.PasswordLoginScreen;
import top.gregtao.concerto.screen.login.QRCodeLoginScreen;
import top.gregtao.concerto.util.QRCodeRenderer;

import java.util.List;

public class NeteaseCloudLoginScreens extends ConcertoScreen {

    public static Component SOURCE_TEXT = Component.translatable("concerto.source.netease_cloud");

    public NeteaseCloudLoginScreens(Screen parent) {
        super(Component.literal(Component.translatable("concerto.screen.login").getString() + SOURCE_TEXT.getString()), parent);
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.login.type.password"),
                button -> Minecraft.getInstance().gui.setScreen(this.passwordLogin())
        ).size(150, 20).pos(this.width / 2 - 75, 40).build());
        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.login.type.captcha"),
                button -> Minecraft.getInstance().gui.setScreen(this.captchaLogin())
        ).size(150, 20).pos(this.width / 2 - 75, 70).build());
        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.login.type.qrcode"),
                button -> Minecraft.getInstance().gui.setScreen(this.qrCodeLogin())
        ).size(150, 20).pos(this.width / 2 - 75, 100).build());
        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.login.type.cookie"),
                button -> Minecraft.getInstance().gui.setScreen(this.cookieLogin())
        ).size(150, 20).pos(this.width / 2 - 75, 130).build());
    }

    private static boolean loginChecker() {
        return NeteaseCloudApiClient.LOCAL_USER.loggedIn;
    }

    public PasswordLoginScreen passwordLogin() {
        return new PasswordLoginScreen(
                NeteaseCloudLoginScreens::loginChecker,
                (username, password) -> {
                    try {
                        Pair<Integer, String> message = TextUtil.isDigit(username) ?
                                NeteaseCloudApiClient.INSTANCE.cellphoneLogin(username, false, password) :
                                NeteaseCloudApiClient.INSTANCE.emailPasswordLogin(username, password);
                        if (message.getFirst() == 200) {
                            return Component.translatable("concerto.login.163.success");
                        } else {
                            return Component.translatable("concerto.login.163.failed", message.getSecond());
                        }
                    } catch (Exception e) {
                        return Component.translatable("concerto.login.163.error");
                    }
                },
                SOURCE_TEXT,
                this
        );
    }

    public CaptchaLoginScreen captchaLogin() {
        return new CaptchaLoginScreen(
                phone -> {
                    try {
                        NeteaseCloudApiClient.INSTANCE.sendPhoneCaptcha(phone);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                NeteaseCloudLoginScreens::loginChecker,
                (username, password) -> {
                    try {
                        Pair<Integer, String> message = NeteaseCloudApiClient.INSTANCE.cellphoneLogin(username, true, password);
                        if (message.getFirst() == 200) {
                            return Component.translatable("concerto.login.163.success");
                        } else {
                            return Component.translatable("concerto.login.163.failed", message.getSecond());
                        }
                    } catch (Exception e) {
                        return Component.translatable("concerto.login.163.error");
                    }
                },
                SOURCE_TEXT,
                this
        );
    }

    public QRCodeLoginScreen qrCodeLogin() {
        return new QRCodeLoginScreen(
                () -> {
                    try {
                        return NeteaseCloudApiClient.INSTANCE.generateQRCodeKey();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                url -> QRCodeRenderer.generateQRCode(NeteaseCloudApiClient.INSTANCE.getQRCodeLoginLink(url)),
                key -> {
                    try {
                        Pair<Integer, String> pair = NeteaseCloudApiClient.INSTANCE.getQRCodeStatus(key);
                        int code = pair.getFirst();
                        if (code == 801 || code == 802) {
                            return QRCodeLoginScreen.Status.WAITING;
                        } else if (code == 800) {
                            return QRCodeLoginScreen.Status.EXPIRED;
                        } else if (code == 803) {
                            NeteaseCloudApiClient.LOCAL_USER.updateLoginStatus();
                            return QRCodeLoginScreen.Status.SUCCESS;
                        } else {
                            return QRCodeLoginScreen.Status.EMPTY;
                        }
                    } catch (Exception e) {
                        ConcertoClient.LOGGER.error("Error in Netease QR Login", e);
                        throw new RuntimeException(e);
                    }
                },
                110, 110,
                SOURCE_TEXT,
                this
        );
    }

    public CookieLoginScreen cookieLogin() {
        return new CookieLoginScreen(
                NeteaseCloudApiClient.LOCAL_USER::updateLoginStatus,
                List.of("http://music.163.com"),
                NeteaseCloudApiClient.INSTANCE,
                SOURCE_TEXT,
                this
        );
    }
}
