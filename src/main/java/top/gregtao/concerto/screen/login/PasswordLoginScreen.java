package top.gregtao.concerto.screen.login;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.*;
import top.gregtao.concerto.screen.ConcertoScreen;
import top.gregtao.concerto.screen.widget.TextWidget;

import java.util.function.BiFunction;
import java.util.function.Supplier;

public class PasswordLoginScreen extends ConcertoScreen {
    private TextFieldWidget usernameField, passwordField;
    private boolean showPassword = true;
    private final BiFunction<String, String, Text> loginHandler;
    private final Supplier<Boolean> loginChecker;

    public PasswordLoginScreen(Supplier<Boolean> loginChecker, BiFunction<String, String, Text> loginHandler, Text title, Screen parent) {
        super(new LiteralText(new TranslatableText("concerto.screen.login").getString() + title.getString()), parent);
        this.loginChecker = loginChecker;
        this.loginHandler = loginHandler;
    }

    @Override
    protected void init() {
        super.init();
        this.usernameField = new TextFieldWidget(this.textRenderer, this.width / 2 - 30, 20, 155, 20, Text.of(""));
        this.addSelectableChild(this.usernameField);
        this.addDrawableChild(this.usernameField);
        TextWidget textWidget = new TextWidget(this.width / 2 - 120, 22, 90, 20,
                new TranslatableText("concerto.screen.login.username"), this.textRenderer);
        textWidget.alignLeft();
        this.addDrawableChild(textWidget);

        this.passwordField = new TextFieldWidget(this.textRenderer, this.width / 2 - 30, 50, 90, 20, Text.of(""));
        this.addSelectableChild(this.passwordField);
        this.addDrawableChild(this.passwordField);
        TextWidget textWidget1 = new TextWidget(this.width / 2 - 120, 52, 90, 20,
                new TranslatableText("concerto.screen.login.password"), this.textRenderer);
        textWidget1.alignLeft();
        this.addDrawableChild(textWidget1);
        this.addDrawableChild(new ButtonWidget(this.width / 2 + 65, 50, 60, 20,
                new TranslatableText("concerto.screen.login.show_password"), button -> this.switchShowPassword()));

        this.addDrawableChild(new ButtonWidget(this.width / 2 - 32, 80, 157, 20,
                new TranslatableText("concerto.screen.login.confirm"), button -> this.tryLogin()));

        this.switchShowPassword();
    }

    public void switchShowPassword() {
        this.showPassword = !this.showPassword;
        this.passwordField.setRenderTextProvider(!this.showPassword ?
                (s, f) -> OrderedText.styledForwardsVisitedString("*".repeat(s.length()), Style.EMPTY) :
                (s, f) -> OrderedText.styledForwardsVisitedString(s, Style.EMPTY)
        );
    }

    public void tryLogin() {
        String username = this.usernameField.getText().trim(), password = this.passwordField.getText().trim();
        if (username.isEmpty() || password.isEmpty()) {
            this.displayAlert(new TranslatableText("concerto.screen.login.empty"));
        } else {
            this.displayAlert(this.loginHandler.apply(username, password));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.loginChecker.get()) {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player != null) {
                player.sendMessage(new TranslatableText("concerto.screen.login.success"), false);
            }
            MinecraftClient.getInstance().setScreen(null);
        }
    }
}
