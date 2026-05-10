package top.gregtao.concerto.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
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
        this.layout.setHeaderHeight(18);
        this.initBody();
        this.initFooter();
        this.layout.visitWidgets(this::addRenderableWidget);
        this.repositionElements();
        super.init();
    }

    protected void initBody() {
        this.body = this.layout.addToContents(new ConcertoOptionListWidget(this.minecraft, this.width, this));
        this.addOptions();
    }

    protected void addOptions() {
        this.body.addAll(ConcertoOptions.INSTANCE.getOptions());
    }

    protected void initFooter() {
        LinearLayout directionalLayoutWidget = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
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
            this.body.updateSize(this.width, this.layout);
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
