package top.gregtao.concerto.bridge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import top.gregtao.concerto.ConcertoServer;
import top.gregtao.concerto.core.bridge.CoreBridge;
import top.gregtao.concerto.core.network.SyncRecord;
import top.gregtao.concerto.core.player.MusicPlayerState;
import top.gregtao.concerto.core.room.MusicRoom;

public class CoreBridgeImpl implements CoreBridge {

    private static volatile boolean clientAccessEnabled;

    /** Called by the platform client bootstrap after client classes are available. */
    public static void enableClientAccess() {
        clientAccessEnabled = true;
    }

    @Override
    public String getTranslatable(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    @Override
    public void setClientClipboard(String text) {
        if (clientAccessEnabled) ClientAccess.setClipboard(text);
    }

    @Override
    public String getClientPlayerName() {
        return clientAccessEnabled ? ClientAccess.getPlayerName() : null;
    }

    @Override
    public void sendMessageToClientPlayer(String message, boolean overlay) {
        if (clientAccessEnabled) {
            ClientAccess.sendMessage(message, overlay);
        } else {
            ConcertoServer.LOGGER.info("[Message] {}", message);
        }
    }

    @Override
    public SyncRecord<MusicPlayerState> getCurrentPlayerState() {
        return MusicRoom.CLIENT_ROOM != null ? MusicRoom.CLIENT_ROOM.clientState : null;
    }

    private static class ClientAccess {
        static void setClipboard(String text) {
            Minecraft.getInstance().keyboardHandler.setClipboard(text);
        }

        static String getPlayerName() {
            LocalPlayer player = Minecraft.getInstance().player;
            return player != null ? player.getName().getString() : null;
        }

        static void sendMessage(String message, boolean overlay) {
            Minecraft.getInstance().execute(() -> {
                LocalPlayer player = Minecraft.getInstance().player;
                if (player != null) {
                    player.sendSystemMessage(Component.literal(message));
                }
            });
        }
    }
}
