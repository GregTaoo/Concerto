package top.gregtao.concerto.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import top.gregtao.concerto.screen.widget.PressableTextWidget;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ConcertoScreen extends Screen {
    private final Screen parent;
    private Text message;
    private boolean messageVisible = false;

    public ConcertoScreen(Text title, Screen parent) {
        super(title.shallowCopy().formatted(Formatting.DARK_AQUA));
        this.parent = parent;
    }

    public void remove(Element element) {
        this.children.remove(element);
    }

    public void displayAlert(Text text) {
        this.message = text;
        this.messageVisible = true;
        CompletableFuture.delayedExecutor(3, TimeUnit.SECONDS).execute(() -> {
            this.message = Text.of("");
            this.messageVisible = false;
        });
    }

    @Override
    protected void init() {
        super.init();

        if (!(this instanceof AcknowledgmentScreen)) {
            Text text = new TranslatableText("concerto.donate");
            int width = this.textRenderer.getWidth(text);
            this.addButton(
                    new PressableTextWidget(this.width - 5 - width, this.height - 5 - this.textRenderer.fontHeight, width,
                            this.textRenderer.fontHeight,
                            text, button -> MinecraftClient.getInstance().openScreen(new AcknowledgmentScreen(this)),
                            this.textRenderer)
            );
        }

        this.messageVisible = false;
    }

    @Override
    public void onClose() {
        for (Element element : this.children()) {
            if (element instanceof Closeable closeable) {
                try {
                    closeable.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        super.onClose();
        MinecraftClient.getInstance().openScreen(this.parent);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        super.render(matrices, mouseX, mouseY, delta);
        ConcertoScreen.drawCenteredTextWithShadow(matrices, this.textRenderer, this.title.asOrderedText(), this.width / 2, 5, 0xffffffff);
        if (this.messageVisible) {
            this.textRenderer.draw(matrices, this.message, (float) (this.width - this.textRenderer.getWidth(this.message)) / 2,
                    (float) this.height / 2 - 10, 0xffffffff);
        }
    }

    public static void drawCenteredTextWithShadow(MatrixStack matrices, TextRenderer textRenderer, OrderedText text, int centerX, int y, int color) {
        textRenderer.drawWithShadow(matrices, text, (float)(centerX - textRenderer.getWidth(text) / 2), (float)y, color);
    }
}
