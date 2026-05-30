package top.gregtao.concerto.util;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class CommandUtil {

    public static Component PAGE_SPLIT = Component.literal("==============================================").withStyle(ChatFormatting.DARK_AQUA);

    public static void commandMessageClient(Component text) {
        Minecraft.getInstance().execute(() -> {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) player.displayClientMessage(text, false);
        });
    }

    public static void commandMessageServer(CommandContext<CommandSourceStack> context, Component text) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player != null) player.sendSystemMessage(text);
    }
}
