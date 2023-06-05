package top.gregtao.concerto.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import top.gregtao.concerto.config.ClientConfig;
import top.gregtao.concerto.enums.TextAlignment;
import top.gregtao.concerto.http.qrcode.QRCode;
import top.gregtao.concerto.player.MusicPlayer;
import top.gregtao.concerto.player.MusicPlayerStatus;
import top.gregtao.concerto.util.TextUtil;

import java.util.List;

public class InGameHudRenderer {

    public static void render(MatrixStack matrixStack, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (MusicPlayer.INSTANCE.isPlaying()) {
            if (!(ClientConfig.INSTANCE.options.hideWhenChat && client.currentScreen instanceof ChatScreen)) {
                int scaledWidth = client.getWindow().getScaledWidth(), scaledHeight = client.getWindow().getScaledHeight();
                String[] texts = MusicPlayerStatus.INSTANCE.getDisplayTexts();

                String settingStr = ClientConfig.INSTANCE.options.displayMusicInfo;
                settingStr = settingStr.length() < 3 ? "111" : settingStr;
                List<Boolean> displaySetting = settingStr.chars().mapToObj(value -> value == '1').toList();

                int positionType = ClientConfig.INSTANCE.options.musicInfoPosition;
                positionType = Math.max(0, Math.min(positionType, 3));

                int startX = (positionType == 0 || positionType == 2) ? scaledWidth / 2 :
                        (positionType == 1 ? 5 : scaledWidth - 5);
                int startY = positionType == 0 ? scaledHeight - 80 : 5;
                TextAlignment align = (positionType == 0 || positionType == 2) ? TextAlignment.CENTER :
                        (positionType == 1 ? TextAlignment.LEFT : TextAlignment.RIGHT);

                if (displaySetting.get(0)) {
                    TextUtil.renderText(Text.literal(texts[0]).formatted(Formatting.DARK_AQUA), align,
                            startX, startY, matrixStack, client.textRenderer, 0xffffffff);
                    startY += 10;
                }
                if (displaySetting.get(1)) {
                    TextUtil.renderText(Text.literal(texts[1]), align,
                            startX, startY, matrixStack, client.textRenderer, 0xffffffff);
                    startY += 10;
                }
                if (displaySetting.get(2)) {
                    TextUtil.renderText(Text.literal(texts[2]), align,
                            startX, startY, matrixStack, client.textRenderer, 0xffffffff);
                }
            }
        }
        if (client.currentScreen == null || client.currentScreen instanceof ChatScreen) {
            QRCode.drawQRCode(matrixStack, 5, 5);
        }
    }
}
