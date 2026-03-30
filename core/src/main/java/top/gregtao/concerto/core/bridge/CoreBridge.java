package top.gregtao.concerto.core.bridge;

import top.gregtao.concerto.core.network.SyncRecord;
import top.gregtao.concerto.core.player.MusicPlayerState;

public interface CoreBridge {

    // Common
    String getTranslatableText(String key, Object... args);

    // Client
    void setClientClipboard(String text);
    String getClientPlayerName();
    void sendMessageToClientPlayer(String message, boolean overlay);
    SyncRecord<MusicPlayerState> getCurrentPlayerState();

    default void sendTranslatableToClientPlayer(String key, boolean overlay, Object... args) {
        sendMessageToClientPlayer(getTranslatableText(key, args), overlay);
    }
}
