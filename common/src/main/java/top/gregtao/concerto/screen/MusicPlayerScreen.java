package top.gregtao.concerto.screen;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import top.gregtao.concerto.core.config.ClientConfig;
import top.gregtao.concerto.core.enums.OrderType;
import top.gregtao.concerto.core.enums.TextAlignment;
import top.gregtao.concerto.core.music.MusicTimestamp;
import top.gregtao.concerto.core.music.lyrics.Lyrics;
import top.gregtao.concerto.core.music.meta.music.MusicMetaData;
import top.gregtao.concerto.core.player.MusicPlayer;
import top.gregtao.concerto.core.player.MusicPlayerHandler;
import top.gregtao.concerto.core.player.PlayerPermissions;
import top.gregtao.concerto.core.util.Pair;
import top.gregtao.concerto.mixin.GuiGraphicsAccessor;

import java.util.ArrayList;

public class MusicPlayerScreen extends ConcertoScreen {

    private Button playPauseButton;
    private Button nextButton;
    private CycleButton<@NotNull OrderType> orderButton;

    private float rotationAngle = 0f;
    private int scrollOffset = 0;

    public MusicPlayerScreen(Screen parent) {
        super(Component.empty(), parent);
    }

    @Override
    protected void init() {
        super.init();

        int y = this.height - 30;
        int totalWidth = 4 * 80 + 3 * 2; // four buttons, 2px gaps
        int x = (this.width - totalWidth) / 2;

        Button playlistButton = Button.builder(
                Component.translatable("concerto.screen.general_list"),
                button -> this.minecraft.setScreen(new GeneralPlaylistScreen(this))
        ).pos(x, y).size(80, 20).build();
        this.addRenderableWidget(playlistButton);
        x += 82;

        this.playPauseButton = Button.builder(
                Component.translatable(MusicPlayerHandler.INSTANCE.getState().get().paused ? "concerto.screen.play" : "concerto.screen.pause"),
                button -> {
                    boolean paused = MusicPlayerHandler.INSTANCE.isPaused();
                    MusicPlayerHandler.INSTANCE.tryForcePause(!paused);
                    button.setMessage(Component.translatable(!paused ? "concerto.screen.play" : "concerto.screen.pause"));
                }
        ).pos(x, y).size(80, 20).build();
        x += 82;

        this.nextButton = Button.builder(
                Component.translatable("concerto.screen.next"),
                button -> MusicPlayerHandler.INSTANCE.playNextAsync(1)
        ).pos(x, y).size(80, 20).build();
        x += 82;

        this.orderButton = CycleButton.builder((OrderType val) -> Component.literal(val.getName()), MusicPlayerHandler.INSTANCE.getOrderType())
                .withValues(OrderType.values())
                .create(x, y, 80, 20, Component.translatable("concerto.screen.order"),
                        (widget, orderType) -> MusicPlayerHandler.INSTANCE.setOrderType(orderType));

        this.addRenderableWidget(this.playPauseButton);
        this.addRenderableWidget(this.nextButton);
        this.addRenderableWidget(this.orderButton);

        this.updateButtonStates();
    }

