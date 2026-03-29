package top.gregtao.concerto.core.bridge;

import top.gregtao.concerto.core.network.SyncRecord;
import top.gregtao.concerto.core.player.MusicPlayerState;

public interface CoreBridge {

    String getTranslatableText(String key, Object... args);
    void sendMessageToClientPlayer(String message, boolean overlay);
    SyncRecord<MusicPlayerState> getCurrentState();
}
