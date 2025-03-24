package top.gregtao.concerto.screen.widget;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

public abstract class AbstractTextWidget extends ClickableWidget {
    private final TextRenderer textRenderer;
    private int textColor = 16777215;

    public AbstractTextWidget(int x, int y, int width, int height, Text message, TextRenderer textRenderer) {
        super(x, y, width, height, message);
        this.textRenderer = textRenderer;
    }

    public AbstractTextWidget setTextColor(int textColor) {
        this.textColor = textColor;
        return this;
    }

    protected final TextRenderer getTextRenderer() {
        return this.textRenderer;
    }

    protected final int getTextColor() {
        return this.textColor;
    }
}
