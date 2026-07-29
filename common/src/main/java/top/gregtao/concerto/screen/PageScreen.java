package top.gregtao.concerto.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.gregtao.concerto.screen.widget.ConcertoListWidget;

public abstract class PageScreen extends ConcertoScreen {
    protected int page = 0, maxPage = Integer.MAX_VALUE;
    private int actionBarX;
    private Button pageLabel;
    private final int pageLabelWidth;

    public PageScreen(Component title, Screen parent) {
        super(title, parent);
        Font renderer = Minecraft.getInstance().font;
        this.pageLabelWidth = renderer.width(Component.translatable("concerto.screen.page", 999));
    }

    public PageScreen(Component title, int maxPage, Screen parent) {
        this(title, parent);
        this.maxPage = maxPage;
    }

    abstract public void onPageTurned(int page);

    protected final int bottomBarY() {
        return this.standardBottomActionY();
    }

    protected final int actionBarX() {
        return this.actionBarX;
    }

    protected final int actionBarWidth() {
        return this.standardContentRight() - this.actionBarX;
    }

    private void changePage(int page) {
        this.page = page;
        this.pageLabel.setMessage(Component.translatable("concerto.screen.page", this.page + 1));
        this.onPageTurned(this.page);
    }

    @Override
    protected void init() {
        super.init();
        int y = this.standardBottomActionY();
        int x = this.standardContentX();
        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.previous_page"), button -> {
            if (this.page > 0) {
                this.changePage(this.page - 1);
            }
        }).size(20, 20).pos(x, y).build());
        x += 20 + STANDARD_ACTION_GAP;

        this.pageLabel = Button.builder(Component.translatable("concerto.screen.page", this.page + 1), button -> {
        }).size(this.pageLabelWidth, 20).pos(x, y).build();
        this.pageLabel.active = false;
        this.addRenderableWidget(this.pageLabel);
        x += this.pageLabelWidth + STANDARD_ACTION_GAP;

        this.addRenderableWidget(Button.builder(Component.translatable("concerto.screen.next_page"), button -> {
            if (this.page < this.maxPage) {
                this.changePage(this.page + 1);
            }
        }).size(20, 20).pos(x, y).build());
        this.actionBarX = x + 20 + STANDARD_ACTION_GAP;
    }
}
