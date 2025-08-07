package top.gregtao.concerto.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Util;
import net.minecraft.util.math.Quaternion;
import net.minecraft.util.math.Vec3f;
import top.gregtao.concerto.ConcertoClient;
import top.gregtao.concerto.config.ClientConfig;
import top.gregtao.concerto.player.MusicPlayer;
import top.gregtao.concerto.player.MusicPlayerHandler;
import top.gregtao.concerto.util.TextUtil;
import top.gregtao.concerto.util.Vector2i;

public class InGameHudRenderer {

    public static ScrollingText MUSIC_DETAIL_SCROLL = new ScrollingText();

    public static class ScrollingText {
        public static int STOP_TICKS = 180;

        private int width = 0, maxWidth = 0;
        private float dx = 0, stopTicks = 0;
        private boolean stop = false, go_back = false;

        private long lastRenderTime = 0;

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
            // 强制 Unicode 字体时，该宽度经常小范围变动，因此设置容许范围
            if (maxWidth > this.maxWidth + 5 || maxWidth < this.maxWidth - 5) this.reset();
            this.maxWidth = maxWidth;
        }

        public long getRenderTimeDelta() {
            long delta = System.currentTimeMillis() - this.lastRenderTime;
            delta = Math.min(delta, 100);
            this.lastRenderTime = System.currentTimeMillis();
            return delta;
        }

        public void tick(float speed) {
            if (this.width <= this.maxWidth) return;

            float delta = speed * 0.04f * this.getRenderTimeDelta();
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

    public static void render(MatrixStack matrices, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (MusicPlayer.INSTANCE.isPlaying()) {

            ClientConfig config = ClientConfig.INSTANCE;
            ClientConfig.ClientConfigOptions options = config.options;

            if (!(options.hideWhenChat && client.currentScreen instanceof ChatScreen)) {
                int scaledWidth = client.getWindow().getScaledWidth(), scaledHeight = client.getWindow().getScaledHeight();
                String[] texts = MusicPlayerHandler.INSTANCE.getDisplayTexts();

                matrices = new MatrixStack();
                if (options.displayLyrics) {
                    Vector2i pos = config.lyricsPosSupplier.getPos(scaledWidth, scaledHeight);
                    TextUtil.renderText(new LiteralText(texts[0]), options.lyricsAlignment,
                            pos.getX(), pos.getY(), matrices, client.textRenderer, (int) config.lyricsColor.getNumber());
                }
                if (options.displaySubLyrics) {
                    Vector2i pos = config.subLyricsPosSupplier.getPos(scaledWidth, scaledHeight);
                    TextUtil.renderText(new LiteralText(texts[1]), options.subLyricsAlignment,
                            pos.getX(), pos.getY(), matrices, client.textRenderer, (int) config.subLyricsColor.getNumber());
                }

                Text text3 = new LiteralText(texts[3]);
                int text3Width = client.textRenderer.getWidth(text3);

                if (options.displayMusicDetails) {
                    Vector2i pos = config.musicDetailsPosSupplier.getPos(scaledWidth, scaledHeight);

                    String state = MusicPlayer.INSTANCE.isPlayingTemp ?
                            ConcertoClient.clientState == ConcertoClient.ClientState.MUSIC_AGENT ? " | " + new TranslatableText("concerto.agent").getString() :
                            (ConcertoClient.clientState == ConcertoClient.ClientState.MUSIC_ROOM ? " | " + new TranslatableText("concerto.room").getString() : "")
                            : "";

                    Text text2 = new LiteralText(texts[2] + state);
                    MUSIC_DETAIL_SCROLL.setMaxWidth(text3Width);
                    MUSIC_DETAIL_SCROLL.setWidth(client.textRenderer.getWidth(text2));
                    MUSIC_DETAIL_SCROLL.tick(options.scrollingTextSpeed);

                    int startX = TextUtil.getTextRenderX(text3, options.musicDetailsAlignment, client.textRenderer, pos.getX());
                    enableScissor(startX, pos.getY(), startX + text3Width, pos.getY() + client.textRenderer.fontHeight);
                    if (ClientConfig.INSTANCE.options.textShadow) {
                        client.textRenderer.drawWithShadow(
                                matrices, text2, startX + MUSIC_DETAIL_SCROLL.getDx(),
                                pos.getY(), (int) config.musicDetailsColor.getNumber()
                        );
                    } else {
                        client.textRenderer.draw(
                                matrices, text2, startX + MUSIC_DETAIL_SCROLL.getDx(),
                                pos.getY(), (int) config.musicDetailsColor.getNumber()
                        );
                    }
                    disableScissor();
                }
                if (options.displayTimeProgress) {
                    Vector2i pos = config.timeProgressPosSupplier.getPos(scaledWidth, scaledHeight);
                    TextUtil.renderText(text3, options.timeProgressAlignment,
                            pos.getX(), pos.getY(), matrices, client.textRenderer, (int) config.timeProgressTextColor.getNumber());
                    int blankWidth = client.textRenderer.getWidth("                              "); // 兼容不同字体
                    int timeWidth = (text3Width - blankWidth) / 2;
                    if (MusicPlayerHandler.INSTANCE.currentMeta != null && MusicPlayerHandler.INSTANCE.currentMeta.getDuration() != null) {
                        int x;
                        switch (options.timeProgressAlignment) {
                            case LEFT -> x = pos.getX() + timeWidth + 9;
                            case CENTER -> x = pos.getX() - blankWidth / 2 + 9;
                            default -> x = pos.getX() - blankWidth - timeWidth + 9;
                        }
                        DrawableHelper.fill(matrices, x, pos.getY() + 3, x + blankWidth - 20, pos.getY() + 5,
                                (int) config.timeProgressBgColor.getNumber());
                        DrawableHelper.fill(matrices, x, pos.getY() + 3, (int) (x + (blankWidth - 20) * MusicPlayerHandler.INSTANCE.progressPercentage),
                                pos.getY() + 5, (int) config.timeProgressColor.getNumber());
                    }
                }

                if (options.displayCoverImg) {
                    matrices.push();

                    Vector2i pos = config.coverImgPosSupplier.getPos(scaledWidth, scaledHeight);
                    int size = config.options.coverImgSize;
                    MusicPlayerHandler.INSTANCE.headPicture.setX(pos.getX());
                    MusicPlayerHandler.INSTANCE.headPicture.setY(pos.getY());
                    MusicPlayerHandler.INSTANCE.headPicture.setSize(size, size);

                    if (options.coverImgRotate) {
                        float cx = pos.getX() + size / 2f;
                        float cy = pos.getY() + size / 2f;
                        float angleRad = Util.getMeasuringTimeMs() * (float) Math.PI / 180f / 50;

                        matrices.translate(cx, cy, 0); // 先平移到中心
                        matrices.multiply(new Quaternion(Vec3f.POSITIVE_Z, angleRad, false)); // 旋转
                        matrices.translate(-cx, -cy, 0); // 再平移回来
                    }

                    MusicPlayerHandler.INSTANCE.headPicture.render(matrices, 0, 0, delta);
                    matrices.pop();
                }
            }
        }
    }
}
