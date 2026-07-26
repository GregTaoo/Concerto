package top.gregtao.concerto.screen.login;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import top.gregtao.concerto.screen.ConcertoScreen;

import java.util.function.BiFunction;
import java.util.function.Supplier;

public class PasswordLoginScreen extends ConcertoScreen {
    private EditBox usernameField, passwordField;
    private boolean showPassword = true;
    private final BiFunction<String, String, Component> loginHandler;
    private final Supplier<Boolean> loginChecker;

    public PasswordLoginScreen(Supplier<Boolean> loginChecker, BiFunction<String, String, Component> loginHandler, Component title, Screen parent) {
        super(Component.literal(Component.translatable("concerto.screen.login").getString() + title.getString()), parent);
        this.loginChecker = loginChecker;
        this.loginHandler = loginHandler;
    }

    @Override
    protected void init() {
        super.init();
        this.usernameField = new EditBox(this.font, this.width / 2 - 30, 20, 155, 20, Component.empty());
        this.addWidget(this.usernameField);
        this.addRenderableWidget(this.usernameField);
        StringWidget textWidget = new StringWidget(this.width / 2 - 120, 22, 90, 20, Component.translatable("concerto.screen.login.username"), this.font);
        this.addRenderableWidget(textWidget);

        this.passwordField = new EditBox(this.font, this.width / 2 - 30, 50, 90, 20, Component.empty());
        this.passwordField.addFormatter((s, f) -> !this.showPassword ?
                FormattedCharSequence.forward("*".repeat(s.length()), Style.EMPTY) :
                FormattedCharSequence.forward(s, Style.EMPTY)
        );
        this.addWidget(this.passwordField);
        this.addRenderableWidget(this.passwordField);
        StringWidget textWidget1 = new StringWidget(this.width / 2 - 120, 52, 90, 20, Component.translatable("concerto.screen.login.password"), this.font);
        this.addRenderableWidget(textWidget1);
        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.login.show_password"), button -> this.switchShowPassword())
                .pos(this.width / 2 + 65, 50).size(60, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.login.confirm"), button -> this.tryLogin())
                .pos(this.width / 2 - 32, 80).size(157, 20).build());

        this.switchShowPassword();
    }

    public void switchShowPassword() {
        this.showPassword = !this.showPassword;
    }

    public void tryLogin() {
        String username = this.usernameField.getValue().trim(), password = this.passwordField.getValue().trim();
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
                player.sendSystemMessage(Component.translatable("concerto.screen.login.success"));
            }
            Minecraft.getInstance().gui.setScreen(null);
        }
    }
}
