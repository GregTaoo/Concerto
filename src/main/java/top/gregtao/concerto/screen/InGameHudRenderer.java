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

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (MusicPlayer.INSTANCE.isPlaying()) {
            if (!(ClientConfig.INSTANCE.options.hideWhenChat && client.currentScreen instanceof ChatScreen)) {

                int scaledWidth = client.getWindow().getScaledWidth(), scaledHeight = client.getWindow().getScaledHeight();
                String[] texts = MusicPlayerHandler.INSTANCE.getDisplayTexts();

                ClientConfig.ClientConfigOptions options = ClientConfig.INSTANCE.options;
                if (options.displayLyrics) {
                    Vector2i pos = ClientConfig.parsePosition(options.lyricsPosition, scaledWidth, scaledHeight);
                    TextUtil.renderText(Text.literal(texts[0]).formatted(Formatting.DARK_AQUA), options.lyricsAlignment,
                            pos.x, pos.y, context, client.textRenderer, 0xffffffff);
                }
                if (options.displaySubLyrics) {
                    Vector2i pos = ClientConfig.parsePosition(options.subLyricsPosition, scaledWidth, scaledHeight);
                    TextUtil.renderText(Text.literal(texts[1]).formatted(Formatting.GOLD), options.subLyricsAlignment,
                            pos.x, pos.y, context, client.textRenderer, 0xffffffff);
                }
                if (options.displayMusicDetails) {
                    Vector2i pos = ClientConfig.parsePosition(options.musicDetailsPosition, scaledWidth, scaledHeight);
                    TextUtil.renderText(Text.literal(texts[2]), options.musicDetailsAlignment,
                            pos.x, pos.y, context, client.textRenderer, 0xffffffff);
                }
                if (options.displayTimeProgress) {
                    Vector2i pos = ClientConfig.parsePosition(options.timeProgressPosition, scaledWidth, scaledHeight);
                    TextUtil.renderText(Text.literal(texts[3]), options.timeProgressAlignment,
                            pos.x, pos.y, context, client.textRenderer, 0xffffffff);
                    int blankWidth = client.textRenderer.getWidth("                              "); // 兼容不同字体
                    int timeWidth = (client.textRenderer.getWidth(Text.literal(texts[3])) - blankWidth) / 2;
                    if (MusicPlayerHandler.INSTANCE.currentMeta != null && MusicPlayerHandler.INSTANCE.currentMeta.getDuration() != null) {
                        int x;
                        switch (options.timeProgressAlignment) {
                            case LEFT -> x = pos.x + timeWidth + 9;
                            case CENTER -> x = pos.x - blankWidth / 2 - 10;
                            default -> x = pos.x - blankWidth - 15;
                        }
                        context.fill(x, pos.y + 3, x + blankWidth - 20, pos.y + 5, 0xffa1c7f6);
                        context.fill(x, pos.y + 3, (int) (x + (blankWidth - 20) * MusicPlayerHandler.INSTANCE.progressPercentage),
                                pos.y + 5, 0xff0155bc);
                    }
                }
            }
        }
        if (client.currentScreen == null || client.currentScreen instanceof ChatScreen) {
            QRCodeRenderer.drawQRCode(context, 5, 5);
        }
    }
}
