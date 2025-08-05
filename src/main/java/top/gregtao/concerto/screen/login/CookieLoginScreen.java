package top.gregtao.concerto.screen.login;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import top.gregtao.concerto.http.HttpApiClient;
import top.gregtao.concerto.screen.ConcertoScreen;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CookieLoginScreen extends ConcertoScreen {
    private final HttpApiClient client;
    private final List<String> urlList;
    private TextFieldWidget field;
    private final Supplier<Boolean> loginStatusUpdater;

    public CookieLoginScreen(Supplier<Boolean> loginStatusUpdater, List<String> urlList, HttpApiClient client, Text title, Screen parent) {
        super(Text.literal(Text.translatable("concerto.screen.login").getString() + title.getString()), parent);
        this.client = client;
        this.urlList = urlList;
        this.loginStatusUpdater = loginStatusUpdater;
    }

    @Override
    protected void init() {
        super.init();

        TextWidget textWidget = new TextWidget(this.width / 2 - 200, 20, 400, 10, Text.translatable("concerto.screen.login.cookie"), this.textRenderer);
        this.addDrawableChild(textWidget);

        this.field = new TextFieldWidget(this.textRenderer, this.width / 2 - 125, 30, 250, 20, Text.empty());
        this.field.setMaxLength(50000);
        this.addDrawableChild(this.field);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.login.confirm"), button -> this.tryLogin())
                .position(this.width / 2 - 125, 60).size(250, 20).build());
    }

    public static Map<String, String> parseCookiesRegex(String cookieHeader) {
        Map<String, String> cookies = new HashMap<>();
        if (cookieHeader == null || cookieHeader.isEmpty()) {
            return cookies;
        }

        Pattern pattern = Pattern.compile("(?<!\\S)([^=\\s]+)=([^;]*)(?=;|$)");
        Matcher matcher = pattern.matcher(cookieHeader);

        while (matcher.find()) {
            String key = matcher.group(1).trim();
            String value = matcher.group(2).trim();
            cookies.put(key, value);
        }

        return cookies;
    }

    private void tryLogin() {
        Map<String, String> cookies = parseCookiesRegex(this.field.getText().trim());
        if (cookies.isEmpty()) {
            this.displayAlert(Text.translatable("concerto.screen.login.cookie.empty"));
        } else {
            for (String url : this.urlList) {
                try {
                    this.client.setCookies(url, cookies);
                } catch (IOException | URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            }
            if (this.loginStatusUpdater.get()) {
                ClientPlayerEntity player = MinecraftClient.getInstance().player;
                if (player != null) {
                    player.sendMessage(Text.translatable("concerto.screen.login.success"), false);
                }
                MinecraftClient.getInstance().setScreen(null);
            } else {
                this.displayAlert(Text.translatable("concerto.screen.login.cookie.failed"));
            }
        }
    }
}
