package top.gregtao.concerto.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.FocusableTextWidget;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ConcertoScreen extends Screen {
    public static final int STANDARD_CONTENT_MARGIN = 20;
    protected static final int STANDARD_ACTION_GAP = 2;
    private final Screen parent;
    private FocusableTextWidget message;
    private int alertGeneration = 0;

    public static Component getTextWithColor(Component text, ChatFormatting color) {
        List<Component> textList = text.toFlatList(Style.EMPTY.withColor(color));
        return textList.isEmpty() ? text : textList.get(0);
    }

    public ConcertoScreen(Component title, Screen parent) {
        super(getTextWithColor(title, ChatFormatting.DARK_AQUA));
        this.parent = parent;
    }
    protected final int standardContentX() {
        return STANDARD_CONTENT_MARGIN;
    }

    protected final int standardContentRight() {
        return this.width - STANDARD_CONTENT_MARGIN;
    }

    protected final int standardContentWidth() {
        return this.width - STANDARD_CONTENT_MARGIN * 2;
    }

    protected final int standardBottomActionY() {
        return this.height - 30;
    }


    // Callable from any thread. Each alert supersedes the previous one; an
    // older alert's expiry timer must not clear a newer alert early.
    public void displayAlert(Component text) {
        Minecraft.getInstance().execute(() -> {
            this.message.setMessage(text);
            this.initTabNavigation();
            this.message.visible = true;
            int generation = ++this.alertGeneration;
            CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS).execute(() -> Minecraft.getInstance().execute(() -> {
                if (generation != this.alertGeneration) return;
                this.message.setMessage(Component.empty());
                this.message.visible = false;
            }));
        });
    }

    @Override
    protected void init() {
        super.init();

        if (!(this instanceof AcknowledgmentScreen)) {
            Component text = Component.translatable("concerto.donate");
            int width = this.font.width(text);
            this.addRenderableWidget(
                    new PlainTextButton(this.width - 5 - width, this.height - 5 - this.font.lineHeight, width,
                            this.font.lineHeight,
                            text, button -> Minecraft.getInstance().setScreen(new AcknowledgmentScreen(this)),
                            this.font)
            );
        }

        this.message = this.addWidget(new FocusableTextWidget(
                this.width, Component.empty(), this.font, 12));
        this.message.visible = false;
        this.initTabNavigation();
    }

    protected void initTabNavigation() {
        if (this.message != null) {
            this.message.containWithin(this.width);
            this.message.setPosition(this.width / 2 - this.message.getWidth() / 2,
                    this.height / 2 - this.font.lineHeight / 2);
        }
    }

    protected Screen getParent() {
        return this.parent;
    }

    @Override
    public void onClose() {
        super.onClose();
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    public void render(GuiGraphics matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        matrices.drawCenteredString(this.font, this.title, this.width / 2, 5, 0xffffffff);
        this.message.render(matrices, mouseX, mouseY, delta);
    }
}
