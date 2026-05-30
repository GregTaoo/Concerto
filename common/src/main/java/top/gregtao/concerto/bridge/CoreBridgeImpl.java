package top.gregtao.concerto.bridge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import top.gregtao.concerto.core.bridge.CoreBridge;
import top.gregtao.concerto.core.network.SyncRecord;
import top.gregtao.concerto.core.player.MusicPlayerState;
import top.gregtao.concerto.core.room.MusicRoom;

public class CoreBridgeImpl implements CoreBridge {

    @Override
    public String getTranslatable(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    @Override
    public void setClientClipboard(String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
    }

    @Override
    public String getClientPlayerName() {
        if (Minecraft.getInstance().player != null) {
            return Minecraft.getInstance().player.getName().getString();
        }
        return null;
    }

    @Override
    public void sendMessageToClientPlayer(String message, boolean overlay) {
        Minecraft.getInstance().execute(() -> {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(Component.literal(message), overlay);
            }
        });
    }

    @Override
    public SyncRecord<MusicPlayerState> getCurrentPlayerState() {
        return MusicRoom.CLIENT_ROOM != null ? MusicRoom.CLIENT_ROOM.clientState : null;
    }
}
