package top.gregtao.concerto.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.config.ClientConfig;
import top.gregtao.concerto.player.MusicPlayer;
import top.gregtao.concerto.player.MusicPlayerHandler;
import top.gregtao.concerto.util.TextUtil;
import top.gregtao.concerto.util.Vector2i;

public class InGameHudRenderer {

    public static void render(MatrixStack matrices) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (MusicPlayer.INSTANCE.isPlaying()) {
            if (!(ClientConfig.INSTANCE.options.hideWhenChat && client.currentScreen instanceof ChatScreen)) {

                int scaledWidth = client.getWindow().getScaledWidth(), scaledHeight = client.getWindow().getScaledHeight();
                String[] texts = MusicPlayerHandler.INSTANCE.getDisplayTexts();

                MatrixStack matrixStack = new MatrixStack();
                matrixStack.push();
                ClientConfig.ClientConfigOptions options = ClientConfig.INSTANCE.options;
                if (options.displayLyrics) {
                    Vector2i pos = ClientConfig.INSTANCE.lyricsPosSupplier.getPos(scaledWidth, scaledHeight);
                    TextUtil.renderText(new LiteralText(texts[0]).formatted(Formatting.DARK_AQUA), options.lyricsAlignment,
                            pos.getX(), pos.getY(), matrixStack, client.textRenderer, 0xffffffff);
                }
                if (options.displaySubLyrics) {
                    Vector2i pos = ClientConfig.INSTANCE.subLyricsPosSupplier.getPos(scaledWidth, scaledHeight);
                    TextUtil.renderText(new LiteralText(texts[1]).formatted(Formatting.GOLD), options.subLyricsAlignment,
                            pos.getX(), pos.getY(), matrixStack, client.textRenderer, 0xffffffff);
                }
                if (options.displayMusicDetails) {
                    Vector2i pos = ClientConfig.INSTANCE.musicDetailsPosSupplier.getPos(scaledWidth, scaledHeight);

                    String state = MusicPlayer.INSTANCE.isPlayingTemp ?
                            ConcertoClient.clientState == ConcertoClient.ClientState.MUSIC_AGENT ? " | " + new TranslatableText("concerto.agent").getString() :
                            (ConcertoClient.clientState == ConcertoClient.ClientState.MUSIC_ROOM ? " | " + new TranslatableText("concerto.room").getString() : "")
                            : "";

                    TextUtil.renderText(new LiteralText(texts[2] + state), options.musicDetailsAlignment,
                            pos.getX(), pos.getY(), matrixStack, client.textRenderer, 0xffffffff);
                }
                if (options.displayTimeProgress) {
                    Vector2i pos = ClientConfig.INSTANCE.timeProgressPosSupplier.getPos(scaledWidth, scaledHeight);
                    TextUtil.renderText(new LiteralText(texts[3]), options.timeProgressAlignment,
                            pos.getX(), pos.getY(), matrixStack, client.textRenderer, 0xffffffff);
                    int blankWidth = client.textRenderer.getWidth("                              "); // 兼容不同字体
                    int timeWidth = (client.textRenderer.getWidth(new LiteralText(texts[3])) - blankWidth) / 2;
                    if (MusicPlayerHandler.INSTANCE.currentMeta != null && MusicPlayerHandler.INSTANCE.currentMeta.getDuration() != null) {
                        int x;
                        switch (options.timeProgressAlignment) {
                            case LEFT -> x = (pos.getX() + timeWidth + 9);
                            case CENTER -> x = (int) (pos.getX() - (float) blankWidth / 2 - 10);
                            default -> x = (pos.getX() - blankWidth - 15);
                        }
                        DrawableHelper.fill(matrixStack, x, pos.getY() + 3, x + blankWidth - 20, pos.getY() + 5,
                                (int) ClientConfig.INSTANCE.timeProgressBgColor.getNumber());
                        DrawableHelper.fill(matrixStack, x, pos.getY() + 3, (int) (x + (blankWidth - 20) * MusicPlayerHandler.INSTANCE.progressPercentage),
                                pos.getY() + 5, (int) ClientConfig.INSTANCE.timeProgressColor.getNumber());
                    }
                }
                matrixStack.pop();
            }
        }
        if (client.currentScreen == null || client.currentScreen instanceof ChatScreen) {
            QRCodeRenderer.drawQRCode(matrices, 5, 5);
        }
    }
}
