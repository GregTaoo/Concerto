package top.gregtao.concerto.screen.login;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import top.gregtao.concerto.screen.ConcertoScreen;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class CaptchaLoginScreen extends ConcertoScreen {
    private EditBox usernameField, captchaField;
    private Button captchaButton;
    private int captchaTimer = -1;
    private final Consumer<String> callForCaptcha;
    private final BiFunction<String, String, Component> loginHandler;
    private final Supplier<Boolean> loginChecker;

    public CaptchaLoginScreen(Consumer<String> callForCaptcha, Supplier<Boolean> loginChecker,
                              BiFunction<String, String, Component> loginHandler, Component title, Screen parent) {
        super(Component.literal(Component.translatable("concerto.screen.login").getString() + title.getString()), parent);
        this.callForCaptcha = callForCaptcha;
        this.loginChecker = loginChecker;
        this.loginHandler = loginHandler;
    }

    @Override
    protected void init() {
        super.init();
        this.usernameField = new EditBox(this.font, this.width / 2 - 30, 20, 90, 20, Component.empty());
        this.addWidget(this.usernameField);
        this.addRenderableWidget(this.usernameField);
        StringWidget textWidget = new StringWidget(this.width / 2 - 120, 22, 90, 20, Component.translatable("concerto.screen.login.username"), this.font);
        textWidget.alignLeft();
        this.addRenderableWidget(textWidget);
        this.captchaButton = Button.builder(Component.translatable("concerto.screen.login.get_captcha"), button -> {
            if (this.usernameField.getValue().isEmpty()) {
                this.displayAlert(Component.translatable("concerto.screen.login.empty"));
            } else {
                this.captchaButton.active = false;
                this.captchaTimer = 400;
                this.callForCaptcha.accept(this.usernameField.getValue());
            }
        }).pos(this.width / 2 + 65, 20).size(60, 20).build();
        this.addRenderableWidget(this.captchaButton);

        this.captchaField = new EditBox(this.font, this.width / 2 - 30, 50, 155, 20, Component.empty());
        this.addWidget(this.captchaField);
        this.addRenderableWidget(this.captchaField);
        StringWidget textWidget1 = new StringWidget(this.width / 2 - 120, 52, 90, 20, Component.translatable("concerto.screen.login.captcha"), this.font);
        textWidget1.alignLeft();
        this.addRenderableWidget(textWidget1);

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.login.confirm"), button -> this.tryLogin())
                .pos(this.width / 2 - 32, 80).size(157, 20).build());
    }

    public void tryLogin() {
        String username = this.usernameField.getValue().trim(), password = this.captchaField.getValue().trim();
        if (username.isEmpty() || password.isEmpty()) {
            this.displayAlert(Component.translatable("concerto.screen.login.empty"));
        } else {
            this.displayAlert(this.loginHandler.apply(username, password));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.loginChecker.get()) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(Component.translatable("concerto.screen.login.success"), false);
            }
            Minecraft.getInstance().setScreen(null);
        }
        if (this.captchaTimer > 0 && --this.captchaTimer == 0) {
            this.captchaButton.active = true;
            this.captchaTimer = -1;
        }
    }
}
