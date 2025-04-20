package top.gregtao.concerto.screen;

import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ScreenTexts;
import net.minecraft.client.gui.widget.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.TranslatableText;
import top.gregtao.concerto.util.ConcertoOptions;

public class ConcertoOptionsScreen extends ConcertoScreen {
    protected ButtonListWidget buttonList;

    public ConcertoOptionsScreen(Screen parent) {
        super(new TranslatableText("concerto.screen.options"), parent);
    }

    @Override
    protected void init() {
        this.buttonList = new ButtonListWidget(this.client, this.width, this.height, 18, this.height - 32, 25);
        this.buttonList.addAll(ConcertoOptions.INSTANCE.getOptions());
        this.addSelectableChild(this.buttonList);
        this.addDrawableChild(this.buttonList);
        ButtonWidget resetButton = new ButtonWidget(this.width / 2 - 155, this.height - 26, 150, 20,
                new TranslatableText("concerto.reset"), button -> {
            if (this.client != null) {
                this.client.setScreen(new ConfirmScreen(confirmed -> {
                    ConcertoOptions.INSTANCE.resetOptions();
                    this.client.setScreen(new ConcertoOptionsScreen(this.getParent()));
                }, this.title, new TranslatableText("concerto.reset_confirm")));
            }
        });
        this.addSelectableChild(resetButton);
        this.addDrawableChild(resetButton);
        ButtonWidget doneButton = new ButtonWidget(this.width / 2 + 5, this.height - 26, 150, 20,
                ScreenTexts.DONE, button -> this.close()
        );
        this.addSelectableChild(doneButton);
        this.addDrawableChild(doneButton);
    }

    @Override
    public void removed() {
        ConcertoOptions.INSTANCE.saveOptions();
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        InGameHudRenderer.render(matrices);
    }
}
