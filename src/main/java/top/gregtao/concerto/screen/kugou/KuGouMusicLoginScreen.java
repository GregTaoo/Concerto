package top.gregtao.concerto.screen.kugou;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.http.kugou.KuGouMusicApiClient;
import top.gregtao.concerto.screen.ConcertoScreen;
import top.gregtao.concerto.screen.login.CaptchaLoginScreen;
import top.gregtao.concerto.screen.login.CookieLoginScreen;
import top.gregtao.concerto.screen.login.PasswordLoginScreen;
import top.gregtao.concerto.screen.login.QRCodeLoginScreen;
import top.gregtao.concerto.util.Pair;
import top.gregtao.concerto.util.QRCodeRenderer;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class KuGouMusicLoginScreen extends ConcertoScreen {

    public static Text SOURCE_TEXT = Text.translatable("concerto.source.kugou_music");

    public KuGouMusicLoginScreen(Screen parent) {
        super(Text.literal(Text.translatable("concerto.screen.login").getString() + SOURCE_TEXT.getString()), parent);
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
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.login.type.cookie"),
                button -> MinecraftClient.getInstance().setScreen(this.cookieLogin())
        ).size(100, 20).position(this.width / 2 - 50, 130).build());
    }

    private static boolean loginChecker() {
        return KuGouMusicApiClient.LOCAL_USER.isLoggedIn();
    }

    public PasswordLoginScreen passwordLogin() {
        return new PasswordLoginScreen(
                KuGouMusicLoginScreen::loginChecker,
                (username, password) -> {
                    try {
                        Optional<Pair<Long, String>> optional = KuGouMusicApiClient.INSTANCE.login(username, password);
                        if (optional.isPresent()) {
                            Pair<Long, String> pair = optional.get();
                            setCookies(pair.getFirst(), pair.getSecond());
                            return Text.translatable("concerto.login.kugou.success");
                        } else {
                            return Text.translatable("concerto.login.kugou.failed");
                        }
                    } catch (Exception e) {
                        return Text.translatable("concerto.login.kugou.error");
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
                        KuGouMusicApiClient.INSTANCE.sendPhoneCaptcha(phone);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                KuGouMusicLoginScreen::loginChecker,
                (username, password) -> {
                    try {
                        Optional<Pair<Long, String>> result = KuGouMusicApiClient.INSTANCE.cellphoneLogin(username, password);
                        if (result.isPresent()) {
                            Pair<Long, String> pair = result.get();

                            setCookies(pair.getFirst(), pair.getSecond());
                            return Text.translatable("concerto.login.kugou.success");
                        } else {
                            return Text.translatable("concerto.login.kugou.failed");
                        }
                    } catch (Exception e) {
                        return Text.translatable("concerto.login.kugou.error");
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
                        return KuGouMusicApiClient.INSTANCE.generateQRCodeKey();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                key -> QRCodeRenderer.generateQRCode(KuGouMusicApiClient.INSTANCE.getQRCodeLoginLink(key), 5),
                key -> {
                    try {
                        Optional<JsonObject> optional = KuGouMusicApiClient.INSTANCE.getQRCodeStatus(key);
                        Optional<JsonObject> dataOpt = optional.map(json -> json.getAsJsonObject("data"));
                        Integer status = dataOpt.map(data -> data.get("status"))
                                .map(JsonElement::getAsInt)
                                .orElse(0);
                        if (status == 1 || status == 2) {
                            return QRCodeLoginScreen.Status.WAITING;
                        } else if (status == 0) {
                            return QRCodeLoginScreen.Status.EXPIRED;
                        } else if (status == 4) {
                            Long userId = dataOpt.map(data -> data.get("userid"))
                                    .map(JsonElement::getAsLong)
                                    .orElseThrow();

                            String token = dataOpt.map(data -> data.get("token"))
                                    .map(JsonElement::getAsString)
                                    .orElseThrow();

                            setCookies(userId, token);
                            return QRCodeLoginScreen.Status.SUCCESS;
                        } else {
                            return QRCodeLoginScreen.Status.EMPTY;
                        }
                    } catch (Exception e) {
                        ConcertoClient.LOGGER.error("Error in KuGou Music QR Login", e);
                        throw new RuntimeException(e);
                    }
                },
                110, 110,
                SOURCE_TEXT,
                this
        );
    }

    public void setCookies(Long userId, String token) throws IOException, URISyntaxException {
        KuGouMusicApiClient.LOCAL_USER.setUserId(userId);
        KuGouMusicApiClient.INSTANCE.clearCookie();
        KuGouMusicApiClient.INSTANCE.setCookies(
                "https://www.kugou.com/",
                new HashMap<>(Map.of(
                        "userid", userId.toString(),
                        "token", token
                ))
        );
        KuGouMusicApiClient.LOCAL_USER.updateLoginStatusAndDfid();
        KuGouMusicApiClient.LOCAL_USER.updateVIPStatus();
    }

    public CookieLoginScreen cookieLogin() {
        return new CookieLoginScreen(
                () -> {
                    boolean updated = KuGouMusicApiClient.LOCAL_USER.updateLoginStatusAndDfid();
                    if (updated) {
                        KuGouMusicApiClient.LOCAL_USER.updateVIPStatus();
                    }
                    return updated;
                },
                List.of("https://www.kugou.com/"),
                KuGouMusicApiClient.INSTANCE,
                SOURCE_TEXT,
                this
        );
    }
}
