package top.gregtao.concerto.bridge;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import top.gregtao.concerto.core.bridge.CoreBridge;
import top.gregtao.concerto.core.network.SyncRecord;
import top.gregtao.concerto.core.player.MusicPlayerState;
import top.gregtao.concerto.core.room.MusicRoom;

public class CoreBridgeImpl implements CoreBridge {

    @Override
    public String getTranslatableText(String key, Object... args) {
        return Text.translatable(key, args).getString();
    }

    @Override
    public void setClientClipboard(String text) {
        MinecraftClient.getInstance().keyboard.setClipboard(text);
    }

    @Override
    public String getClientPlayerName() {
        if (MinecraftClient.getInstance().player != null) {
            return MinecraftClient.getInstance().player.getName().getString();
        }
        return null;
    }

    @Override
    public void sendMessageToClientPlayer(String message, boolean overlay) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            player.sendMessage(Text.literal(message), overlay);
        }
    }

    @Override
    public SyncRecord<MusicPlayerState> getCurrentPlayerState() {
        return MusicRoom.CLIENT_ROOM != null ? MusicRoom.CLIENT_ROOM.clientState : null;
    }
}
