package top.gregtao.concerto.screen.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * The "♪" button with its pop-up volume slider. Add via {@code addWidget} for
 * event handling and call {@link #render} at the end of the screen's render
 * pass so the pop-up draws on top of screen content. The screen must also call
 * {@link #collapseIfClickedOutside} from its {@code mouseClicked} override:
 * click dispatch only reaches the child under the cursor, so the widget never
 * sees outside clicks itself.
 */
public class VolumeControlWidget extends AbstractWidget {

    private static final int SLIDER_WIDTH = 20;
    private static final int SLIDER_HEIGHT = 84;

    private final VolumeButton button;
    private final VolumeSliderWidget slider;
    private boolean expanded = false;

    public VolumeControlWidget(Font font, int x, int y, int width, int height) {
        super(x, y, width, height, Component.translatable("concerto.screen.volume"));
        this.button = new VolumeButton(x, y, width, height, b -> this.setExpanded(!this.expanded));
        this.slider = new VolumeSliderWidget(font, x, y - SLIDER_HEIGHT - 2, SLIDER_WIDTH, SLIDER_HEIGHT);
        this.slider.visible = false;
    }

    private void setExpanded(boolean expanded) {
        this.expanded = expanded;
        this.slider.visible = expanded;
    }

    @Override
    protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        this.button.render(context, mouseX, mouseY, delta);
        if (this.slider.visible) {
            this.slider.render(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if (this.slider.visible && this.slider.mouseClicked(mouseX, mouseY, mouseButton)) {
            return true;
        }
        return this.button.mouseClicked(mouseX, mouseY, mouseButton);
    }

    // Dismiss but let the click through: swallowing it would force a second
    // click on whatever button the user actually aimed at
    public void collapseIfClickedOutside(double mouseX, double mouseY) {
        if (this.expanded && !this.isMouseOver(mouseX, mouseY)) {
            this.setExpanded(false);
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int mouseButton, double dragX, double dragY) {
        return this.slider.visible && this.slider.mouseDragged(mouseX, mouseY, mouseButton, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int mouseButton) {
        return this.slider.visible && this.slider.mouseReleased(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return this.button.isMouseOver(mouseX, mouseY) || (this.slider.visible && this.slider.isMouseOver(mouseX, mouseY));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }
}
