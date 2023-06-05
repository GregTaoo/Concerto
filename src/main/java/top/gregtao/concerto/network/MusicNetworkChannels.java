package top.gregtao.concerto.network;

import net.minecraft.util.Identifier;
import top.gregtao.concerto.ConcertoClient;

public class MusicNetworkChannels {

    public static Identifier CHANNEL_MUSIC_DATA = createChannel("0");
    public static Identifier CHANNEL_HANDSHAKE = createChannel("1");
    public static Identifier CHANNEL_AUDITION_SYNC = createChannel("2");

    public static String HANDSHAKE_STRING = "CONCERTO:";

    public static int WAIT_LIST_MAX_SIZE = 300;

    public static Identifier createChannel(String name) {
        return new Identifier(ConcertoClient.MOD_ID, name);
    }
}
