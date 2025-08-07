package top.gregtao.concerto.screen.netease;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.http.netease.NeteaseCloudApiClient;
import top.gregtao.concerto.screen.ConcertoScreen;
import top.gregtao.concerto.screen.login.CookieLoginScreen;
import top.gregtao.concerto.util.QRCodeRenderer;
import top.gregtao.concerto.screen.login.CaptchaLoginScreen;
import top.gregtao.concerto.screen.login.PasswordLoginScreen;
import top.gregtao.concerto.screen.login.QRCodeLoginScreen;
import top.gregtao.concerto.util.Pair;
import top.gregtao.concerto.util.QRCodeRenderer;
import top.gregtao.concerto.util.TextUtil;

import java.util.List;

public class NeteaseCloudLoginScreens extends ConcertoScreen {

    public static Text SOURCE_TEXT = new TranslatableText("concerto.source.netease_cloud");

    public NeteaseCloudLoginScreens(Screen parent) {
        super(new LiteralText(new TranslatableText("concerto.screen.login").getString() + SOURCE_TEXT.getString()), parent);
    }

    @Override
    protected void init() {
        super.init();
        this.addDrawableChild(new ButtonWidget(this.width / 2 - 50, 40, 100, 20,
                new TranslatableText("concerto.screen.login.type.password"),
                button -> MinecraftClient.getInstance().setScreen(this.passwordLogin())
        ));
        this.addDrawableChild(new ButtonWidget(this.width / 2 - 50, 70, 100, 20,
                new TranslatableText("concerto.screen.login.type.captcha"),
                button -> MinecraftClient.getInstance().setScreen(this.captchaLogin())
        ));
        this.addDrawableChild(new ButtonWidget(this.width / 2 - 50, 100, 100, 20,
                new TranslatableText("concerto.screen.login.type.qrcode"),
                button -> MinecraftClient.getInstance().setScreen(this.qrCodeLogin())
        ));
        this.addDrawableChild(new ButtonWidget(this.width / 2 - 50, 130, 100, 20,
                new TranslatableText("concerto.screen.login.type.cookie"),
                button -> MinecraftClient.getInstance().setScreen(this.cookieLogin())
        ));
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
                            return new TranslatableText("concerto.login.163.success");
                        } else {
                            return new TranslatableText("concerto.login.163.failed", message.getSecond());
                        }
                    } catch (Exception e) {
                        return new TranslatableText("concerto.login.163.error");
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
                            return new TranslatableText("concerto.login.163.success");
                        } else {
                            return new TranslatableText("concerto.login.163.failed", message.getSecond());
                        }
                    } catch (Exception e) {
                        return new TranslatableText("concerto.login.163.error");
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
