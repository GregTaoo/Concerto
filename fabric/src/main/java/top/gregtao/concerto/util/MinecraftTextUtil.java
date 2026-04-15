package top.gregtao.concerto.util;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;
import top.gregtao.concerto.core.config.ClientConfig;
import top.gregtao.concerto.core.enums.TextAlignment;

public class MinecraftTextUtil {

    public static Component PAGE_SPLIT = Component.literal("==============================================").withStyle(ChatFormatting.DARK_AQUA);

    public static void commandMessageClient(CommandContext<FabricClientCommandSource> context, Component text) {
        LocalPlayer player = context.getSource().getPlayer();
        player.displayClientMessage(text, false);
    }

    public static void commandMessageServer(CommandContext<CommandSourceStack> context, Component text) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player != null) player.sendSystemMessage(text);
    }

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
        return Style.EMPTY.withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(command).withStyle(ChatFormatting.AQUA)));
    }
}
