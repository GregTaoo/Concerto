package top.gregtao.concerto.paper.bridge;

import top.gregtao.concerto.core.bridge.CoreBridge;
import top.gregtao.concerto.core.network.SyncRecord;
import top.gregtao.concerto.core.player.MusicPlayerState;
import top.gregtao.concerto.paper.util.ComponentUtil;

public class CoreBridgeImpl implements CoreBridge {

    @Override
    public String getTranslatable(String key, Object... args) {
        return ComponentUtil.translatable(key, args).insertion();
    }

    @Override
    public void setClientClipboard(String text) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getClientPlayerName() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void sendMessageToClientPlayer(String message, boolean overlay) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SyncRecord<MusicPlayerState> getCurrentPlayerState() {
        throw new UnsupportedOperationException();
    }
}
