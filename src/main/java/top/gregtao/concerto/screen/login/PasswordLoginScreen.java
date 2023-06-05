package top.gregtao.concerto.screen.login;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import top.gregtao.concerto.screen.ConcertoScreen;

import java.util.function.BiFunction;
import java.util.function.Supplier;

public class PasswordLoginScreen extends ConcertoScreen {
    private TextFieldWidget usernameField, passwordField;
    private boolean showPassword = true;
    private final BiFunction<String, String, Text> loginHandler;
    private final Supplier<Boolean> loginChecker;
    private Text message = Text.empty();

    public PasswordLoginScreen(Supplier<Boolean> loginChecker, BiFunction<String, String, Text> loginHandler, Text title, Screen parent) {
        super(Text.literal(Text.translatable("concerto.screen.login").getString() + title.getString()), parent);
        this.loginChecker = loginChecker;
        this.loginHandler = loginHandler;
    }

    @Override
    protected void init() {
        super.init();
        this.usernameField = new TextFieldWidget(this.textRenderer, this.width / 2 - 30, 20, 155, 20, Text.empty());
        this.addSelectableChild(this.usernameField);
        TextWidget textWidget = new TextWidget(this.width / 2 - 120, 22, 90, 20, Text.translatable("concerto.screen.login.username"), this.textRenderer);
        textWidget.alignLeft();
        this.addDrawableChild(textWidget);

        this.passwordField = new TextFieldWidget(this.textRenderer, this.width / 2 - 30, 50, 90, 20, Text.empty());
        this.addSelectableChild(this.passwordField);
        TextWidget textWidget1 = new TextWidget(this.width / 2 - 120, 52, 90, 20, Text.translatable("concerto.screen.login.password"), this.textRenderer);
        textWidget1.alignLeft();
        this.addDrawableChild(textWidget1);
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.login.show_password"), button -> this.switchShowPassword())
                .position(this.width / 2 + 65, 50).size(60, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.login.confirm"), button -> this.tryLogin())
                .position(this.width / 2 - 32, 80).size(157, 20).build());

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
            this.message = Text.translatable("concerto.screen.login.empty");
        } else {
            this.message = this.loginHandler.apply(username, password);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.loginChecker.get()) {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player != null) {
                player.sendMessage(Text.translatable("concerto.screen.login.success"));
            }
            MinecraftClient.getInstance().setScreen(null);
        }
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        this.usernameField.render(matrices, mouseX, mouseY, delta);
        this.passwordField.render(matrices, mouseX, mouseY, delta);
        DrawableHelper.drawCenteredTextWithShadow(matrices, this.textRenderer, this.message, this.width / 2, 120, 0xffffffff);
    }
}
