package top.gregtao.concerto.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

public class AcknowledgmentScreen extends ConcertoScreen {

    public AcknowledgmentScreen(Screen parent) {
        super(Text.translatable("concerto.screen.acknowledgement"), parent);
    }

    @Override
    protected void init() {
        super.init();
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.donate.afdian"),
                button -> Util.getOperatingSystem().open("https://afdian.com/a/gregtao")
        ).position(this.width / 2 - 75, 40).size(150, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.donate.bilibili"),
                button -> Util.getOperatingSystem().open("https://space.bilibili.com/491552285")
        ).position(this.width / 2 - 75, 65).size(150, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.donate.ko-fi"),
                button -> Util.getOperatingSystem().open("https://ko-fi.com/gregtao")
        ).position(this.width / 2 - 75, 90).size(150, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("concerto.donate.supporters"),
                button -> Util.getOperatingSystem().open("https://github.com/GregTaoo/Concerto/blob/dev/supporters.md")
        ).position(this.width / 2 - 75, 115).size(150, 20).build());
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        Text text = Text.translatable("concerto.thank_you");
        DrawableHelper.drawCenteredTextWithShadow(matrices, renderer, text, this.width / 2, 150, 0xffffffff);
    }
}
