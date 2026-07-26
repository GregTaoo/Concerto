package top.gregtao.concerto.bridge;

import net.minecraft.network.chat.Component;
import top.gregtao.concerto.ConcertoServer;
import top.gregtao.concerto.core.bridge.CoreBridge;
import top.gregtao.concerto.core.network.SyncRecord;
import top.gregtao.concerto.core.player.MusicPlayerState;
import top.gregtao.concerto.core.room.MusicRoom;

public class CoreBridgeImpl implements CoreBridge {

    // 专用服务器上不存在 client 类;client 引用集中在 ClientAccess 内,
    // 保证本类在专用服上可安全加载和调用
    private static final boolean CLIENT_ENV = detectClientEnv();

    private static boolean detectClientEnv() {
        try {
            Class.forName("net.minecraft.client.Minecraft");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public String getTranslatable(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    @Override
    public void setClientClipboard(String text) {
        if (CLIENT_ENV) ClientAccess.setClipboard(text);
    }

    @Override
    public String getClientPlayerName() {
        return CLIENT_ENV ? ClientAccess.getPlayerName() : null;
    }

    @Override
    public void sendMessageToClientPlayer(String message, boolean overlay) {
        if (CLIENT_ENV) {
            ClientAccess.sendMessage(message, overlay);
        } else {
            ConcertoServer.LOGGER.info("[player message] {}", message);
        }
    }

    @Override
    public SyncRecord<MusicPlayerState> getCurrentPlayerState() {
        return MusicRoom.CLIENT_ROOM != null ? MusicRoom.CLIENT_ROOM.clientState : null;
    }

    private static class ClientAccess {
        static void setClipboard(String text) {
            net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(text);
        }

        static String getPlayerName() {
            net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
            return player != null ? player.getName().getString() : null;
        }

        static void sendMessage(String message, boolean overlay) {
            net.minecraft.client.Minecraft.getInstance().execute(() -> {
                net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
                if (player != null) {
                    player.displayClientMessage(Component.literal(message), overlay);
                }
            });
        }
    }
}
