package top.gregtao.concerto.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class ConcertoPayload implements CustomPacketPayload {

    public static final Type<ConcertoPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath("concerto", "string"));
    public String string;
    public Channel channel;

    public ConcertoPayload(Channel channel, String s) {
        this.channel = channel;
        this.string = s;
    }

    public static final StreamCodec<FriendlyByteBuf, ConcertoPayload> CODEC = new StreamCodec<>() {
        @Override
        public void encode(FriendlyByteBuf buf, ConcertoPayload value) {
            buf.writeUtf(value.channel.id + value.string, Integer.MAX_VALUE);
        }

        @Override
        public @NotNull ConcertoPayload decode(FriendlyByteBuf buf) {
            String s = buf.readUtf(Integer.MAX_VALUE);
            Channel channel1 = Channel.getById(s.charAt(0));
            return new ConcertoPayload(channel1, s.substring(1));
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public enum Channel {
        MUSIC_DATA('0'),
        HANDSHAKE('1'),
        AUDITION_SYNC('2'),
        MUSIC_ROOM('3'),
        PRESET_RADIOS('4'),
        MUSIC_AGENT('5');

        public static Channel getById(char id) {
            for (Channel channel1 : values()) {
                if (channel1.id == id) {
                    return channel1;
                }
            }
            return MUSIC_DATA;
        }

        public final char id;
        Channel(char id) {
            this.id = id;
        }
    }
}
