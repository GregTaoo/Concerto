package top.gregtao.concerto.paper.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ConcertoPayload {

    public static final String VERSION = "2";
    public static final String ID = "concerto:main";
    public static final String HANDSHAKE_STRING = "CONCERTO:" + VERSION + ":";

    public String string;
    public Channel channel;

    public ConcertoPayload(Channel channel, String s) {
        this.channel = channel;
        this.string = s;
    }

    public static void writeVarInt(ByteArrayOutputStream out, int value) {
        while ((value & -128) != 0) {
            out.write((value & 127) | 128);
            value >>>= 7;
        }
        out.write(value);
    }

    public static int readVarInt(ByteArrayInputStream in) {
        try {
            int i = 0;
            int j = 0;

            byte b;
            do {
                b = in.readNBytes(1)[0];
                i |= (b & 127) << j++ * 7;
                if (j > 5) {
                    throw new RuntimeException("VarInt too big");
                }
            } while ((b & 128) == 128);

            return i;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] encode() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] bytes = (this.channel.id + this.string).getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes, 0, bytes.length);
        return out.toByteArray();
    }

    public static ConcertoPayload decode(byte[] buf) {
        ByteArrayInputStream in = new ByteArrayInputStream(buf);
        int len = readVarInt(in);
        byte[] bytes = new byte[len];
        int read = in.read(bytes, 0, len);
        if (read != len) {
            throw new RuntimeException("EOF");
        }
        String s = new String(bytes, StandardCharsets.UTF_8);
        Channel channel1 = Channel.getById(s.charAt(0));
        return new ConcertoPayload(channel1, s.substring(1));
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
