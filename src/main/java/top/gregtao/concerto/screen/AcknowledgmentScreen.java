package top.gregtao.concerto.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Util;

public class AcknowledgmentScreen extends ConcertoScreen {

    public AcknowledgmentScreen(Screen parent) {
        super(new TranslatableText("concerto.screen.acknowledgement"), parent);
    }

    @Override
    protected void init() {
        super.init();
        this.addDrawableChild(new ButtonWidget(this.width / 2 - 75, 40, 150, 20, new TranslatableText("concerto.donate.afdian"),
                button -> Util.getOperatingSystem().open("https://afdian.com/a/gregtao")
        ));
        this.addDrawableChild(new ButtonWidget(this.width / 2 - 75, 65, 150, 20, new TranslatableText("concerto.donate.bilibili"),
                button -> Util.getOperatingSystem().open("https://space.bilibili.com/491552285")
        ));
        this.addDrawableChild(new ButtonWidget(this.width / 2 - 75, 90, 150, 20, new TranslatableText("concerto.donate.ko-fi"),
                button -> Util.getOperatingSystem().open("https://ko-fi.com/gregtao")
        ));
        this.addDrawableChild(new ButtonWidget(this.width / 2 - 75, 115, 150, 20, new TranslatableText("concerto.donate.supporters"),
                button -> Util.getOperatingSystem().open("https://github.com/GregTaoo/Concerto/blob/dev/supporters.md")
        ));
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        Text text = new TranslatableText("concerto.thank_you");
        DrawableHelper.drawCenteredTextWithShadow(matrices, renderer, text.asOrderedText(), this.width / 2, 150, 0xffffffff);
    }
}
