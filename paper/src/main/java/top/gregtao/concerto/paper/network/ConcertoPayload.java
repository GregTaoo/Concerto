package top.gregtao.concerto.paper.network;

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

    public byte[] encode() {
        return (this.channel.id + this.string).getBytes(StandardCharsets.UTF_8);
    }

    public static ConcertoPayload decode(byte[] buf) {
        String s = new String(buf, StandardCharsets.UTF_8);
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
