package top.gregtao.concerto.screen;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class AcknowledgmentScreen extends ConcertoScreen {

    public AcknowledgmentScreen(Screen parent) {
        super(Component.translatable("concerto.screen.acknowledgement"), parent);
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(Button.builder(Component.translatable("concerto.donate.afdian"),
                button -> Util.getPlatform().openUri("https://afdian.com/a/gregtao")
        ).pos(this.width / 2 - 75, 40).size(150, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("concerto.donate.bilibili"),
                button -> Util.getPlatform().openUri("https://space.bilibili.com/491552285")
        ).pos(this.width / 2 - 75, 65).size(150, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("concerto.donate.ko-fi"),
                button -> Util.getPlatform().openUri("https://ko-fi.com/gregtao")
        ).pos(this.width / 2 - 75, 90).size(150, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("concerto.donate.supporters"),
                button -> Util.getPlatform().openUri("https://github.com/GregTaoo/Concerto/blob/dev/supporters.md")
        ).pos(this.width / 2 - 75, 115).size(150, 20).build());
    }

    @Override
    public void render(GuiGraphics matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        Font renderer = Minecraft.getInstance().font;
        Component text = Component.translatable("concerto.thank_you");
        matrices.drawCenteredString(renderer, text, this.width / 2, 150, 0xffffffff);
    }
}
