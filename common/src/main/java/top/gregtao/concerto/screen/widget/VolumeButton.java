package top.gregtao.concerto.screen.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class VolumeButton extends Button {
    public VolumeButton(int x, int y, int width, int height, OnPress onPress) {
        super(x, y, width, height, Component.literal("♪"), onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.renderWidget(context, mouseX, mouseY, delta);
    }
}
