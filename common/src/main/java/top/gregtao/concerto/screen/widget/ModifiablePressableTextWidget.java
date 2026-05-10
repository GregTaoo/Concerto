package top.gregtao.concerto.screen.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;

public class ModifiablePressableTextWidget extends PlainTextButton {
    private Font textRenderer;
    private Component text;
    private Component hoverText;

    public ModifiablePressableTextWidget(int x, int y, int width, int height, Component text, OnPress onPress, Font textRenderer) {
        super(x, y, width, height, text, onPress, textRenderer);
        this.textRenderer = textRenderer;
        this.text = text;
        this.hoverText = ComponentUtils.mergeStyles(text.copy(), Style.EMPTY.withUnderlined(true));
    }

    @Override
    public void renderWidget(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
        Component text = this.isHoveredOrFocused() ? this.hoverText : this.text;
        context.drawString(this.textRenderer, text, this.getX(), this.getY(), 16777215 | Mth.ceil(this.alpha * 255.0F) << 24);
    }

    public void setText(Component text) {
        this.text = text;
        this.hoverText = ComponentUtils.mergeStyles(text.copy(), Style.EMPTY.withUnderlined(true));
        this.setWidth(this.textRenderer.width(text));
        this.setHeight(this.textRenderer.lineHeight);
    }
}
