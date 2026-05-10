package top.gregtao.concerto.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.FocusableTextWidget;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ConcertoScreen extends Screen {
    private final Screen parent;
    private FocusableTextWidget message;

    public static Component getTextWithColor(Component text, ChatFormatting color) {
        List<Component> textList = text.toFlatList(Style.EMPTY.withColor(color));
        return textList.isEmpty() ? text : textList.get(0);
    }

    public ConcertoScreen(Component title, Screen parent) {
        super(getTextWithColor(title, ChatFormatting.DARK_AQUA));
        this.parent = parent;
    }

    public void displayAlert(Component text) {
        this.message.setMessage(text);
        this.initTabNavigation();
        this.message.visible = true;
        CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS).execute(() -> {
            this.message.setMessage(Component.empty());
            this.message.visible = false;
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

        this.message = this.addWidget(
                FocusableTextWidget.builder(Component.empty(), this.font).textWidth(12).build());
        this.message.visible = false;
        this.initTabNavigation();
    }

    protected void initTabNavigation() {
        if (this.message != null) {
            this.message.setMaxWidth(this.width);
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
    public void extractRenderState(@NotNull GuiGraphicsExtractor matrices, int mouseX, int mouseY, float delta) {
        super.extractRenderState(matrices, mouseX, mouseY, delta);
        matrices.centeredText(this.font, this.title, this.width / 2, 5, 0xffffffff);
        this.message.extractRenderState(matrices, mouseX, mouseY, delta);
    }
}
