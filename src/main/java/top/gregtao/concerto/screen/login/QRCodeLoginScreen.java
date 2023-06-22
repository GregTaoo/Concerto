package top.gregtao.concerto.screen.login;

import com.google.zxing.WriterException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import top.gregtao.concerto.http.QRCode;
import top.gregtao.concerto.player.MusicPlayer;
import top.gregtao.concerto.screen.ConcertoScreen;
import top.gregtao.concerto.screen.widget.URLImageWidget;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.function.Function;
import java.util.function.Supplier;

public class QRCodeLoginScreen extends ConcertoScreen {
    private final Supplier<String> qrKeySupplier;
    private final Function<String, String> qrCodeLinkGetter;
    private final Function<String, Status> statusUpdater;
    private final Function<URL, byte[]> imageUpdater;
    private String key;
    private Status status = Status.EMPTY;
    private int timer = 0;
    private final int qrWidth;
    private final int qrHeight;
    private Text message = Text.empty();
    private boolean updaterLock = false;
    private final boolean isSpecificImage;
    private URLImageWidget urlImageWidget;

    public QRCodeLoginScreen(Supplier<String> qrKeySupplier, Function<String, String> qrCodeLinkGetter, Function<URL, byte[]> imageUpdater, Function<String, Status> statusUpdater, boolean isSpecificImage, int width, int height, Text title, Screen parent) {
        super(Text.literal(Text.translatable("concerto.screen.login").getString() + title.getString()), parent);
        this.qrKeySupplier = qrKeySupplier;
        this.qrCodeLinkGetter = qrCodeLinkGetter;
        this.statusUpdater = statusUpdater;
        this.isSpecificImage = isSpecificImage;
        this.imageUpdater = imageUpdater;
        this.qrWidth = width;
        this.qrHeight = height;
    }

    @Override
    protected void init() {
        super.init();
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.login.qrcode.refresh"), button -> {
            this.timer = 0;
            this.status = Status.EMPTY;
        }).size(100, 20).position(this.width / 2 - 50, this.height - 40).build());
        if (this.isSpecificImage) {
            this.urlImageWidget = new URLImageWidget(this.qrWidth, this.qrHeight, this.width / 2 - this.qrWidth / 2, 30, null);
        }
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
            if (this.isSpecificImage) {
                String link = this.qrKeySupplier.get();
                try {
                    this.urlImageWidget.setUrl(new URL(link));
                    if (this.imageUpdater != null) this.urlImageWidget.loadImage(this.imageUpdater);
                    else this.urlImageWidget.loadImage();
                    this.status = Status.WAITING;
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }
            } else {
                this.key = this.qrKeySupplier.get();
                String link = this.qrCodeLinkGetter.apply(this.key);
                try {
                    QRCode.load(link);
                    this.status = Status.WAITING;
                } catch (WriterException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    public void loadQRCode(Text msg) {
        this.message = msg;
        this.loadQRCode();
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        if (this.isSpecificImage) {
            this.urlImageWidget.render(matrices, mouseX, mouseY, delta);
        } else {
            QRCode.drawQRCode(matrices, this.width / 2 - this.qrWidth / 2, 30);
        }
        DrawableHelper.drawCenteredTextWithShadow(matrices, this.textRenderer, this.message, this.width / 2, this.height - 20, 0xffffffff);
    }

    @Override
    public void close() {
        super.close();
        if (this.isSpecificImage) this.urlImageWidget.close();
        else QRCode.clear();
    }

    public enum Status {
        EMPTY,
        WAITING,
        EXPIRED,
        SUCCESS,
        FAILED
    }
}
