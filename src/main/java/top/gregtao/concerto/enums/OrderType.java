package top.gregtao.concerto.enums;

import net.minecraft.text.Text;
import net.minecraft.util.StringIdentifiable;
import top.gregtao.concerto.api.SimpleStringIdentifiable;

public enum OrderType implements SimpleStringIdentifiable {
    NORMAL,
    RANDOM,
    REVERSED,
    LOOP;

    // ???
    public static final com.mojang.serialization.Codec<OrderType> CODEC = StringIdentifiable.createCodec(OrderType::values);

    public Text getName() {
        return Text.translatable("concerto.order." + this.asString());
    }
}