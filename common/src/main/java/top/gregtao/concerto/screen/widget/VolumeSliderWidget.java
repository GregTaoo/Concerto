package top.gregtao.concerto.screen.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.core.config.ClientConfig;

public class VolumeSliderWidget extends AbstractWidget {
    private static final int HANDLE_HEIGHT = 7;
    private static final int TRACK_WIDTH = 8;

    private final Font font;
    private boolean dragging;

    public VolumeSliderWidget(Font font, int x, int y, int width, int height) {
        super(x, y, width, height, Component.translatable("concerto.screen.volume"));
        this.font = font;
    }

    @Override
    protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        int left = this.getX();
        int top = this.getY();
        int bottom = top + this.height;

        int trackX = left + (this.width - TRACK_WIDTH) / 2;
        context.fill(trackX - 1, top - 1, trackX + TRACK_WIDTH + 1, bottom + 1, 0x99555555);
        context.fill(trackX, top, trackX + TRACK_WIDTH, bottom, 0x77999999);

        double volume = Math.clamp(ClientConfig.INSTANCE.options.playerVolume, 0.0, 1.0);
        int handleY = bottom - HANDLE_HEIGHT - (int) Math.round(volume * (this.height - HANDLE_HEIGHT));
        context.fill(left, handleY, left + this.width, handleY + HANDLE_HEIGHT, 0xFFFFFFFF);

        String text = (int) Math.round(volume * 100.0) + "%";
        context.drawCenteredString(this.font, text, left + this.width / 2, top - 10, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.active || !this.visible || button != 0 || !this.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        this.dragging = true;
        this.updateVolume(mouseY);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!this.dragging || button != 0) {
            return false;
        }
        this.updateVolume(mouseY);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.dragging && button == 0) {
            this.dragging = false;
            ClientConfig.INSTANCE.writeOptions();
            return true;
        }
        return false;
    }

    private void updateVolume(double mouseY) {
        double range = this.height - HANDLE_HEIGHT;
        double value = 1.0 - ((mouseY - this.getY() - HANDLE_HEIGHT / 2.0) / range);
        ClientConfig.INSTANCE.options.playerVolume = Math.clamp(value, 0.0, 1.0);
        ConcertoClient.syncPlayerVolume();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }
}
