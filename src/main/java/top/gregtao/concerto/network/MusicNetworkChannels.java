package top.gregtao.concerto.network;

import net.minecraft.util.Identifier;
import top.gregtao.concerto.ConcertoClient;

public class MusicNetworkChannels {

    public static final Identifier CHANNEL_MUSIC_DATA = createChannel("0");
    public static final Identifier CHANNEL_HANDSHAKE = createChannel("1");
    public static final Identifier CHANNEL_AUDITION_SYNC = createChannel("2");

    public static final String HANDSHAKE_STRING = "CONCERTO:";

    public static final int WAIT_LIST_MAX_SIZE = 300;

    public static Identifier createChannel(String name) {
        return new Identifier(ConcertoClient.MOD_ID, name);
    }
}
