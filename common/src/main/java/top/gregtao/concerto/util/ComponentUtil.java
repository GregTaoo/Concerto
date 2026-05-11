package top.gregtao.concerto.util;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import top.gregtao.concerto.core.config.ClientConfig;
import top.gregtao.concerto.core.enums.TextAlignment;

public class ComponentUtil {

    public static int getTextRenderX(Component text, TextAlignment align, Font renderer, int x) {
        int realX = x, textWidth = renderer.width(text);
        if (align == TextAlignment.CENTER) {
            realX -= textWidth / 2;
        } else if (align == TextAlignment.RIGHT) {
            realX -= textWidth;
        }
        return realX;
    }

    public static void renderText(Component text, TextAlignment align, int x, int y, GuiGraphics matrices, Font renderer, int color) {
        matrices.drawString(renderer, text, getTextRenderX(text, align, renderer, x), y, color, ClientConfig.INSTANCE.options.textShadow);
    }

    public static Style getRunCommandStyle(String command) {
        return Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command).withStyle(ChatFormatting.AQUA)));
    }
}
