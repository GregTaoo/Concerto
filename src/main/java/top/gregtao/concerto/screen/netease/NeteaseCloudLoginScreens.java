package top.gregtao.concerto.screen.netease;

import com.mojang.datafixers.util.Pair;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import top.gregtao.concerto.http.netease.NeteaseCloudApiClient;
import top.gregtao.concerto.screen.ConcertoScreen;
import top.gregtao.concerto.screen.login.CaptchaLoginScreen;
import top.gregtao.concerto.screen.login.PasswordLoginScreen;
import top.gregtao.concerto.screen.login.QRCodeLoginScreen;
import top.gregtao.concerto.util.TextUtil;

import java.net.MalformedURLException;

public class NeteaseCloudLoginScreens extends ConcertoScreen {

    public static Text SOURCE_TEXT = Text.translatable("concerto.source.netease_cloud");

    public NeteaseCloudLoginScreens(Screen parent) {
        super(Text.translatable("concerto.screen.login").append(SOURCE_TEXT), parent);
    }

    @Override
    protected void init() {
        super.init();
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.login.type.password"),
                button -> MinecraftClient.getInstance().setScreen(this.passwordLogin())
        ).size(100, 20).position(this.width / 2 - 50, 40).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.login.type.captcha"),
                button -> MinecraftClient.getInstance().setScreen(this.captchaLogin())
        ).size(100, 20).position(this.width / 2 - 50, 70).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.login.type.qrcode"),
                button -> MinecraftClient.getInstance().setScreen(this.qrCodeLogin())
        ).size(100, 20).position(this.width / 2 - 50, 100).build());
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
                            return Text.translatable("concerto.login.163.success");
                        } else {
                            return Text.translatable("concerto.login.163.failed", message.getSecond());
                        }
                    } catch (Exception e) {
                        return Text.translatable("concerto.login.163.error");
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
                            return Text.translatable("concerto.login.163.success");
                        } else {
                            return Text.translatable("concerto.login.163.failed", message.getSecond());
                        }
                    } catch (Exception e) {
                        return Text.translatable("concerto.login.163.error");
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
                key -> {
                    try {
                        return NeteaseCloudApiClient.INSTANCE.getQRCodeLoginLink(key);
                    } catch (MalformedURLException e) {
                        throw new RuntimeException(e);
                    }
                },
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
                        throw new RuntimeException(e);
                    }
                },
                SOURCE_TEXT,
                this
        );
    }
}
