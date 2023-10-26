package top.gregtao.concerto.util;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import top.gregtao.concerto.enums.TextAlignment;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

public class TextUtil {

    public static Text PAGE_SPLIT = Text.literal("==============================================").formatted(Formatting.DARK_AQUA);

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

    public static void renderText(Text text, TextAlignment align, int x, int y, DrawContext matrices, TextRenderer renderer, int color) {
        OrderedText orderedText = text.asOrderedText();
        int realX = x, textWidth = renderer.getWidth(orderedText);
        if (align == TextAlignment.CENTER) {
            realX -= textWidth / 2;
        } else if (align == TextAlignment.RIGHT) {
            realX -= textWidth;
        }
        matrices.drawTextWithShadow(renderer, orderedText, realX, y, color);
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

    public static String toBase64(String str) {
        return Base64.getEncoder().encodeToString(str.getBytes(StandardCharsets.UTF_8));
    }

    public static String fromBase64(String str) {
        return new String(Base64.getDecoder().decode(str), StandardCharsets.UTF_8);
    }

    public static int getStringWidth(String s) {
        s = s.replaceAll("[^\\x80-\\xff]", "**");
        return s.length();
    }

    public static String cutIfTooLong(String str, int limit) {
        limit += 1;
        if (str.getBytes().length > limit) {
            String str1 = new String(Arrays.copyOfRange(str.getBytes(), 0, limit));
            return str1.substring(0, str1.length() - 2) + "...";
        }
        return str;
    }
}
