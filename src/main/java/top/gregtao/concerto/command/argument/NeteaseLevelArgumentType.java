package top.gregtao.concerto.command.argument;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.argument.EnumArgumentType;
import top.gregtao.concerto.music.NeteaseCloudMusic;

public class NeteaseLevelArgumentType extends EnumArgumentType<NeteaseCloudMusic.Level> {
    private NeteaseLevelArgumentType() {
        super(NeteaseCloudMusic.Level.CODEC, NeteaseCloudMusic.Level::values);
    }

    public static NeteaseLevelArgumentType level() {
        return new NeteaseLevelArgumentType();
    }

    public static NeteaseCloudMusic.Level getOrderType(CommandContext<FabricClientCommandSource> context, String id) {
        try {
            return context.getArgument(id, NeteaseCloudMusic.Level.class);
        } catch (IllegalArgumentException e) {
            return NeteaseCloudMusic.Level.STANDARD;
        }
    }
}
