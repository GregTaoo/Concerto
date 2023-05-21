package top.gregtao.concerto.screen.login;

import com.google.zxing.WriterException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import top.gregtao.concerto.http.qrcode.QRCode;
import top.gregtao.concerto.screen.ConcertoScreen;

import java.util.function.Function;
import java.util.function.Supplier;

public class QRCodeLoginScreen extends ConcertoScreen {
    private final Supplier<String> qrKeySupplier;
    private final Function<String, String> qrCodeLinkGetter;
    private final Function<String, Status> statusUpdater;
    private String key;
    private Status status = Status.EMPTY;
    private int timer = 0;
    private Text message = Text.empty();

    public QRCodeLoginScreen(Supplier<String> qrKeySupplier, Function<String, String> qrCodeLinkGetter, Function<String, Status> statusUpdater, Text title, Screen parent) {
        super(Text.translatable("concerto.screen.login").append(title), parent);
        this.qrKeySupplier = qrKeySupplier;
        this.qrCodeLinkGetter = qrCodeLinkGetter;
        this.statusUpdater = statusUpdater;
    }

    @Override
    protected void init() {
        super.init();
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.login.qrcode.refresh"), button -> {
            this.timer = 0;
            this.status = Status.EMPTY;
        }).size(100, 20).position(this.width / 2 - 50, 160).build());
    }

    @Override
    public void tick() {
        super.tick();
        if (this.timer == 0) {
            switch (this.status) {
                case EMPTY -> this.loadQRCode();
                case FAILED -> this.loadQRCode(Text.translatable("concerto.screen.login.qrcode.failed"));
                case EXPIRED -> this.loadQRCode(Text.translatable("concerto.screen.login.qrcode.expired"));
                case SUCCESS -> {
                    ClientPlayerEntity player = MinecraftClient.getInstance().player;
                    if (player != null) {
                        player.sendMessage(Text.translatable("concerto.screen.login.qrcode.success"));
                    }
                    MinecraftClient.getInstance().setScreen(null);
                    QRCode.clear();
                }
                case WAITING -> this.status = this.statusUpdater.apply(this.key);
            }
        }
        this.timer = (this.timer + 1) % 40;
    }

    public void loadQRCode() {
        this.key = this.qrKeySupplier.get();
        String link = this.qrCodeLinkGetter.apply(this.key);
        try {
            QRCode.load(link);
            this.status = Status.WAITING;
        } catch (WriterException e) {
            throw new RuntimeException(e);
        }
    }

    public void loadQRCode(Text msg) {
        this.message = msg;
        this.loadQRCode();
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        QRCode.drawQRCode(matrices, this.width / 2 - 55, 30);
        DrawableHelper.drawCenteredTextWithShadow(matrices, this.textRenderer, this.message, this.width / 2, this.height - 20, 0xffffffff);
    }

    @Override
    public void close() {
        super.close();
        QRCode.clear();
    }

    public enum Status {
        EMPTY,
        WAITING,
        EXPIRED,
        SUCCESS,
        FAILED
    }
}
