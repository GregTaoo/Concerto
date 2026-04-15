package top.gregtao.concerto.bridge;

import net.minecraft.client.Minecraft;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.core.config.ClientConfig;
import top.gregtao.concerto.core.event.ConcertoEvents;
import top.gregtao.concerto.core.player.MusicPlayer;
import top.gregtao.concerto.screen.InGameHudRenderer;

public class ConcertoEventListeners {

    public static void registerClientListeners() {

        ConcertoEvents.ON_PLAYER_START.subscribe(() -> {
            Minecraft client = Minecraft.getInstance();
            client.getMusicManager().stopPlaying();
            ConcertoClient.syncPlayerVolume();
        });

//        ConcertoEvents.ON_PLAYER_PAUSE.subscribe(() -> MusicRoom.clientPause(true));

//        ConcertoEvents.ON_PLAYER_RESUME.subscribe(() -> MusicRoom.clientPause(false));

        ConcertoEvents.ON_MUSIC_INFO_RESET.subscribe(() -> InGameHudRenderer.COVER_IMAGE.setUrl(null));

        ConcertoEvents.ON_MUSIC_INFO_UPDATE.subscribe(() -> {
            if (!MusicPlayer.INSTANCE.currentMeta.headPictureUrl().isEmpty()) {
                InGameHudRenderer.COVER_IMAGE.setUrl(MusicPlayer.INSTANCE.currentMeta.headPictureUrl());
                InGameHudRenderer.COVER_IMAGE.loadImage(true, ClientConfig.INSTANCE.options.coverImgInCircle);
            }
        });

//        ConcertoEvents.ON_NEXT_MUSIC.subscribe(MusicRoom::clientUpdate);
    }
}
