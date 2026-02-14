package top.gregtao.concerto.screen.widget;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.PressableTextWidget;
import net.minecraft.text.Style;
import net.minecraft.text.Texts;
import net.minecraft.util.math.MathHelper;

public class ModifiablePressableTextWidget extends PressableTextWidget {
    private final TextRenderer textRenderer;
    private net.minecraft.text.Text text;
    private net.minecraft.text.Text hoverText;

    public ModifiablePressableTextWidget(int x, int y, int width, int height, net.minecraft.text.Text text, PressAction onPress, TextRenderer textRenderer) {
        super(x, y, width, height, text, onPress, textRenderer);
        this.textRenderer = textRenderer;
        this.text = text;
        this.hoverText = Texts.setStyleIfAbsent(text.copy(), Style.EMPTY.withUnderline(true));
    }

    @Override
    public void drawIcon(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        net.minecraft.text.Text text = this.isSelected() ? this.hoverText : this.text;
        context.drawTextWithShadow(this.textRenderer, text, this.getX(), this.getY(), 16777215 | MathHelper.ceil(this.alpha * 255.0F) << 24);
    }

    public void setText(net.minecraft.text.Text text) {
        this.text = text;
        this.hoverText = Texts.setStyleIfAbsent(text.copy(), Style.EMPTY.withUnderline(true));
        this.setWidth(this.textRenderer.getWidth(text));
        this.setHeight(this.textRenderer.fontHeight);
    }
}
