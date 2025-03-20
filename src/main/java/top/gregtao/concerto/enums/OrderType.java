package top.gregtao.concerto.enums;

import com.mojang.serialization.Codec;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.StringIdentifiable;
import top.gregtao.concerto.api.SimpleStringIdentifiable;

public enum OrderType implements SimpleStringIdentifiable {
    NORMAL,
    RANDOM,
    REVERSED,
    LOOP;

    public static final Codec<OrderType> CODEC = StringIdentifiable.createCodec(OrderType::values,
            s -> SimpleStringIdentifiable.fromString(OrderType.class, s));

    public Text getName() {
        return new TranslatableText("concerto.order." + this.asString());
    }
}