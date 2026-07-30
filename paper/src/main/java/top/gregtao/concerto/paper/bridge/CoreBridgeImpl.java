package top.gregtao.concerto.paper.bridge;

import top.gregtao.concerto.core.Concerto;
import top.gregtao.concerto.core.bridge.CoreBridge;
import top.gregtao.concerto.core.network.SyncRecord;
import top.gregtao.concerto.core.player.MusicPlayerState;
import top.gregtao.concerto.paper.util.I18n;

public class CoreBridgeImpl implements CoreBridge {

    @Override
    public String getTranslatable(String key, Object... args) {
        return I18n.get(key, args);
    }

    @Override
    public void setClientClipboard(String text) {
        Concerto.getLogger().error("Calling client function!");
        throw new UnsupportedOperationException();
    }

    @Override
    public String getClientPlayerName() {
        Concerto.getLogger().error("Calling client function!");
        throw new UnsupportedOperationException();
    }

    @Override
    public void sendMessageToClientPlayer(String message, boolean overlay) {
        // 服务端没有"本地玩家",这类提示对服务端无意义;降级为日志,避免共享
        // 解析路径(如点播室解析试听曲目)被一个提示消息打断
        Concerto.getLogger().info("[player message] " + message);
    }

    @Override
    public SyncRecord<MusicPlayerState> getCurrentPlayerState() {
        Concerto.getLogger().error("Calling client function!");
        throw new UnsupportedOperationException();
    }
}