    private void updateButtonStates() {
        this.playPauseButton.active = PlayerPermissions.canControlPlayback();
        this.nextButton.active = PlayerPermissions.canChangeMusicIndex();
        this.orderButton.active = PlayerPermissions.canChangeOrderType();

        boolean isPaused = MusicPlayerHandler.INSTANCE.getState().get().paused;
        this.playPauseButton.setMessage(Component.translatable(isPaused ? "concerto.screen.play" : "concerto.screen.pause"));
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        MusicMetaData metaData = MusicPlayer.INSTANCE.currentMeta;
        if ((!MusicPlayer.INSTANCE.isPlaying() && !MusicPlayer.INSTANCE.isPaused()) || metaData == null) {
            context.drawCenteredString(this.font, Component.translatable("concerto.not_playing"), this.width / 2, this.height / 2, 0xAAAAAAFF);
            return;
        }

        if (!MusicPlayerHandler.INSTANCE.getState().get().paused) {
            this.rotationAngle += delta * 0.3f;
            if (this.rotationAngle >= 360f) this.rotationAngle -= 360f;
        }

        int leftWidth = this.width / 2;
        int imgSize = Math.max(96, Math.min(120, leftWidth - 60));
        int imgX = (leftWidth - imgSize) / 2;
        int imgY = (this.height - imgSize) / 2 - 8;

        Matrix3x2fStack matrices = context.pose();
        matrices.pushMatrix();

        InGameHudRenderer.COVER_IMAGE.setX(imgX);
        InGameHudRenderer.COVER_IMAGE.setY(imgY);
        InGameHudRenderer.COVER_IMAGE.setSize(imgSize, imgSize);

        matrices.translate(imgX + imgSize / 2f, imgY + imgSize / 2f);
        matrices.rotate(this.rotationAngle * (float) Math.PI / 180f); // 旋转
        matrices.translate(-(imgX + imgSize / 2f), -(imgY + imgSize / 2f));

        InGameHudRenderer.COVER_IMAGE.render(context, mouseX, mouseY, delta);

        matrices.popMatrix();

        int rightHalfX = this.width / 2; // lyrics start at mid-screen for >= 1/2 width
        int lyricsWidth = this.width - rightHalfX - 20;

        String title = metaData.title();
        String author = metaData.author();
        int centerX = this.width / 2;
        int titleY = 8;
        context.drawString(this.font, title, centerX - this.font.width(title) / 2, titleY, 0xFFFFFFFF, false);
        if (author != null && !author.isEmpty()) {
            int authorY = titleY + 12;
            context.drawString(this.font, author, centerX - this.font.width(author) / 2, authorY, 0xFFAAAAAA, false);
        }

        renderTopProgressBar(context, metaData);

        Lyrics currentLyrics = MusicPlayer.INSTANCE.currentLyrics;
        Lyrics currentSubLyrics = MusicPlayer.INSTANCE.currentSubLyrics;
        if (currentLyrics != null && !currentLyrics.isEmpty()) {
            ArrayList<Pair<MusicTimestamp, String>> lyrics = currentLyrics.getLyricBody();
            ArrayList<Pair<MusicTimestamp, String>> subLyrics = currentSubLyrics != null ? currentSubLyrics.getLyricBody() : null;

            long currentTime = MusicPlayer.INSTANCE.getInterpolatedCurrentTimeMilliseconds();

            int activeIndex = currentLyrics.getCurrentIndex();

            int lineHeight = subLyrics != null ? 27 : 17;
            int startY = 45;
            int endY = this.height - 40;

            int targetScrollOffset = (activeIndex * lineHeight) - ((endY - startY) / 2) + (lineHeight / 2);
            this.scrollOffset += (int) ((targetScrollOffset - this.scrollOffset) * 0.15f);

            context.enableScissor(rightHalfX, startY, this.width - 20, endY);
            for (int i = 0; i < lyrics.size(); i++) {
                String line = lyrics.get(i).getSecond();
                String subLine = null;
                int subIndex = -1;
                int[] subLyricsMapping = MusicPlayer.INSTANCE.currentSubLyricsMapping;
                if (subLyrics != null && i < subLyricsMapping.length) {
                    subIndex = subLyricsMapping[i];
                    if (subIndex >= 0 && subIndex < subLyrics.size()) {
                        subLine = subLyrics.get(subIndex).getSecond();
                    }
                }

                int y = startY + (i * lineHeight) - this.scrollOffset;

                if (y > startY - lineHeight && y < endY + lineHeight) {
                    boolean isActive = (i == activeIndex);
                    int alpha = Math.max(15, 255 - Math.abs(i - activeIndex) * 35);

                    int color = isActive ? ((int) ClientConfig.INSTANCE.lyricsColor.getNumber() | 0xFF000000) : ((alpha << 24) | 0xAAAAAA);
                    long lineStart = currentLyrics.getLineStartMilliseconds(i);
                    long lineEnd = currentLyrics.getLineEndMilliseconds(i, metaData.getDuration());
                    InGameHudRenderer.renderTimedScrollableText(context, Component.literal(line),
                            TextAlignment.CENTER,
                            rightHalfX + lyricsWidth / 2, y, rightHalfX, lyricsWidth,
                            lineStart, currentTime, lineEnd, color, isActive);

                    if (subLine != null) {
                        int subColor = isActive ? ((int) ClientConfig.INSTANCE.subLyricsColor.getNumber() | 0xFF000000) : ((alpha << 24) | 0x888888);
                        int subY = y + 12;
                        long subLineStart = currentSubLyrics.getLineStartMilliseconds(subIndex);
                        long subLineEnd = currentSubLyrics.getLineEndMilliseconds(subIndex, metaData.getDuration());
                        InGameHudRenderer.renderTimedScrollableText(context, Component.literal(subLine),
                                TextAlignment.CENTER,
                                rightHalfX + lyricsWidth / 2, subY, rightHalfX, lyricsWidth,
                                subLineStart, currentTime, subLineEnd, subColor, false);
                    }
                }
            }
            context.disableScissor();
        } else {
            int placeholderY = this.height / 2;
            context.drawCenteredString(this.font, Component.translatable("concerto.no_subtitle"), rightHalfX + lyricsWidth / 2, placeholderY, 0xFFAAAAAA);
        }
        renderSpectrum(context);
    }

    private void renderTopProgressBar(GuiGraphics context, MusicMetaData metaData) {
        if (metaData == null || metaData.getDuration() == null) {
            return;
        }

        int barHeight = 2;
        double progress = MusicPlayer.INSTANCE.progressPercentage;
        progress = Math.max(0.0D, Math.min(1.0D, progress));

        int bgColor = (int) ClientConfig.INSTANCE.timeProgressBgColor.getNumber();
        int progressColor = (int) ClientConfig.INSTANCE.timeProgressColor.getNumber();

        context.fill(0, 0, this.width, barHeight, bgColor);
        context.fill(0, 0, (int) Math.round(this.width * progress), barHeight, progressColor);
    }

