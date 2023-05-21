package top.gregtao.concerto.util;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import top.gregtao.concerto.enums.TextAlign;

public class TextUtil {

    public static Text PAGE_SPLIT = Text.literal("==============================================");

    public static String getTranslatable(String key) {
        return Text.translatable(key).getString();
    }

    public static void commandMessageClient(CommandContext<FabricClientCommandSource> context, Text text) {
        ClientPlayerEntity player = context.getSource().getPlayer();
        player.sendMessage(text);
    }

    public static void commandMessageServer(CommandContext<ServerCommandSource> context, Text text) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player != null) player.sendMessage(text);
    }

    public static void renderText(Text text, TextAlign align, int x, int y, MatrixStack matrices, TextRenderer renderer, int color) {
        OrderedText orderedText = text.asOrderedText();
        int realX = x, textWidth = renderer.getWidth(orderedText);
        if (align == TextAlign.CENTER) {
            realX -= textWidth / 2;
        } else if (align == TextAlign.RIGHT) {
            realX -= textWidth;
        }
        renderer.drawWithShadow(matrices, orderedText, (float) realX, (float) y, color);
    }

    public static Style getRunCommandStyle(String command) {
        return Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(command).formatted(Formatting.AQUA)));
    }

    public static boolean isDigit(String str) {
        for (char ch : str.toCharArray()) {
            if (!Character.isDigit(ch)) return false;
        }
        return true;
    }

    public static String getCurrentTime() {
        return String.valueOf(System.currentTimeMillis());
    }
}
