package top.gregtao.concerto.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import top.gregtao.concerto.screen.widget.ConcertoOptionListWidget;
import top.gregtao.concerto.util.ConcertoOptions;

public class ConcertoOptionsScreen extends ConcertoScreen {
    protected ConcertoOptionListWidget body;
    public final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);

    public ConcertoOptionsScreen(Screen parent) {
        super(Component.translatable("concerto.screen.options"), parent);
    }

    @Override
    protected void init() {
        // The option instances are a startup-time singleton; without a re-read
        // they show stale values here and saveOptions() on close would write
        // them back, clobbering config changes made elsewhere (volume pop-up)
        ConcertoOptions.INSTANCE.readOptions();
        this.layout.setHeaderHeight(18);
        this.initBody();
        this.initFooter();
        this.layout.visitWidgets(this::addRenderableWidget);
        this.repositionElements();
        super.init();
    }

    protected void initBody() {
        // 1.20.1 selection lists are not LayoutElements, so the list can't live
        // inside the HeaderAndFooterLayout; it positions itself from the same
        // header/footer bounds instead (see repositionElements)
        this.body = this.addRenderableWidget(new ConcertoOptionListWidget(this.minecraft, this.width, this));
        this.addOptions();
    }

    protected void addOptions() {
        this.body.addAll(ConcertoOptions.INSTANCE.getOptions());
    }

    protected void initFooter() {
        GridLayout grid = this.layout.addToFooter(new GridLayout().columnSpacing(8));
        GridLayout.RowHelper directionalLayoutWidget = grid.createRowHelper(2);
        directionalLayoutWidget.addChild(Button.builder(
                Component.translatable("concerto.reset"), button -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new ConfirmScreen(confirmed -> {
                            if (confirmed) ConcertoOptions.INSTANCE.resetOptions();
                            this.minecraft.setScreen(new ConcertoOptionsScreen(this.getParent()));
                        }, this.title, Component.translatable("concerto.reset_confirm")));
                    }
                }).build());
        directionalLayoutWidget.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).build());
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
        if (this.body != null) {
            this.body.updateSize(this.width, this.height,
                    this.layout.getHeaderHeight(), this.height - this.layout.getFooterHeight());
        }
    }

    @Override
    public void removed() {
        ConcertoOptions.INSTANCE.saveOptions();
    }

    @Override
    public void onClose() {
        if (this.body != null) {
            this.body.applyAllPendingValues();
        }
        super.onClose();
    }

    @Override
    public void render(GuiGraphics matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        InGameHudRenderer.render(matrices, mouseX, mouseY, delta);
    }
}
