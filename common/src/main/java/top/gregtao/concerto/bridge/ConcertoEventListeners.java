package top.gregtao.concerto.bridge;

import net.minecraft.client.Minecraft;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.core.config.ClientConfig;
import top.gregtao.concerto.core.event.ConcertoEvents;
import top.gregtao.concerto.core.player.MusicPlayer;
import top.gregtao.concerto.core.util.ConcertoRunner;
import top.gregtao.concerto.screen.InGameHudRenderer;

public class ConcertoEventListeners {

    public static void registerClientListeners() {

        ConcertoEvents.ON_PLAYER_START.subscribe(() -> {
            Minecraft client = Minecraft.getInstance();
            client.getMusicManager().stopPlaying();
            ConcertoClient.syncPlayerVolume();
        });

        ConcertoEvents.ON_MUSIC_INFO_RESET.subscribe(() -> InGameHudRenderer.COVER_IMAGE.setUrl(null));

        ConcertoEvents.ON_MUSIC_INFO_UPDATE.subscribe(() -> {
            if (!MusicPlayer.INSTANCE.currentMeta.headPictureUrl().isEmpty()) {
                InGameHudRenderer.COVER_IMAGE.setUrl(MusicPlayer.INSTANCE.currentMeta.headPictureUrl());
                // This event fires on the Concerto-Loader thread before the
                // engine gets the session; a synchronous download here (with
                // retries) would delay the start of audio by seconds
                ConcertoRunner.run(() ->
                        InGameHudRenderer.COVER_IMAGE.loadImage(true, ClientConfig.INSTANCE.options.coverImgInCircle));
            }
        });
    }
}
