package top.gregtao.concerto.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.NarratedMultilineTextWidget;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ConcertoScreen extends Screen {
    private final Screen parent;
    private NarratedMultilineTextWidget message;

    public ConcertoScreen(Text title, Screen parent) {
        super(title.getWithStyle(Style.EMPTY.withColor(Formatting.DARK_AQUA)).get(0));
        this.parent = parent;
    }

    public void displayAlert(Text text) {
        this.message.setMessage(text);
        this.initTabNavigation();
        this.message.visible = true;
        CompletableFuture.delayedExecutor(3, TimeUnit.SECONDS).execute(() -> {
            this.message.setMessage(Text.empty());
            this.message.visible = false;
        });
    }

    @Override
    protected void init() {
        super.init();
        this.message = this.addDrawableChild(new NarratedMultilineTextWidget(
                this.textRenderer, Text.empty(), this.width));
        this.message.visible = false;
        this.initTabNavigation();
    }

    @Override
    protected void initTabNavigation() {
        if (this.message != null) {
            this.message.setMaxWidth(this.width);
            this.message.setPosition(this.width / 2 - this.message.getWidth() / 2,
                    this.height / 2 - this.textRenderer.fontHeight / 2);
        }
    }

    @Override
    public void close() {
        super.close();
        MinecraftClient.getInstance().setScreen(this.parent);
    }

    @Override
    public void render(DrawContext matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        super.render(matrices, mouseX, mouseY, delta);
        matrices.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 5, 0xffffffff);
        this.message.render(matrices, mouseX, mouseY, delta);
    }
}
