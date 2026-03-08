package top.gregtao.concerto.util;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import top.gregtao.concerto.core.config.ClientConfig;
import top.gregtao.concerto.core.enums.TextAlignment;

public class MinecraftTextUtil {

    public static Text PAGE_SPLIT = Text.literal("==============================================").formatted(Formatting.DARK_AQUA);

    public static void commandMessageClient(CommandContext<FabricClientCommandSource> context, Text text) {
        ClientPlayerEntity player = context.getSource().getPlayer();
        player.sendMessage(text, false);
    }

    public static void commandMessageServer(CommandContext<ServerCommandSource> context, Text text) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player != null) player.sendMessage(text);
    }

    public static int getTextRenderX(Text text, TextAlignment align, TextRenderer renderer, int x) {
        int realX = x, textWidth = renderer.getWidth(text);
        if (align == TextAlignment.CENTER) {
            realX -= textWidth / 2;
        } else if (align == TextAlignment.RIGHT) {
            realX -= textWidth;
        }
        return realX;
    }

    public static void renderText(Text text, TextAlignment align, int x, int y, DrawContext matrices, TextRenderer renderer, int color) {
        matrices.drawText(renderer, text, getTextRenderX(text, align, renderer, x), y, color, ClientConfig.INSTANCE.options.textShadow);
    }

    public static Style getRunCommandStyle(String command) {
        return Style.EMPTY.withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Text.literal(command).formatted(Formatting.AQUA)));
    }
}
