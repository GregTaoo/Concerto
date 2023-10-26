package top.gregtao.concerto.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.joml.Vector2i;
import top.gregtao.concerto.config.ClientConfig;
import top.gregtao.concerto.player.MusicPlayer;
import top.gregtao.concerto.player.MusicPlayerHandler;
import top.gregtao.concerto.util.TextUtil;

public class InGameHudRenderer {

    public static void render(DrawContext matrixStack, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (MusicPlayer.INSTANCE.isPlaying()) {
            if (!(ClientConfig.INSTANCE.options.hideWhenChat && client.currentScreen instanceof ChatScreen)) {

                int scaledWidth = client.getWindow().getScaledWidth(), scaledHeight = client.getWindow().getScaledHeight();
                String[] texts = MusicPlayerHandler.INSTANCE.getDisplayTexts();

                ClientConfig.ClientConfigOptions options = ClientConfig.INSTANCE.options;
                if (options.displayLyrics) {
                    Vector2i pos = ClientConfig.parsePosition(options.lyricsPosition, scaledWidth, scaledHeight);
                    TextUtil.renderText(Text.literal(texts[0]).formatted(Formatting.DARK_AQUA), options.lyricsAlignment,
                            pos.x, pos.y, matrixStack, client.textRenderer, 0xffffffff);
                }
                if (options.displaySubLyrics) {
                    Vector2i pos = ClientConfig.parsePosition(options.subLyricsPosition, scaledWidth, scaledHeight);
                    TextUtil.renderText(Text.literal(texts[1]).formatted(Formatting.GOLD), options.subLyricsAlignment,
                            pos.x, pos.y, matrixStack, client.textRenderer, 0xffffffff);
                }
                if (options.displayMusicDetails) {
                    Vector2i pos = ClientConfig.parsePosition(options.musicDetailsPosition, scaledWidth, scaledHeight);
                    TextUtil.renderText(Text.literal(texts[2]), options.musicDetailsAlignment,
                            pos.x, pos.y, matrixStack, client.textRenderer, 0xffffffff);
                }
                if (options.displayTimeProgress) {
                    Vector2i pos = ClientConfig.parsePosition(options.timeProgressPosition, scaledWidth, scaledHeight);
                    TextUtil.renderText(Text.literal(texts[3]), options.timeProgressAlignment,
                            pos.x, pos.y, matrixStack, client.textRenderer, 0xffffffff);
                    if (MusicPlayerHandler.INSTANCE.currentMeta != null && MusicPlayerHandler.INSTANCE.currentMeta.getDuration() != null) {
                        int x;
                        switch (options.timeProgressAlignment) {
                            case LEFT -> x = pos.x + 35;
                            case CENTER -> x = pos.x - 50;
                            default -> x = pos.x - 135;
                        }
                        matrixStack.fill(x, pos.y + 3, x + 100, pos.y + 5, 0xffa1c7f6);
                        matrixStack.fill(x, pos.y + 3, (int) (x + 100 * MusicPlayerHandler.INSTANCE.progressPercentage),
                                pos.y + 5, 0xff0155bc);
                    }
                }
            }
        }
        if (client.currentScreen == null || client.currentScreen instanceof ChatScreen) {
            QRCodeRenderer.drawQRCode(matrixStack, 5, 5);
        }
    }
}
