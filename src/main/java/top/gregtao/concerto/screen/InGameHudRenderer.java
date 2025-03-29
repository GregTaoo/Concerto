package top.gregtao.concerto.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.joml.Vector2i;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.config.ClientConfig;
import top.gregtao.concerto.player.MusicPlayer;
import top.gregtao.concerto.player.MusicPlayerHandler;
import top.gregtao.concerto.util.TextUtil;

public class InGameHudRenderer {

    public static ScrollingText MUSIC_DETAIL_SCROLL = new ScrollingText();

    public static class ScrollingText {
        public static int STOP_TICKS = 180;

        private int width = 0, maxWidth = 0;
        private float dx = 0, stopTicks = 0;
        private boolean stop = false, go_back = false;

        private void reset() {
            this.dx = 0;
            this.go_back = false;
            this.stop = true;
            this.stopTicks = STOP_TICKS;
        }

        public void setWidth(int width) {
            if (width != this.width) this.reset();
            this.width = width;
        }

        public void setMaxWidth(int maxWidth) {
            if (maxWidth != this.maxWidth) this.reset();
            this.maxWidth = maxWidth;
        }

        public void tick(float speed) {
            if (this.width <= this.maxWidth) return;

            float delta = speed * 40f / MinecraftClient.getInstance().getCurrentFps();
            if (this.stop) {
                this.stopTicks -= delta;
                if (this.stopTicks <= 0) {
                    this.stop = false;
                    this.go_back = !this.go_back;
                }
            } else {
                float limit = this.go_back ? 0 : (this.maxWidth - this.width);
                this.dx = this.go_back ? Math.min(limit, this.dx + delta) : Math.max(limit, this.dx - delta);
                if (this.dx == limit) {
                    this.stop = true;
                    this.stopTicks = STOP_TICKS;
                }
            }
        }

        public int getDx() {
           return this.width <= this.maxWidth ? (this.maxWidth - this.width) / 2 : (int) this.dx;
        }
    }

    public static void enableScissor(int x1, int y1, int x2, int y2) {
        Window window = MinecraftClient.getInstance().getWindow();
        int bufferHeight = window.getFramebufferHeight();
        double scaleFactor = window.getScaleFactor();
        int rX1 = (int) (x1 * scaleFactor);
        int rY1 = (int) (bufferHeight - y2 * scaleFactor);
        int rWidth = (int) ((x2 - x1) * scaleFactor);
        int rHeight = (int) ((y2 - y1) * scaleFactor);
        RenderSystem.enableScissor(rX1, rY1, Math.max(0, rWidth), Math.max(0, rHeight));
    }

    public static void disableScissor() {
        RenderSystem.disableScissor();
    }

    public static void render(MatrixStack matrices) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (MusicPlayer.INSTANCE.isPlaying()) {

            ClientConfig config = ClientConfig.INSTANCE;
            ClientConfig.ClientConfigOptions options = config.options;

            if (!(options.hideWhenChat && client.currentScreen instanceof ChatScreen)) {
                int scaledWidth = client.getWindow().getScaledWidth(), scaledHeight = client.getWindow().getScaledHeight();
                String[] texts = MusicPlayerHandler.INSTANCE.getDisplayTexts();

                if (options.displayLyrics) {
                    Vector2i pos = config.lyricsPosSupplier.getPos(scaledWidth, scaledHeight);
                    TextUtil.renderText(Text.literal(texts[0]), options.lyricsAlignment,
                            pos.x, pos.y, matrices, client.textRenderer, (int) config.lyricsColor.getNumber());
                }
                if (options.displaySubLyrics) {
                    Vector2i pos = config.subLyricsPosSupplier.getPos(scaledWidth, scaledHeight);
                    TextUtil.renderText(Text.literal(texts[1]), options.subLyricsAlignment,
                            pos.x, pos.y, matrices, client.textRenderer, (int) config.subLyricsColor.getNumber());
                }

                Text text3 = Text.literal(texts[3]);
                int text3Width = client.textRenderer.getWidth(text3);

                if (options.displayMusicDetails) {
                    Vector2i pos = config.musicDetailsPosSupplier.getPos(scaledWidth, scaledHeight);

                    String state = MusicPlayer.INSTANCE.isPlayingTemp ?
                            ConcertoClient.clientState == ConcertoClient.ClientState.MUSIC_AGENT ? " | " + Text.translatable("concerto.agent").getString() :
                            (ConcertoClient.clientState == ConcertoClient.ClientState.MUSIC_ROOM ? " | " + Text.translatable("concerto.room").getString() : "")
                            : "";

                    Text text2 = Text.literal(texts[2] + state);
                    MUSIC_DETAIL_SCROLL.setMaxWidth(text3Width);
                    MUSIC_DETAIL_SCROLL.setWidth(client.textRenderer.getWidth(text2));
                    MUSIC_DETAIL_SCROLL.tick(options.scrollingTextSpeed);

                    int startX = TextUtil.getTextRenderX(text3, options.musicDetailsAlignment, client.textRenderer, pos.x);
                    enableScissor(startX, pos.y, startX + text3Width, pos.y + client.textRenderer.fontHeight);
                    DrawableHelper.drawTextWithShadow(
                            matrices, client.textRenderer, text2, startX + MUSIC_DETAIL_SCROLL.getDx(),
                            pos.y, (int) config.musicDetailsColor.getNumber()
                    );
                    disableScissor();
                }
                if (options.displayTimeProgress) {
                    Vector2i pos = config.timeProgressPosSupplier.getPos(scaledWidth, scaledHeight);
                    TextUtil.renderText(text3, options.timeProgressAlignment,
                            pos.x, pos.y, matrices, client.textRenderer, (int) config.timeProgressTextColor.getNumber());
                    int blankWidth = client.textRenderer.getWidth("                              "); // 兼容不同字体
                    int timeWidth = (text3Width - blankWidth) / 2;
                    if (MusicPlayerHandler.INSTANCE.currentMeta != null && MusicPlayerHandler.INSTANCE.currentMeta.getDuration() != null) {
                        int x;
                        switch (options.timeProgressAlignment) {
                            case LEFT -> x = pos.x + timeWidth + 9;
                            case CENTER -> x = pos.x - blankWidth / 2 - 10;
                            default -> x = pos.x - blankWidth - 15;
                        }
                        DrawableHelper.fill(matrices, x, pos.y + 3, x + blankWidth - 20, pos.y + 5,
                                (int) config.timeProgressBgColor.getNumber());
                        DrawableHelper.fill(matrices, x, pos.y + 3, (int) (x + (blankWidth - 20) * MusicPlayerHandler.INSTANCE.progressPercentage),
                                pos.y + 5, (int) config.timeProgressColor.getNumber());
                    }
                }
            }
        }
    }
}
