package top.gregtao.concerto.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public class ConcertoNetworking {

    public static final String HANDSHAKE_STRING = "CONCERTO:";

    public static final int WAIT_LIST_MAX_SIZE = 300;

    public static void register() {
        PayloadTypeRegistry.playS2C().register(ConcertoPayload.ID, ConcertoPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ConcertoPayload.ID, ConcertoPayload.CODEC);
    }
}