    private static final int SPECTRUM_BAR_COUNT = 64;

    private void renderSpectrum(GuiGraphics g) {
        MusicPlayer.INSTANCE.audioSpectrum.update();
        float[] spectrumBars = MusicPlayer.INSTANCE.audioSpectrum.getSpectrum(SPECTRUM_BAR_COUNT);
        int barCount = spectrumBars.length;

        int leftWidth = this.width / 2;
        int imageSize = Math.max(96, Math.min(120, leftWidth - 60));
        int imageX = (leftWidth - imageSize) / 2;
        int imageY = (this.height - imageSize) / 2 - 8;
        float centerX = imageX + imageSize / 2f;
        float centerY = imageY + imageSize / 2f;

        float radiusInner = imageSize / 2f + 8f;
        float radiusOuter = radiusInner + 38f;
        float maxBarLength = radiusOuter - radiusInner;

        float angleStep = 360f / barCount;
        float spanDegrees = angleStep * 0.85f;

        GuiGraphicsAccessor accessor = (GuiGraphicsAccessor) g;
        Matrix3x2fStack matrix = g.pose();

        for (int i = 0; i < barCount; i++) {
            float value = spectrumBars[i];

            accessor.getGuiRenderState().submitGuiElement(
                    new SpectrumQuadRenderState(
                            RenderPipelines.GUI,
                            TextureSetup.noTexture(),
                            new Matrix3x2f(matrix),
                            i, value, centerX, centerY, radiusInner, maxBarLength, angleStep, spanDegrees
                    )
            );
        }
    }

    public record SpectrumQuadRenderState(
            RenderPipeline pipeline,
            TextureSetup textureSetup,
            Matrix3x2f pose,
            int index, float value, float centerX, float centerY, float radiusInner, float maxBarLength,
            float angleStep, float spanDegrees,
            ScreenRectangle scissorArea,
            ScreenRectangle bounds
    ) implements GuiElementRenderState {

        public SpectrumQuadRenderState(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2f pose, int index, float value, float centerX, float centerY, float radiusInner, float maxBarLength, float angleStep, float spanDegrees) {
            this(pipeline, textureSetup, pose, index, value, centerX, centerY, radiusInner, maxBarLength, angleStep, spanDegrees, null,
                    new ScreenRectangle((int) (centerX - (radiusInner + maxBarLength) - 10), (int) (centerY - (radiusInner + maxBarLength) - 10), (int) ((radiusInner + maxBarLength) * 2 + 20), (int) ((radiusInner + maxBarLength) * 2 + 20)));
        }

        @Override
        public void buildVertices(VertexConsumer vertexConsumer) {
            float val = Float.isNaN(this.value) ? 0f : this.value;

            float dynamicScale = (float) (1.3f * Math.log10(1.0 + val * 60.0));

            float barLength = dynamicScale * (this.maxBarLength / 1.5f);
            barLength = Math.max(2f, Math.min(barLength, this.maxBarLength));

            float currentOuterRadius = this.radiusInner + barLength;

            float alphaFactor = 0.35f + 0.65f * Math.min(dynamicScale, 1.0f);
            int a = (int) (alphaFactor * 255f);
            int r = 150, green = 200, b = 255;
            int color = (a << 24) | (r << 16) | (green << 8) | b;

            float centerAngle = (this.index * this.angleStep) - 90f;
            float a1 = (float) Math.toRadians(centerAngle - this.spanDegrees / 2f);
            float a2 = (float) Math.toRadians(centerAngle + this.spanDegrees / 2f);

            float cos1 = (float) Math.cos(a1);
            float sin1 = (float) Math.sin(a1);
            float cos2 = (float) Math.cos(a2);
            float sin2 = (float) Math.sin(a2);

            float x1 = this.centerX + cos1 * this.radiusInner;
            float y1 = this.centerY + sin1 * this.radiusInner;
            float x2 = this.centerX + cos1 * currentOuterRadius;
            float y2 = this.centerY + sin1 * currentOuterRadius;
            float x3 = this.centerX + cos2 * currentOuterRadius;
            float y3 = this.centerY + sin2 * currentOuterRadius;
            float x4 = this.centerX + cos2 * this.radiusInner;
            float y4 = this.centerY + sin2 * this.radiusInner;

            vertexConsumer.addVertexWith2DPose(this.pose(), x1, y1).setColor(color);
            vertexConsumer.addVertexWith2DPose(this.pose(), x4, y4).setColor(color);
            vertexConsumer.addVertexWith2DPose(this.pose(), x3, y3).setColor(color);
            vertexConsumer.addVertexWith2DPose(this.pose(), x2, y2).setColor(color);
        }
    }
}
