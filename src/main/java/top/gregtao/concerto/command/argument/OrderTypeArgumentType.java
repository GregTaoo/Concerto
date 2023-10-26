package top.gregtao.concerto.command.argument;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.argument.EnumArgumentType;
import top.gregtao.concerto.enums.OrderType;

public class OrderTypeArgumentType extends EnumArgumentType<OrderType> {
    private OrderTypeArgumentType() {
        super(OrderType.CODEC, OrderType::values);
    }

    public static OrderTypeArgumentType orderType() {
        return new OrderTypeArgumentType();
    }

    public static OrderType getOrderType(CommandContext<FabricClientCommandSource> context, String id) {
        return context.getArgument(id, OrderType.class);
    }
}
