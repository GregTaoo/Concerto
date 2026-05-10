package top.gregtao.concerto.screen.login;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import top.gregtao.concerto.core.http.HttpApiClient;
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
    private EditBox field;
    private final Supplier<Boolean> loginStatusUpdater;

    public CookieLoginScreen(Supplier<Boolean> loginStatusUpdater, List<String> urlList, HttpApiClient client, Component title, Screen parent) {
        super(Component.literal(Component.translatable("concerto.screen.login").getString() + title.getString()), parent);
        this.client = client;
        this.urlList = urlList;
        this.loginStatusUpdater = loginStatusUpdater;
    }

    @Override
    protected void init() {
        super.init();

        StringWidget textWidget = new StringWidget(this.width / 2 - 200, 20, 400, 10, Component.translatable("concerto.screen.login.cookie"), this.font);
        this.addRenderableWidget(textWidget);

        this.field = new EditBox(this.font, this.width / 2 - 125, 30, 250, 20, Component.empty());
        this.field.setMaxLength(50000);
        this.addRenderableWidget(this.field);

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.login.confirm"), button -> this.tryLogin())
                .pos(this.width / 2 - 125, 60).size(250, 20).build());
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
        Map<String, String> cookies = parseCookiesRegex(this.field.getValue().trim());
        if (cookies.isEmpty()) {
            this.displayAlert(Component.translatable("concerto.screen.login.cookie.empty"));
        } else {
            for (String url : this.urlList) {
                try {
                    this.client.setCookies(url, cookies);
                } catch (IOException | URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            }
            if (this.loginStatusUpdater.get()) {
                LocalPlayer player = Minecraft.getInstance().player;
                if (player != null) {
                    player.sendSystemMessage(Component.translatable("concerto.screen.login.success"));
                }
                Minecraft.getInstance().setScreen(null);
            } else {
                this.displayAlert(Component.translatable("concerto.screen.login.cookie.failed"));
            }
        }
    }
}
