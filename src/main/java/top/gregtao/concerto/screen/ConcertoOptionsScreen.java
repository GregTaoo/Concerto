package top.gregtao.concerto.screen;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import top.gregtao.concerto.screen.widget.ConcertoOptionListWidget;
import top.gregtao.concerto.util.ConcertoOptions;

public class ConcertoOptionsScreen extends ConcertoScreen {
    protected ConcertoOptionListWidget body;
    public final ThreePartsLayoutWidget layout = new ThreePartsLayoutWidget(this);

    public ConcertoOptionsScreen(Screen parent) {
        super(Text.translatable("concerto.screen.options"), parent);
    }

    @Override
    protected void init() {
        this.initBody();
        this.initFooter();
        this.layout.forEachChild(this::addDrawableChild);
        this.refreshWidgetPositions();
    }

    protected void initBody() {
        this.body = this.layout.addBody(new ConcertoOptionListWidget(this.client, this.width, this));
        this.addOptions();
    }

    protected void addOptions() {
        this.body.addAll(ConcertoOptions.INSTANCE.getOptions());
    }

    protected void initFooter() {
        DirectionalLayoutWidget directionalLayoutWidget = this.layout.addFooter(DirectionalLayoutWidget.horizontal().spacing(8));
        directionalLayoutWidget.add(ButtonWidget.builder(
                Text.translatable("concerto.reset"), button -> {
                    ConcertoOptions.INSTANCE.resetOptions();
                    if (this.client != null) {
                        this.client.setScreen(new ConcertoOptionsScreen(this.getParent()));
                    }
                }).build());
        directionalLayoutWidget.add(ButtonWidget.builder(ScreenTexts.DONE, button -> this.close()).build());
    }

    @Override
    protected void refreshWidgetPositions() {
        this.layout.refreshPositions();
        if (this.body != null) {
            this.body.position(this.width, this.layout);
        }
    }

    @Override
    public void removed() {
        ConcertoOptions.INSTANCE.saveOptions();
    }

    @Override
    public void close() {
        if (this.body != null) {
            this.body.applyAllPendingValues();
        }
        super.close();
    }
}
