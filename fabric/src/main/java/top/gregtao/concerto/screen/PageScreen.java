package top.gregtao.concerto.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public abstract class PageScreen extends ConcertoScreen {
    protected int page = 0, maxPage = Integer.MAX_VALUE, buttonX, buttonY, widgetWidth;

    public PageScreen(Text title, Screen parent) {
        super(title, parent);
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        this.widgetWidth = renderer.getWidth(Text.translatable("concerto.screen.page", 999));
    }

    public PageScreen(Text title, int maxPage, Screen parent) {
        this(title, parent);
        this.maxPage = maxPage;
    }

    abstract public void onPageTurned(int page);

    /**
     * MUST BE CALLED BEFORE super.init()
     */
    private void configure(int buttonX, int buttonY) {
        this.buttonX = buttonX;
        this.buttonY = buttonY;
    }

    @Override
    protected void init() {
        this.configure(this.width / 2 - 120, this.height - 30);
        super.init();
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.previous_page"), button -> {
            if (this.page > 0) {
                this.page -= 1;
                this.onPageTurned(this.page);
            }
        }).size(20, 20).position(this.buttonX - this.widgetWidth / 2 - 22, this.buttonY).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.screen.next_page"), button -> {
            if (this.page < this.maxPage) {
                this.page += 1;
                this.onPageTurned(this.page);
            }
        }).size(20, 20).position(this.buttonX + this.widgetWidth / 2, this.buttonY).build());
    }

    @Override
    public void render(DrawContext matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        Text text = Text.translatable("concerto.screen.page", this.page + 1);
        matrices.drawCenteredTextWithShadow(renderer, text, this.buttonX, this.buttonY + 5, 0xffffffff);
    }
}
