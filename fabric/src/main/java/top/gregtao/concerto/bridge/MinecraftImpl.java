package top.gregtao.concerto.bridge;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import top.gregtao.concerto.core.bridge.Minecraft;

public class MinecraftImpl implements Minecraft {

    @Override
    public String getTranslatableText(String key, Object... args) {
        return Text.translatable(key, args).getString();
    }

    @Override
    public void sendMessageToClientPlayer(String message, boolean overlay) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            player.sendMessage(Text.literal(message), overlay);
        }
    }
}
