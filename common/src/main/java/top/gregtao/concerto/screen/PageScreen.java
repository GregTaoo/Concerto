package top.gregtao.concerto.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class PageScreen extends ConcertoScreen {
    protected int page = 0, maxPage = Integer.MAX_VALUE, buttonX, buttonY, widgetWidth;

    public PageScreen(Component title, Screen parent) {
        super(title, parent);
        Font renderer = Minecraft.getInstance().font;
        this.widgetWidth = renderer.width(Component.translatable("concerto.screen.page", 999));
    }

    public PageScreen(Component title, int maxPage, Screen parent) {
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
        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.previous_page"), button -> {
            if (this.page > 0) {
                this.page -= 1;
                this.onPageTurned(this.page);
            }
        }).size(20, 20).pos(this.buttonX - this.widgetWidth / 2 - 22, this.buttonY).build());
        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.next_page"), button -> {
            if (this.page < this.maxPage) {
                this.page += 1;
                this.onPageTurned(this.page);
            }
        }).size(20, 20).pos(this.buttonX + this.widgetWidth / 2, this.buttonY).build());
    }

    @Override
    public void render(GuiGraphics matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        Font renderer = Minecraft.getInstance().font;
        Component text = Component.translatable("concerto.screen.page", this.page + 1);
        matrices.drawCenteredString(renderer, text, this.buttonX, this.buttonY + 5, 0xffffffff);
    }
}
