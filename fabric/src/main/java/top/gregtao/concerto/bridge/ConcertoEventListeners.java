package top.gregtao.concerto.bridge;

import net.minecraft.client.MinecraftClient;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.core.config.ClientConfig;
import top.gregtao.concerto.core.event.ConcertoEvents;
import top.gregtao.concerto.core.player.MusicPlayerHandler;
import top.gregtao.concerto.network.room.MusicRoom;

public class ConcertoEventListeners {

    public static void registerClientListeners() {

        ConcertoEvents.ON_PLAYER_START.register(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            client.getMusicTracker().stop();
            ConcertoClient.syncPlayerVolume();
        });

        ConcertoEvents.ON_PLAYER_PAUSE.register(() -> MusicRoom.clientPause(true));

        ConcertoEvents.ON_PLAYER_RESUME.register(() -> MusicRoom.clientPause(false));

        ConcertoEvents.ON_MUSIC_INFO_RESET.register(() -> ConcertoClient.COVER_IMAGE.setUrl(null));

        ConcertoEvents.ON_MUSIC_INFO_UPDATE.register(() -> {
            if (!MusicPlayerHandler.INSTANCE.currentMeta.headPictureUrl().isEmpty()) {
                ConcertoClient.COVER_IMAGE.setUrl(MusicPlayerHandler.INSTANCE.currentMeta.headPictureUrl());
                ConcertoClient.COVER_IMAGE.loadImage(true, ClientConfig.INSTANCE.options.coverImgInCircle);
            }
        });

        ConcertoEvents.ON_NEXT_MUSIC.register(MusicRoom::clientUpdate);
    }
}
