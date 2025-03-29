package top.gregtao.concerto.screen.login;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import top.gregtao.concerto.player.MusicPlayer;
import top.gregtao.concerto.screen.ConcertoScreen;
import top.gregtao.concerto.screen.widget.URLImageWidget;

import java.util.function.Function;
import java.util.function.Supplier;

public class QRCodeLoginScreen extends ConcertoScreen {
    private final Supplier<String> qrKeySupplier;
    private final Function<String, Status> statusUpdater;
    private final Function<String, byte[]> imageUpdater;
    private String key;
    private Status status = Status.EMPTY;
    private int timer = 0;
    private final int qrWidth;
    private final int qrHeight;
    private Text message = Text.of("");
    private boolean updaterLock = false;
    private URLImageWidget urlImageWidget;

    public QRCodeLoginScreen(Supplier<String> qrKeySupplier, Function<String, byte[]> imageUpdater,
                             Function<String, Status> statusUpdater, int width, int height, Text title, Screen parent) {
        super(new LiteralText(new TranslatableText("concerto.screen.login").getString() + title.getString()), parent);
        this.qrKeySupplier = qrKeySupplier;
        this.statusUpdater = statusUpdater;
        this.imageUpdater = imageUpdater;
        this.qrWidth = width;
        this.qrHeight = height;
    }

    @Override
    protected void init() {
        super.init();
        this.addButton(new ButtonWidget(this.width / 2 - 50, this.height - 40, 100, 20,
                new TranslatableText("concerto.screen.login.qrcode.refresh"), button -> {
            this.timer = 0;
            this.status = Status.EMPTY;
        }));
        this.urlImageWidget = new URLImageWidget(this.qrWidth, this.qrHeight, this.width / 2 - this.qrWidth / 2, 30, null);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.timer == 0) {
            switch (this.status) {
                case EMPTY -> this.loadQRCode();
                case FAILED -> this.loadQRCode(new TranslatableText("concerto.screen.login.qrcode.failed"));
                case EXPIRED -> this.loadQRCode(new TranslatableText("concerto.screen.login.qrcode.expired"));
                case SUCCESS -> {
                    ClientPlayerEntity player = MinecraftClient.getInstance().player;
                    if (player != null) {
                        player.sendMessage(new TranslatableText("concerto.screen.login.qrcode.success"), false);
                    }
                    MinecraftClient.getInstance().openScreen(null);
                }
                case WAITING -> {
                    if (!this.updaterLock) {
                        MusicPlayer.run(() -> {
                            this.updaterLock = true;
                            this.status = this.statusUpdater.apply(this.key);
                            this.updaterLock = false;
                        });
                    }
                }
            }
        }
        this.timer = (this.timer + 1) % 40;
    }

    public void loadQRCode() {
        MusicPlayer.run(() -> {
            String link = this.key = this.qrKeySupplier.get();
            this.urlImageWidget.setUrl(link);
            if (this.imageUpdater != null) this.urlImageWidget.loadImage(this.imageUpdater, false);
            else this.urlImageWidget.loadImage();
            this.status = Status.WAITING;
        });
    }

    public void loadQRCode(Text msg) {
        this.message = msg;
        this.loadQRCode();
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        this.urlImageWidget.render(matrices, mouseX, mouseY, delta);
        ConcertoScreen.drawCenteredTextWithShadow(matrices, this.textRenderer, this.message.asOrderedText(), this.width / 2, 120, 0xffffffff);
    }

    @Override
    public void onClose() {
        super.onClose();
        this.urlImageWidget.close();
    }

    public enum Status {
        EMPTY,
        WAITING,
        EXPIRED,
        SUCCESS,
        FAILED
    }
}
