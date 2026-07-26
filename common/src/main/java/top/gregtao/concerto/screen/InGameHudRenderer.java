package top.gregtao.concerto.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;
import top.gregtao.concerto.core.config.ClientConfig;
import top.gregtao.concerto.core.enums.TextAlignment;
import top.gregtao.concerto.core.music.MusicTimestamp;
import top.gregtao.concerto.core.music.lyrics.Lyrics;
import top.gregtao.concerto.core.player.MusicPlayer;
import top.gregtao.concerto.core.player.MusicPlayerHandler;
import top.gregtao.concerto.core.room.MusicRoom;
import top.gregtao.concerto.core.util.Vector2i;
import top.gregtao.concerto.mixin.GuiGraphicsExtractorAccessor;
import top.gregtao.concerto.screen.widget.URLImageWidget;
import top.gregtao.concerto.util.ComponentUtil;

public class InGameHudRenderer {

    public static ScrollingText MUSIC_DETAIL_SCROLL = new ScrollingText();
    public static URLImageWidget COVER_IMAGE = new URLImageWidget(20, 20, 0, 0, null, false);

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
            // 强制 Unicode 字体时，该宽度经常小范围变动，因此设置容许范围
            if (maxWidth > this.maxWidth + 5 || maxWidth < this.maxWidth - 5) this.reset();
            this.maxWidth = maxWidth;
        }

        public void tick(float speed) {
            if (this.width <= this.maxWidth) return;

            float delta = speed * 40f / Math.max(1, Minecraft.getInstance().getFps());
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

    public static void renderTimedScrollableText(
            GuiGraphicsExtractor context, Component text, TextAlignment align, int x, int y, int areaX, int areaWidth,
            long startMs, long currentMs, long endMs, int color, boolean shadow
    ) {
        Minecraft client = Minecraft.getInstance();
        int textWidth = client.font.width(text);

        if (textWidth <= areaWidth) {
            context.text(client.font, text, ComponentUtil.getTextRenderX(text, align, client.font, x), y, color, shadow);
            return;
        }

        long duration = Math.max(1L, endMs - startMs);
        float scrollingDuration = Math.max(1f, duration * 0.9f);
        float progress = Math.max(0f, Math.min(1f, (currentMs - startMs) / scrollingDuration));
        int offset = Math.round((areaWidth - textWidth) * progress);

        context.enableScissor(areaX, y, areaX + areaWidth, y + client.font.lineHeight);
        context.text(
                client.font, text, areaX + offset, y, color, shadow
        );
        context.disableScissor();
    }

    private static long getCurrentTimeMs() {
        return MusicPlayer.INSTANCE.getInterpolatedCurrentTimeMilliseconds();
    }

    private static long[] getCurrentLyricLineTimes(Lyrics lyrics, long currentTimeMs) {
        if (lyrics == null || lyrics.isEmpty()) {
            return new long[]{currentTimeMs, currentTimeMs};
        }

        int activeIndex = lyrics.getCurrentIndex();
        MusicTimestamp duration = MusicPlayer.INSTANCE.currentMeta == null ? null : MusicPlayer.INSTANCE.currentMeta.getDuration();
        return new long[]{
                lyrics.getLineStartMilliseconds(activeIndex),
                lyrics.getLineEndMilliseconds(activeIndex, duration)
        };
    }

    public static void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        Minecraft client = Minecraft.getInstance();
        if (MusicPlayer.INSTANCE.isPlaying()) {

            ClientConfig config = ClientConfig.INSTANCE;
            ClientConfig.ClientConfigOptions options = config.options;

            if (!(options.hideWhenChat && client.gui.screen() instanceof ChatScreen)) {
                int scaledWidth = client.getWindow().getGuiScaledWidth(), scaledHeight = client.getWindow().getGuiScaledHeight();
                String[] texts = MusicPlayerHandler.INSTANCE.getDisplayTexts();

                context = new GuiGraphicsExtractor(Minecraft.getInstance(),
                        ((GuiGraphicsExtractorAccessor) context).getGuiRenderState(), mouseX, mouseY);

                if (options.displayLyrics) {
                    Vector2i pos = config.lyricsPosSupplier.getPos(scaledWidth, scaledHeight);
                    long currentTimeMs = getCurrentTimeMs();
                    long[] lineTimes = getCurrentLyricLineTimes(MusicPlayer.INSTANCE.currentLyrics, currentTimeMs);
                    renderTimedScrollableText(context, Component.literal(texts[0]), options.lyricsAlignment,
                            pos.x, pos.y, 0, scaledWidth, lineTimes[0], currentTimeMs, lineTimes[1],
                            (int) config.lyricsColor.getNumber(), options.textShadow);
                }
                if (options.displaySubLyrics) {
                    Vector2i pos = config.subLyricsPosSupplier.getPos(scaledWidth, scaledHeight);
                    long currentTimeMs = getCurrentTimeMs();
                    long[] lineTimes = getCurrentLyricLineTimes(MusicPlayer.INSTANCE.currentSubLyrics, currentTimeMs);
                    renderTimedScrollableText(context, Component.literal(texts[1]), options.subLyricsAlignment,
                            pos.x, pos.y, 0, scaledWidth, lineTimes[0], currentTimeMs, lineTimes[1],
                            (int) config.subLyricsColor.getNumber(), options.textShadow);
                }

                Component text3 = Component.literal(texts[3]);
                int text3Width = client.font.width(text3);

                if (options.displayMusicDetails) {
                    Vector2i pos = config.musicDetailsPosSupplier.getPos(scaledWidth, scaledHeight);

                    MusicRoom.ClientState roomState = MusicRoom.clientGetState();
                    String state = MusicPlayer.INSTANCE.isPlayingTemp ?
                            roomState == MusicRoom.ClientState.MUSIC_AGENT ? " | " + Component.translatable("concerto.agent").getString() :
                                    (roomState == MusicRoom.ClientState.MUSIC_ROOM ? " | " + Component.translatable("concerto.room").getString() : "")
                            : "";

                    Component text2 = Component.literal(texts[2] + state);
                    MUSIC_DETAIL_SCROLL.setMaxWidth(text3Width);
                    MUSIC_DETAIL_SCROLL.setWidth(client.font.width(text2));
                    MUSIC_DETAIL_SCROLL.tick(options.scrollingTextSpeed);

                    int startX = ComponentUtil.getTextRenderX(text3, options.musicDetailsAlignment, client.font, pos.x);
                    context.enableScissor(startX, pos.y, startX + text3Width, pos.y + client.font.lineHeight);
                    context.text(
                            client.font, text2, startX + MUSIC_DETAIL_SCROLL.getDx(),
                            pos.y, (int) config.musicDetailsColor.getNumber(),
                            options.textShadow
                    );
                    context.disableScissor();
                }
                if (options.displayTimeProgress) {
                    Vector2i pos = config.timeProgressPosSupplier.getPos(scaledWidth, scaledHeight);
                    ComponentUtil.renderText(text3, options.timeProgressAlignment,
                            pos.x, pos.y, context, client.font, (int) config.timeProgressTextColor.getNumber());
                    int blankWidth = client.font.width("                              "); // 兼容不同字体
                    int timeWidth = (text3Width - blankWidth) / 2;
                    if (MusicPlayer.INSTANCE.currentMeta != null && MusicPlayer.INSTANCE.currentMeta.getDuration() != null) {
                        int x;
                        switch (options.timeProgressAlignment) {
                            case LEFT -> x = pos.x + timeWidth + 9;
                            case CENTER -> x = pos.x - blankWidth / 2 + 9;
                            default -> x = pos.x - blankWidth - timeWidth + 9;
                        }
                        context.fill(x, pos.y + 3, x + blankWidth - 20, pos.y + 5,
                                (int) config.timeProgressBgColor.getNumber());
                        context.fill(x, pos.y + 3, (int) (x + (blankWidth - 20) * MusicPlayer.INSTANCE.progressPercentage),
                                pos.y + 5, (int) config.timeProgressColor.getNumber());
                    }
                }

                if (options.displayCoverImg) {
                    Vector2i pos = config.coverImgPosSupplier.getPos(scaledWidth, scaledHeight);
                    int size = config.options.coverImgSize;
                    COVER_IMAGE.setX(pos.x);
                    COVER_IMAGE.setY(pos.y);
                    COVER_IMAGE.setSize(size, size);

                    if (options.coverImgRotate) {
                        float cx = pos.x + size / 2f;
                        float cy = pos.y + size / 2f;
                        float angleRad = delta * (float) Math.PI / 180f;

                        Matrix3x2fStack matrices = context.pose();
                        matrices.translate(cx, cy, matrices); // 先平移到中心
                        matrices.rotate(angleRad); // 旋转
                        matrices.translate(-cx, -cy, matrices); // 再平移回来
                    }

                    COVER_IMAGE.extractRenderState(context, mouseX, mouseY, delta);
                }
            }
        }
    }
}
