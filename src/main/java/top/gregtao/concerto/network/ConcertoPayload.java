package top.gregtao.concerto.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public class ConcertoPayload implements CustomPayload {

    public static final Id<ConcertoPayload> ID = new Id<>(Identifier.of("concerto", "string"));
    public String string;
    public Channel channel;

    public ConcertoPayload(Channel channel, String s) {
        this.channel = channel;
        this.string = s;
    }

    public static final PacketCodec<PacketByteBuf, ConcertoPayload> CODEC = new PacketCodec<>() {
        @Override
        public void encode(PacketByteBuf buf, ConcertoPayload value) {
            buf.writeString(value.channel.id + value.string, Integer.MAX_VALUE);
        }

        @Override
        public ConcertoPayload decode(PacketByteBuf buf) {
            String s = buf.readString(Integer.MAX_VALUE);
            Channel channel1 = Channel.getById(s.charAt(0));
            return new ConcertoPayload(channel1, s.substring(1));
        }
    };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public enum Channel {
        MUSIC_DATA('0'),
        HANDSHAKE('1'),
        AUDITION_SYNC('2'),
        MUSIC_ROOM('3');

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
