package top.gregtao.concerto.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import top.gregtao.concerto.util.ConcertoOptions;

public class ConcertoOptionsScreen extends ConcertoScreen {
    protected OptionListWidget buttonList;

    public ConcertoOptionsScreen(Screen parent) {
        super(Text.translatable("concerto.screen.options"), parent);
    }

    @Override
    protected void init() {
        this.buttonList = new OptionListWidget(this.client, this.width, this.height, 18, 25);
        this.buttonList.addAll(ConcertoOptions.INSTANCE.getOptions());
        this.addSelectableChild(this.buttonList);
        this.addDrawableChild(this.buttonList);
        ButtonWidget resetButton = ButtonWidget.builder(Text.translatable("concerto.reset"), button -> {
            if (this.client != null) {
                this.client.setScreen(new ConfirmScreen(confirmed -> {
                    ConcertoOptions.INSTANCE.resetOptions();
                    this.client.setScreen(new ConcertoOptionsScreen(this.getParent()));
                }, this.title, Text.translatable("concerto.reset_confirm")));
            }
        }).position(this.width / 2 - 155, this.height - 26).build();
        this.addSelectableChild(resetButton);
        this.addDrawableChild(resetButton);
        ButtonWidget doneButton = ButtonWidget.builder(
                ScreenTexts.DONE, button -> this.close()
        ).position(this.width / 2 + 5, this.height - 26).build();
        this.addSelectableChild(doneButton);
        this.addDrawableChild(doneButton);
        super.init();
    }

    @Override
    public void removed() {
        ConcertoOptions.INSTANCE.saveOptions();
    }

    @Override
    public void render(DrawContext matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        InGameHudRenderer.render(matrices, mouseX, mouseY, delta);
    }
}
