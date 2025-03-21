package top.gregtao.concerto.network;

import net.minecraft.util.Identifier;
import top.gregtao.concerto.ConcertoClient;

public class ConcertoNetworking {

    public static final Identifier MUSIC_DATA = createChannel("0");
    public static final Identifier HANDSHAKE = createChannel("1");
    public static final Identifier AUDITION_SYNC = createChannel("2");
    public static final Identifier MUSIC_ROOM = createChannel("3");
    public static final Identifier PRESET_RADIOS = createChannel("4");
    public static final Identifier MUSIC_AGENT = createChannel("5");

    public static final String HANDSHAKE_STRING = "CONCERTO:";

    public static final int WAIT_LIST_MAX_SIZE = 300;

    public static Identifier createChannel(String name) {
        return new Identifier(ConcertoClient.MOD_ID, name);
    }
}
